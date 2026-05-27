package com.mymusic.muy

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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
        // Menggunakan layout sederhana bawaan android
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lyrics[position]
        holder.tvLine.apply {
            text = item.content
            gravity = Gravity.CENTER
            // Padding atas-bawah dibikin lebar biar enak dilihat pas di tengah
            setPadding(40, 50, 40, 50) 

            if (item.isCurrent) {
                setTextColor(Color.WHITE)
                textSize = 26f // Lu minta fokus, gue bikin mantap
                setTypeface(null, Typeface.BOLD)
                alpha = 1.0f
            } else {
                setTextColor(Color.parseColor("#80FFFFFF")) // Lebih transparan biar fokus ke tengah
                textSize = 18f
                setTypeface(null, Typeface.NORMAL)
                alpha = 0.4f
            }
        }
    }

    // Fungsi sakti biar nggak perlu notifyDataSetChanged() terus-menerus
    fun updateSelection(newIndex: Int) {
        val oldIndex = lyrics.indexOfFirst { it.isCurrent }
        if (oldIndex != -1) {
            lyrics[oldIndex].isCurrent = false
            notifyItemChanged(oldIndex)
        }
        if (newIndex in lyrics.indices) {
            lyrics[newIndex].isCurrent = true
            notifyItemChanged(newIndex)
        }
    }

    override fun getItemCount(): Int = lyrics.size
}
