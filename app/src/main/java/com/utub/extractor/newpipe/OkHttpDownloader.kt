package com.utub.extractor.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** NewPipeExtractor가 사용할 HTTP 클라이언트 구현 */
class OkHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : Downloader() {

    override fun execute(request: Request): Response {
        val bodyBytes = request.dataToSend()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), bodyBytes?.toRequestBody())

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header("User-Agent", USER_AGENT)
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("rate limited", request.url())
            }
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                resp.body?.string(),
                resp.request.url.toString(),
            )
        }
    }

    companion object {
        // 데스크톱 브라우저 UA — NewPipe 관례
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
    }
}
