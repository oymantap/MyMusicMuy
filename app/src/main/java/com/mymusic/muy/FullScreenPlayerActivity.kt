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
    private lateinit var btnPlayPauseInner: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    
    // Tab Views
    private lateinit var tabSampul: TextView
    private lateinit var tabLirik: TextView
    
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

        viewPager.adapter = FspAdapter(this)
        
        // Sinkronisasi Tab pas di-swipe manual
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabHighlight(position)
            }
        })

        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        startUpdateLoop()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootFSP)
        viewPager = findViewById(R.id.viewPagerFSP)
        txtTitle = findViewById(R.id.fspTitle)
        txtArtist = findViewById(R.id.fspArtist)
        btnPlayPauseInner = findViewById(R.id.btnPlayPauseInner)
        seekBar = findViewById(R.id.fspSeekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        
        tabSampul = findViewById(R.id.tabSampul)
        tabLirik = findViewById(R.id.tabLirik)
    }

    private fun setupListeners() {
        // Klik Tab Manual
        tabSampul.setOnClickListener { viewPager.currentItem = 0 }
        tabLirik.setOnClickListener { viewPager.currentItem = 1 }

        val playAction = View.OnClickListener {
            musicService?.togglePlay()
            updateUI()
        }
        
        btnPlayPauseInner.setOnClickListener(playAction)

        findViewById<ImageButton>(R.id.fspPrev).setOnClickListener {
            musicService?.playPrevious() 
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

    private fun updateTabHighlight(position: Int) {
        if (position == 0) {
            tabSampul.setTextColor(Color.WHITE)
            tabSampul.setBackgroundColor(Color.parseColor("#4DFFFFFF"))
            tabLirik.setTextColor(Color.parseColor("#80FFFFFF"))
            tabLirik.setBackgroundColor(Color.TRANSPARENT)
        } else {
            tabLirik.setTextColor(Color.WHITE)
            tabLirik.setBackgroundColor(Color.parseColor("#4DFFFFFF"))
            tabSampul.setTextColor(Color.parseColor("#80FFFFFF"))
            tabSampul.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun updateUI() {
        musicService?.let { service ->
            val currentIndex = service.currentIndex
            if (currentIndex == -1 || service.songList.isEmpty()) return

            val (title, artist, _) = service.songList[currentIndex]
            txtTitle.text = title
            txtArtist.text = artist
            txtTitle.isSelected = true 

            val art = service.getAlbumArt()
            
            val fragment = supportFragmentManager.findFragmentByTag("f0") as? CoverFragment
            fragment?.updateCover(art)

            if (art != null) {
                Palette.from(art).generate { palette ->
                    val color = palette?.getDominantColor(Color.parseColor("#121212")) ?: Color.BLACK
                    rootLayout.setBackgroundColor(color)
                    findViewById<ImageView>(R.id.fspBlurBg)?.setImageBitmap(art)
                }
            } else {
                rootLayout.setBackgroundColor(Color.parseColor("#121212"))
                findViewById<ImageView>(R.id.fspBlurBg)?.setImageDrawable(null)
            }
            
            val isPlaying = service.isPlaying()
            val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
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
                if (isBound) {
                    val current = musicService?.getCurrentPos() ?: 0
                    seekBar.progress = current
                    tvCurrentTime.text = formatTime(current)
                }
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
