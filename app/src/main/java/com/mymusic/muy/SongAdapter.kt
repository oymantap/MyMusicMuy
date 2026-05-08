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

    // Pake cara ini biar tetep smooth tapi cover muncul
    Glide.with(ctx)
        .asBitmap() // Ambil sebagai bitmap biar Glide lebih fokus nyari gambar
        .load(u) 
        .placeholder(android.R.drawable.ic_media_play)
        .error(android.R.drawable.ic_media_play)
        .fallback(android.R.drawable.ic_media_play) // Kalau URI-nya kosong
        .thumbnail(0.1f)
        .centerCrop()
        .into(h.img)

    h.itemView.setOnClickListener { onClick(t, a, u) }
}

    override fun getItemCount() = songs.size
}
