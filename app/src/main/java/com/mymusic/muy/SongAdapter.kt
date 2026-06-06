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

        // 🌟 AKSI TEKAN LAMA UNTUK MASUKKAN KE ANTREAN MANTAP (MAKSIMAL 5)
        h.itemView.setOnLongClickListener {
            val serviceIntent = android.content.Intent(ctx, MusicService::class.java)
            ctx.bindService(serviceIntent, object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                    val binder = service as? MusicService.MusicBinder
                    val musicService = binder?.getService()
                    
                    musicService?.let { ws ->
                        if (ws.isQueueModeActive) {
                            Toast.makeText(ctx, "Matikan mode antrean di player dulu sebelum menambah lagu!", Toast.LENGTH_SHORT).show()
                            return@let
                        }

                        val isAlreadyInQueue = ws.customQueue.any { it.third == uri }
                        
                        if (isAlreadyInQueue) {
                            Toast.makeText(ctx, "Lagu ini sudah ada di antrean!", Toast.LENGTH_SHORT).show()
                        } else if (ws.customQueue.size >= 5) {
                            Toast.makeText(ctx, "Antrean penuh! Maksimal hanya 5 lagu.", Toast.LENGTH_SHORT).show()
                        } else {
                            ws.customQueue.add(Triple(title, artist, uri))
                            Toast.makeText(ctx, "Berhasil ditambah ke Antrean (${ws.customQueue.size}/5) 🎧", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ctx.unbindService(this)
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {}
            }, Context.BIND_AUTO_CREATE)
            
            true
        }
    } // Tanda kurung penutup onBindViewHolder aman di sini!

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
