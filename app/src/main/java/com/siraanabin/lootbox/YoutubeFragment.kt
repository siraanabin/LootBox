package com.siraanabin.lootbox

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

class YoutubeFragment : Fragment() {
    private var webView: WebView? = null
    private var progress: ProgressBar? = null
    private var topChromeView: View? = null
    private var fullScreenCustomView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemBarsVisible: Boolean = true
    private var backCallback: OnBackPressedCallback? = null
    private var youtubeFallbackAttempted: Boolean = false
    private var rendererRecoveryScheduled: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 14, 19))
        }

        // Vanced-inspired native dark app bar; hide only YouTube's duplicate web header.
        val topChrome = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 15, 15))
        }
        topChromeView = topChrome

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
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
            setOnClickListener { openYoutubeSearch() }
        }
        header.addView(searchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        topChrome.addView(header)
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
            // Keep WebView on the system-managed rendering path; forcing a hardware layer
            // can leave WebView content black on some Android GPU/driver combinations.
            settings.javaScriptEnabled = true
            // Keep Android WebView's default User-Agent; modifying it can break YouTube rendering.
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.safeBrowsingEnabled = true
            // YouTube needs first- and third-party cookies for consent and page sessions.
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
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
                    host.endsWith(".googleapis.com") || host.endsWith(".gstatic.com") ||
                    host.endsWith(".ggpht.com") || host.endsWith(".googleusercontent.com") ||
                    host.endsWith(".youtube-nocookie.com")
                if (!insideYoutube && (request.url.scheme == "https" || request.url.scheme == "http")) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))) }
                    return true
                }
                return !insideYoutube
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && !youtubeFallbackAttempted && isAdded) {
                    youtubeFallbackAttempted = true
                    view.post { view.loadUrl("https://m.youtube.com/") }
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                // A WebView whose renderer exited is permanently unusable. Replace the Fragment's
                // WebView instead of trying to reload the crashed instance (which leaves it black).
                if (isAdded && !rendererRecoveryScheduled) {
                    rendererRecoveryScheduled = true
                    val resumeUrl = view.url
                        ?.takeIf { it.startsWith("https://") && (
                            it.contains("youtube.com") || it.contains("youtu.be")
                        ) }
                        ?: "https://www.youtube.com/"
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        val fm = parentFragmentManager
                        val oldTag = tag ?: "lootbox-tab-${R.id.nav_youtube}"
                        val wasHidden = isHidden
                        val replacement = YoutubeFragment().apply {
                            arguments = Bundle().apply { putString(ARG_INITIAL_URL, resumeUrl) }
                        }
                        val transaction = fm.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(this@YoutubeFragment)
                            .add(R.id.container, replacement, oldTag)
                        if (wasHidden) transaction.hide(replacement)
                        transaction.commitAllowingStateLoss()
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url?.startsWith("https://m.youtube.com") == true || url?.startsWith("https://www.youtube.com") == true) {
                    progress?.visibility = View.VISIBLE
                }
                progress?.visibility = View.VISIBLE
                progress?.progress = 8
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress?.visibility = View.GONE
                // Diagnostic isolation: first verify that an unmodified YouTube page renders.
                // Re-enable the custom CSS/JS only after the plain page is confirmed working.
                injectMinimalYoutubeChrome(view)
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
                previousSystemBarsVisible = ViewCompat.getRootWindowInsets(decor)
                    ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
                topChromeView?.visibility = View.GONE
                WindowInsetsControllerCompat(requireActivity().window, decor).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
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
                decor?.let {
                    val controller = WindowInsetsControllerCompat(requireActivity().window, it)
                    if (previousSystemBarsVisible) controller.show(WindowInsetsCompat.Type.systemBars())
                    else controller.hide(WindowInsetsCompat.Type.systemBars())
                }
                if (locationIsWatchPage()) topChromeView?.visibility = View.GONE
                else topChromeView?.visibility = View.VISIBLE
            }
        }
        frame.addView(wv)
        root.addView(frame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        if (state == null) {
            youtubeFallbackAttempted = false
            rendererRecoveryScheduled = false
            val initialUrl = arguments?.getString(ARG_INITIAL_URL) ?: "https://www.youtube.com/"
            wv.loadUrl(initialUrl)
        }
        return root
    }

    private fun injectMinimalYoutubeChrome(view: WebView) {
        // Keep the native mobile YouTube feed untouched to protect WebView rendering.
        // Only remove the duplicate web header and match the dark Vanced-style surface.
        val script = """
            (function() {
              var css = 'ytm-mobile-topbar-renderer, ytm-header, ytd-masthead, #masthead-container { display:none !important; }' +
                        'html, body, ytm-app, #app { background:#0f0f0f !important; }' +
                        'ytm-bottom-navigation-renderer { display:none !important; }';
              var style = document.getElementById('lootbox-vanced-chrome');
              if (!style) {
                style = document.createElement('style');
                style.id = 'lootbox-vanced-chrome';
                (document.head || document.documentElement).appendChild(style);
              }
              if (style.textContent !== css) style.textContent = css;
              var watching = location.pathname === '/watch' || location.pathname.indexOf('/watch/') === 0;
              if (window.LootBoxBridge && window.LootBoxBridge.setWatchMode) {
                window.LootBoxBridge.setWatchMode(watching);
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun openYoutubeSearch() {
        // Open YouTube's own search UI rather than showing a separate LootBox dialog.
        val script = """
            (function() {
              var selectors = [
                'ytm-searchbox button',
                'ytm-searchbox [role="button"]',
                'button[aria-label="Search"]',
                'button[aria-label*="Search"]',
                'a[aria-label="Search"]',
                'ytm-mobile-topbar-renderer button'
              ];
              for (var i = 0; i < selectors.length; i++) {
                var button = document.querySelector(selectors[i]);
                if (button) { button.click(); return; }
              }
              location.href = 'https://www.youtube.com/results?search_query=';
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
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
                'ytm-mobile-topbar-renderer, ytm-header, ytm-pivot-bar-renderer, ytm-bottom-navigation-renderer, ytm-miniplayer, ytm-mini-guide-renderer, ytd-masthead, #masthead-container, ytd-mini-guide-renderer { display:none !important; }',
                /* Keep YouTube's category chips visible and horizontally scrollable. */
                'ytm-chip-cloud-renderer, ytm-feed-filter-chip-bar-renderer, ytd-feed-filter-chip-bar-renderer { display:block !important; visibility:visible !important; overflow-x:auto !important; overflow-y:hidden !important; white-space:nowrap !important; position:sticky !important; top:0 !important; z-index:12 !important; background:#0f0f0f !important; scrollbar-width:none !important; }',
                'ytm-chip-cloud-renderer #chips, ytm-chip-cloud-renderer [role="list"], ytm-feed-filter-chip-bar-renderer #chips { display:flex !important; flex-wrap:nowrap !important; width:max-content !important; min-width:100% !important; overflow:visible !important; gap:8px !important; }',
                'ytm-chip-cloud-chip-renderer, yt-chip-cloud-chip-renderer, ytm-feed-filter-chip-bar-renderer button, ytd-feed-filter-chip-bar-renderer button { display:inline-flex !important; flex:0 0 auto !important; white-space:nowrap !important; }',
                'ytm-app, #app { padding-top:0 !important; }',
                'ytm-reel-shelf-renderer, ytm-reel-item-renderer, ytm-shorts-lockup-view-model, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts] { display:none !important; }',
                /* Watch page only: reset native YouTube top offsets and preserve the player layout. */
                'ytm-watch, ytd-watch-flexy, ytm-watch #content, ytd-watch-flexy #columns { min-height:0 !important; overflow:visible !important; }',
                'ytm-watch #player-container-id, ytm-watch #player-container, ytm-watch ytm-player, ytm-watch .player-container, ytd-watch-flexy #player-container, ytd-watch-flexy #player-container-inner { position:sticky !important; top:0 !important; z-index:30 !important; display:block !important; box-sizing:border-box !important; width:100% !important; max-width:100% !important; aspect-ratio:16/9 !important; margin:0 !important; padding:0 !important; background:#000 !important; }',
                'ytm-watch #player-container-id video, ytm-watch #player-container video, ytm-watch ytm-player video, ytd-watch-flexy #player-container video { display:block !important; width:100% !important; height:100% !important; max-width:100% !important; object-fit:contain !important; }',
                /* Full-width, square-corner video thumbnails. */
                'ytm-browse ytm-rich-item-renderer, ytm-browse ytm-video-with-context-renderer, ytm-browse ytd-rich-item-renderer { display:block !important; box-sizing:border-box !important; width:100% !important; max-width:100% !important; margin:0 0 10px 0 !important; padding:0 !important; }',
                'ytm-thumbnail, ytm-thumbnail a, ytm-thumbnail img, ytd-thumbnail, ytd-thumbnail a, ytd-thumbnail img, .thumbnail, .thumbnail img { border-radius:0 !important; }',
                'ytm-browse ytm-thumbnail, ytm-browse ytm-thumbnail a, ytm-browse ytd-thumbnail, ytm-browse ytd-thumbnail a, ytm-browse .thumbnail { display:block !important; box-sizing:border-box !important; width:100% !important; max-width:100% !important; aspect-ratio:16/9 !important; overflow:hidden !important; margin:0 !important; padding:0 !important; }',
                'ytm-browse ytm-thumbnail img, ytm-browse ytd-thumbnail img, ytm-browse .thumbnail img { display:block !important; width:100% !important; height:100% !important; aspect-ratio:16/9 !important; object-fit:cover !important; object-position:center !important; }',
                /* Remove reserved promo, nudge and shelf space above the first feed item. */
                'ytm-browse ytm-rich-section-renderer, ytm-browse ytm-statement-banner-renderer, ytm-browse ytm-mealbar-promo-renderer, ytm-browse ytm-feed-nudge-renderer, ytm-browse ytm-companion-slot, ytm-browse ytm-promoted-sparkles-web-renderer, ytm-browse ytm-reel-shelf-renderer { display:none !important; height:0 !important; min-height:0 !important; margin:0 !important; padding:0 !important; }',
                'ytm-browse, ytm-browse #contents, ytm-browse ytm-rich-grid-renderer, ytm-browse ytm-rich-grid-renderer #contents, ytm-browse .rich-grid-renderer-contents { margin-top:0 !important; padding-top:0 !important; min-height:0 !important; }',
                'ytm-browse ytm-feed-filter-chip-bar-renderer, ytm-browse ytm-chip-cloud-renderer { margin-bottom:0 !important; padding-bottom:0 !important; }',
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
            // Avoid writing identical inline styles: MutationObserver watches the whole
            // page, so repeated style writes here can otherwise create an endless loop.
            function setImportantIfChanged(element, property, value) {
              if (!element) return;
              if (element.style.getPropertyValue(property) !== value ||
                  element.style.getPropertyPriority(property) !== 'important') {
                element.style.setProperty(property, value, 'important');
              }
            }
            function hideShorts() {
              document.querySelectorAll('ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model, ytm-reel-item-renderer, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts]').forEach(function(el) {
                setImportantIfChanged(el, 'display', 'none');
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
              if (!isWatchPage) return;

              var player = document.querySelector(
                '#player-container-id, #player-container, ytm-player, ytd-watch-flexy #player-container-inner, #movie_player'
              );
              if (player) {
                var stickyPlayer = player.closest(
                  '#player-container-id, #player-container, ytm-player, #player-container-inner'
                ) || player;
                setImportantIfChanged(stickyPlayer, 'position', 'sticky');
                setImportantIfChanged(stickyPlayer, 'top', '0px');
                setImportantIfChanged(stickyPlayer, 'z-index', '30');
                setImportantIfChanged(stickyPlayer, 'width', '100%');
                setImportantIfChanged(stickyPlayer, 'max-width', '100%');
                setImportantIfChanged(stickyPlayer, 'aspect-ratio', '16 / 9');
                setImportantIfChanged(stickyPlayer, 'background', '#000');
              }
              var watch = document.querySelector('ytm-watch, ytd-watch-flexy');
              if (watch) {
                setImportantIfChanged(watch, 'margin-top', '0px');
                setImportantIfChanged(watch, 'padding-top', '0px');
                setImportantIfChanged(watch, 'overflow', 'visible');
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
        backCallback = object : OnBackPressedCallback(!isHidden) {
            override fun handleOnBackPressed() {
                // Keep edge-back on YouTube Home from closing LootBox or changing tabs.
                if (wv.canGoBack()) wv.goBack()
                // At the YouTube home root, consume the back gesture.
            }
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        backCallback?.isEnabled = !hidden
    }

    override fun onResume() {
        super.onResume()
        backCallback?.isEnabled = !isHidden
    }

    override fun onDestroyView() {
        backCallback = null
        webView?.apply {
            stopLoading()
            removeJavascriptInterface("LootBoxBridge")
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        fullScreenCustomView?.let { custom ->
            (activity?.window?.decorView as? ViewGroup)?.removeView(custom)
        }
        fullScreenCustomView = null
        fullScreenCallback?.onCustomViewHidden()
        fullScreenCallback = null
        webView = null
        progress = null
        topChromeView = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_INITIAL_URL = "youtube_initial_url"
    }
}
