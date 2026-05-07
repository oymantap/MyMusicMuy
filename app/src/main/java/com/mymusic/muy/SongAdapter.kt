package com.mymusic.muy

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val context: Context,
    private val songs: List<Triple<String, String, Uri>>,
    private val onClick: (String, String, Uri) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongVH>() {

    class SongVH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.txtTitle)
        val artist: TextView = v.findViewById(R.id.txtArtist)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): SongVH {
        // Pake layout custom item_song yang udah kita buat
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_song, p, false)
        return SongVH(v)
    }

    override fun onBindViewHolder(h: SongVH, p: Int) {
        val (title, artist, uri) = songs[p]
        h.title.text = title
        h.artist.text = artist
        
        // RAHASIA TEKS JALAN (MARQUEE)
        h.title.isSelected = true 
        h.title.requestFocus()

        // Ambil Cover Art asli dari file pake Glide
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            if (art != null) {
                Glide.with(context).load(art).into(h.img)
            } else {
                h.img.setImageResource(R.drawable.default_cover) // Sediakan icon default
            }
            retriever.release()
        } catch (e: Exception) {
            h.img.setImageResource(R.drawable.default_cover)
        }

        h.itemView.setOnClickListener { onClick(title, artist, uri) }
    }

    override fun getItemCount() = songs.size
}