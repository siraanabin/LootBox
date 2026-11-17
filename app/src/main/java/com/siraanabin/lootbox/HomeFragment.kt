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
        // Swiping on Home must never open another tab.
        b.homePager.isUserInputEnabled = false
        b.homePager.overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onDestroyView() {
        b.homePager.adapter = null
        _b = null
        super.onDestroyView()
    }

    private class HomeSwipeAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = 1
        override fun createFragment(position: Int): Fragment =
            HomeContentFragment()
    }
}
