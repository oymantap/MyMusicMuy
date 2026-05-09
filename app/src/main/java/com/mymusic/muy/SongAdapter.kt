package com.mymusic.muy

import android.content.Context
import android.graphics.BitmapFactory
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(ctx).inflate(R.layout.item_song, parent, false))
    }

    override fun onBindViewHolder(h: VH, p: Int) {
        val (title, artist, uri) = songs[p]

        h.title.text = title
        h.artist.text = artist

        loadCover(uri, h.img)

        h.itemView.alpha = 0f
        h.itemView.translationY = 40f
        h.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        h.itemView.setOnClickListener {
            onClick(title, artist, uri)
        }
    }

    private fun loadCover(uri: Uri, img: ImageView) {
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(ctx, uri)

            val art = mmr.embeddedPicture
            mmr.release()

            if (art != null) {
                Glide.with(ctx)
                    .load(art)
                    .override(120, 120)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(img)
            } else {
                img.setImageResource(android.R.drawable.ic_media_play)
            }

        } catch (e: Exception) {
            img.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun getItemCount() = songs.size
}