package com.siraanabin.lootbox

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

class YoutubeFragment : Fragment() {
    private var webView: WebView? = null
    private var progress: android.widget.ProgressBar? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(16, 17, 20))
        }
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
        }
        val title = TextView(ctx).apply {
            text = "YouTube"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        val refresh = ImageButton(ctx).apply {
            contentDescription = "Refresh YouTube"
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { webView?.reload() }
        }
        bar.addView(title)
        bar.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(bar)

        val frame = FrameLayout(ctx)
        val p = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        progress = p
        frame.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP))
        val wv = WebView(ctx)
        webView = wv
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = true
        wv.settings.javaScriptCanOpenWindowsAutomatically = false
        wv.settings.setSupportMultipleWindows(false)
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.settings.safeBrowsingEnabled = true
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty().lowercase()
                return !(host == "youtube.com" || host.endsWith(".youtube.com") ||
                    host == "youtu.be" || host.endsWith(".googlevideo.com") ||
                    host.endsWith(".ytimg.com") || host.endsWith(".google.com"))
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress?.visibility = View.VISIBLE
                progress?.progress = 8
            }
            override fun onPageFinished(view: WebView, url: String?) {
                progress?.visibility = View.GONE
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress?.progress = newProgress
                if (newProgress >= 100) progress?.visibility = View.GONE
            }
        }
        frame.addView(wv)
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (state == null) wv.loadUrl("https://m.youtube.com/")
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (wv.canGoBack()) wv.goBack() else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        return root
    }

    override fun onDestroyView() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        webView = null
        progress = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
