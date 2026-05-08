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
import com.bumptech.glide.Glide
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
    
    // VARIABEL BARU UNTUK SEEKBAR & TIMER
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val guiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "UPDATE_GUI" -> {
                    val isPlaying = intent.getBooleanExtra("isPlaying", false)
                    val title = intent.getStringExtra("title")
                    val uriStr = intent.getStringExtra("uri")
                    
                    btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                    miniTitle.text = title ?: "Unknown"
                    miniPlayer.visibility = View.VISIBLE
                    uriStr?.let { updateMiniCover(Uri.parse(it)) }
                }
                "HIDE_MINI_PLAYER" -> {
                    miniPlayer.visibility = View.GONE
                }
                "FINISH_APP" -> {
                    finish()
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService()
            isBound = true
            val prefs = getSharedPreferences("MusicPrefs", MODE_PRIVATE)
            prefs.getString("last_folder", null)?.let { loadSongs(Uri.parse(it)) }
        }
        override fun onServiceDisconnected(p0: ComponentName?) { isBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        loadingAnim = findViewById(R.id.loadingAnim)
        miniCover = findViewById(R.id.miniCover)
        miniPlayer = findViewById(R.id.miniPlayer)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnCloseMini = findViewById(R.id.btnCloseMini)
        miniTitle = findViewById(R.id.miniTitle)
        
        // INIT VIEW SEEKBAR & TIMER
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        
        miniTitle.isSelected = true
        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        val filter = IntentFilter().apply {
            addAction("UPDATE_GUI")
            addAction("HIDE_MINI_PLAYER")
            addAction("FINISH_APP")
        }
        registerReceiver(guiReceiver, filter)

        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        btnPlayPause.setOnClickListener { musicService?.togglePlay() }
        
        // FIX: Klik X matikan lagu & tutup bar, tapi APP TIDAK CLOSE
        btnCloseMini.setOnClickListener {
            val stopIntent = Intent(this, MusicService::class.java).apply { action = MusicService.ACTION_STOP }
            startService(stopIntent)
            // miniPlayer.visibility = View.GONE -> sudah dihandle di guiReceiver "HIDE_MINI_PLAYER"
        }

        // Handle Geser SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(i, 100)
        }

        startSeekBarUpdate()
    }

    // UPDATE PROGRESS SETIAP DETIK
    private fun startSeekBarUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                musicService?.let {
                    if (it.mediaPlayer != null && it.mediaPlayer!!.isPlaying) {
                        val current = it.getCurrentPos()
                        val duration = it.getDuration()
                        seekBar.max = duration
                        seekBar.progress = current
                        tvCurrentTime.text = formatTime(current)
                        tvTotalTime.text = formatTime(duration)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == 100) {
            data?.data?.let { uri ->
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                getSharedPreferences("MusicPrefs", MODE_PRIVATE).edit()
                    .putString("last_folder", uri.toString()).apply()
                loadSongs(uri)
            }
        }
    }

    private fun loadSongs(uri: Uri) {
        loadingAnim.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Triple<String, String, Uri>>()
            try {
                val root = DocumentFile.fromTreeUri(this@MainActivity, uri)
                val mmr = MediaMetadataRetriever()
                root?.listFiles()?.forEach { file ->
                    val name = file.name?.lowercase() ?: ""
                    if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav")) {
                        try {
                            mmr.setDataSource(this@MainActivity, file.uri)
                            val t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown"
                            val a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                            list.add(Triple(t, a, file.uri))
                        } catch (e: Exception) {
                            list.add(Triple(file.name ?: "Unknown", "Unknown Artist", file.uri))
                        }
                    }
                }
                mmr.release()
            } catch (e: Exception) { e.printStackTrace() }

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

    private fun updateMiniCover(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(this@MainActivity, uri)
                val art = mmr.embeddedPicture
                withContext(Dispatchers.Main) {
                    Glide.with(this@MainActivity)
                        .load(art ?: R.drawable.ic_play)
                        .error(android.R.drawable.ic_media_play)
                        .into(miniCover)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { miniCover.setImageResource(android.R.drawable.ic_media_play) }
            } finally { mmr.release() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (isBound) unbindService(connection)
        unregisterReceiver(guiReceiver)
    }
}
