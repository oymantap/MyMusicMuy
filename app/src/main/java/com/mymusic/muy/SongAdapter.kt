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

    // Jalan ninja: Ambil cover pake MediaMetadataRetriever tapi DI DALAM Glide
    // Jadi Glide yang tanggung jawab ngerjain di background thread
    Glide.with(ctx)
        .load(u)
        .signature(com.bumptech.glide.signature.ObjectKey(u.toString())) // Biar gak salah ambil cache
        .placeholder(android.R.drawable.ic_media_play)
        .error(android.R.drawable.ic_media_play)
        .centerCrop()
        .into(h.img)

    h.itemView.setOnClickListener { onClick(t, a, u) }
}

    override fun getItemCount() = songs.size
}
