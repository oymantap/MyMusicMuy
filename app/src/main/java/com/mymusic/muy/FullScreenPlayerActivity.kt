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

private val updateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        updateUI() // Setiap ada sinyal lagu berubah, refresh UI (termasuk lirik)
    }
}

override fun onResume() {
    super.onResume()
    registerReceiver(updateReceiver, IntentFilter("UPDATE_GUI"))
}

override fun onPause() {
    super.onPause()
    unregisterReceiver(updateReceiver)
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
                // Setiap swipe, kita trigger update UI biar lirik/cover bener
                updateUI() 
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

    // Update Tab biar Modern (Pake Background Drawable yang ada radiusnya)
    private fun updateTabHighlight(position: Int) {
        if (position == 0) {
            tabSampul.setTextColor(Color.WHITE)
            tabSampul.setBackgroundResource(R.drawable.bg_tab_selected)
            tabLirik.setTextColor(Color.parseColor("#80FFFFFF"))
            tabLirik.setBackgroundResource(0)
        } else {
            tabLirik.setTextColor(Color.WHITE)
            tabLirik.setBackgroundResource(R.drawable.bg_tab_selected)
            tabSampul.setTextColor(Color.parseColor("#80FFFFFF"))
            tabSampul.setBackgroundResource(0)
        }
    }

private fun updateUI() {
    musicService?.let { service ->
        val currentIndex = service.currentIndex
        if (currentIndex == -1 || service.songList.isEmpty()) return

        val (title, artist, uri) = service.songList[currentIndex]
        txtTitle.text = title
        txtArtist.text = artist
        txtTitle.isSelected = true 

        val coverFragment = supportFragmentManager.findFragmentByTag("f0") as? CoverFragment
        val lyricsFragment = supportFragmentManager.findFragmentByTag("f1") as? LyricsFragment

        val art = service.getAlbumArt()
        coverFragment?.updateCover(art)
        lyricsFragment?.loadLyrics(uri)

        if (art != null) {
            Palette.from(art).generate { palette ->
                val color = palette?.getDarkVibrantColor(Color.parseColor("#121212")) ?: Color.BLACK
                rootLayout.setBackgroundColor(color)
            }
            findViewById<ImageView>(R.id.fspBlurBg)?.setImageBitmap(art)
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#121212"))
            // GANTI INI: Balikin ke ic_play biar kaga error build lagi
            findViewById<ImageView>(R.id.fspBlurBg)?.setImageResource(R.drawable.ic_play)
        }
        
        btnPlayPauseInner.setImageResource(if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play)

        val duration = service.getDuration()
        if (duration > 0) {
            seekBar.max = duration
            tvTotalTime.text = formatTime(duration)
        }
    }
}

  private fun startUpdateLoop() {
    handler.post(object : Runnable {
        override fun run() {
            musicService?.let { service ->
                if (isBound) {
                    val current = service.getCurrentPos()
                    seekBar.progress = current
                    tvCurrentTime.text = formatTime(current)

                    // AMBIL FRAGMENT LIRIK (Pake tag "f1" bawaan ViewPager2)
                    val lyricsFragment = supportFragmentManager.findFragmentByTag("f1") as? LyricsFragment
                    
                    // KIRIM DURASI SEKARANG KE SISTEM CERDAS LIRIK
                    // Kita panggil terus biar lirik nyesuaiin posisi biarpun lagu di-pause/seek
                    lyricsFragment?.updateLyricsHighlight(current.toLong())
                }
            }
            // Tetap jalanin loop setiap 1 detik
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
