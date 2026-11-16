package com.siraanabin.lootbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.siraanabin.lootbox.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.homePager.adapter = HomeSwipeAdapter(this)
        b.homePager.offscreenPageLimit = 1
        b.homePager.setPageTransformer { page, position ->
            page.alpha = 1f - (kotlin.math.abs(position) * 0.12f).coerceAtMost(0.12f)
            page.translationX = -position * page.width * 0.035f
        }
    }

    override fun onDestroyView() {
        b.homePager.adapter = null
        _b = null
        super.onDestroyView()
    }

    private class HomeSwipeAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment =
            if (position == 0) HomeContentFragment() else YoutubeFragment()
    }
}
