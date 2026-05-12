package com.mymusic.muy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LyricsAdapter(private val lyrics: List<String>) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Kita pake ID text1 karena di fragment kita pake layout simple_list_item_1
        val tvLine: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Menggunakan layout standar Android untuk setiap baris lirik
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

 override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.tvLine.apply {
        text = lyrics[position]
        setTextColor(android.graphics.Color.WHITE)
        textSize = 18f
        gravity = android.view.Gravity.CENTER // Biar liriknya di tengah kayak karaoke
        setPadding(20, 15, 20, 15)
    }
}

    override fun getItemCount(): Int = lyrics.size
}
