package com.mymusic.muy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private var musicService: MusicService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        // Cek apakah ada folder yang pernah dipilih sebelumnya
        val prefs = getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val lastUri = prefs.getString("last_folder", null)
        if (lastUri != null) loadSongs(Uri.parse(lastUri))

        // Button Pick Folder harus tetep ada buat ganti folder
        findViewById<android.widget.Button>(R.id.btnPickFolder).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.data?.let { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE).edit().putString("last_folder", uri.toString()).apply()
            loadSongs(uri)
        }
    }

private fun loadSongs(uri: Uri) {
    val root = DocumentFile.fromTreeUri(this, uri)
    val list = mutableListOf<Triple<String, String, Uri>>()
    val supportedFormats = listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    
    // Pake Retriever buat ambil judul asli & artis asli dari file
    val retriever = MediaMetadataRetriever()

    root?.listFiles()?.forEach { file ->
        val ext = file.name?.substringAfterLast('.', "")?.lowercase()
        if (file.isFile && supportedFormats.contains(ext)) {
            try {
                retriever.setDataSource(this, file.uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown"
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                list.add(Triple(title, artist, file.uri))
            } catch (e: Exception) {
                // Fallback kalau file korup/metadata kosong
                list.add(Triple(file.name ?: "Unknown", "Unknown Artist", file.uri))
            }
        }
    }
    retriever.release()

    // Update Adapter dengan data yang jauh lebih lengkap
    rv.adapter = SongAdapter(this, list) { title, artist, songUri ->
        musicService?.playMusic(songUri, title) // Langsung kirim judul ke notif
    }
  }
}
