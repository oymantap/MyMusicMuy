package com.mymusic.muy

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(private val songs: List<Pair<String, Uri>>, private val onClick: (String, Uri) -> Unit) : 
    RecyclerView.Adapter<SongAdapter.VH>() {
    
    class VH(v: View) : RecyclerView.ViewHolder(v) { val t: TextView = v.findViewById(android.R.id.text1) }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_1, p, false)
        // Set background item biar jadi kaca transparan di atas background hitam
        v.setBackgroundResource(R.drawable.bg_glass_card)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, p: Int) {
        h.t.text = songs[p].first
        h.t.setTextColor(android.graphics.Color.WHITE)
        h.itemView.setOnClickListener { onClick(songs[p].first, songs[p].second) }
    }
    override fun getItemCount() = songs.size
}
