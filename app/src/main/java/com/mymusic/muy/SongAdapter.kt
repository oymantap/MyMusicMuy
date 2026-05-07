package com.mymusic.muy

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SongAdapter(
    private val context: Context,
    private val songs: List<Triple<String, String, Uri>>,
    private val onClick: (String, String, Uri) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongVH>() {

    class SongVH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.txtTitle)
        val artist: TextView = v.findViewById(R.id.txtArtist)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): SongVH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_song, p, false)
        return SongVH(v)
    }

    override fun onBindViewHolder(h: SongVH, p: Int) {
        val (title, artist, uri) = songs[p]
        h.title.text = title
        h.artist.text = artist
        h.title.isSelected = true 

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            Glide.with(context)
                .load(art ?: android.R.drawable.ic_media_play) // Pake icon default android dulu
                .into(h.img)
            retriever.release()
        } catch (e: Exception) {
            h.img.setImageResource(android.R.drawable.ic_media_play)
        }

        h.itemView.setOnClickListener { onClick(title, artist, uri) }
    }

    override fun getItemCount() = songs.size
}
