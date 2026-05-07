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
    var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private var currentTitle: String = ""

    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService")
    }

    fun playMusic(uri: Uri, title: String) {
        currentTitle = title
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, uri).apply {
            setOnCompletionListener { showNotification(currentTitle, false) }
            start()
        }
        showNotification(title, true)
    }

    fun togglePlay(): Boolean {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            showNotification(currentTitle, it.isPlaying)
            return it.isPlaying
        }
        return false
    }

    private fun showNotification(title: String, isPlaying: Boolean) {
        val channelId = "music_muy"
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Music", NotificationManager.IMPORTANCE_LOW))
        }

        val stopIntent = Intent(this, MusicService::class.java).apply { action = "STOP" }
        val pStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MyMusicMuy")
            .setContentText(title)
            .setOngoing(isPlaying)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Pause", null)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStop)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            mediaPlayer?.stop()
            stopForeground(true)
            stopSelf()
        }
        return START_STICKY
    }
}
