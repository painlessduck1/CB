// CookieExporter.kt
// Dumps all cookies for current URL — Netscape format, works with curl/requests/etc.

package com.dj.cookiebrowser

import android.webkit.CookieManager

object CookieExporter {

    fun exportForUrl(url: String): String {
        val raw = CookieManager.getInstance().getCookie(url) ?: return "no cookies found"
        val domain = extractDomain(url)

        val sb = StringBuilder()
        sb.appendLine("# Netscape HTTP Cookie File")
        sb.appendLine("# Exported by CookieBrowser")
        sb.appendLine()

        raw.split(";").forEach { pair ->
            val trimmed = pair.trim()
            if (trimmed.isNotEmpty()) {
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val name = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    sb.appendLine("$domain\tTRUE\t/\tFALSE\t0\t$name\t$value")
                }
            }
        }

        return sb.toString()
    }

    fun exportRaw(url: String): String {
        return CookieManager.getInstance().getCookie(url) ?: "no cookies found"
    }

    private fun extractDomain(url: String): String {
        return try {
            val host = java.net.URL(url).host
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) { url }
    }
}
