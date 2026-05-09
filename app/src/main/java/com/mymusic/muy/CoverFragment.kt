package com.mymusic.muy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment

class CoverFragment : Fragment() {
    private var imgBigCover: ImageView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Manggil layout fragment_cover.xml yang udah lu bikin
        val view = inflater.inflate(R.layout.fragment_cover, container, false)
        imgBigCover = view.findViewById(R.id.fspBigCover)
        return view
    }

    fun updateCover(bitmap: android.graphics.Bitmap?) {
        imgBigCover?.let {
            if (bitmap != null) it.setImageBitmap(bitmap)
            else it.setImageResource(R.drawable.ic_play)
        }
    }
}
