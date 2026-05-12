package com.mymusic.muy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// TAMBAHKAN BARIS DI BAWAH INI:
import androidx.lifecycle.lifecycleScope
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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

    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val context = requireContext()
            // 1. Ambil nama file asli dari Uri (misal: "lagu.mp3")
            val songFile = DocumentFile.fromSingleUri(context, songUri)
            val fullFileName = songFile?.name ?: ""
            
            if (fullFileName.isNotEmpty()) {
                // 2. Cari file .lrc dengan nama yang sama (misal: "lagu.lrc")
                val lrcName = fullFileName.substringBeforeLast(".") + ".lrc"
                
                // 3. Kita butuh parent folder-nya. 
                // Karena kita dapet SingleUri, kita harus cari manual di folder yang udah di-pick user
                val treeUriStr = context.getSharedPreferences("MusicPrefs", android.content.Context.MODE_PRIVATE)
                    .getString("last_folder", null)

                if (treeUriStr != null) {
                    val rootTree = DocumentFile.fromTreeUri(context, android.net.Uri.parse(treeUriStr))
                    val lrcFile = rootTree?.findFile(lrcName)

                    if (lrcFile != null && lrcFile.exists()) {
                        // 4. Baca konten file liriknya pake ContentResolver
                        context.contentResolver.openInputStream(lrcFile.uri)?.use { inputStream ->
                            val lines = inputStream.bufferedReader().readLines()
                            
                            val tempLyrics = mutableListOf<String>()
                            for (line in lines) {
                                // Bersihin timestamp [00:12.34]
                                val cleanLine = line.replace(Regex("\\[.*?\\]"), "").trim()
                                if (cleanLine.isNotEmpty()) {
                                    tempLyrics.add(cleanLine)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                if (tempLyrics.isNotEmpty()) {
                                    lyricsList.addAll(tempLyrics)
                                    showLyrics()
                                } else {
                                    showNoLyrics()
                                }
                            }
                            return@launch
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        withContext(Dispatchers.Main) {
            showNoLyrics()
        }
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
