package com.mymusic.muy

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class WebDownloaderActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private val tabs = listOf("Spotify", "YouTube", "YTDown", "SPDown", "Vocalify", "Lainnya")
    private val urls = listOf(
        "https://open.spotify.com",
        "https://m.youtube.com",
        "https://v4.www-y2mate.blog/en/youtube-to-mp3",
        "https://spotidown.app",
        "https://lrcstudio.42web.io",
        "https://google.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_downloader)

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        val btnResetHome: ImageButton = findViewById(R.id.btnResetHome)

        viewPager.adapter = WebPagerAdapter(this, urls)
        viewPager.offscreenPageLimit = 5 

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabs[position]
        }.attach()

        // Tombol Panah di Header: Balik ke URL awal tab tersebut
        btnResetHome.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}") as? WebFragment
            currentFragment?.resetToHome()
        }

        // Handle Tombol Back HP
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}") as? WebFragment
                if (currentFragment?.canGoBack() == true) {
                    currentFragment.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private inner class WebPagerAdapter(activity: AppCompatActivity, private val urlList: List<String>) : 
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = urlList.size
        override fun createFragment(position: Int): Fragment = WebFragment.newInstance(urlList[position])
    }
}
