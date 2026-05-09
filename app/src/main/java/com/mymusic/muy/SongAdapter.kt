package com.mymusic.muy

import android.content.Context
import android.net.Uri
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class SongAdapter(
    private val ctx: Context,
    private val songs: List<Triple<String, String, Uri>>,
    private val onClick: (String, String, Uri) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private var lastPosition = -1

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

        // 1. LOAD COVER - STRATEGI AMPUH
        // Kita paksa Glide buat nyari metadata bitmap di dalam URI file tersebut
        Glide.with(ctx)
            .asBitmap()
            .load(u) 
            .transform(CenterCrop(), RoundedCorners(24)) // Tambah pojok bulat biar makin modern
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(h.img)

        // 2. ANIMASI SCROLL (Scale Up)
        setAnimation(h.itemView, p)

        h.itemView.setOnClickListener { onClick(t, a, u) }
    }

    override fun getItemCount() = songs.size

    // Fungsi untuk animasi muncul
    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            // Kita bikin animasi scale sederhana programmatically
            val anim = AnimationUtils.loadAnimation(ctx, android.R.anim.fade_in)
            anim.duration = 400
            viewToAnimate.startAnimation(anim)
            
            // Efek Scale Up
            viewToAnimate.scaleX = 0.8f
            viewToAnimate.scaleY = 0.8f
            viewToAnimate.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .start()
                
            lastPosition = position
        }
    }

    // Penting biar animasi gak aneh pas scroll balik
    override fun onViewDetachedFromWindow(holder: VH) {
        holder.itemView.clearAnimation()
        super.onViewDetachedFromWindow(holder)
    }
}
