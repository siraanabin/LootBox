package com.siraanabin.lootbox

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.siraanabin.lootbox.databinding.FragmentHomeContentBinding
import android.widget.TextView
import android.view.Gravity
import androidx.core.content.ContextCompat

class HomeContentFragment : Fragment() {

    private var _b: FragmentHomeContentBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeContentBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val activity = requireActivity() as MainActivity

        b.settingsBtn.setOnClickListener { activity.openTab(R.id.nav_settings) }
        b.browserBtn.setOnClickListener { startActivity(Intent(ctx, BrowserActivity::class.java)) }
        b.bookmarkBtn.setOnClickListener {
            val url = b.urlInput.text.toString().trim()
            val error = validateUrl(url)
            if (error != null) {
                b.errorText.text = error
                b.errorText.visibility = View.VISIBLE
            } else {
                LinkStore.addBookmark(ctx, url)
                renderSavedLinks()
                toast(ctx, "Bookmark saved")
            }
        }
        renderSavedLinks()

        b.pasteBtn.setOnClickListener {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(ctx)?.toString()?.trim().orEmpty()
            } else {
                ""
            }
            if (text.isEmpty()) {
                toast(ctx, "Clipboard is empty")
            } else {
                b.urlInput.setText(extractUrl(text))
                b.urlInput.setSelection(b.urlInput.text.length)
            }
        }

        b.downloadBtn.setOnClickListener {
            val url = b.urlInput.text.toString().trim()
            val error = validateUrl(url)
            if (error != null) {
                b.errorText.text = error
                b.errorText.visibility = View.VISIBLE
            } else {
                b.errorText.visibility = View.GONE
                LinkStore.addHistory(ctx, url)
                renderSavedLinks()
                if (isMediaSite(url)) {
                    askQuality(url)
                } else {
                    start(url, "")
                }
            }
        }

        activity.sharedUrl.observe(viewLifecycleOwner) { url ->
            if (url != null) {
                b.urlInput.setText(url)
                activity.sharedUrl.value = null
            }
        }

        DownloadStore.live.observe(viewLifecycleOwner) { list ->
            val last = list.firstOrNull()
            if (last == null) {
                b.latestCard.visibility = View.GONE
            } else {
                b.latestCard.visibility = View.VISIBLE
                b.latestName.text = last.title
                b.latestStatus.text = last.statusText()
                val p = if (last.status == Status.COMPLETED) 100 else last.progress
                b.latestProgress.progress = p
                b.latestPercent.text = if (last.status == Status.FAILED) "" else "$p%"
            }
        }
    }

    private fun renderSavedLinks() {
        val ctx = requireContext()
        fun render(container: android.widget.LinearLayout, urls: List<String>, bookmark: Boolean) {
            container.removeAllViews()
            urls.forEach { url ->
                val row = TextView(ctx).apply {
                    text = url
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundResource(R.drawable.bg_field)
                    isClickable = true
                    setOnClickListener {
                        b.urlInput.setText(url)
                        b.urlInput.setSelection(b.urlInput.text.length)
                    }
                    setOnLongClickListener {
                        LinkStore.remove(ctx, url, bookmark)
                        renderSavedLinks()
                        toast(ctx, if (bookmark) "Bookmark removed" else "History item removed")
                        true
                    }
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
                ).apply { bottomMargin = dp(6) }
                container.addView(row, lp)
            }
        }
        render(b.bookmarksContainer, LinkStore.bookmarks(ctx), true)
        render(b.historyContainer, LinkStore.history(ctx), false)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun start(url: String, quality: String) {
        DownloadService.add(requireContext(), url, quality)
        b.urlInput.setText("")
        toast(requireContext(), "Download started")
    }

    private fun askQuality(url: String) {
        val prefs = Prefs.get(requireContext())
        var sel = Prefs.QUALITY_KEYS.indexOf(prefs.getString(Prefs.QUALITY, "best")).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle("Quality")
            .setSingleChoiceItems(Prefs.QUALITY_LABELS, sel) { _, which -> sel = which }
            .setPositiveButton("Download") { _, _ -> start(url, Prefs.QUALITY_KEYS[sel]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
