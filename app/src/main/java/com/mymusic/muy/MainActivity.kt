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
        super.onActivityResult(requestCode: Int, resultCode: Int, data) // Baris ini yang tadi error
        
        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == RESULT_OK) {
            val treeUri: Uri? = data?.data
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
            if (file.isFile && isAudio(file.name ?: "")) {
                songNames.add(file.name ?: "Unknown")
            }
        }

        if (songNames.isEmpty()) {
            Toast.makeText(this, "Ga ada lagu, Rycl!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ketemu ${songNames.size} lagu!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAudio(name: String): Boolean {
        val ext = listOf(".mp3", ".wav", ".flac", ".m4a")
        return ext.any { name.lowercase().endsWith(it) }
    }
}
