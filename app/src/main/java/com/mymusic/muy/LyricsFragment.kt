package com.mymusic.muy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class LyricsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Manggil layout fragment_lyrics.xml yang lu kasih di atas
        val view = inflater.inflate(R.layout.fragment_lyrics, container, false)
        val tvNoLyrics = view.findViewById<TextView>(R.id.tvNoLyrics)
        
        // Buat ngetes awal, kita munculin pesannya
        tvNoLyrics?.visibility = View.VISIBLE
        return view
    }
}
