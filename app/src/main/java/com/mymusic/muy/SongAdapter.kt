package com.mymusic.muy

import android.content.Context
import android.net.Uri
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions

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

        // 1. LOAD COVER DENGAN KOMPRESI & CACHE AGRESSIVE
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Simpan hasil kompresi di disk
            .override(150, 150) // KOMPRES: Paksa gambar jadi kecil (150px) biar RAM enteng
            .centerCrop()
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)

        Glide.with(ctx)
            .load(u) // Masukkan URI lagu (Glide pinter, dia bakal cari metadata)
            .apply(requestOptions)
            .transition(DrawableTransitionOptions.withCrossFade()) // Efek muncul halus
            .into(h.img)

        // 2. ANIMASI SMOOTH (Bukan kaku sekali jalan)
        h.itemView.translationY = 100f
        h.itemView.alpha = 0f
        h.itemView.animate()
            .translationY(0f)
            .alpha(1f)
            .setInterpolator(DecelerateInterpolator()) // Makin lama makin pelan (Smooth)
            .setDuration(500)
            .start()

        h.itemView.setOnClickListener { onClick(t, a, u) }
    }

    override fun getItemCount() = songs.size
}
