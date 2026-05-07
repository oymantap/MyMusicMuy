package com.mymusic.muy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPickFolder = findViewById<Button>(R.id.btnPickFolder)

        btnPickFolder.setOnClickListener {
            // Ini nanti tempat kita panggil Folder Picker
            Toast.makeText(this, "Membuka Penyimpanan...", Toast.LENGTH_SHORT).show()
        }
    }
}
