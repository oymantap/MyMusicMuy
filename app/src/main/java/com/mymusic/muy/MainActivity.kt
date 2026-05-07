package com.mymusic.muy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri  // INI WAJIB ADA
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var rv: RecyclerView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) { isBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)

        rv = findViewById(R.id.recyclerViewMusic)
        rv.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(pickIntent, 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadSongs(uri)
            }
        }
    }

    private fun loadSongs(uri: Uri) {
        val root = DocumentFile.fromTreeUri(this, uri)
        val songs = mutableListOf<Pair<String, Uri>>()
        root?.listFiles()?.filter { it.name?.lowercase()?.endsWith(".mp3") == true || it.name?.lowercase()?.endsWith(".wav") == true }
            ?.forEach { songs.add(it.name!! to it.uri) }

        rv.adapter = SongAdapter(songs) { title, songUri ->
            musicService?.playMusic(songUri, title)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }
}
