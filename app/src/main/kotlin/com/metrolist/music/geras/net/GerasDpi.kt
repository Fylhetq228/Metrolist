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

    var enabled: Boolean
        get() = prefs?.getBoolean("dpi", false) ?: false
        set(v) {
            prefs?.edit()?.putBoolean("dpi", v)?.apply()
        }

    /** Where the split happens: 0 = TLS record split + TCP split (default), 1 = TCP split only. */
    var method: Int
        get() = prefs?.getInt("method", 0) ?: 0
        set(v) {
            prefs?.edit()?.putInt("method", v)?.apply()
        }

    /** The proxy to give HTTP clients, or null when the bypass is off. Starts it on first use. */
    val proxy: Proxy?
        get() {
            if (!enabled) return null
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
                if (first[0] == 0x16.toByte() && n > 5) sendDesync(up, first, host) else {
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

    private fun connect(host: String, port: Int): Socket = Socket().apply {
        tcpNoDelay = true
        connect(InetSocketAddress(host, port), 12_000)
        soTimeout = 0
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

    private fun sendDesync(out: OutputStream, data: ByteArray, host: String) {
        val sni = sniOffset(data, host)
        val cut = if (sni > 5) sni + minOf(2, host.length / 2) else 6
        val payload: ByteArray = if (method == 0) splitRecord(data, cut) ?: data else data
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
