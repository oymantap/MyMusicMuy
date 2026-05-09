package com.mymusic.muy

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var miniPlayer: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnCloseMini: ImageButton
    private lateinit var miniTitle: TextView
    private lateinit var miniCover: ImageView
    private lateinit var loadingAnim: ProgressBar
    
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private val handler = Handler(Looper.getMainLooper())

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private val guiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "UPDATE_GUI" -> updateUIFromService()
                "HIDE_MINI_PLAYER" -> miniPlayer.visibility = View.GONE
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            
            val prefs = getSharedPreferences("MusicPrefs", MODE_PRIVATE)
            prefs.getString("last_folder", null)?.let { loadSongs(Uri.parse(it)) }
            updateUIFromService()
        }
        override fun onServiceDisconnected(p0: ComponentName?) { 
            isBound = false 
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }

        initViews()
        setupListeners()

        val filter = IntentFilter().apply {
            addAction("UPDATE_GUI")
            addAction("HIDE_MINI_PLAYER")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(guiReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(guiReceiver, filter)
        }

        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
        startSeekBarUpdate()
    }

    private fun initViews() {
        loadingAnim = findViewById(R.id.loadingAnim)
        miniCover = findViewById(R.id.miniCover)
        miniPlayer = findViewById(R.id.miniPlayer)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnCloseMini = findViewById(R.id.btnCloseMini)
        miniTitle = findViewById(R.id.miniTitle)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)
        
        miniPlayer.visibility = View.GONE
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener { musicService?.togglePlay() }
        
        // PINTU KE FULL SCREEN PLAYER (FSP) - FIX BIAR GAK MENTAL
        miniPlayer.setOnClickListener {
            val service = musicService
            if (service != null && service.currentIndex != -1) {
                val intent = Intent(this, FullScreenPlayerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        }

        btnCloseMini.setOnClickListener {
            val stopIntent = Intent(this, MusicService::class.java).apply { 
                action = MusicService.ACTION_STOP 
            }
            startService(stopIntent)
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(i, 100)
        }
    }

    private fun updateUIFromService() {
        musicService?.let { service ->
            if (service.currentIndex == -1) {
                miniPlayer.visibility = View.GONE
                return
            }

            val playing = service.isPlaying()
            val title = service.getCurrentTitle() ?: "No Song"
            val art = service.getAlbumArt()

            btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            
            // PERBAIKAN MARQUEE: Hanya update teks jika judulnya berbeda
            if (miniTitle.text.toString() != title) {
                miniTitle.text = title
                miniTitle.isSelected = true 
            }
            
            miniPlayer.visibility = View.VISIBLE

            // RESET gambar dulu biar gak nempel cover sebelumnya
            miniCover.setImageDrawable(null)
            if (art != null) {
                miniCover.setImageBitmap(art)
            } else {
                miniCover.setImageResource(R.drawable.ic_play)
            }
        }
    }

private fun loadSongs(uri: Uri) {
    loadingAnim.visibility = View.VISIBLE
    lifecycleScope.launch(Dispatchers.IO) {
        val list = mutableListOf<Triple<String, String, Uri>>()
        
        val supportedExtensions = listOf(".mp3", ".m4a", ".wav", ".flac", ".ogg", ".aac", ".ts", ".mid", ".xmf", ".ota", ".opus")

        try {
            val root = DocumentFile.fromTreeUri(this@MainActivity, uri)
            root?.listFiles()?.forEach { file ->
                val fileName = file.name?.lowercase() ?: ""
                
                val isAudio = supportedExtensions.any { fileName.endsWith(it) } || 
                              file.type?.startsWith("audio/") == true

                if (isAudio) {
                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(this@MainActivity, file.uri)
                        
                        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                                    ?: file.name?.substringBeforeLast(".") 
                                    ?: "Unknown Title"
                                    
                        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                                     ?: "Unknown Artist"
                        
                        list.add(Triple(title, artist, file.uri))
                    } catch (e: Exception) {
                        val fallbackTitle = file.name?.substringBeforeLast(".") ?: "Unknown"
                        list.add(Triple(fallbackTitle, "Unknown Artist", file.uri))
                    } finally {
                        try { mmr.release() } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }

        withContext(Dispatchers.Main) {
            loadingAnim.visibility = View.GONE
            musicService?.setList(list)
            rv.adapter = SongAdapter(this@MainActivity, list) { _, _, songUri ->
                val index = list.indexOfFirst { it.third == songUri }
                musicService?.playMusic(index)
            }
        }
    }
}

    private fun startSeekBarUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                musicService?.let { service ->
                    try {
                        if (service.isPlaying()) {
                            val current = service.getCurrentPos()
                            val duration = service.getDuration()
                            if (duration > 0) {
                                seekBar.max = duration
                                seekBar.progress = current
                                tvCurrentTime.text = formatTime(current)
                                tvTotalTime.text = formatTime(duration)
                            }
                        }
                    } catch (e: Exception) { }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == 100) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("MusicPrefs", MODE_PRIVATE).edit()
                    .putString("last_folder", uri.toString()).apply()
                loadSongs(uri)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (isBound) unbindService(connection)
        try { unregisterReceiver(guiReceiver) } catch (e: Exception) {}
    }
}
