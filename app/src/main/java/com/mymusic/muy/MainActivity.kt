package com.mymusic.muy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Service
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        // Load folder terakhir kalau ada
        val prefs = getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val lastUri = prefs.getString("last_folder", null)
        if (lastUri != null) {
            loadSongs(Uri.parse(lastUri))
        }

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(treeIntent, 100)
        }
    }

    // INI YANG TADI LU LUPA ATAU KETINGGALAN, MAKANYA GAK KETARIK
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Kasih izin akses folder secara permanen
                contentResolver.takePersistableUriPermission(uri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                
                // Simpan di memori biar gak ilang pas buka-tutup
                getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
                    .edit().putString("last_folder", uri.toString()).apply()
                
                // JALANKAN SCAN LAGU
                loadSongs(uri)
            }
        }
    }

    private fun loadSongs(uri: Uri) {
        val root = DocumentFile.fromTreeUri(this, uri)
        val list = mutableListOf<Triple<String, String, Uri>>()
        val formats = listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val retriever = MediaMetadataRetriever()

        // Pastikan root ada isinya
        val files = root?.listFiles()
        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "Folder kosong atau gak kebaca Manis!", Toast.LENGTH_SHORT).show()
            return
        }

        files.forEach { file ->
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

        if (list.isEmpty()) {
            Toast.makeText(this, "Gak nemu file musik di folder ini Syg!", Toast.LENGTH_SHORT).show()
        }

        // Set ke adapter
        rv.adapter = SongAdapter(this, list) { title, artist, songUri ->
            if (isBound) {
                musicService?.playMusic(songUri, title)
            } else {
                Toast.makeText(this, "Sabar syg, service lagi nyambung...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }
}
