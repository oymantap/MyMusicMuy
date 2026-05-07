package com.mymusic.muy

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// ... (Import sama kayak sebelumnya)

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private var musicService: MusicService? = null
    private var isBound = false

    // KONEKSI KE SERVICE (WAJIB ADA)
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // START & BIND SERVICE BIAR GA MATI
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val lastUri = prefs.getString("last_folder", null)
        if (lastUri != null) loadSongs(Uri.parse(lastUri))

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 100)
        }
    }

    private fun loadSongs(uri: Uri) {
        val root = DocumentFile.fromTreeUri(this, uri)
        val list = mutableListOf<Triple<String, String, Uri>>()
        val formats = listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val retriever = MediaMetadataRetriever()

        root?.listFiles()?.forEach { file ->
            val ext = file.name?.substringAfterLast('.', "")?.lowercase()
            if (file.isFile && formats.contains(ext)) {
                try {
                    retriever.setDataSource(this, file.uri)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown"
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    list.add(Triple(title, artist, file.uri))
                } catch (e: Exception) {
                    list.add(Triple(file.name ?: "Unknown", "Unknown Artist", file.uri))
                }
            }
        }
        retriever.release()

        // SEKARANG BAGIAN KLIKNYA DIISI BIAR BUNYI
        rv.adapter = SongAdapter(this, list) { title, artist, songUri ->
            musicService?.playMusic(songUri, title) // INI YANG BIKIN LAGU JALAN
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }
}