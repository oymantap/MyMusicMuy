package com.mymusic.muy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private val PICK_FOLDER_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPickFolder = findViewById<Button>(R.id.btnPickFolder)
        btnPickFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // PERBAIKAN: Di sini tidak boleh ada ": Int" atau ": Intent?"
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == RESULT_OK) {
            val treeUri = data?.data
            if (treeUri != null) {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                scanSongs(treeUri)
            }
        }
    }

    private fun scanSongs(folderUri: Uri) {
        val rootFolder = DocumentFile.fromTreeUri(this, folderUri)
        val songNames = mutableListOf<String>()

        rootFolder?.listFiles()?.forEach { file ->
            val name = file.name ?: ""
            if (file.isFile && (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac"))) {
                songNames.add(name)
            }
        }

        if (songNames.isEmpty()) {
            Toast.makeText(this, "Folder kosongnya, Manis!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ketemu ${songNames.size} lagu!", Toast.LENGTH_SHORT).show()
        }
    }
}

