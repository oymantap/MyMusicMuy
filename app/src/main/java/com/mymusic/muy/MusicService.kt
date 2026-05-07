package com.mymusic.muy

import android.app.*
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat

    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService")
    }

    fun playMusic(uri: Uri, title: String) {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer.create(this, uri).apply {
            setOnCompletionListener { /* Next Song Logic Here */ }
            start()
        }
        showNotification(title, true)
    }

    fun togglePlay() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            // Update notification icon here
        }
    }

    private fun showNotification(title: String, isPlaying: Boolean) {
        val channelId = "music_muy"
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Control", NotificationManager.IMPORTANCE_LOW))
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.my_icon)
            .setContentTitle(title)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .addAction(android.R.drawable.ic_media_previous, "Prev", null)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Toggle", null)
            .addAction(android.R.drawable.ic_media_next, "Next", null)
            .build()

        startForeground(1, notification)
    }
}
