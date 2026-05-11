package com.mymusic.muy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class LyricsFragment : Fragment() {
    private lateinit var rvLyrics: RecyclerView
    private lateinit var tvNoLyrics: TextView
    private lateinit var lyricsAdapter: LyricsAdapter
    private val lyricsList = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_lyrics, container, false)
        rvLyrics = view.findViewById(R.id.rvLyrics)
        tvNoLyrics = view.findViewById(R.id.tvNoLyrics)
        
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        lyricsAdapter = LyricsAdapter(lyricsList)
        rvLyrics.layoutManager = LinearLayoutManager(requireContext())
        rvLyrics.adapter = lyricsAdapter
    }

fun loadLyrics(songUri: android.net.Uri?) {
    lyricsList.clear()
    if (songUri == null) {
        showNoLyrics()
        return
    }

    try {
        // CARA BARU: Ambil path asli dari URI
        val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
        val cursor = requireContext().contentResolver.query(songUri, projection, null, null, null)
        val actualPath = cursor?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)) else null
        }

        if (actualPath != null) {
            val lrcPath = actualPath.substringBeforeLast(".") + ".lrc"
            val lrcFile = File(lrcPath)

            if (lrcFile.exists()) {
                val lines = lrcFile.readLines(Charsets.UTF_8)
                for (line in lines) {
                    val cleanLine = line.replace(Regex("\\[.*?\\]"), "").trim()
                    if (cleanLine.isNotEmpty()) lyricsList.add(cleanLine)
                }
                if (lyricsList.isNotEmpty()) { showLyrics(); return }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    
    showNoLyrics() // Kalau semua cara gagal
}

    private fun showLyrics() {
        tvNoLyrics.visibility = View.GONE
        rvLyrics.visibility = View.VISIBLE
        lyricsAdapter.notifyDataSetChanged()
        rvLyrics.scrollToPosition(0)
    }

    private fun showNoLyrics() {
        tvNoLyrics.visibility = View.VISIBLE
        rvLyrics.visibility = View.GONE
        lyricsAdapter.notifyDataSetChanged()
    }
}
