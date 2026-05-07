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

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private var musicService: MusicService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val lastUri = prefs.getString("last_folder", null)
        if (lastUri != null) loadSongs(Uri.parse(lastUri))

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE).edit().putString("last_folder", uri.toString()).apply()
                loadSongs(uri)
            }
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
        rv.adapter = SongAdapter(this, list) { title, artist, songUri ->
            // Logic play ke service
        }
    }
}
