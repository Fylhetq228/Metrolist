package com.metrolist.music.geras.net

import android.content.Context
import android.content.SharedPreferences
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * "Bypass throttling without VPN": a tiny HTTP CONNECT proxy on 127.0.0.1 that only this app
 * uses. It forwards traffic untouched except for the very first TLS message (ClientHello),
 * which carries the site name in clear text. That message is cut into two TLS records right
 * inside the host name and sent as several TCP segments, so DPI boxes that look for
 * "googlevideo.com" in one packet do not recognise it. No server, no VPN, nothing leaves the
 * phone except the normal connections. Same idea as ByeDPI / zapret.
 */
object GerasDpi {
    private var prefs: SharedPreferences? = null
    @Volatile private var server: ServerSocket? = null

    private val pool = Executors.newCachedThreadPool(
        ThreadFactory { r -> Thread(r, "geras-dpi").apply { isDaemon = true } },
    )

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("geras_net", Context.MODE_PRIVATE)
    }

    /** 0 = direct, 1 = built-in bypass (no VPN), 2 = the user's own HTTP/SOCKS5 proxy. */
    var mode: Int
        get() = prefs?.let { p -> if (p.contains("mode")) p.getInt("mode", 0) else if (p.getBoolean("dpi", false)) 1 else 0 } ?: 0
        set(v) {
            prefs?.edit()?.putInt("mode", v)?.apply()
        }

    /** Kept for older callers: true when any proxying is on. */
    var enabled: Boolean
        get() = mode != 0
        set(v) {
            mode = if (v) 1 else 0
        }

    /** The user's own proxy. type 0 = HTTP, 1 = SOCKS5. */
    data class Custom(val type: Int, val host: String, val port: Int, val user: String, val pass: String) {
        val valid get() = host.isNotBlank() && port in 1..65535
    }

    var custom: Custom
        get() = prefs?.let { p ->
            Custom(p.getInt("px_type", 1), p.getString("px_host", "").orEmpty(), p.getInt("px_port", 1080), p.getString("px_user", "").orEmpty(), p.getString("px_pass", "").orEmpty())
        } ?: Custom(1, "", 1080, "", "")
        set(c) {
            prefs?.edit()?.putInt("px_type", c.type)?.putString("px_host", c.host.trim())?.putInt("px_port", c.port)
                ?.putString("px_user", c.user)?.putString("px_pass", c.pass)?.apply()
        }

    /** Which services go through the proxy: "yt", "sc", "other". */
    fun service(key: String): Boolean = prefs?.getBoolean("svc_$key", true) ?: true

    fun setService(key: String, on: Boolean) {
        prefs?.edit()?.putBoolean("svc_$key", on)?.apply()
    }

    private val ytHosts = listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com", "googleapis.com", "youtu.be", "googleusercontent.com", "google.com")
    private val scHosts = listOf("soundcloud.com", "sndcdn.com", "soundcloud.cloud")

    internal fun serviceOf(host: String): String {
        val h = host.lowercase()
        fun under(list: List<String>) = list.any { h == it || h.endsWith(".$it") }
        return when {
            under(ytHosts) -> "yt"
            under(scHosts) -> "sc"
            else -> "other"
        }
    }

    /**
     * 0 = disorder: the first piece is sent with TTL 1 so it dies on the way and is resent later,
     *     the filter sees the pieces out of order (the strongest, like ByeDPI --disorder);
     * 1 = TLS record split + TCP split; 2 = TCP split only.
     */
    var method: Int
        get() = prefs?.getInt("method2", 0) ?: 0
        set(v) {
            prefs?.edit()?.putInt("method2", v)?.apply()
        }

    /**
     * Installs a global ProxySelector, so every OkHttp/Ktor/HttpURLConnection client in the
     * process goes through the bypass while it is on, and directly while it is off. Switching
     * takes effect for new connections immediately, no restart.
     */
    fun install() {
        val previous = java.net.ProxySelector.getDefault()
        if (previous is Selector) return
        java.net.ProxySelector.setDefault(Selector(previous))
    }

    private class Selector(private val fallback: java.net.ProxySelector?) : java.net.ProxySelector() {
        override fun select(uri: java.net.URI?): MutableList<Proxy> {
            val host = uri?.host.orEmpty()
            val local = host == "localhost" || host.startsWith("127.") || host == "::1"
            val scheme = uri?.scheme?.lowercase()
            if (!local && (scheme == "https" || scheme == "http" || scheme == "socket") && service(serviceOf(host))) proxy?.let { return mutableListOf(it) }
            return fallback?.select(uri) ?: mutableListOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
            fallback?.connectFailed(uri, sa, ioe)
        }
    }

    /** The proxy to give HTTP clients, or null when the bypass is off. Starts it on first use. */
    val proxy: Proxy?
        get() {
            val m = mode
            if (m == 0 || (m == 2 && !custom.valid)) return null
            val s = server ?: synchronized(this) { server ?: start() } ?: return null
            return Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLoopbackAddress(), s.localPort))
        }

    private fun start(): ServerSocket? = runCatching {
        val ss = ServerSocket(0, 64, InetAddress.getLoopbackAddress())
        server = ss
        Thread({
            while (!ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: continue
                pool.execute { handle(client) }
            }
        }, "geras-dpi-accept").apply { isDaemon = true }.start()
        ss
    }.getOrNull()

    private fun handle(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            val input = BufferedInputStream(client.getInputStream())
            val head = readHead(input) ?: return
            val firstLine = head.substringBefore("\r\n")
            val parts = firstLine.split(' ')
            if (parts.size < 3) return
            val out = client.getOutputStream()
            if (parts[0].equals("CONNECT", true)) {
                val host = parts[1].substringBeforeLast(':')
                val port = parts[1].substringAfterLast(':').toIntOrNull() ?: 443
                upstream = connect(host, port)
                out.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                out.flush()
                val buf = ByteArray(16 * 1024)
                val n = input.read(buf)
                if (n <= 0) return
                val first = buf.copyOf(n)
                val up = upstream.getOutputStream()
                if (mode == 1 && first[0] == 0x16.toByte() && n > 5) sendDesync(upstream, up, first, host) else {
                    up.write(first)
                    up.flush()
                }
                pipe(input, out, upstream)
            } else {
                // Plain HTTP through the proxy: rewrite the absolute URL to a path and forward.
                val url = java.net.URI(parts[1])
                val port = if (url.port > 0) url.port else 80
                upstream = connect(url.host, port)
                val path = (url.rawPath ?: "/").ifEmpty { "/" } + (url.rawQuery?.let { "?$it" } ?: "")
                val rest = head.substringAfter("\r\n")
                val up = upstream.getOutputStream()
                up.write("${parts[0]} $path ${parts[2]}\r\n$rest".toByteArray())
                up.flush()
                pipe(input, out, upstream)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { client.close() }
            runCatching { upstream?.close() }
        }
    }

    /** Test hook: lets unit tests reach the network through their own gateway. */
    @Volatile internal var upstreamConnector: ((String, Int) -> Socket)? = null

    private fun connect(host: String, port: Int): Socket {
        upstreamConnector?.let { return it(host, port) }
        val c = custom
        if (mode == 2 && c.valid) return if (c.type == 0) viaHttp(c, host, port) else viaSocks(c, host, port)
        return Socket(Proxy.NO_PROXY).apply {
            tcpNoDelay = true
            connect(InetSocketAddress(host, port), 12_000)
            soTimeout = 0
        }
    }

    private fun rawTo(c: Custom): Socket = Socket(Proxy.NO_PROXY).apply {
        tcpNoDelay = true
        connect(InetSocketAddress(c.host, c.port), 12_000)
        soTimeout = 15_000
    }

    /** CONNECT through the user's HTTP proxy, with Basic auth when a login is set. */
    internal fun viaHttp(c: Custom, host: String, port: Int): Socket {
        val s = rawTo(c)
        try {
            val auth = if (c.user.isNotEmpty()) {
                "Proxy-Authorization: Basic " + android.util.Base64.encodeToString("${c.user}:${c.pass}".toByteArray(), android.util.Base64.NO_WRAP) + "\r\n"
            } else ""
            s.getOutputStream().apply {
                write("CONNECT $host:$port HTTP/1.1\r\nHost: $host:$port\r\n$auth\r\n".toByteArray())
                flush()
            }
            val head = readHead(s.getInputStream()) ?: error("proxy closed")
            val code = head.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull()
            if (code != 200) error("proxy answered $code")
            s.soTimeout = 0
            return s
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
    }

    /** SOCKS5 with optional username/password (RFC 1928/1929); the proxy resolves the name. */
    internal fun viaSocks(c: Custom, host: String, port: Int): Socket {
        val s = rawTo(c)
        try {
            val out = s.getOutputStream()
            val inp = java.io.DataInputStream(s.getInputStream())
            val withAuth = c.user.isNotEmpty()
            out.write(if (withAuth) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
            out.flush()
            if (inp.readUnsignedByte() != 5) error("not socks5")
            when (inp.readUnsignedByte()) {
                0 -> Unit
                2 -> {
                    val u = c.user.toByteArray()
                    val p = c.pass.toByteArray()
                    out.write(byteArrayOf(1, u.size.toByte()) + u + byteArrayOf(p.size.toByte()) + p)
                    out.flush()
                    inp.readUnsignedByte()
                    if (inp.readUnsignedByte() != 0) error("socks login rejected")
                }
                else -> error("socks auth not accepted")
            }
            val h = host.toByteArray()
            out.write(byteArrayOf(5, 1, 0, 3, h.size.toByte()) + h + byteArrayOf((port shr 8).toByte(), port.toByte()))
            out.flush()
            if (inp.readUnsignedByte() != 5) error("bad socks reply")
            val rep = inp.readUnsignedByte()
            if (rep != 0) error("socks connect failed $rep")
            inp.readUnsignedByte()
            val skip = when (inp.readUnsignedByte()) {
                1 -> 4
                4 -> 16
                3 -> inp.readUnsignedByte()
                else -> error("bad socks address")
            }
            inp.readFully(ByteArray(skip + 2))
            s.soTimeout = 0
            return s
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
    }

    /**
     * Checks the current setting the way the app will use it: one request to YouTube and one to
     * SoundCloud. Returns milliseconds per service, or null when that one failed.
     */
    fun check(): Map<String, Long?> = listOf(
        "yt" to "https://www.youtube.com/generate_204",
        "sc" to "https://soundcloud.com/robots.txt",
    ).associate { (key, url) ->
        key to runCatching {
            val t0 = System.nanoTime()
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                if (conn.responseCode !in 200..399) error("http ${conn.responseCode}")
            } finally {
                conn.disconnect()
            }
            (System.nanoTime() - t0) / 1_000_000
        }.getOrNull()
    }

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 16 * 1024) {
            val c = input.read()
            if (c < 0) return null
            sb.append(c.toChar())
            if (sb.endsWith("\r\n\r\n")) return sb.toString()
        }
        return null
    }

    /** Copy both ways until either side closes. */
    private fun pipe(clientIn: InputStream, clientOut: OutputStream, upstream: Socket) {
        val upIn = upstream.getInputStream()
        val upOut = upstream.getOutputStream()
        val back = pool.submit {
            runCatching {
                val b = ByteArray(32 * 1024)
                while (true) {
                    val n = upIn.read(b)
                    if (n < 0) break
                    clientOut.write(b, 0, n)
                    clientOut.flush()
                }
            }
            runCatching { upstream.shutdownInput() }
        }
        runCatching {
            val b = ByteArray(32 * 1024)
            while (true) {
                val n = clientIn.read(b)
                if (n < 0) break
                upOut.write(b, 0, n)
                upOut.flush()
            }
        }
        runCatching { upstream.shutdownOutput() }
        runCatching { back.get() }
    }

    /** Index of the SNI host name bytes inside the ClientHello, or -1. */
    private fun sniOffset(data: ByteArray, host: String): Int {
        val h = host.toByteArray()
        if (h.isEmpty()) return -1
        outer@ for (i in 5..data.size - h.size) {
            for (k in h.indices) if (data[i + k] != h[k]) continue@outer
            return i
        }
        return -1
    }

    private fun sendDesync(socket: Socket, out: OutputStream, data: ByteArray, host: String) {
        val sni = sniOffset(data, host)
        val cut = if (sni > 5) sni + minOf(2, host.length / 2) else 6
        if (method == 0 && disorder(socket, out, data, cut)) return
        val payload: ByteArray = if (method == 1) splitRecord(data, cut) ?: data else data
        val tcpCut = cut
        // Segment 1: one byte. Segment 2: up to the middle of the host name. Segment 3: the rest.
        writeSeg(out, payload, 0, 1)
        writeSeg(out, payload, 1, tcpCut.coerceIn(2, payload.size) - 1)
        if (tcpCut < payload.size) writeSeg(out, payload, tcpCut, payload.size - tcpCut)
    }

    private fun writeSeg(out: OutputStream, b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        out.write(b, off, len)
        out.flush()
        // A short pause keeps the kernel from merging the pieces back into one packet.
        Thread.sleep(3)
    }

    /**
     * Disorder: the head of the ClientHello (up to the middle of the host name) leaves with
     * TTL 1 and is dropped by the first router; the tail goes out normally. The phone's TCP stack
     * then retransmits the head with the normal TTL, so the server gets everything, while the
     * filter on the way only ever saw the tail first. Needs no root: TTL is a plain socket option.
     */
    private fun disorder(socket: Socket, out: OutputStream, data: ByteArray, cut: Int): Boolean {
        val pfd = runCatching { android.os.ParcelFileDescriptor.fromSocket(socket) }.getOrNull() ?: return false
        return try {
            val fd = pfd.fileDescriptor
            val v6 = socket.inetAddress is java.net.Inet6Address
            fun ttl(v: Int) {
                if (v6) {
                    android.system.Os.setsockoptInt(fd, android.system.OsConstants.IPPROTO_IPV6, android.system.OsConstants.IPV6_UNICAST_HOPS, v)
                } else {
                    android.system.Os.setsockoptInt(fd, android.system.OsConstants.IPPROTO_IP, IP_TTL, v)
                }
            }
            val payload = splitRecord(data, cut) ?: data
            ttl(1)
            out.write(payload, 0, cut)
            out.flush()
            Thread.sleep(5)
            ttl(64)
            out.write(payload, cut, payload.size - cut)
            out.flush()
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { pfd.close() }
        }
    }

    private const val IP_TTL = 2

    /** Splits the first TLS record into two records at [cut] (an offset in [data]). */
    private fun splitRecord(data: ByteArray, cut: Int): ByteArray? {
        if (data.size < 6) return null
        val recLen = ((data[3].toInt() and 0xff) shl 8) or (data[4].toInt() and 0xff)
        val recEnd = 5 + recLen
        if (recEnd > data.size || cut <= 5 || cut >= recEnd) return null
        val firstLen = cut - 5
        val secondLen = recLen - firstLen
        val out = ByteArray(data.size + 5)
        // Record 1: header + first part of the handshake.
        out[0] = data[0]; out[1] = data[1]; out[2] = data[2]
        out[3] = (firstLen shr 8).toByte(); out[4] = firstLen.toByte()
        System.arraycopy(data, 5, out, 5, firstLen)
        // Record 2: header + the rest, then anything that followed the original record.
        val h2 = 5 + firstLen
        out[h2] = data[0]; out[h2 + 1] = data[1]; out[h2 + 2] = data[2]
        out[h2 + 3] = (secondLen shr 8).toByte(); out[h2 + 4] = secondLen.toByte()
        System.arraycopy(data, cut, out, h2 + 5, data.size - cut)
        return out
    }
}
