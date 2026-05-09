package com.mymusic.muy

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
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

        // KONFIGURASI KOMPRESI BRUTAL
        val brutalCompression = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565) // Hemat RAM 50%
            .override(80, 80) // Turunin resolusi sampe titik darah penghabisan
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Simpan hasil buriknya aja
            .priority(Priority.IMMEDIATE)
            .centerCrop()

        Glide.with(ctx)
            .asBitmap()
            .load(u) // Glide sebenernya bisa fetch metadata secara internal
            .apply(brutalCompression)
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .thumbnail(0.05f) // Load versi super burik dulu buat pancingan
            .into(h.img)

        // ANIMASI SINGKAT & HALUS
        h.itemView.alpha = 0f
        h.itemView.animate()
            .alpha(1f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(300)
            .start()

        h.itemView.setOnClickListener { onClick(t, a, u) }
    }

    override fun getItemCount() = songs.size
}
