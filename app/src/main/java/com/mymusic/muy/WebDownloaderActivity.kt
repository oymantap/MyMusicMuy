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
    private lateinit var adapter: WebPagerAdapter // Simpan sebagai variabel class
    
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

        // Inisialisasi adapter
        adapter = WebPagerAdapter(this, urls)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = tabs.size 

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabs[position]
        }.attach()

        // Perbaikan: Ambil fragment yang sedang aktif lewat adapter
        btnResetHome.setOnClickListener {
            val currentFragment = adapter.getFragment(viewPager.currentItem)
            currentFragment?.resetToHome()
        }

        // Perbaikan: Handle Back HP lewat adapter
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = adapter.getFragment(viewPager.currentItem)
                if (currentFragment?.canGoBack() == true) {
                    currentFragment.goBack()
                } else {
                    // Jika tidak bisa back lagi, matikan callback ini dan biarkan sistem menghandle (keluar aplikasi)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // Adapter yang bisa menyimpan referensi Fragment secara otomatis
    private inner class WebPagerAdapter(activity: AppCompatActivity, private val urlList: List<String>) : 
        FragmentStateAdapter(activity) {
        
        private val fragmentMap = mutableMapOf<Int, WebFragment>()

        override fun getItemCount(): Int = urlList.size

        override fun createFragment(position: Int): Fragment {
            val fragment = WebFragment.newInstance(urlList[position])
            fragmentMap[position] = fragment
            return fragment
        }

        // Fungsi pembantu untuk mengambil fragment berdasarkan posisi
        fun getFragment(position: Int): WebFragment? {
            return fragmentMap[position]
        }
    }
}
