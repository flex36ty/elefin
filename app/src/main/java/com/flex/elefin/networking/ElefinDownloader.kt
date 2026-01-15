package com.flex.elefin.networking

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ElefinDownloader : Downloader() {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class, org.schabi.newpipe.extractor.exceptions.ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder()
            .url(url)

        // Add headers
        for ((key, values) in headers) {
            for (value in values) {
                builder.addHeader(key, value)
            }
        }

        // Handle method and body
        when (httpMethod) {
            "POST" -> {
                val contentType = headers["Content-Type"]?.firstOrNull() ?: "application/octet-stream"
                val body = dataToSend?.toRequestBody(contentType.toMediaTypeOrNull()) 
                    ?: ByteArray(0).toRequestBody(null)
                builder.post(body)
            }
            "HEAD" -> builder.head()
            "GET" -> builder.get() // Default, but explicit is fine
            else -> builder.method(httpMethod, if (dataToSend != null) {
                 val contentType = headers["Content-Type"]?.firstOrNull() ?: "application/octet-stream"
                 dataToSend.toRequestBody(contentType.toMediaTypeOrNull())
            } else null)
        }

        val okResponse = client.newCall(builder.build()).execute()
        
        val responseBody = okResponse.body?.string()
        val responseCode = okResponse.code
        val responseMessage = okResponse.message
        
        // Map OkHttp headers to Map<String, List<String>>
        val responseHeaders = mutableMapOf<String, List<String>>()
        for (i in 0 until okResponse.headers.size) {
            val name = okResponse.headers.name(i)
            val value = okResponse.headers.value(i)
            val list = responseHeaders.getOrPut(name) { mutableListOf() } as MutableList
            list.add(value)
        }

        return Response(
            responseCode,
            responseMessage,
            responseHeaders,
            responseBody,
            okResponse.request.url.toString()
        )
    }
}
