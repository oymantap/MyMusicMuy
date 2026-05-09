package com.mymusic.muy

import android.content.*
import android.graphics.Color
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.palette.graphics.Palette
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide

class FullScreenPlayerActivity : AppCompatActivity() {
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var rootLayout: RelativeLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var txtTitle: TextView
    private lateinit var btnPlayPause: ImageButton
    
    private val handler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateUI()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_player)

        rootLayout = findViewById(R.id.rootFSP)
        viewPager = findViewById(R.id.viewPagerFSP)
        txtTitle = findViewById(R.id.fspTitle)
        btnPlayPause = findViewById(R.id.fspPlayPause)

        // Bind ke Service yang sudah jalan
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)

        btnPlayPause.setOnClickListener {
            if (musicService?.togglePlay() == true) {
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play)
            }
        }
        
        startUpdateLoop()
    }

    private fun updateUI() {
        musicService?.let { service ->
            val title = service.getCurrentTitle() ?: "Unknown Title"
            txtTitle.text = title
            txtTitle.isSelected = true // Biar Marquee jalan di FSP

            val art = service.getAlbumArt()
            if (art != null) {
                // SIKAT WARNA DOMINAN PAKE PALETTE API
                Palette.from(art).generate { palette ->
                    val color = palette?.getDominantColor(Color.parseColor("#121212")) ?: Color.BLACK
                    rootLayout.setBackgroundColor(color)
                }
            }
            
            val isPlaying = service.isPlaying()
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    private fun startUpdateLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isBound) updateUI()
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
        handler.removeCallbacksAndMessages(null)
    }
}
