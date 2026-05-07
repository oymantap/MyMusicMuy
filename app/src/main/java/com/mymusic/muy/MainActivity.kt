package com.mymusic.muy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    // Kode unik untuk mengenali hasil pilihan folder
    private val PICK_FOLDER_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPickFolder = findViewById<Button>(R.id.btnPickFolder)

        btnPickFolder.setOnClickListener {
            openFolderPicker()
        }
    }

    // Fungsi untuk ngebuka manager file HP
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE)
    }

    // Fungsi yang jalan setelah user milih folder
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
        
        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == RESULT_OK) {
            val treeUri: Uri? = data?.data
            if (treeUri != null) {
                // Kasih izin akses permanen ke folder ini (biar ga nanya lagi pas app dibuka ulang)
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Mulai scan lagu
                scanSongsInFolder(treeUri)
            }
        }
    }

    private fun scanSongsInFolder(folderUri: Uri) {
        val rootFolder = DocumentFile.fromTreeUri(this, folderUri)
        val songList = mutableListOf<String>()

        // Looping semua file di dalam folder
        rootFolder?.listFiles()?.forEach { file ->
            // Cek apakah itu file musik (mp3, wav, flac, m4a, dll)
            if (file.isFile && isAudioFile(file.name ?: "")) {
                songList.add(file.name ?: "Unknown Song")
            }
        }

        if (songList.isEmpty()) {
            Toast.makeText(this, "Ga ada lagu di folder ini, Manis!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Ketemu ${songList.size} lagu!", Toast.LENGTH_SHORT).show()
            // Nanti di sini kita masukin songList ke RecyclerView (List UI)
        }
    }

    private fun isAudioFile(fileName: String): Boolean {
        val extensions = listOf(".mp3", ".wav", ".flac", ".m4a", ".ogg")
        return extensions.any { fileName.lowercase().endsWith(it) }
    }
}
