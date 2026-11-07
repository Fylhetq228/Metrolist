package com.metrolist.music.geras.source.soundcloud

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

/** OkHttp transport for NewPipeExtractor. */
class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        @Volatile
        private var initialized = false

        fun ensureInit() {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                val client = OkHttpClient.Builder()
                    .proxy(com.metrolist.music.geras.net.GerasDpi.proxy)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                val locale = Locale.getDefault()
                NewPipe.init(
                    NewPipeDownloader(client),
                    Localization(locale.language, locale.country.ifEmpty { null }),
                    ContentCountry(locale.country.ifEmpty { "US" }),
                )
                initialized = true
            }
        }
    }
}
