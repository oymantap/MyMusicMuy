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
            val path = songUri.path
            if (path != null) {
                // Cari file .lrc (nama file harus sama persis)
                val lrcPath = path.substringBeforeLast(".") + ".lrc"
                val lrcFile = File(lrcPath)

                if (lrcFile.exists()) {
                    val lines = lrcFile.readLines(Charsets.UTF_8)
                    for (line in lines) {
                        // Bersihin tag waktu [00:12.34] biar tinggal teksnya doang
                        val cleanLine = line.replace(Regex("\\[.*?\\]"), "").trim()
                        if (cleanLine.isNotEmpty()) {
                            lyricsList.add(cleanLine)
                        }
                    }

                    if (lyricsList.isNotEmpty()) {
                        showLyrics()
                    } else {
                        showNoLyrics()
                    }
                } else {
                    showNoLyrics()
                }
            }
        } catch (e: Exception) {
            showNoLyrics()
        }
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
