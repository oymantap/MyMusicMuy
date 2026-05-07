package com.mymusic.muy

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat

    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService")
    }

    fun playMusic(uri: Uri, title: String) {
    if (mediaPlayer != null) {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
    }
    mediaPlayer = MediaPlayer().apply {
        setDataSource(applicationContext, uri)
        prepare()
        start()
    }
    showNotification(title)
}

    private fun showNotification(title: String) {
        val channelId = "music_muy"
        val nm = getSystemService(NotificationManager::class.java) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Control", NotificationManager.IMPORTANCE_LOW))
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play) // Pake icon default
            .setContentTitle("MyMusicMuy")
            .setContentText(title)
            .setOngoing(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .build()

        startForeground(1, notification)
    }
}
