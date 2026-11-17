package com.siraanabin.lootbox

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

class YoutubeFragment : Fragment() {
    private var webView: WebView? = null
    private var progress: ProgressBar? = null
    private var searchField: EditText? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 14, 19))
        }

        // LootBox search stays at the top; YouTube's own logo/header is hidden in the page.
        val searchBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 34, 43))
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(14), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }
        }
        val searchIcon = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            contentDescription = "Search YouTube"
            isFocusable = false
        }
        searchBar.addView(searchIcon, LinearLayout.LayoutParams(dp(38), dp(44)))
        val input = EditText(ctx).apply {
            hint = "Search YouTube..."
            setHintTextColor(Color.rgb(160, 166, 180))
            setTextColor(Color.WHITE)
            textSize = 16f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setPadding(dp(8), 0, dp(4), 0)
        }
        searchField = input
        searchBar.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        val clear = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.LTGRAY)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            contentDescription = "Clear search"
            setOnClickListener { input.text?.clear() }
        }
        searchBar.addView(clear, LinearLayout.LayoutParams(dp(40), dp(44)))
        root.addView(searchBar)

        val frame = FrameLayout(ctx)
        val p = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235))
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235))
        }
        progress = p
        frame.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP))

        val wv = WebView(ctx).apply {
            setBackgroundColor(Color.rgb(11, 14, 19))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.safeBrowsingEnabled = true
            addJavascriptInterface(DownloadBridge(), "LootBoxBridge")
        }
        webView = wv
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty().lowercase()
                val insideYoutube = host == "youtube.com" || host.endsWith(".youtube.com") ||
                    host == "youtu.be" || host.endsWith(".googlevideo.com") ||
                    host.endsWith(".ytimg.com") || host.endsWith(".google.com") ||
                    host.endsWith(".youtube-nocookie.com")
                if (!insideYoutube && (request.url.scheme == "https" || request.url.scheme == "http")) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))) }
                    return true
                }
                return !insideYoutube
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress?.visibility = View.VISIBLE
                progress?.progress = 8
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress?.visibility = View.GONE
                injectYoutubeLayout(view)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress?.progress = newProgress
                if (newProgress >= 100) progress?.visibility = View.GONE
            }
        }
        frame.addView(wv)
        root.addView(frame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        fun runSearch() {
            val query = input.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                wv.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query))
            }
        }
        searchIcon.setOnClickListener { runSearch() }
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                runSearch()
                true
            } else false
        }

        if (state == null) wv.loadUrl("https://m.youtube.com/")
        return root
    }

    private fun injectYoutubeLayout(view: WebView) {
        val script = """
          (function() {
            if (!document.getElementById('lootbox-youtube-style')) {
              var style = document.createElement('style');
              style.id = 'lootbox-youtube-style';
              style.textContent = [
                /* Remove YouTube's own chrome and bottom tabs; LootBox provides search/navigation. */
                'ytm-mobile-topbar-renderer, ytm-header, ytm-pivot-bar-renderer, ytm-bottom-navigation-renderer, ytm-mini-guide-renderer, ytd-masthead, #masthead-container, ytd-mini-guide-renderer { display:none !important; }',
                'ytm-reel-shelf-renderer, ytm-reel-item-renderer, ytm-shorts-lockup-view-model, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts] { display:none !important; }',
                /* Watch page: keep the player flush to the top of the WebView, directly below LootBox search. */
                'ytm-watch, ytm-watch #player-container-id, ytm-watch #player-container, ytm-watch ytm-player, ytm-watch .player-container, ytm-watch #player { margin-top:0 !important; padding-top:0 !important; }',
                'ytm-watch { min-height:0 !important; }',
                'ytm-watch #player-container-id, ytm-watch #player-container { width:100% !important; max-width:100% !important; }',
                /* Full-width, square-corner video thumbnails. */
                'ytm-rich-item-renderer, ytm-video-with-context-renderer, ytd-rich-item-renderer { display:block !important; box-sizing:border-box !important; width:100vw !important; max-width:none !important; margin-left:calc((100% - 100vw)/2) !important; margin-right:calc((100% - 100vw)/2) !important; padding-left:0 !important; padding-right:0 !important; }',
                'ytm-thumbnail, ytm-thumbnail a, ytm-thumbnail img, ytd-thumbnail, ytd-thumbnail a, ytd-thumbnail img, .thumbnail, .thumbnail img { border-radius:0 !important; }',
                'ytm-thumbnail, ytm-thumbnail a, ytd-thumbnail, ytd-thumbnail a, .thumbnail { display:block !important; width:100vw !important; max-width:100vw !important; }',
                'ytm-thumbnail img, ytd-thumbnail img, .thumbnail img { display:block !important; width:100% !important; height:auto !important; aspect-ratio:16/9 !important; object-fit:cover !important; }',
                '.lootbox-download { position:absolute !important; right:10px !important; top:50% !important; transform:translateY(-50%) !important; width:40px !important; height:40px !important; border:0 !important; border-radius:50% !important; background:#263bcb86 !important; color:#2dd4bf !important; display:flex !important; align-items:center !important; justify-content:center !important; z-index:999 !important; padding:0 !important; margin:0 !important; }',
                '.lootbox-download svg { width:22px !important; height:22px !important; fill:none !important; stroke:#ffffff !important; stroke-width:2.2 !important; }'
              ].join('');
              document.head.appendChild(style);
            }
            function hideShorts() {
              document.querySelectorAll('ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model, ytm-reel-item-renderer, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts]').forEach(function(el) {
                el.style.setProperty('display', 'none', 'important');
              });
            }
            function addDownloadButtons() {
              document.querySelectorAll('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytd-rich-item-renderer').forEach(function(card) {
                var link = card.querySelector('a[href*="/watch?v="], a[href^="/watch"]');
                if (!link || card.querySelector('.lootbox-download')) return;
                var parsed;
                try { parsed = new URL(link.href, location.href); } catch (e) { return; }
                var videoId = parsed.searchParams.get('v');
                if (!videoId) return;
                var info = card.querySelector('ytm-media-item, .media-item-info, .details, #details, .media-channel, ytm-video-meta-block') || card;
                info.style.setProperty('position', 'relative');
                info.style.setProperty('padding-right', '60px');
                var button = document.createElement('button');
                button.className = 'lootbox-download';
                button.type = 'button';
                button.setAttribute('aria-label', 'Download video');
                button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M4 20h16"/></svg>';
                button.addEventListener('click', function(event) {
                  event.preventDefault();
                  event.stopPropagation();
                  if (window.LootBoxBridge && window.LootBoxBridge.download) {
                    window.LootBoxBridge.download('https://www.youtube.com/watch?v=' + encodeURIComponent(videoId));
                  }
                });
                info.appendChild(button);
              });
            }
            function keepWatchPlayerAtTop() {
              if (/\\/watch(?:\\?|$)/.test(location.pathname + location.search)) {
                window.scrollTo(0, 0);
                var watch = document.querySelector('ytm-watch');
                if (watch) {
                  watch.style.setProperty('margin-top', '0', 'important');
                  watch.style.setProperty('padding-top', '0', 'important');
                }
              }
            }
            function applyLootBoxFeed() { hideShorts(); addDownloadButtons(); keepWatchPlayerAtTop(); }
            applyLootBoxFeed();
            if (!window.__lootboxYoutubeObserver) {
              window.__lootboxYoutubeObserver = new MutationObserver(applyLootBoxFeed);
              window.__lootboxYoutubeObserver.observe(document.documentElement, { childList:true, subtree:true });
            }
            if (!window.__lootboxYoutubeUrlTimer) {
              var lastUrl = location.href;
              window.__lootboxYoutubeUrlTimer = setInterval(function() {
                if (location.href !== lastUrl) { lastUrl = location.href; keepWatchPlayerAtTop(); }
              }, 400);
            }
          })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private inner class DownloadBridge {
        @JavascriptInterface
        fun download(url: String) {
            val appContext = context?.applicationContext ?: return
            if (!isAdded || !url.startsWith("https://www.youtube.com/watch")) return
            activity?.runOnUiThread {
                DownloadService.add(appContext, url)
                Toast.makeText(appContext, "Added to Downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val wv = webView ?: return
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (wv.canGoBack()) wv.goBack() else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroyView() {
        webView?.apply {
            stopLoading()
            removeJavascriptInterface("LootBoxBridge")
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        webView = null
        progress = null
        searchField = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
