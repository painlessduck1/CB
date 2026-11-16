// BrowserWebViewClient.kt
package com.dj.cookiebrowser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.*
import android.widget.Toast

class BrowserWebViewClient(
    private val context: Context,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        return when {
            url.startsWith("intent://") -> handleIntentUri(url, view)
            url.startsWith("market://") -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (e: ActivityNotFoundException) { false }
            }
            url.startsWith("mailto:") -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
                    true
                } catch (e: Exception) { false }
            }
            url.startsWith("tel:") -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(url)))
                    true
                } catch (e: Exception) { false }
            }
            else -> false
        }
    }

    // shouldInterceptRequest removed — OkHttp was breaking auth flows

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
        // Note: anti-detection JS is now injected via addDocumentStartJavaScript
        // in MainActivity (runs before page JS, not after). No evaluateJavascript here.
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            super.onReceivedError(view, request, error)
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: android.net.http.SslError
    ) {
        handler.proceed()
    }

    private fun handleIntentUri(url: String, view: WebView): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved != null) {
                context.startActivity(intent)
            } else {
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (!fallback.isNullOrBlank()) {
                    view.loadUrl(fallback)
                } else {
                    Toast.makeText(context, "Google app not found", Toast.LENGTH_SHORT).show()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
