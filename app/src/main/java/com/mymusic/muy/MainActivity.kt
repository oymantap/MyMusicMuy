package com.mymusic.muy

import android.content.*
import android.net.Uri
import android.os.*
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
        root?.listFiles()?.filter { it.name?.endsWith(".mp3") == true || it.name?.endsWith(".wav") == true }
            ?.forEach { songs.add(it.name!! to it.uri) }

        rv.adapter = SongAdapter(songs) { title, songUri ->
            musicService?.playMusic(songUri, title)
        }
    }
}
