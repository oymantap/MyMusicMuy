package com.mymusic.muy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val PICK_FOLDER = 100
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewMusic)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_FOLDER)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_FOLDER && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadSongs(uri)
            }
        }
    }

    private fun loadSongs(uri: Uri) {
        val root = DocumentFile.fromTreeUri(this, uri)
        val list = mutableListOf<String>()
        val formats = listOf(".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac", ".opus")

        root?.listFiles()?.forEach { file ->
            val name = file.name?.lowercase() ?: ""
            if (file.isFile && formats.any { name.endsWith(it) }) {
                list.add(file.name!!)
            }
        }

        if (list.isEmpty()) {
            Toast.makeText(this, "Ga ada musiknya, Sayang!", Toast.LENGTH_SHORT).show()
        }
        
        recyclerView.adapter = SongAdapter(list)
    }
}
