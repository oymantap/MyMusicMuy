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

    // HAPUS MediaMetadataRetriever-nya, ganti pake ini:
    Glide.with(ctx)
        .load(u) // Glide bisa langsung baca cover dari URI lagu!
        .placeholder(android.R.drawable.ic_media_play) // Gambar sementara pas loading
        .error(android.R.drawable.ic_media_play) // Gambar kalau lagu gak punya cover
        .thumbnail(0.1f) // Load versi buram dulu biar cepet
        .centerCrop()
        .into(h.img)

    h.itemView.setOnClickListener { onClick(t, a, u) }
}


    override fun getItemCount() = songs.size
}
