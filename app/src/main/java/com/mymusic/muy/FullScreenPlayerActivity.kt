package com.mymusic.muy

import android.content.*
import android.graphics.Color
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.palette.graphics.Palette
import androidx.viewpager2.widget.ViewPager2

class FullScreenPlayerActivity : AppCompatActivity() {
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var rootLayout: RelativeLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPlayPauseInner: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    
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

        initViews()
        setupListeners()

        // Setup ViewPager dengan Adapter
        viewPager.adapter = FspAdapter(this)

        // Bind ke Service
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        startUpdateLoop()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootFSP)
        viewPager = findViewById(R.id.viewPagerFSP)
        txtTitle = findViewById(R.id.fspTitle)
        txtArtist = findViewById(R.id.fspArtist)
        
        // Handle dua kemungkinan ID ImageButton (kalo lu pake layout modern yang gue kasih tadi)
        btnPlayPause = findViewById(R.id.fspPlayPause) 
        btnPlayPauseInner = findViewById(R.id.btnPlayPauseInner) ?: btnPlayPause
        
        seekBar = findViewById(R.id.fspSeekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
    }

    private fun setupListeners() {
        val playAction = View.OnClickListener {
            musicService?.togglePlay()
            updateUI()
        }
        btnPlayPause.setOnClickListener(playAction)
        btnPlayPauseInner.setOnClickListener(playAction)

        findViewById<ImageButton>(R.id.fspPrev).setOnClickListener {
            musicService?.playPrev()
            updateUI()
        }
        findViewById<ImageButton>(R.id.fspNext).setOnClickListener {
            musicService?.playNext()
            updateUI()
        }
        findViewById<ImageButton>(R.id.btnCloseFSP).setOnClickListener {
            finish()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun updateUI() {
        musicService?.let { service ->
            val title = service.getCurrentTitle() ?: "Unknown Title"
            txtTitle.text = title
            txtTitle.isSelected = true 

            val art = service.getAlbumArt()
            
            // 1. UPDATE COVER DI FRAGMENT (KUNCI UTAMA)
            // ViewPager2 pake tag "f" + position buat fragment-nya
            val fragment = supportFragmentManager.findFragmentByTag("f0") as? CoverFragment
            fragment?.updateCover(art)

            // 2. UPDATE BACKGROUND GLASS/PALETTE
            if (art != null) {
                Palette.from(art).generate { palette ->
                    val color = palette?.getDominantColor(Color.parseColor("#121212")) ?: Color.BLACK
                    rootLayout.setBackgroundColor(color)
                    // Lu bisa tambahin background blur di ImageView fspBlurBg di sini kalo mau
                }
            } else {
                rootLayout.setBackgroundColor(Color.parseColor("#121212"))
            }
            
            // 3. UPDATE TOMBOL & SEEKBAR
            val isPlaying = service.isPlaying()
            val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            btnPlayPause.setImageResource(icon)
            btnPlayPauseInner.setImageResource(icon)

            val current = service.getCurrentPos()
            val duration = service.getDuration()
            if (duration > 0) {
                seekBar.max = duration
                seekBar.progress = current
                tvCurrentTime.text = formatTime(current)
                tvTotalTime.text = formatTime(duration)
            }
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

    private fun formatTime(ms: Int): String {
        val min = (ms / 1000) / 60
        val sec = (ms / 1000) % 60
        return String.format("%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
        handler.removeCallbacksAndMessages(null)
    }
}
