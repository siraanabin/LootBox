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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

class YoutubeFragment : Fragment() {
    private var webView: WebView? = null
    private var progress: ProgressBar? = null
    private var searchField: EditText? = null
    private var topChromeView: View? = null
    private var fullScreenCustomView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility: Int = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 14, 19))
        }

        // Native YouTube-style header and horizontally scrollable category chips.
        val topChrome = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 15, 15))
        }
        topChromeView = topChrome

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        }
        val logo = TextView(ctx).apply {
            text = "▶"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 0, 0))
                cornerRadius = dp(6).toFloat()
            }
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(30), dp(21)))
        val brand = TextView(ctx).apply {
            text = "YouTube"
            setTextColor(Color.WHITE)
            textSize = 21f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(dp(7), 0, 0, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val searchButton = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            contentDescription = "Search YouTube"
            setOnClickListener { showSearchDialog() }
        }
        header.addView(searchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        topChrome.addView(header)

        // The native 48dp category strip was removed; keep only the header.

        root.addView(topChrome)

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
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
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

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullScreenCustomView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                val decor = requireActivity().window.decorView as? ViewGroup ?: return
                fullScreenCustomView = view
                fullScreenCallback = callback
                previousSystemUiVisibility = decor.systemUiVisibility
                topChromeView?.visibility = View.GONE
                decor.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
                decor.addView(view, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }

            override fun onHideCustomView() {
                val decor = activity?.window?.decorView as? ViewGroup
                fullScreenCustomView?.let { decor?.removeView(it) }
                fullScreenCustomView = null
                fullScreenCallback?.onCustomViewHidden()
                fullScreenCallback = null
                decor?.systemUiVisibility = previousSystemUiVisibility
                if (locationIsWatchPage()) topChromeView?.visibility = View.GONE
                else topChromeView?.visibility = View.VISIBLE
            }
        }
        frame.addView(wv)
        root.addView(frame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        if (state == null) wv.loadUrl("https://m.youtube.com/")
        return root
    }

    private fun showSearchDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Search YouTube"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Search YouTube")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    webView?.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query))
                }
            }
            .show()
        searchField = input
    }

    private fun locationIsWatchPage(): Boolean = webView?.url?.let { it.contains("/watch") } == true

    private fun wvSafeLoad(url: String) {
        webView?.loadUrl(url)
    }

    private fun injectYoutubeLayout(view: WebView) {
        val script = """
          (function() {
            var lootboxStyleText = [
                /* Remove YouTube's own chrome and bottom tabs; LootBox provides search/navigation. */
                'ytm-mobile-topbar-renderer, ytm-header, ytm-pivot-bar-renderer, ytm-bottom-navigation-renderer, ytm-mini-guide-renderer, ytd-masthead, #masthead-container, ytd-mini-guide-renderer, ytm-chip-cloud-renderer, ytm-chip-cloud-chip-renderer { display:none !important; }',
                'ytm-app, #app { padding-top:0 !important; }',
                'ytm-reel-shelf-renderer, ytm-reel-item-renderer, ytm-shorts-lockup-view-model, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts] { display:none !important; }',
                /* Watch page only: reset native YouTube top offsets and preserve the player layout. */
                'ytm-watch, ytm-watch #player-container-id, ytm-watch #player-container, ytm-watch ytm-player, ytm-watch .player-container { margin-top:0 !important; padding-top:0 !important; }',
                'ytm-watch { min-height:0 !important; }',
                'ytm-watch #player-container-id, ytm-watch #player-container { display:block !important; width:100% !important; max-width:100% !important; aspect-ratio:16/9 !important; }',
                'ytm-watch #player-container-id video, ytm-watch #player-container video { max-width:100% !important; }',
                /* Full-width, square-corner video thumbnails. */
                'ytm-browse ytm-rich-item-renderer, ytm-browse ytm-video-with-context-renderer, ytm-browse ytd-rich-item-renderer { display:block !important; box-sizing:border-box !important; width:100% !important; max-width:100% !important; margin:0 0 10px 0 !important; padding:0 !important; }',
                'ytm-thumbnail, ytm-thumbnail a, ytm-thumbnail img, ytd-thumbnail, ytd-thumbnail a, ytd-thumbnail img, .thumbnail, .thumbnail img { border-radius:0 !important; }',
                'ytm-browse ytm-thumbnail, ytm-browse ytm-thumbnail a, ytm-browse ytd-thumbnail, ytm-browse ytd-thumbnail a, ytm-browse .thumbnail { display:block !important; box-sizing:border-box !important; width:100% !important; max-width:100% !important; aspect-ratio:16/9 !important; overflow:hidden !important; margin:0 !important; padding:0 !important; }',
                'ytm-browse ytm-thumbnail img, ytm-browse ytd-thumbnail img, ytm-browse .thumbnail img { display:block !important; width:100% !important; height:100% !important; aspect-ratio:16/9 !important; object-fit:cover !important; object-position:center !important; }',
                /* Remove reserved promo, nudge and shelf space above the first feed item. */
                'ytm-browse ytm-rich-section-renderer, ytm-browse ytm-statement-banner-renderer, ytm-browse ytm-mealbar-promo-renderer, ytm-browse ytm-feed-nudge-renderer, ytm-browse ytm-companion-slot, ytm-browse ytm-promoted-sparkles-web-renderer, ytm-browse ytm-reel-shelf-renderer { display:none !important; height:0 !important; min-height:0 !important; margin:0 !important; padding:0 !important; }',
                'ytm-browse, ytm-browse #contents, ytm-browse ytm-rich-grid-renderer, ytm-browse ytm-rich-grid-renderer #contents, ytm-browse .rich-grid-renderer-contents { margin-top:0 !important; padding-top:0 !important; min-height:0 !important; }',
                'ytm-browse ytm-feed-filter-chip-bar-renderer { margin-bottom:0 !important; padding-bottom:0 !important; }',
                /* Feed cards: no extra borders, padding or background bars. */
                'ytm-rich-item-renderer, ytm-video-with-context-renderer { margin:0 0 10px 0 !important; padding:0 !important; border:0 !important; background:transparent !important; }',
                /* Thumbnail containers fill the available card width at 16:9. */
                'ytm-thumbnail, ytm-thumbnail a, .video-thumbnail-container-large, .video-thumbnail-container-compact { display:block !important; width:100% !important; aspect-ratio:16/9 !important; overflow:hidden !important; background:transparent !important; padding:0 !important; margin:0 !important; }',
                'ytm-thumbnail img, ytm-thumbnail .ytCoreImageHost, ytm-thumbnail yt-core-image img { width:100% !important; height:100% !important; object-fit:cover !important; object-position:center !important; }',

                '.lootbox-download { position:absolute !important; right:10px !important; top:50% !important; transform:translateY(-50%) !important; width:40px !important; height:40px !important; border:0 !important; border-radius:50% !important; background:#263bcb86 !important; color:#2dd4bf !important; display:flex !important; align-items:center !important; justify-content:center !important; z-index:999 !important; padding:0 !important; margin:0 !important; }',
                '.lootbox-download svg { width:22px !important; height:22px !important; fill:none !important; stroke:#ffffff !important; stroke-width:2.2 !important; }'
            ].join('');
            function ensureLootBoxStyle() {
              var target = document.head || document.documentElement;
              if (!target) return;
              var style = document.getElementById('lootbox-youtube-style');
              if (!style) {
                style = document.createElement('style');
                style.id = 'lootbox-youtube-style';
              }
              if (style.textContent !== lootboxStyleText) style.textContent = lootboxStyleText;
              if (style.parentNode !== target) target.appendChild(style);
            }
            ensureLootBoxStyle();
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
              var isWatchPage = location.pathname === '/watch' || location.pathname.indexOf('/watch/') === 0;
              if (window.LootBoxBridge && window.LootBoxBridge.setWatchMode) {
                window.LootBoxBridge.setWatchMode(isWatchPage);
              }
              if (isWatchPage) {
                window.scrollTo(0, 0);
                var watch = document.querySelector('ytm-watch');
                if (watch) {
                  watch.style.setProperty('margin-top', '0', 'important');
                  watch.style.setProperty('padding-top', '0', 'important');
                }
              }
            }
            function applyLootBoxFeed() { ensureLootBoxStyle(); hideShorts(); addDownloadButtons(); keepWatchPlayerAtTop(); }
            applyLootBoxFeed();
            if (!window.__lootboxYoutubeObserver) {
              window.__lootboxYoutubeObserver = new MutationObserver(applyLootBoxFeed);
              window.__lootboxYoutubeObserver.observe(document.documentElement, { childList:true, subtree:true });
            }
            if (!window.__lootboxYoutubeUrlTimer) {
              var lastUrl = location.href;
              window.__lootboxYoutubeUrlTimer = setInterval(function() {
                if (location.href !== lastUrl) { lastUrl = location.href; applyLootBoxFeed(); }
              }, 400);
            }
          })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private inner class DownloadBridge {
        @JavascriptInterface
        fun setWatchMode(isWatching: Boolean) {
            activity?.runOnUiThread {
                topChromeView?.visibility = if (isWatching) View.GONE else View.VISIBLE
            }
        }

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
        fullScreenCustomView = null
        fullScreenCallback = null
        webView = null
        progress = null
        searchField = null
        topChromeView = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
