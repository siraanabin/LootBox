package com.siraanabin.lootbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.siraanabin.lootbox.databinding.FragmentSettingsBinding
import java.io.File

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private var onCookiesPicked: (() -> Unit)? = null

    private val pickCookies =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val ctx = requireContext()
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    File(ctx.filesDir, "cookies.txt").outputStream().use { input.copyTo(it) }
                }
                toast(ctx, "Cookies imported")
                onCookiesPicked?.invoke()
            } catch (e: Exception) {
                toast(requireContext(), "Could not read that file")
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = Prefs.get(requireContext())
        val keys = Prefs.QUALITY_KEYS
        val labels = Prefs.QUALITY_LABELS
        fun showQuality() {
            val k = prefs.getString(Prefs.QUALITY, "best")
            b.qualityValue.text = labels[keys.indexOf(k).coerceAtLeast(0)]
        }
        showQuality()
        b.qualityRow.setOnClickListener {
            val cur = keys.indexOf(prefs.getString(Prefs.QUALITY, "best")).coerceAtLeast(0)
            AlertDialog.Builder(requireContext())
                .setTitle("Video quality")
                .setSingleChoiceItems(labels, cur) { d, which ->
                    prefs.edit().putString(Prefs.QUALITY, keys[which]).apply()
                    showQuality()
                    d.dismiss()
                }.show()
        }

        val activity = requireActivity() as AppCompatActivity
        val cookies = File(requireContext().filesDir, "cookies.txt")
        fun showCookies() {
            b.cookiesValue.text = if (cookies.exists()) "Imported. Tap to replace or remove"
            else "Not set. Tap to import a cookies.txt file"
        }
        showCookies()
        b.cookiesRow.setOnClickListener {
            if (cookies.exists()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Cookies")
                    .setItems(arrayOf("Replace file", "Remove cookies")) { _, which ->
                        if (which == 0) pickCookies.launch(arrayOf("*/*"))
                        else { cookies.delete(); showCookies() }
                    }.show()
            } else pickCookies.launch(arrayOf("*/*"))
        }
        onCookiesPicked = { showCookies() }

        b.swPlaylist.isChecked = prefs.getBoolean(Prefs.PLAYLIST, false)
        b.swPlaylist.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.PLAYLIST, on).apply() }
        b.updateValue.text = "Version ${BuildConfig.VERSION_NAME}. Tap to check"
        b.updateRow.setOnClickListener { Updater.check(activity, false) }
        b.engineRow.setOnClickListener {
            toast(requireContext(), "Updating download engine…")
            Updater.updateEngine(activity)
        }
        b.swMulti.isChecked = prefs.getBoolean(Prefs.MULTI, true)
        b.swWifi.isChecked = prefs.getBoolean(Prefs.WIFI, false)
        b.swNotify.isChecked = prefs.getBoolean(Prefs.NOTIFY, true)
        b.swDark.isChecked = prefs.getBoolean(Prefs.DARK, true)
        b.swMulti.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.MULTI, on).apply() }
        b.swWifi.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.WIFI, on).apply() }
        b.swNotify.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.NOTIFY, on).apply() }
        b.swDark.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(Prefs.DARK, on).apply()
            AppCompatDelegate.setDefaultNightMode(if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onCookiesPicked = null
        _b = null
    }
}
