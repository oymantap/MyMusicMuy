package com.mymusic.muy

import android.app.*
import android.content.*
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
    
    var songList = mutableListOf<Triple<String, String, Uri>>()
    var currentIndex = -1

    companion object {
        const val ACTION_TOGGLE = "action_toggle"
        const val ACTION_STOP = "action_stop"
        const val ACTION_NEXT = "action_next"
    }

    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService")
    }

    fun setList(newList: List<Triple<String, String, Uri>>) {
        songList.clear()
        songList.addAll(newList)
    }

    fun playMusic(index: Int) {
        if (index < 0 || index >= songList.size) return
        currentIndex = index
        val (title, artist, uri) = songList[index]

        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer.create(this, uri).apply {
            start()
            // FITUR AUTO-PLAY BERIKUTNYA
            setOnCompletionListener { playNext() }
        }

        showNotification(title, true)
        updateActivityUI(true, title)
    }

    fun playNext() {
        if (songList.isEmpty()) return
        val nextIndex = (currentIndex + 1) % songList.size
        playMusic(nextIndex)
    }

    fun togglePlay(): Boolean {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            val (title, _, _) = songList[currentIndex]
            showNotification(title, it.isPlaying)
            updateActivityUI(it.isPlaying, title)
            return it.isPlaying
        }
        return false
    }

    private fun updateActivityUI(isPlaying: Boolean, title: String) {
        val intent = Intent("UPDATE_GUI").apply {
            putExtra("isPlaying", isPlaying)
            putExtra("title", title)
        }
        sendBroadcast(intent)
    }

    private fun showNotification(title: String, isPlaying: Boolean) {
        val channelId = "music_muy_final"
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Music", NotificationManager.IMPORTANCE_LOW))
        }

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pToggle = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_TOGGLE }, flag)
        val pNext = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, flag)
        val pStop = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).apply { action = ACTION_STOP }, flag)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MyMusicMuy")
            .setContentText(title)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play", pToggle)
            .addAction(android.R.drawable.ic_media_next, "Next", pNext)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStop)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1))
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> togglePlay()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> {
                mediaPlayer?.stop()
                stopForeground(true)
                stopSelf()
                sendBroadcast(Intent("FINISH_APP"))
            }
        }
        return START_STICKY
    }
}
