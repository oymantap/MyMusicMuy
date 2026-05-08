package com.mymusic.muy

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

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

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = 
        VH(LayoutInflater.from(ctx).inflate(R.layout.item_song, p, false))

    override fun onBindViewHolder(h: VH, p: Int) {
        val (t, a, u) = songs[p]
        h.title.text = t
        h.artist.text = a

        // AMBIL ALBUM ID DARI URI UNTUK COVER
        try {
            val songId = u.lastPathSegment?.toLong() ?: 0L
            // Ini URI sakti buat manggil cover album lewat database media Android
            val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
            val albumArtUri = ContentUris.withAppendedId(sArtworkUri, songId)

            Glide.with(ctx)
                .load(u) // Coba load dari file dulu (Glide otomatis ekstrak metadata)
                .error(Glide.with(ctx).load(albumArtUri)) // Kalau gagal, coba cari di folder albumart
                .placeholder(android.R.drawable.ic_media_play)
                .error(android.R.drawable.ic_media_play)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Biar scroll balik tetep cepet
                .into(h.img)
        } catch (e: Exception) {
            h.img.setImageResource(android.R.drawable.ic_media_play)
        }

        h.itemView.setOnClickListener { onClick(t, a, u) }
    }

    override fun getItemCount() = songs.size
}
