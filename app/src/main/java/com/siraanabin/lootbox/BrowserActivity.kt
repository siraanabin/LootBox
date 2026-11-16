package com.siraanabin.lootbox

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BrowserActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(16, 17, 20))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun action(label: String, onClick: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                minWidth = dp(44)
                minHeight = dp(44)
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { onClick() }
            }
        toolbar.addView(action("‹") { if (web.canGoBack()) web.goBack() else finish() })
        address = EditText(this).apply {
            hint = "Search or enter website"
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.LTGRAY)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundResource(R.drawable.bg_field)
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f))
        toolbar.addView(action("Go") { navigate(address.text.toString()) })
        toolbar.addView(action("↻") { web.reload() })
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF8B82FF.toInt())
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.settings.safeBrowsingEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank()) {
                    address.setText(url)
                    LinkStore.addHistory(this@BrowserActivity, url)
                }
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) {
                progress.progress = value
                progress.visibility = if (value >= 100) View.GONE else View.VISIBLE
            }
        }
        web.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            if (validateUrl(url) == null) {
                DownloadService.add(this, url, "")
                toast(this, "Download added to manager")
            } else toast(this, "This download link is not supported")
        })
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(address.text.toString())
                true
            } else false
        }
        navigate("https://www.google.com/")
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val candidate = if (value.contains(" ") || (!value.contains("://") && !value.contains("."))) {
            "https://www.google.com/search?q=" + Uri.encode(value)
        } else if (!value.contains("://")) "https://$value" else value
        if (validateUrl(candidate) != null) {
            toast(this, "Enter a valid website address")
            return
        }
        web.loadUrl(candidate)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
