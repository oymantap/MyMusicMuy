package com.mymusic.muy

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.*
import android.view.animation.DecelerateInterpolator
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

        // CARA KERAS: Ambil byte gambar, kompres, lalu kasih ke Glide
        val imageBytes = getSongThumbnail(u)
        
        Glide.with(ctx)
            .asBitmap()
            .load(imageBytes) // Load dari byte yang udah diekstrak
            .override(150, 150) // KOMPRES: Paksa kecil biar scroll gak lag
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // CACHE: Biar gak ekstrak ulang
            .centerCrop()
            .into(h.img)

        // ANIMASI SMOOTH (Bukan sekali jalan, tapi ngikutin arus scroll)
        h.itemView.alpha = 0f
        h.itemView.translationY = 50f
        h.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(400)
            .start()

        h.itemView.setOnClickListener { onClick(t, a, u) }
    }

    override fun getItemCount() = songs.size

    // Fungsi ekstraksi byte gambar biar Glide gak kerja sendirian
    private fun getSongThumbnail(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ctx, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
