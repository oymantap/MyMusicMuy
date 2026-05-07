package com.mymusic.muy

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri // INI WAJIB ADA
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun playMusic(uri: Uri, title: String) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, uri)
        mediaPlayer?.start()
        showNotification(title)
    }

    private fun showNotification(title: String) {
        val channelId = "music_channel"
        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Music", NotificationManager.IMPORTANCE_LOW))
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MyMusicMuy")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }
}

