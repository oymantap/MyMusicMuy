package com.mymusic.muy

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsFragment : Fragment() {
    private lateinit var rvLyrics: RecyclerView
    private lateinit var tvNoLyrics: TextView
    private lateinit var lyricsAdapter: LyricsAdapter
    private val lyricsList = mutableListOf<LyricLine>()
    
    // Sistem Cerdas Variables
    private var isUserScrolling = false
    private val scrollHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_lyrics, container, false)
        rvLyrics = view.findViewById(R.id.rvLyrics)
        tvNoLyrics = view.findViewById(R.id.tvNoLyrics)
        
        setupRecyclerView()
        setupScrollListener()
        return view
    }

    private fun setupRecyclerView() {
        lyricsAdapter = LyricsAdapter(lyricsList)
        rvLyrics.layoutManager = LinearLayoutManager(requireContext())
        rvLyrics.adapter = lyricsAdapter
    }

    private fun setupScrollListener() {
        rvLyrics.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true // User lagi nyentuh layar
                    scrollHandler.removeCallbacksAndMessages(null)
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Jika diam selama 3 detik, kembalikan kontrol ke sistem otomatis
                    scrollHandler.postDelayed({
                        isUserScrolling = false
                    }, 3000)
                }
            }
        })
    }

    // Fungsi Utama: Panggil ini dari FullScreenPlayerActivity setiap detik
    fun updateLyricsHighlight(currentMs: Long) {
        if (lyricsList.isEmpty()) return

        var targetIndex = -1
        for (i in lyricsList.indices) {
            if (currentMs >= lyricsList[i].timeMs) {
                targetIndex = i
            }
        }

        if (targetIndex != -1 && !lyricsList[targetIndex].isCurrent) {
            // Update status lirik di UI
            for (i in lyricsList.indices) {
                lyricsList[i].isCurrent = (i == targetIndex)
            }
            
            lyricsAdapter.notifyDataSetChanged()

            // Sistem Cerdas: Auto scroll hanya jika user tidak sedang scrolling manual
            if (!isUserScrolling) {
                rvLyrics.smoothScrollToPosition(targetIndex)
            }
        }
    }

    fun loadLyrics(songUri: Uri?) {
        lyricsList.clear()
        if (songUri == null) {
            showNoLyrics()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val songFile = DocumentFile.fromSingleUri(context, songUri)
                val fullFileName = songFile?.name ?: ""
                
                if (fullFileName.isNotEmpty()) {
                    val lrcName = fullFileName.substringBeforeLast(".") + ".lrc"
                    val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
                        .getString("last_folder", null)

                    if (treeUriStr != null) {
                        val rootTree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                        val lrcFile = rootTree?.findFile(lrcName)

                        if (lrcFile != null && lrcFile.exists()) {
                            context.contentResolver.openInputStream(lrcFile.uri)?.use { inputStream ->
                                val lines = inputStream.bufferedReader().readLines()
                                val tempLyrics = mutableListOf<LyricLine>()

                                for (line in lines) {
                                    // Regex untuk mengambil [00:12.34]
                                    val timeMatch = Regex("\\[(\\d+):(\\d+\\.\\d+)\\]").find(line)
                                    if (timeMatch != null) {
                                        val min = timeMatch.groupValues[1].toLong()
                                        val sec = (timeMatch.groupValues[2].toDouble() * 1000).toLong()
                                        val totalMs = (min * 60 * 1000) + sec
                                        
                                        val content = line.replace(Regex("\\[.*?\\]"), "").trim()
                                        if (content.isNotEmpty()) {
                                            tempLyrics.add(LyricLine(totalMs, content))
                                        }
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
