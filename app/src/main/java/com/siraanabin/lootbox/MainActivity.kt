package com.siraanabin.lootbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.siraanabin.lootbox.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var activeTabTag: String? = null
    val sharedUrl = MutableLiveData<String?>()
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Keep vector drawable colors intact: the YouTube play triangle must stay white.
        b.nav.itemIconTintList = null
        b.nav.itemTextColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(getColor(R.color.nav_active), getColor(R.color.muted))
        )
        b.nav.itemRippleColor = ColorStateList.valueOf(Color.TRANSPARENT)

        activeTabTag = savedInstanceState?.getString(STATE_ACTIVE_TAB)
            ?: supportFragmentManager.fragments.firstOrNull {
                it.id == R.id.container && !it.isHidden
            }?.tag

        b.nav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            b.nav.selectedItemId = R.id.nav_home
        } else {
            showTab(b.nav.selectedItemId)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        handleIntent(intent)
        if (savedInstanceState == null) Updater.check(this, true)
    }

    private fun showTab(id: Int) {
        val tag = "lootbox-tab-$id"
        val fm = supportFragmentManager
        val target = fm.findFragmentByTag(tag) ?: createTab(id)
        val current = activeTabTag?.let(fm::findFragmentByTag)
            ?: fm.fragments.firstOrNull { it.id == R.id.container && !it.isHidden }

        if (current === target) {
            activeTabTag = tag
            return
        }

        fm.beginTransaction().setReorderingAllowed(true).apply {
            current?.let { hide(it) }
            if (target.isAdded) show(target) else add(R.id.container, target, tag)
        }.commit()
        activeTabTag = tag
    }

    private fun createTab(id: Int): Fragment = when (id) {
        R.id.nav_youtube -> YoutubeFragment()
        R.id.nav_downloads -> DownloadsFragment()
        R.id.nav_library -> LibraryFragment()
        R.id.nav_settings -> SettingsFragment()
        else -> HomeFragment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ACTIVE_TAB, activeTabTag)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        sharedUrl.value = url
        b.nav.selectedItemId = R.id.nav_home
    }

    fun openTab(id: Int) {
        b.nav.selectedItemId = id
    }

    companion object {
        private const val STATE_ACTIVE_TAB = "active_tab_tag"
    }
}
