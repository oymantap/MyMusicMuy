package com.mymusic.muy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class WebDownloaderActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    // Daftar Judul Tab yang sudah di-samarkan (No Promosi Gratis)
    private val tabs = listOf("Spotify", "YouTube", "YTDown", "SPDown", "Vocalify", "Lainnya")
    
    // URL tetap mengarah ke mesin pencarinya
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

        // Set Adapter
        viewPager.adapter = WebPagerAdapter(this, urls)
        
        // Simpan state 5 tab di latar belakang biar gak reload terus
        viewPager.offscreenPageLimit = 5 

        // Hubungkan Tab dengan ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabs[position]
        }.attach()
    }

    private inner class WebPagerAdapter(activity: AppCompatActivity, private val urlList: List<String>) : 
        FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = urlList.size

        override fun createFragment(position: Int): Fragment {
            return WebFragment.newInstance(urlList[position])
        }
    }
}
