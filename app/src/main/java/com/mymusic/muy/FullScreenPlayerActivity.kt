package com.mymusic.muy

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.palette.graphics.Palette
import androidx.viewpager2.widget.ViewPager2

class FullScreenPlayerActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var isBound = false
    private var isReceiverRegistered = false

    private lateinit var rootLayout: RelativeLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var btnPlayPauseInner: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    
    // 🌟 LOGIC LOCK QUEUE / ANTRIAN BERULANG
    private lateinit var btnRepeatCustom: ImageButton
    private var isQueueLocked = false
    private var masterBackupList = mutableListOf<Triple<String, String, android.net.Uri>>()

    // Tab Views
    private lateinit var tabSampul: TextView
    private lateinit var tabLirik: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicService.MusicBinder ?: return
            musicService = binder.getService()
            isBound = true

            // Sinkronisasi status tombol jika Service ternyata sudah dalam mode lock sebelumnya
            musicService?.let {
                if (it.songList.size == 1 && masterBackupList.isEmpty()) {
                    // Jika lagu di service sisa 1 tapi backup kosong, anggap tidak sengaja ter-lock
                    isQueueLocked = false
                }
            }
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(updateReceiver, IntentFilter("UPDATE_GUI"), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(updateReceiver, IntentFilter("UPDATE_GUI"))
            }
            isReceiverRegistered = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (isReceiverRegistered) {
            unregisterReceiver(updateReceiver) 
            isReceiverRegistered = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_full_screen_player)
            initViews()
            setupListeners()
            
            viewPager.adapter = FspAdapter(this)
            viewPager.offscreenPageLimit = 2
            viewPager.setCurrentItem(0, false)
            updateTabHighlight(0)

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTabHighlight(position)
                    Handler(Looper.getMainLooper()).post {
                        updateUI()
                    }
                }
            })

            val intent = Intent(this, MusicService::class.java)
            bindService(intent, connection, BIND_AUTO_CREATE)
            startUpdateLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootFSP)
        viewPager = findViewById(R.id.viewPagerFSP)
        txtTitle = findViewById(R.id.fspTitle)
        txtArtist = findViewById(R.id.fspArtist)
        btnPlayPauseInner = findViewById(R.id.btnPlayPauseInner)
        seekBar = findViewById(R.id.fspSeekBar)
        tvCurrentTime = findViewById(R.id.fspCurrentTime)
        tvTotalTime = findViewById(R.id.fspTotalTime)
        tabSampul = findViewById(R.id.tabSampul)
        tabLirik = findViewById(R.id.tabLirik)
        
        // Inisialisasi Tombol Custom Repeat Baru
        btnRepeatCustom = findViewById(R.id.btnRepeatCustom)
    }

    private fun setupListeners() {
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
            overridePendingTransition(R.anim.no_anim, R.anim.slide_down)
        }

        // 🌟 LOGIKA TOMBOL SAKELAR ANTREAN MANTAP
        btnRepeatCustom.setOnClickListener {
            val service = musicService ?: return@setOnClickListener
            
            if (!service.isQueueModeActive) {
                // AKTIFKAN MODE ANTREAN MANTAP
                if (service.customQueue.isEmpty()) {
                    // JIKA ANTREAN CUSTOM KOSONG, OTOMATIS JADIKAN REPEAT 1 LAGU SAJA (Skenario Poin 1)
                    if (service.currentIndex == -1 || service.songList.isEmpty()) return@setOnClickListener
                    
                    val currentSong = service.songList[service.currentIndex]
                    
                    service.originalSongListBackup.clear()
                    service.originalSongListBackup.addAll(service.songList)
                    
                    service.songList.clear()
                    service.songList.add(currentSong)
                    service.currentIndex = 0
                    service.isQueueModeActive = true
                    
                    btnRepeatCustom.setColorFilter(Color.parseColor("#00E5FF")) // Warna Cyan (Aktif)
                    Toast.makeText(this, "Repeat Satu Lagu Aktif! 🔁", Toast.LENGTH_SHORT).show()
                } else {
                    // JIKA ADA ISI ANTREAN NYA (Skenario Poin 2: Maksimal 5 Lagu)
                    val currentPlayingUri = if (service.currentIndex != -1) service.songList[service.currentIndex].third else null
                    
                    service.originalSongListBackup.clear()
                    service.originalSongListBackup.addAll(service.songList)
                    
                    // Ganti daftar putar utama service dengan 5 lagu pilihan tadi
                    service.songList.clear()
                    service.songList.addAll(service.customQueue)
                    
                    // Sesuaikan index putar agar lagu yang lagi jalan tidak mati tiba-tiba
                    val matchIndex = service.songList.indexOfFirst { it.third == currentPlayingUri }
                    service.currentIndex = if (matchIndex != -1) matchIndex else 0
                    service.isQueueModeActive = true
                    
                    btnRepeatCustom.setColorFilter(Color.parseColor("#00E5FF")) // Warna Cyan
                    Toast.makeText(this, "Antrean Mantap Aktif (${service.songList.size} Lagu) 🎶", Toast.LENGTH_SHORT).show()
                }
            } else {
                // MATIKAN MODE ANTREAN (BALIK NORMAL)
                if (service.songList.isEmpty()) return@setOnClickListener
                
                val currentPlayingUri = service.songList[service.currentIndex].third
                
                // Kembalikan seluruh isi folder awal
                service.songList.clear()
                service.songList.addAll(service.originalSongListBackup)
                
                // Cari index lagu tersebut di dalam daftar folder utama agar posisi putar sinkron
                val restoreIndex = service.songList.indexOfFirst { it.third == currentPlayingUri }
                service.currentIndex = if (restoreIndex != -1) restoreIndex else 0
                
                service.isQueueModeActive = false
                // Kosongkan list antrean custom biar user bisa bikin daftar antrean baru dari awal
                service.customQueue.clear() 
                
                btnRepeatCustom.setColorFilter(Color.parseColor("#80FFFFFF")) // Balik Abu-abu (Mati)
                Toast.makeText(this, "Antrean Normal Kembali (Koleksi Folder)", Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { musicService?.seekTo(progress) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

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
        try {
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

                if (coverFragment != null && art != null) {
                    coverFragment.updateCover(art)
                }

                if (lyricsFragment != null) {
                    lyricsFragment.view?.post { lyricsFragment.loadLyrics(uri) }
                }

                if (art != null) {
                    Palette.from(art).generate { palette ->
                        val color = palette?.getDarkVibrantColor(Color.parseColor("#121212")) ?: Color.BLACK
                        rootLayout.setBackgroundColor(color)
                    }
                    findViewById<ImageView>(R.id.fspBlurBg).setImageBitmap(art)
                } else {
                    rootLayout.setBackgroundColor(Color.parseColor("#121212"))
                    findViewById<ImageView>(R.id.fspBlurBg).setImageResource(R.drawable.ic_play)
                }

                btnPlayPauseInner.setImageResource(
                    if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
                )

                // Update visual status tombol repeat berdasarkan state lock saat UI ter-refresh
                if (isQueueLocked) {
                    btnRepeatCustom.setColorFilter(Color.parseColor("#00E5FF"))
                } else {
                    btnRepeatCustom.setColorFilter(Color.parseColor("#80FFFFFF"))
                }

                val duration = service.getDuration()
                if (duration > 0) {
                    seekBar.max = duration
                    tvTotalTime.text = formatTime(duration)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

                        val lyricsFragment = supportFragmentManager.fragments
                            .find { it is LyricsFragment } as? LyricsFragment
                        if (lyricsFragment != null) {
                            lyricsFragment.updateLyricsHighlight(current.toLong())
                        }
                    }
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
        if (isReceiverRegistered) {
            unregisterReceiver(updateReceiver)
            isReceiverRegistered = false
        }
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        handler.removeCallbacksAndMessages(null)
    }
}
