package com.siraanabin.lootbox

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import android.widget.HorizontalScrollView
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
    private var auditReportText: TextView? = null
    private var auditReportPanel: View? = null
    private var latestAuditReport: String = ""

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

        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(12), dp(2), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun makeChip(label: String, selected: Boolean, iconOnly: Boolean = false): TextView {
            return TextView(ctx).apply {
                text = label
                textSize = if (iconOnly) 19f else 14f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (selected) Color.WHITE else Color.rgb(39, 39, 39))
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(if (iconOnly) dp(13) else dp(18), 0, if (iconOnly) dp(13) else dp(18), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
                ).apply { marginEnd = dp(8) }
                minWidth = if (iconOnly) dp(44) else dp(54)
            }
        }
        val explore = makeChip("◎", false, true).apply {
            contentDescription = "Explore"
            setOnClickListener { wvSafeLoad("https://m.youtube.com/feed/explore") }
        }
        chipRow.addView(explore)
        val categories = listOf("All", "News", "Music", "User interface")
        val chipViews = mutableListOf<TextView>()
        categories.forEachIndexed { index, category ->
            val chip = makeChip(category, index == 0)
            chip.setOnClickListener {
                chipViews.forEachIndexed { i, view ->
                    val active = i == index
                    view.setTextColor(if (active) Color.BLACK else Color.WHITE)
                    view.background = GradientDrawable().apply {
                        setColor(if (active) Color.WHITE else Color.rgb(39, 39, 39))
                        cornerRadius = dp(10).toFloat()
                    }
                }
                when (category) {
                    "All" -> webView?.loadUrl("https://m.youtube.com/")
                    else -> webView?.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(category))
                }
            }
            chipViews.add(chip)
            chipRow.addView(chip)
        }
        chipScroll.addView(chipRow)
        topChrome.addView(chipScroll)
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

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Log.d("LootBoxDomAudit", "[JS] ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
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

        // Debug-only DOM audit overlay: stays inside the YouTube screen and can be copied.
        val auditPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 29, 39))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(75, 85, 99))
            }
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }
        val auditHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val auditTitle = TextView(ctx).apply {
            text = "DOM GAP AUDIT"
            textSize = 12f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(147, 197, 253))
        }
        auditHeader.addView(auditTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val copyButton = TextView(ctx).apply {
            text = "Copy"
            textSize = 13f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(37, 99, 235))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LootBox DOM audit", latestAuditReport))
                text = "Copied"
                postDelayed({ text = "Copy" }, 1200)
            }
        }
        auditHeader.addView(copyButton)
        val rerunButton = TextView(ctx).apply {
            text = "Re-run"
            textSize = 13f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(55, 65, 81))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                visibility = View.VISIBLE
                auditReportPanel?.visibility = View.VISIBLE
                auditReportText?.text = "Waiting 5 seconds for feed…"
                webView?.evaluateJavascript("window.__lootboxRunDomAudit && window.__lootboxRunDomAudit(true);", null)
            }
        }
        val hideButton = TextView(ctx).apply {
            text = "Hide"
            textSize = 13f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(75, 85, 99))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { auditReportPanel?.visibility = View.GONE }
        }
        auditHeader.addView(rerunButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        auditHeader.addView(hideButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        auditPanel.addView(auditHeader)

        val auditScroll = android.widget.ScrollView(ctx).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            setPadding(0, dp(6), 0, 0)
        }
        val auditText = TextView(ctx).apply {
            text = "Waiting for Home feed DOM report…"
            textSize = 11f
            setTextColor(Color.rgb(229, 231, 235))
            setTextIsSelectable(true)
            setPadding(0, 0, dp(4), dp(4))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        auditReportText = auditText
        auditScroll.addView(auditText, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        auditPanel.addView(auditScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(190)
        ))
        auditReportPanel = auditPanel
        frame.addView(auditPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ).apply {
            leftMargin = dp(8)
            rightMargin = dp(8)
            topMargin = dp(8)
        })

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
            if (!document.getElementById('lootbox-youtube-style')) {
              var style = document.createElement('style');
              style.id = 'lootbox-youtube-style';
              style.textContent = [
                /* Remove YouTube's own chrome and bottom tabs; LootBox provides search/navigation. */
                'ytm-mobile-topbar-renderer, ytm-header, ytm-pivot-bar-renderer, ytm-bottom-navigation-renderer, ytm-mini-guide-renderer, ytd-masthead, #masthead-container, ytd-mini-guide-renderer, ytm-chip-cloud-renderer, ytm-chip-cloud-chip-renderer { display:none !important; }',
                'ytm-app, #app { padding-top:0 !important; }',
                'ytm-pivot-bar-renderer { padding-bottom:0 !important; margin-bottom:0 !important; height:0 !important; min-height:0 !important; }',
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
            function debugDomAudit(force) {
              var isWatchPage = location.pathname === '/watch' || location.pathname.indexOf('/watch/') === 0;
              var auditKey = location.href + '|' + (isWatchPage ? 'watch' : 'home');
              if (force) {
                if (window.__lootboxDomAuditTimer) clearTimeout(window.__lootboxDomAuditTimer);
                window.__lootboxDomAuditTimer = null;
                window.__lootboxDomAuditKey = null;
                window.__lootboxDomAuditAttempt = 0;
              }
              if (window.__lootboxDomAuditKey === auditKey && window.__lootboxDomAuditTimer) return;
              if (window.__lootboxDomAuditKey === auditKey && window.__lootboxDomAuditDone && !force) return;
              window.__lootboxDomAuditKey = auditKey;
              window.__lootboxDomAuditDone = false;
              window.__lootboxDomAuditAttempt = 0;
              var retryHistory = [];

              function getCounts() {
                var counts = {};
                Array.from(document.querySelectorAll('*')).forEach(function(el) {
                  var tag = (el.tagName || '').toLowerCase();
                  if (tag.indexOf('ytm-') === 0 || tag.indexOf('yt-') === 0) {
                    counts[tag] = (counts[tag] || 0) + 1;
                  }
                });
                return counts;
              }

              function findTarget() {
                var anchor = document.querySelector('a[href*="/watch"]');
                if (anchor) return {element:anchor, method:'a[href*="/watch"]'};
                var image = document.querySelector('img[src*="ytimg"]');
                if (image) return {element:image, method:'img[src*="ytimg"]'};
                var card = document.querySelector('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-media-item, yt-lockup-view-model, lockup-view-model');
                if (card) return {element:card, method:'custom video-card fallback'};
                return null;
              }

              function nameOf(el) {
                var cls = typeof el.className === 'string' ? el.className.trim().split(/\s+/)[0] : '';
                return (el.tagName || '') + (el.id ? '#' + el.id : '') + (cls ? '.' + cls : '');
              }

              function describeElement(el) {
                var cs = getComputedStyle(el);
                var rect = el.getBoundingClientRect();
                return nameOf(el) + ' | top=' + (Math.round(rect.top*10)/10) +
                  ' | h=' + (el.offsetHeight || 0) + ' | pt=' + cs.paddingTop +
                  ' | mt=' + cs.marginTop + ' | ' + cs.display + ' | ' + cs.position;
              }

              function makeDomOutline(root) {
                if (!root) return '(ytm-browse and ytm-app not found)';
                var lines = [];
                var skipped = {STYLE:1,SCRIPT:1,SVG:1,PATH:1};
                function walk(el, depth) {
                  if (!el || el.nodeType !== 1 || skipped[el.tagName]) return;
                  if (lines.length >= 120) return;
                  var children = Array.from(el.children || []).filter(function(ch) {
                    return ch.nodeType === 1 && !skipped[ch.tagName];
                  });
                  var h = el.offsetHeight || 0;
                  if (h >= 20 || children.length > 0) {
                    lines.push(new Array(depth + 1).join('  ') + describeElement(el));
                  }
                  if (depth >= 8) return;
                  children.forEach(function(ch) {
                    if (lines.length < 120) walk(ch, depth + 1);
                  });
                }
                walk(root,0);
                return lines.join('\n') || '(no qualifying elements)';
              }

              function buildReport(found) {
                var loader = document.querySelector('#global-loader');
                var loaderInfo = 'not present';
                if (loader) {
                  var lcs = getComputedStyle(loader);
                  loaderInfo = 'present | display=' + lcs.display + ' | opacity=' + lcs.opacity +
                    ' | visibility=' + lcs.visibility + ' | offsetHeight=' + loader.offsetHeight;
                }
                var counts = getCounts();
                var countLines = Object.keys(counts).sort().map(function(tag) { return tag + ':' + counts[tag]; });
                var anchors = Array.from(document.querySelectorAll('a[href*="/watch"]'));
                var images = Array.from(document.querySelectorAll('img[src*="ytimg"]'));
                var videos = document.querySelectorAll('video').length;
                var root = document.querySelector('ytm-browse') || document.querySelector('ytm-app');
                var outline = makeDomOutline(root);
                var base = 'PAGE: ' + (isWatchPage ? 'WATCH' : 'HOME') +
                  '\nURL: ' + location.href +
                  '\ndocument.readyState: ' + document.readyState +
                  '\n#global-loader: ' + loaderInfo +
                  '\nCustom tags (' + countLines.length + ' types):\n' + (countLines.length ? countLines.join('\n') : '(none)') +
                  '\n\na[href*="/watch"]: ' + anchors.length +
                  '\nimg[src*="ytimg"]: ' + images.length +
                  '\nvideo: ' + videos +
                  '\n\nDOM OUTLINE (root=' + (root ? root.tagName.toLowerCase() : 'not found') + ', max depth 8, max 120 lines):\n' + outline;
                if (!found) {
                  return base +
                    '\n\nVideo target: NOT FOUND after 30 seconds' +
                    '\nRetry history (timestamp | a[href*="/watch"] count):\n' +
                    (retryHistory.length ? retryHistory.join('\n') : '(no retries recorded)');
                }

                var target = found.element;
                var targetRect = target.getBoundingClientRect();
                var chain = [];
                var node = target;
                while (node && node.nodeType === 1) {
                  var cs = getComputedStyle(node);
                  var rect = node.getBoundingClientRect();
                  chain.push({
                    tagName:node.tagName,
                    id:node.id || '',
                    className:typeof node.className === 'string' ? node.className : '',
                    top:Math.round(rect.top*10)/10,
                    height:node.offsetHeight || 0,
                    paddingTop:cs.paddingTop,
                    marginTop:cs.marginTop,
                    position:cs.position
                  });
                  if (node === document.body) break;
                  node = node.parentElement;
                }
                var emptyAbove = [];
                Array.from(document.querySelectorAll('body *')).forEach(function(el) {
                  if (el === target || target.contains(el) || el.contains(target)) return;
                  var r = el.getBoundingClientRect();
                  var h = el.offsetHeight || 0;
                  if (h < 20 || r.bottom > targetRect.top + 1) return;
                  var hasContent = !!((el.innerText || el.textContent || '').trim()) ||
                    !!el.querySelector('img,video,canvas,iframe,button,input');
                  if (!hasContent) emptyAbove.push({el:el,height:h,top:r.top,bottom:r.bottom});
                });
                emptyAbove.sort(function(a,b) { return b.height-a.height; });
                var gapLines = emptyAbove.slice(0,3).map(function(x,i) {
                  return (i+1) + '. ' + nameOf(x.el) + ' | top=' + Math.round(x.top*10)/10 +
                    ' | bottom=' + Math.round(x.bottom*10)/10 + ' | height=' + x.height + 'px';
                });
                var chainLines = chain.map(function(x,i) {
                  var cls = x.className ? '.' + x.className.trim().split(/\s+/)[0] : '';
                  return (i+1) + '. ' + x.tagName + (x.id ? '#'+x.id : '') + cls +
                    ' | top=' + x.top + ' | h=' + x.height + ' | pt=' + x.paddingTop +
                    ' | mt=' + x.marginTop + ' | position=' + x.position;
                });
                return base +
                  '\n\nVideo target: FOUND (' + found.method + ')' +
                  '\nTagName: ' + target.tagName +
                  '\nTarget rect.top: ' + (Math.round(targetRect.top*10)/10) + 'px' +
                  '\n\nTARGET ANCESTORS (target to body):\n' + chainLines.join('\n') +
                  '\n\nTOP 3 EMPTY GAP CANDIDATES ABOVE TARGET:\n' + (gapLines.length ? gapLines.join('\n') : 'None found');
              }

              function runAttempt() {
                var currentKey = location.href + '|' + ((location.pathname === '/watch' || location.pathname.indexOf('/watch/') === 0) ? 'watch' : 'home');
                if (currentKey !== auditKey) return;
                window.__lootboxDomAuditAttempt = (window.__lootboxDomAuditAttempt || 0) + 1;
                var anchors = document.querySelectorAll('a[href*="/watch"]').length;
                var stamp = new Date().toISOString();
                var found = findTarget();
                if (!found) {
                  retryHistory.push(stamp + ' | a[href*="/watch"]=' + anchors);
                  var waiting = 'PAGE: ' + (isWatchPage ? 'WATCH' : 'HOME') +
                    '\nVideo target: waiting for feed…' +
                    '\nRetry: ' + window.__lootboxDomAuditAttempt + '/6 (up to 30 seconds)' +
                    '\nLatest: ' + stamp + ' | a[href*="/watch"]=' + anchors;
                  if (window.LootBoxBridge && window.LootBoxBridge.debugReport) window.LootBoxBridge.debugReport(waiting);
                  if (window.__lootboxDomAuditAttempt < 6) {
                    window.__lootboxDomAuditTimer = setTimeout(runAttempt,5000);
                    return;
                  }
                }
                window.__lootboxDomAuditTimer = null;
                window.__lootboxDomAuditDone = true;
                var report = buildReport(found);
                console.log('LOOTBOX_DOM_AUDIT ' + report);
                if (window.LootBoxBridge && window.LootBoxBridge.debugReport) window.LootBoxBridge.debugReport(report);
              }

              window.__lootboxRunDomAudit = function(forceRun) { debugDomAudit(!!forceRun); };
              window.__lootboxDomAuditTimer = setTimeout(runAttempt,5000);
            }
            function applyLootBoxFeed() { hideShorts(); addDownloadButtons(); keepWatchPlayerAtTop(); debugDomAudit(false); }
            applyLootBoxFeed();
            if (!window.__lootboxYoutubeObserver) {
              window.__lootboxYoutubeObserver = new MutationObserver(applyLootBoxFeed);
              window.__lootboxYoutubeObserver.observe(document.documentElement, { childList:true, subtree:true });
            }
            if (!window.__lootboxYoutubeUrlTimer) {
              var lastUrl = location.href;
              window.__lootboxYoutubeUrlTimer = setInterval(function() {
                if (location.href !== lastUrl) { lastUrl = location.href; keepWatchPlayerAtTop(); debugDomAudit(false); }
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
        fun debugReport(message: String) {
            if (!isAdded) return
            activity?.runOnUiThread {
                latestAuditReport = message
                auditReportText?.text = message
                auditReportPanel?.visibility = View.VISIBLE
            }
            Log.d("LootBoxDomAudit", message)
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
        auditReportText = null
        auditReportPanel = null
        latestAuditReport = ""
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
