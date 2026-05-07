package com.mymusic.muy

import android.content.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var miniPlayer: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var miniTitle: TextView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService()
            isBound = true
        }
        override fun onServiceDisconnected(p0: ComponentName?) { isBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Izin Notif Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        miniPlayer = findViewById(R.id.miniPlayer)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        miniTitle = findViewById(R.id.miniTitle)
        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        btnPlayPause.setOnClickListener {
            val isPlaying = musicService?.togglePlay() ?: false
            btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        val prefs = getSharedPreferences("MusicPrefs", MODE_PRIVATE)
        prefs.getString("last_folder", null)?.let { loadSongs(Uri.parse(it)) }

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == 100) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("MusicPrefs", MODE_PRIVATE).edit().putString("last_folder", uri.toString()).apply()
                loadSongs(uri)
            }
        }
    }

    private fun loadSongs(uri: Uri) {
        val root = DocumentFile.fromTreeUri(this, uri)
        val list = mutableListOf<Triple<String, String, Uri>>()
        val formats = listOf("mp3", "flac", "wav", "m4a", "ogg", "aac")
        val mmr = MediaMetadataRetriever()

        root?.listFiles()?.forEach { file ->
            if (formats.contains(file.name?.substringAfterLast('.')?.lowercase())) {
                try {
                    mmr.setDataSource(this, file.uri)
                    val t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown"
                    val a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    list.add(Triple(t, a, file.uri))
                } catch (e: Exception) { list.add(Triple(file.name!!, "Unknown Artist", file.uri)) }
            }
        }
        mmr.release()

        rv.adapter = SongAdapter(this, list) { title, artist, songUri ->
            musicService?.playMusic(songUri, title)
            miniPlayer.visibility = View.VISIBLE
            miniTitle.text = title
            miniTitle.isSelected = true
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }
}
