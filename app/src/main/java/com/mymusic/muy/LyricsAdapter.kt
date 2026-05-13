package com.mymusic.muy

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Data class untuk menyimpan waktu dan isi lirik
data class LyricLine(
    val timeMs: Long,
    val content: String,
    var isCurrent: Boolean = false
)

class LyricsAdapter(private val lyrics: List<LyricLine>) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLine: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lyrics[position]
        holder.tvLine.apply {
            text = item.content
            gravity = Gravity.CENTER
            setPadding(20, 30, 20, 30) // Padding lebih lega buat mode fokus

            if (item.isCurrent) {
                // MODE FOKUS: Terang, Gede, Bold
                setTextColor(Color.WHITE)
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                alpha = 1.0f
            } else {
                // MODE BIASA: Agak gelap, kecil
                setTextColor(Color.parseColor("#B3FFFFFF"))
                textSize = 18f
                setTypeface(null, Typeface.NORMAL)
                alpha = 0.5f
            }
        }
    }

    override fun getItemCount(): Int = lyrics.size
}
