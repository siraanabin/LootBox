package com.siraanabin.lootbox

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * YouTube tab.
 *  - Feed WebView = main page (pull down to refresh -> new videos).
 *  - Tapping a video opens it in a second "watch" WebView that sits on top (player pinned at the top).
 *  - Back while watching = minimize to a small player; the feed underneath is untouched.
 *  - Tapping a suggestion while watching stays inside the watch WebView; Back still minimizes and
 *    returns to the original main page.
 */
class YoutubeFragment : Fragment() {
    private enum class Mode { HOME, EXPANDED, MINI }

    private var feedWeb: WebView? = null
    private var watchWeb: WebView? = null
    private var swipe: SwipeRefreshLayout? = null
    private var frame: FrameLayout? = null
    private var holder: FrameLayout? = null
    private var miniOverlay: FrameLayout? = null
    private var progress: ProgressBar? = null
    private var searchField: EditText? = null
    private var headerRow: View? = null
    private var searchRow: View? = null
    private var topChromeView: View? = null
    private var fullScreenCustomView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility: Int = 0
    private var feedFallbackAttempted: Boolean = false
    @Volatile private var mode: Mode = Mode.HOME
    private var currentVideoId: String? = null
    private var miniRadiusPx: Float = 0f

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
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        val logo = TextView(ctx).apply {
            text = "▶"
            textSize = 13f
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
            textSize = 22f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            letterSpacing = -0.04f
            setPadding(dp(6), 0, 0, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Search pill (like the YouTube app): tap anywhere on it to search.
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(39, 39, 39))
                cornerRadius = dp(20).toFloat()
            }
            contentDescription = "Search YouTube"
            isClickable = true
            setOnClickListener { openSearch() }
        }
        val searchIcon = android.widget.ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
        }
        pill.addView(searchIcon, LinearLayout.LayoutParams(dp(22), dp(22)))
        header.addView(pill, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(14), 0, 0, 0) })
        headerRow = header
        topChrome.addView(header)

        // Inline search bar (replaces the old popup dialog).
        val sRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(12), 0)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        val backBtn = TextView(ctx).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Close search"
            setOnClickListener { closeSearch() }
        }
        sRow.addView(backBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
        val field = EditText(ctx).apply {
            hint = "Search YouTube"
            setHintTextColor(Color.rgb(170, 170, 170))
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(39, 39, 39))
                cornerRadius = dp(20).toFloat()
            }
            setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    runSearch(v.text?.toString().orEmpty())
                    true
                } else false
            }
        }
        searchField = field
        sRow.addView(field, LinearLayout.LayoutParams(0, dp(40), 1f))
        searchRow = sRow
        topChrome.addView(sRow)

        root.addView(topChrome)

        val fr = FrameLayout(ctx)
        frame = fr

        // Main page (feed) with pull-to-refresh.
        val feed = newWebView("feed")
        feedWeb = feed
        val sw = SwipeRefreshLayout(ctx).apply {
            setProgressBackgroundColorSchemeColor(Color.rgb(39, 39, 39))
            setColorSchemeColors(Color.WHITE)
            setOnChildScrollUpCallback { _, _ -> (feedWeb?.scrollY ?: 0) > 0 }
            setOnRefreshListener {
                refreshFeed()
                postDelayed({ isRefreshing = false }, 8000)
            }
        }
        swipe = sw
        sw.addView(feed, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fr.addView(sw, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val p = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235))
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235))
        }
        progress = p
        fr.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP))

        // Watch layer: full screen when expanded, small floating player when minimized.
        val h = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, miniRadiusPx)
                }
            }
            clipToOutline = true
        }
        val overlay = FrameLayout(ctx).apply {
            visibility = View.GONE
            setOnClickListener { expand() }
        }
        val closeBtn = TextView(ctx).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Close video"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(170, 0, 0, 0))
            }
            setOnClickListener { closeVideo() }
        }
        overlay.addView(closeBtn, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(4), dp(4), 0)
        })
        h.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        miniOverlay = overlay
        holder = h
        fr.addView(h, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(fr, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        if (state == null) {
            feedFallbackAttempted = false
            feed.loadUrl("https://www.youtube.com/")
        }
        return root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(role: String): WebView {
        return WebView(requireContext()).apply {
            setBackgroundColor(Color.rgb(11, 14, 19))
            // Keep WebView on the system-managed rendering path and the default User-Agent.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(DownloadBridge(), "LootBoxBridge")
            webViewClient = makeClient(role)
            webChromeClient = makeChrome(role)
        }
    }

    private fun videoIdOf(url: String?): String? {
        if (url == null) return null
        val uri = Uri.parse(url)
        if (uri.path != "/watch") return null
        return uri.getQueryParameter("v")?.takeIf { VIDEO_ID.matches(it) }
    }

    private fun makeClient(role: String) = object : WebViewClient() {
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

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            super.onReceivedError(view, request, error)
            if (role == "feed" && request.isForMainFrame && !feedFallbackAttempted && isAdded) {
                feedFallbackAttempted = true
                view.post { view.loadUrl("https://www.youtube.com/") }
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            if (isAdded && role == "feed" && !feedFallbackAttempted) {
                feedFallbackAttempted = true
                view.post { view.loadUrl("https://www.youtube.com/") }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (role == "feed") {
                progress?.visibility = View.VISIBLE
                progress?.progress = 8
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (role == "feed") {
                progress?.visibility = View.GONE
                swipe?.isRefreshing = false
            }
            injectYoutubeLayout(view, role)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            val id = videoIdOf(url)
            if (role == "watch") {
                if (id != null) currentVideoId = id
            } else if (id != null) {
                // Safety net: if a video link slipped past the click hook, open it in the watch layer
                // and put the feed back where it was.
                view.post {
                    openVideo(id)
                    if (view.canGoBack()) view.goBack()
                }
            }
        }
    }

    private fun makeChrome(role: String) = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (role != "feed") return
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
            exitFullScreen()
        }
    }

    private fun exitFullScreen() {
        val decor = activity?.window?.decorView as? ViewGroup
        fullScreenCustomView?.let { decor?.removeView(it) }
        fullScreenCustomView = null
        fullScreenCallback?.onCustomViewHidden()
        fullScreenCallback = null
        decor?.systemUiVisibility = previousSystemUiVisibility
        topChromeView?.visibility = if (mode == Mode.EXPANDED) View.GONE else View.VISIBLE
    }

    // ---- video layer: open / expand / minimize / close ----

    private fun openVideo(id: String) {
        if (!isAdded || !VIDEO_ID.matches(id)) return
        val h = holder ?: return
        closeSearch()
        var w = watchWeb
        if (w == null) {
            w = newWebView("watch")
            watchWeb = w
            h.addView(w, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // Stop any feed preview so two videos never play (and sound) at once.
        feedWeb?.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}})", null)
        if (currentVideoId != id) {
            currentVideoId = id
            w.loadUrl("https://m.youtube.com/watch?v=" + id)
        }
        expand()
    }

    private fun expand() {
        val h = holder ?: return
        val w = watchWeb ?: return
        mode = Mode.EXPANDED
        topChromeView?.visibility = View.GONE
        h.visibility = View.VISIBLE
        h.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.scaleX = 1f
        w.scaleY = 1f
        miniOverlay?.visibility = View.GONE
        h.elevation = 0f
        miniRadiusPx = 0f
        h.invalidateOutline()
    }

    private fun minimize() {
        val fr = frame ?: return
        val h = holder ?: return
        val w = watchWeb ?: return
        val fw = fr.width
        if (fw <= 0) return
        mode = Mode.MINI
        topChromeView?.visibility = View.VISIBLE
        val miniW = minOf(dp(210), (fw * 0.56f).toInt())
        val miniH = miniW * 9 / 16
        val scale = miniW.toFloat() / fw
        h.layoutParams = FrameLayout.LayoutParams(miniW, miniH, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(12), dp(12))
        }
        // The page keeps its full-screen size (so the pinned 16:9 player stays intact) and is scaled down;
        // the holder clips everything except the player.
        w.layoutParams = FrameLayout.LayoutParams(fw, maxOf(fr.height, dp(300)))
        w.pivotX = 0f
        w.pivotY = 0f
        w.scaleX = scale
        w.scaleY = scale
        miniOverlay?.visibility = View.VISIBLE
        h.elevation = dp(8).toFloat()
        miniRadiusPx = dp(10).toFloat()
        h.invalidateOutline()
    }

    private fun closeVideo() {
        watchWeb?.loadUrl("about:blank")
        currentVideoId = null
        holder?.visibility = View.GONE
        mode = Mode.HOME
        topChromeView?.visibility = View.VISIBLE
    }

    private fun refreshFeed() {
        val f = feedWeb ?: return
        val path = f.url?.let { Uri.parse(it).path }
        if (path == null || path == "/" || path.isEmpty()) {
            f.loadUrl("https://m.youtube.com/")
        } else {
            f.reload()
        }
    }

    // ---- inline search bar ----

    private fun openSearch() {
        headerRow?.visibility = View.GONE
        searchRow?.visibility = View.VISIBLE
        searchField?.let {
            it.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            it.post { imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
        }
    }

    private fun closeSearch() {
        if (searchRow?.visibility != View.VISIBLE) return
        searchField?.let {
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        searchRow?.visibility = View.GONE
        headerRow?.visibility = View.VISIBLE
    }

    private fun runSearch(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        feedWeb?.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query))
        searchField?.setText("")
        closeSearch()
    }

    private fun injectYoutubeLayout(view: WebView, role: String) {
        val script = """
          (function() {
            var lootboxStyleText = [
                /* Remove YouTube's own chrome and bottom tabs; LootBox provides search/navigation. */
                'ytm-mobile-topbar-renderer, ytm-header, ytm-pivot-bar-renderer, ytm-bottom-navigation-renderer, ytm-mini-guide-renderer, .pivot-bar-wrapper, ytm-miniplayer, ytm-mealbar-promo-renderer, ytm-upsell-dialog-renderer, ytd-masthead, #masthead-container, ytd-mini-guide-renderer { display:none !important; }',
                'ytm-app, #app, ytm-browse, body { padding-top:0 !important; padding-bottom:0 !important; margin-bottom:0 !important; }',
                'ytm-feed-filter-chip-bar-renderer, ytm-chip-cloud-renderer { display:block !important; position:sticky !important; top:0 !important; z-index:5 !important; background:#0f0f0f !important; margin:0 !important; }',
                'ytm-reel-shelf-renderer, ytm-reel-item-renderer, ytm-shorts-lockup-view-model, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts] { display:none !important; }',
                /* Watch page: keep YouTube's in-flow player placeholder; the pinned player itself is fixed by script. */
                'ytm-watch, ytm-watch #player-container-id, ytm-watch #player-container, ytm-watch ytm-player, ytm-watch .player-container { margin-top:0 !important; padding-top:0 !important; }',
                'ytm-watch { min-height:0 !important; }',
                'ytm-watch #player-container-id, ytm-watch #player-container { display:block !important; width:100% !important; max-width:100% !important; aspect-ratio:16/9 !important; }',
                'ytm-watch #player-container-id video, ytm-watch #player-container video { max-width:100% !important; }',
                '.lb-anc { transform:none !important; contain:none !important; filter:none !important; will-change:auto !important; perspective:none !important; }',
                '.lb-pin { top:0 !important; margin-top:0 !important; z-index:2147483000 !important; background:#000 !important; }',
                '.lb-fullw { width:100% !important; max-width:100% !important; min-width:0 !important; flex:1 1 100% !important; float:none !important; margin-right:0 !important; padding-right:0 !important; box-sizing:border-box !important; }',
                'ytm-compact-autoplay-renderer, ytm-compact-autoplay-renderer > * { max-width:100% !important; padding-right:0 !important; margin-right:0 !important; }',
                '.lb-feed button[aria-label*="mute" i], .lb-feed button[aria-label*="aption" i], .lb-feed button[aria-label*="ubtitle" i] { display:none !important; }',
                '.lb-nopr { padding-right:0 !important; margin-right:0 !important; }',
                '.lb-sticky { position:sticky !important; top:0 !important; z-index:2147483000 !important; background:#000 !important; }',
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

                '.lootbox-download { position:absolute !important; right:10px !important; bottom:6px !important; top:auto !important; transform:none !important; width:40px !important; height:40px !important; border:0 !important; border-radius:0 !important; background:transparent !important; padding:0 !important; margin:0 !important; display:flex !important; align-items:center !important; justify-content:center !important; z-index:20 !important; }',
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
            if (window.__lbRole === 'feed') document.documentElement.classList.add('lb-feed');
            function hideShorts() {
              document.querySelectorAll('ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model, ytm-reel-item-renderer, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts]').forEach(function(el) {
                el.style.setProperty('display', 'none', 'important');
              });
            }
            function addDownloadButtons() {
              document.querySelectorAll('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytd-rich-item-renderer').forEach(function(card) {
                if (card.querySelector('.lootbox-download')) return;
                if (card.parentElement && card.parentElement.closest('.lb-has-dl')) return;
                var link = card.querySelector('a[href*="/watch?v="], a[href^="/watch"]');
                if (!link) return;
                var parsed;
                try { parsed = new URL(link.href, location.href); } catch (e) { return; }
                var videoId = parsed.searchParams.get('v');
                if (!videoId) return;
                // The button floats at the bottom-right of the card (under the three-dot menu); nothing is padded,
                // so thumbnails keep their full width.
                card.classList.add('lb-has-dl');
                card.style.setProperty('position', 'relative');
                var button = document.createElement('button');
                button.className = 'lootbox-download';
                button.type = 'button';
                button.setAttribute('aria-label', 'Download video');
                button.appendChild(lbSvgDownload('22'));
                button.addEventListener('click', function(event) {
                  event.preventDefault();
                  event.stopPropagation();
                  if (window.LootBoxBridge && window.LootBoxBridge.download) {
                    window.LootBoxBridge.download('https://www.youtube.com/watch?v=' + encodeURIComponent(videoId));
                  }
                });
                card.appendChild(button);
              });
            }
            function clearPins() {
              document.querySelectorAll('.lb-pin, .lb-sticky, .lb-anc').forEach(function(el) {
                el.classList.remove('lb-pin'); el.classList.remove('lb-sticky'); el.classList.remove('lb-anc');
              });
            }
            function pinWatchPlayer() {
              var v = document.querySelector('ytm-watch video') || document.querySelector('video');
              if (!v) return;
              var w = window.innerWidth, el = v, found = false, chain = [];
              while (el && el !== document.body && el !== document.documentElement) {
                chain.push(el);
                var pos = getComputedStyle(el).position;
                if (pos === 'fixed' || pos === 'sticky') { el.classList.add('lb-pin'); found = true; }
                el = el.parentElement;
              }
              chain.forEach(function(c) { c.classList.add('lb-anc'); });
              if (!found) {
                // Nothing is pinned by YouTube: pin the player-sized wrapper ourselves.
                var box = v;
                while (box.parentElement && box.parentElement !== document.body) {
                  var r = box.parentElement.getBoundingClientRect();
                  if (r.height > w * 0.65 || r.width < w * 0.9) break;
                  box = box.parentElement;
                }
                box.classList.add('lb-sticky');
              }
            }
            function keepWatchPlayerAtTop() {
              var isWatchPage = location.pathname === '/watch' || location.pathname.indexOf('/watch/') === 0;
              if (isWatchPage) {
                var watch = document.querySelector('ytm-watch');
                if (watch) {
                  watch.style.setProperty('margin-top', '0', 'important');
                  watch.style.setProperty('padding-top', '0', 'important');
                }
                pinWatchPlayer();
              } else {
                clearPins();
              }
            }
            function fixWatchWidths() {
              var w = window.innerWidth;
              var imgs = document.querySelectorAll('ytm-watch img');
              for (var n = 0; n < imgs.length; n++) {
                var ir = imgs[n].getBoundingClientRect();
                if (ir.width < w * 0.5 || ir.height < 40) continue;
                var el = imgs[n].parentElement;
                while (el && el !== document.body && el !== document.documentElement) {
                  var r = el.getBoundingClientRect();
                  if (r.width >= w * 0.99) {
                    if (parseFloat(getComputedStyle(el).paddingRight) > 8) el.classList.add('lb-nopr');
                    break;
                  }
                  if (r.left <= 2 && r.width >= w * 0.5) {
                    el.classList.add('lb-fullw');
                    var par = el.parentElement;
                    if (par) {
                      // Hide a leftover side element (for example an empty Autoplay toggle) that keeps the card narrow.
                      for (var i = 0; i < par.children.length; i++) {
                        var sib = par.children[i];
                        if (sib === el) continue;
                        var sr = sib.getBoundingClientRect();
                        if (sr.width > 0 && sr.left >= r.right - 2 && !sib.querySelector('a[href*="/watch?v="]')) {
                          sib.style.setProperty('display', 'none', 'important');
                        }
                      }
                    }
                  }
                  el = el.parentElement;
                }
              }
            }
            // If a thumbnail is still narrow after the fix, show a small red report so the cause can be read from a screenshot.
            function lbDiag() {
              var w = window.innerWidth, old = document.getElementById('lb-debug'), bad = null;
              var imgs = document.querySelectorAll('ytm-watch img');
              for (var n = 0; n < imgs.length && !bad; n++) {
                var ir = imgs[n].getBoundingClientRect();
                if (ir.width >= w * 0.5 && ir.width < w * 0.97 && ir.left <= 2 && ir.height > 80) bad = imgs[n];
              }
              if (!bad) { if (old) old.remove(); return; }
              var t = [], el = bad;
              for (var k = 0; k < 9 && el && el !== document.documentElement; k++) {
                var cs = getComputedStyle(el), rr = el.getBoundingClientRect();
                t.push(el.tagName.toLowerCase() + '.' + String(el.className).slice(0, 28) + ' w=' + Math.round(rr.width) +
                  ' d=' + cs.display + ' pr=' + cs.paddingRight + ' mr=' + cs.marginRight + ' fx=' + String(cs.flex).slice(0, 14) +
                  ' st=' + String(el.getAttribute('style') || '').slice(0, 30));
                el = el.parentElement;
              }
              var box = old || document.createElement('div');
              box.id = 'lb-debug';
              box.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;background:#7f1d1d;color:#fff;font:9px monospace;padding:4px;white-space:pre-wrap;max-height:40vh;overflow:auto';
              box.textContent = 'LB-DEBUG w=' + w + '\n' + t.join('\n');
              if (!box.parentNode) document.body.appendChild(box);
            }
            var lbWidthTimer = null;
            function scheduleFixWidths() {
              if (lbWidthTimer) return;
              lbWidthTimer = setTimeout(function() { lbWidthTimer = null; fixWatchWidths(); setTimeout(lbDiag, 700); }, 350);
            }
            function lbSvgDownload(size) {
              var ns = 'http://www.w3.org/2000/svg';
              var svg = document.createElementNS(ns, 'svg');
              svg.setAttribute('viewBox', '0 0 24 24');
              svg.setAttribute('width', size);
              svg.setAttribute('height', size);
              svg.setAttribute('aria-hidden', 'true');
              svg.style.cssText = 'fill:none;stroke:#fff;stroke-width:2;stroke-linecap:round;stroke-linejoin:round';
              var path = document.createElementNS(ns, 'path');
              path.setAttribute('d', 'M12 3v12m-5-5 5 5 5-5M4 20h16');
              svg.appendChild(path);
              return svg;
            }
            function lbDownloadCurrent() {
              var id = new URLSearchParams(location.search).get('v');
              if (id && window.LootBoxBridge && window.LootBoxBridge.download) {
                window.LootBoxBridge.download('https://www.youtube.com/watch?v=' + encodeURIComponent(id));
              }
            }
            function lbMakeBtn(fab) {
              var btn = document.createElement('button');
              btn.id = 'lb-watch-dl';
              btn.type = 'button';
              btn.setAttribute('aria-label', 'Download video');
              if (fab) {
                btn.setAttribute('data-fab', '1');
                btn.style.cssText = 'position:fixed;top:calc(56.25vw + 10px);right:10px;z-index:2147483000;width:44px;height:44px;border-radius:50%;border:0;padding:0;background:rgba(38,59,203,0.85);display:flex;align-items:center;justify-content:center;';
              } else {
                btn.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;flex:0 0 auto;width:48px;height:48px;margin:0 2px;padding:0;border:0;background:transparent;';
              }
              btn.appendChild(lbSvgDownload('26'));
              btn.addEventListener('click', function(e) {
                e.preventDefault(); e.stopPropagation();
                lbDownloadCurrent();
              });
              return btn;
            }
            function lbFindShare() {
              var cands = document.querySelectorAll('[aria-label*="hare" i]');
              for (var i = 0; i < cands.length; i++) {
                var r = cands[i].getBoundingClientRect();
                if (r.width > 10 && r.height > 10 && r.top > window.innerWidth * 0.56) return cands[i];
              }
              return null;
            }
            // Watch page: add a Download button to the action row (like / dislike / share / download).
            // If that row cannot be found, a small floating button under the player is used instead.
            function addWatchDownload() {
              if (window.__lbRole !== 'watch' || location.pathname !== '/watch') return;
              var old = document.getElementById('lb-watch-dl');
              var share = lbFindShare();
              if (!share) {
                window.__lbDlTries = (window.__lbDlTries || 0) + 1;
                if (window.__lbDlTries > 6 && !old) document.body.appendChild(lbMakeBtn(true));
                return;
              }
              var item = share, row = share.parentElement;
              while (row && row !== document.body && row.querySelectorAll('button, [role="button"]').length < 3) {
                item = row;
                row = row.parentElement;
              }
              if (!row || row === document.body) return;
              if (row.querySelector('[aria-label*="ownload" i]:not(.lootbox-download):not(#lb-watch-dl)')) {
                if (old) old.remove();
                return;
              }
              if (old && old.parentElement === row && !old.getAttribute('data-fab')) return;
              if (old) old.remove();
              row.insertBefore(lbMakeBtn(false), item.nextSibling);
            }
            // Category chips: equal gap, and the whole row starts close to the screen edge.
            function fixChips() {
              var chips = document.querySelectorAll('ytm-chip-cloud-chip-renderer');
              if (!chips.length) return;
              var last = chips[chips.length - 1];
              var host = chips[0].parentElement;
              while (host && !host.contains(last) && host.parentElement) host = host.parentElement;
              if (!host) return;
              host.style.setProperty('display', 'flex', 'important');
              host.style.setProperty('flex-wrap', 'nowrap', 'important');
              host.style.setProperty('justify-content', 'flex-start', 'important');
              host.style.setProperty('gap', '8px', 'important');
              host.style.setProperty('padding', '0 4px', 'important');
              host.style.setProperty('margin', '0', 'important');
              var ox = getComputedStyle(host).overflowX;
              if (ox !== 'auto' && ox !== 'scroll') {
                host.style.setProperty('width', 'max-content', 'important');
                host.style.setProperty('min-width', '100%', 'important');
                host.style.setProperty('box-sizing', 'border-box', 'important');
              }
              var anc = host.parentElement, depth = 0;
              while (anc && anc !== document.body && depth < 4) {
                anc.style.setProperty('padding-left', '0', 'important');
                anc.style.setProperty('padding-right', '0', 'important');
                anc.style.setProperty('margin-left', '0', 'important');
                anc.style.setProperty('margin-right', '0', 'important');
                if (anc.matches('ytm-feed-filter-chip-bar-renderer, ytm-chip-cloud-renderer')) break;
                anc = anc.parentElement;
                depth++;
              }
              for (var i = 0; i < host.children.length; i++) {
                var c = host.children[i];
                var empty = !c.textContent.trim() && !c.querySelector('svg, img, yt-icon');
                if (empty) { c.style.setProperty('display', 'none', 'important'); continue; }
                c.style.setProperty('flex', '0 0 auto', 'important');
                c.style.setProperty('margin', '0', 'important');
              }
              var first = null;
              for (var j = 0; j < chips.length; j++) {
                chips[j].style.setProperty('margin', '0', 'important');
                chips[j].style.setProperty('flex', '0 0 auto', 'important');
                if (!first && chips[j].getBoundingClientRect().width > 0) first = chips[j];
              }
              // One-time measured correction (never repeated, so the row cannot drift).
              var scrolled = false, p = host;
              for (var k = 0; k < 6 && p; k++) { if (p.scrollLeft > 0) scrolled = true; p = p.parentElement; }
              if (first && !scrolled && window.scrollY < 2 && !host.hasAttribute('data-lb-done')) {
                var fr = first.getBoundingClientRect();
                if (fr.width > 0 && fr.left >= 0 && fr.left < 90) {
                  if (fr.left > 7) host.style.setProperty('margin-left', (-(fr.left - 4)) + 'px', 'important');
                  if (fr.top > 4 && fr.top < 60) host.style.setProperty('margin-top', (-(fr.top - 2)) + 'px', 'important');
                  host.setAttribute('data-lb-done', '1');
                }
              }
            }
            function lbBox(v) {
              return v.closest('ytm-inline-player, ytm-video-with-context-renderer, ytm-rich-item-renderer') || (v.parentElement ? v.parentElement.parentElement : null);
            }
            // Feed previews: hide the "tap to unmute" and CC icons on top of the preview video.
            function hidePreviewControls() {
              if (window.__lbRole !== 'feed') return;
              var vids = document.querySelectorAll('video');
              for (var i = 0; i < vids.length; i++) {
                var vr = vids[i].getBoundingClientRect();
                if (vr.height < 50) continue;
                var box = lbBox(vids[i]);
                if (!box) continue;
                var ctr = box.querySelectorAll('button, [role="button"]');
                var top = null;
                for (var j = 0; j < ctr.length; j++) {
                  var b = ctr[j];
                  if (b.classList.contains('lootbox-download')) continue;
                  var r = b.getBoundingClientRect();
                  var hidden = b.getAttribute('data-lb-ph') === '1';
                  if (hidden) continue;
                  if (r.width > 0 && r.left > vr.left + vr.width * 0.7 && r.top >= vr.top && r.top <= vr.top + vr.height * 0.5) {
                    if (!top || r.top < top.r.top) top = { el: b, r: r };
                    b.setAttribute('data-lb-ph', '1');
                  }
                }
                if (top) top.el.setAttribute('data-lb-mutebtn', '1');
                var all = box.querySelectorAll('[data-lb-ph="1"]');
                for (var m = 0; m < all.length; m++) all[m].style.setProperty('display', 'none', 'important');
              }
            }
            // Videos start with sound on (feed previews start muted by default).
            function autoUnmute() {
              if (!window.LootBoxBridge || !window.LootBoxBridge.canUnmute) return;
              hidePreviewControls();
              var allowed = false;
              try { allowed = window.LootBoxBridge.canUnmute(window.__lbRole); } catch (e) { return; }
              if (!allowed) return;
              var vids = document.querySelectorAll('video');
              for (var i = 0; i < vids.length; i++) {
                (function(v) {
                  if (v.paused) return;
                  if (!v.muted && v.volume > 0) return;
                  if (v.__lbSrc !== v.currentSrc) { v.__lbSrc = v.currentSrc; v.__lbTry = 0; }
                  if (v.__lbTry >= 8) return;
                  v.__lbTry++;
                  var scope = lbBox(v) || document;
                  var b = scope.querySelector('[data-lb-mutebtn="1"], [aria-label*="nmute" i]') || document.querySelector('[aria-label*="nmute" i]');
                  if (b) { try { b.click(); } catch (e2) {} }
                  setTimeout(function() {
                    if (v.muted) v.muted = false;
                    if (v.volume === 0) v.volume = 1;
                  }, 250);
                })(vids[i]);
              }
            }
            if (!window.__lbUnmuteTimer) window.__lbUnmuteTimer = setInterval(function() { lbSafe(autoUnmute); }, 800);
            if (!window.__lbDlHook) {
              window.__lbDlHook = true;
              document.addEventListener('click', function(e) {
                if (window.__lbRole !== 'watch') return;
                var b = e.target && e.target.closest ? e.target.closest('button[aria-label*="ownload" i]') : null;
                if (!b || b.id === 'lb-watch-dl' || b.classList.contains('lootbox-download')) return;
                e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
                lbDownloadCurrent();
              }, true);
            }
            function fixFeedGap() {
              if (location.pathname !== '/' || window.scrollY > 2) return;
              var cards = document.querySelectorAll('ytm-browse ytm-rich-item-renderer, ytm-browse ytm-video-with-context-renderer');
              var card = null;
              for (var i = 0; i < cards.length; i++) {
                var r = cards[i].getBoundingClientRect();
                if (r.height > 50 && r.width > window.innerWidth * 0.8) { card = cards[i]; break; }
              }
              if (!card || !card.parentElement) return;
              var chip = document.querySelector('ytm-feed-filter-chip-bar-renderer, ytm-chip-cloud-renderer');
              var cr = chip ? chip.getBoundingClientRect() : null;
              var want = (cr && cr.height > 0) ? cr.bottom : 0;
              var gap = card.getBoundingClientRect().top - want;
              if (gap > 6) {
                var host = card.parentElement;
                var cur = parseFloat(host.getAttribute('data-lb-shift') || '0');
                var next = cur - gap;
                host.setAttribute('data-lb-shift', String(next));
                host.style.setProperty('margin-top', next + 'px', 'important');
              }
            }
            function lbSafe(f) { try { f(); } catch (e) {} }
            function applyLootBoxFeed() {
              lbSafe(ensureLootBoxStyle); lbSafe(hideShorts); lbSafe(addDownloadButtons);
              if (window.__lbRole === 'watch') {
                lbSafe(keepWatchPlayerAtTop); lbSafe(scheduleFixWidths); lbSafe(addWatchDownload);
              } else {
                lbSafe(fixFeedGap); lbSafe(fixChips);
              }
            }
            applyLootBoxFeed();
            if (!window.__lootboxYoutubeObserver) {
              window.__lootboxYoutubeObserver = new MutationObserver(applyLootBoxFeed);
              window.__lootboxYoutubeObserver.observe(document.documentElement, { childList:true, subtree:true });
            }
            if (!window.__lbClickHook) {
              window.__lbClickHook = true;
              document.addEventListener('click', function(e) {
                if (window.__lbRole !== 'feed') return;
                var t = e.target;
                if (!t || !t.closest || t.closest('.lootbox-download')) return;
                var a = t.closest('a[href*="/watch"]');
                if (!a) return;
                var u;
                try { u = new URL(a.href, location.href); } catch (err) { return; }
                var id = u.searchParams.get('v');
                if (!id || !window.LootBoxBridge || !window.LootBoxBridge.openVideo) return;
                e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
                window.LootBoxBridge.openVideo(id);
              }, true);
            }
            if (!window.__lootboxYoutubeUrlTimer) {
              var lastUrl = location.href;
              window.__lootboxYoutubeUrlTimer = setInterval(function() {
                if (location.href !== lastUrl) { lastUrl = location.href; applyLootBoxFeed(); }
              }, 400);
            }
          })();
        """.trimIndent()
        view.evaluateJavascript("window.__lbRole='" + role + "';\n" + script, null)
    }

    private inner class DownloadBridge {
        @JavascriptInterface
        fun openVideo(id: String) {
            activity?.runOnUiThread { this@YoutubeFragment.openVideo(id) }
        }

        /** The watch video may always un-mute itself; feed previews only while no video is open. */
        @JavascriptInterface
        fun canUnmute(role: String): Boolean = role == "watch" || mode == Mode.HOME

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
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullScreenCustomView != null -> exitFullScreen()
                    searchRow?.visibility == View.VISIBLE -> closeSearch()
                    mode == Mode.EXPANDED -> minimize()
                    else -> {
                        val f = feedWeb
                        if (f != null && f.canGoBack()) f.goBack() else {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        for (w in listOf(feedWeb, watchWeb)) {
            w?.apply {
                stopLoading()
                removeJavascriptInterface("LootBoxBridge")
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
        }
        fullScreenCustomView = null
        fullScreenCallback = null
        feedWeb = null
        watchWeb = null
        swipe = null
        frame = null
        holder = null
        miniOverlay = null
        progress = null
        searchField = null
        headerRow = null
        searchRow = null
        topChromeView = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
