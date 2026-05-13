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
import androidx.recyclerview.widget.LinearSnapHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsFragment : Fragment() {
    private lateinit var rvLyrics: RecyclerView
    private lateinit var tvNoLyrics: TextView
    private lateinit var lyricsAdapter: LyricsAdapter
    private val lyricsList = mutableListOf<LyricLine>()
    
    private var isUserScrolling = false
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var lastActiveIndex = -1

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
        val layoutManager = LinearLayoutManager(requireContext())
        rvLyrics.layoutManager = layoutManager
        rvLyrics.adapter = lyricsAdapter

        // TRIK 1: SnapHelper biar lirik "magnetis" berhenti di tengah
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(rvLyrics)
    }

    private fun setupScrollListener() {
        rvLyrics.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true
                    scrollHandler.removeCallbacksAndMessages(null)
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollHandler.postDelayed({
                        isUserScrolling = false
                    }, 2500)
                }
            }
        })
    }

    // FUNGSI UTAMA: Update Highlight dan Scroll ke Tengah
    fun updateLyricsHighlight(currentMs: Long) {
        if (lyricsList.isEmpty() || isUserScrolling) return

        var targetIndex = -1
        for (i in lyricsList.indices) {
            if (currentMs >= lyricsList[i].timeMs) {
                targetIndex = i
            }
        }

        if (targetIndex != -1 && targetIndex != lastActiveIndex) {
            lastActiveIndex = targetIndex
            
            // Update UI warna lewat adapter tanpa notifyDataSetChanged
            lyricsAdapter.updateSelection(targetIndex)

            // TRIK 2: Scroll ke tengah dengan Offset
            // rvLyrics.height / 2 (Titik tengah) dikurangi setengah tinggi item lirik (~70-100)
            val offset = (rvLyrics.height / 2) - 100
            
            (rvLyrics.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(targetIndex, offset)
        }
    }

    fun loadLyrics(songUri: Uri?) {
        lifecycleScope.launch(Dispatchers.Main) {
            lyricsList.clear()
            lastActiveIndex = -1
            if (songUri == null) {
                showNoLyrics()
                return@launch
            }

            val tempLyrics = withContext(Dispatchers.IO) { parseLrcFile(songUri) }

            if (tempLyrics != null && tempLyrics.isNotEmpty()) {
                lyricsList.addAll(tempLyrics)
                showLyrics()
            } else {
                showNoLyrics()
            }
        }
    }

    private fun parseLrcFile(songUri: Uri): List<LyricLine>? {
        try {
            val context = requireContext()
            val songFile = DocumentFile.fromSingleUri(context, songUri)
            val fullFileName = songFile?.name ?: return null
            val lrcName = fullFileName.substringBeforeLast(".") + ".lrc"

            val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
                .getString("last_folder", null) ?: return null

            val rootTree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
            val lrcFile = rootTree?.findFile(lrcName) ?: return null

            context.contentResolver.openInputStream(lrcFile.uri)?.use { inputStream ->
                val lines = inputStream.bufferedReader().readLines()
                val tempLyrics = mutableListOf<LyricLine>()

                for (line in lines) {
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
                return tempLyrics.sortedBy { it.timeMs }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun showLyrics() {
        tvNoLyrics.visibility = View.GONE
        rvLyrics.visibility = View.VISIBLE
        lyricsAdapter.notifyDataSetChanged()
        rvLyrics.post {
            // Biar lirik pertama langsung di tengah pas load
            val offset = (rvLyrics.height / 2) - 100
            (rvLyrics.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, offset)
        }
    }

    private fun showNoLyrics() {
        tvNoLyrics.visibility = View.VISIBLE
        rvLyrics.visibility = View.GONE
        lyricsAdapter.notifyDataSetChanged()
    }
}
