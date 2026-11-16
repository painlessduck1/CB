// RequestInterceptor.kt
// OkHttp layer — strips X-Requested-With, spoofs headers Google reads
// This is what actually kills the WebView fingerprint at the network level

package com.dj.cookiebrowser

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class RequestInterceptor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // Headers WebView adds that expose it as a WebView — strip all of these
        val HEADERS_TO_STRIP = setOf(
            "x-requested-with",
            "x-wap-profile",
            "x-forwarded-for"
        )
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()

        // Only intercept http/https — let everything else pass through
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        // Non-GET requests are complex to re-issue cleanly — skip them
        // POST (login form submits) must go through WebView natively
        if (request.method != "GET") return null

        return try {
            val cookieHeader = CookieManager.getInstance().getCookie(url)

            val builder = Request.Builder().url(url)

            // Copy original headers, stripping the ones that expose WebView
            request.requestHeaders.forEach { (key, value) ->
                if (key.lowercase() !in HEADERS_TO_STRIP) {
                    try { builder.addHeader(key, value) } catch (e: Exception) { }
                }
            }

            // Force Chrome UA and correct sec-ch-ua headers
            builder.header("User-Agent", CHROME_UA)
            builder.header("sec-ch-ua",
                "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
            builder.header("sec-ch-ua-mobile", "?1")
            builder.header("sec-ch-ua-platform", "\"Android\"")
            builder.header("sec-fetch-site", "none")
            builder.header("sec-fetch-mode", "navigate")
            builder.header("sec-fetch-user", "?1")
            builder.header("sec-fetch-dest", "document")

            if (!cookieHeader.isNullOrBlank()) {
                builder.header("Cookie", cookieHeader)
            }

            val response = client.newCall(builder.build()).execute()
            val body = response.body ?: return null
            val contentType = response.header("Content-Type", "text/html") ?: "text/html"
            val mimeType = contentType.split(";")[0].trim()
            val encoding = if (contentType.contains("charset=")) {
                contentType.substringAfter("charset=").trim()
            } else "utf-8"

            // Sync any Set-Cookie headers back into WebView's cookie jar
            response.headers("Set-Cookie").forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
            }

            WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message.ifBlank { "OK" },
                response.headers.toMap().mapValues { it.value },
                body.byteStream()
            )
        } catch (e: IOException) {
            null // let WebView handle it natively on network errors
        }
    }
}
