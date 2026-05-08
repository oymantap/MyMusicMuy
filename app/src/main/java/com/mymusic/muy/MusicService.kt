package com.mymusic.muy

import android.app.*
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
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
        val (title, _, uri) = songList[index]
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer.create(this, uri).apply {
            start()
            setOnCompletionListener { playNext() }
        }
        
        showNotification(title, true)
        updateActivityUI(true, title, uri)
    }

    fun playNext() {
        if (songList.isNotEmpty()) playMusic((currentIndex + 1) % songList.size)
    }

    fun togglePlay(): Boolean {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            val (t, _, u) = songList[currentIndex]
            showNotification(t, it.isPlaying)
            updateActivityUI(it.isPlaying, t, u)
            return it.isPlaying
        }
        return false
    }

    private fun updateActivityUI(isPlaying: Boolean, title: String, uri: Uri) {
        sendBroadcast(Intent("UPDATE_GUI").apply {
            putExtra("isPlaying", isPlaying)
            putExtra("title", title)
            putExtra("uri", uri.toString()) // WAJIB ADA BUAT COVER
        })
    }

    private fun showNotification(title: String, isPlaying: Boolean) {
        val channelId = "music_muy_v5"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Music", NotificationManager.IMPORTANCE_LOW))
        }

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pToggle = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_TOGGLE }, flag)
        val pNext = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, flag)
        val pStop = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).apply { action = ACTION_STOP }, flag)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_play) // Asset putih 144px lu
            .setContentTitle("MyMusicMuy")
            .setContentText(title)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, "Toggle", pToggle)
            .addAction(R.drawable.ic_next, "Next", pNext)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStop)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
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
