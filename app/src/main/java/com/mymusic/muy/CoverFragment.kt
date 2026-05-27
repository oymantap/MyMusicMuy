
package com.mymusic.muy

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment

class CoverFragment : Fragment() {

    private var imgBigCover: ImageView? = null

    private var pendingBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_cover,
            container,
            false
        )

        imgBigCover =
            view.findViewById(R.id.fspBigCover)

        pendingBitmap?.let {

            imgBigCover?.setImageBitmap(it)
        }

        return view
    }

    fun updateCover(bitmap: Bitmap?) {

        pendingBitmap = bitmap

        imgBigCover?.let {

            if (bitmap != null) {

                it.setImageBitmap(bitmap)

            } else {

                it.setImageResource(R.drawable.ic_play)
            }
        }
    }
}