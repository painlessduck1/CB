// MainActivity.kt
// Full browser UI — address bar, navigation, cookie export

package com.dj.cookiebrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        addressBar = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        setupWebView()
        setupAddressBar()
        setupSwipeRefresh()

        webView.loadUrl("https://accounts.google.com")
    }

    private fun setupWebView() {
        val settings = webView.settings

        settings.userAgentString = RequestInterceptor.CHROME_UA
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = BrowserWebViewClient(
            context = this,
            onPageStarted = { url ->
                addressBar.setText(url)
                progressBar.visibility = android.view.View.VISIBLE
            },
            onPageFinished = { url ->
                addressBar.setText(url)
                progressBar.visibility = android.view.View.GONE
                swipeRefresh.isRefreshing = false
                title = webView.title
            }
        )

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun setupAddressBar() {
        addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val input = addressBar.text.toString().trim()
                val url = if (input.startsWith("http://") || input.startsWith("https://")) {
                    input
                } else if (input.contains(".") && !input.contains(" ")) {
                    "https://$input"
                } else {
                    "https://www.google.com/search?q=${Uri.encode(input)}"
                }
                webView.loadUrl(url)
                true
            } else false
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.browser_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cookies -> {
                showCookies()
                true
            }
            R.id.action_refresh -> {
                webView.reload()
                true
            }
            R.id.action_home -> {
                webView.loadUrl("https://accounts.google.com")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCookies() {
        val url = webView.url ?: return
        val cookies = CookieExporter.exportRaw(url)
        val netscape = CookieExporter.exportForUrl(url)

        AlertDialog.Builder(this)
            .setTitle("Cookies — ${extractDomain(url)}")
            .setMessage(cookies)
            .setPositiveButton("Copy Raw") { _, _ ->
                copyToClipboard(cookies)
            }
            .setNeutralButton("Copy Netscape") { _, _ ->
                copyToClipboard(netscape)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("cookies", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun extractDomain(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) { url }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
