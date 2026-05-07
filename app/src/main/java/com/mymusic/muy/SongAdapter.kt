package com.mymusic.muy

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SongAdapter(
    private val ctx: Context,
    private val songs: List<Triple<String, String, Uri>>,
    private val onClick: (String, String, Uri) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.txtTitle)
        val artist: TextView = v.findViewById(R.id.txtArtist)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(ctx).inflate(R.layout.item_song, p, false))

    override fun onBindViewHolder(h: VH, p: Int) {
        val (t, a, u) = songs[p]
        h.title.text = t
        h.artist.text = a
        h.title.isSelected = true

        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(ctx, u)
            val art = mmr.embeddedPicture
            Glide.with(ctx).load(art ?: android.R.drawable.ic_media_play).into(h.img)
            mmr.release()
        } catch (e: Exception) { h.img.setImageResource(android.R.drawable.ic_media_play) }

        h.itemView.setOnClickListener { onClick(t, a, u) }
    }

    override fun getItemCount() = songs.size
}
