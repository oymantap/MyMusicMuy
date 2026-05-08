package com.mymusic.muy

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
        const val CHANNEL_ID = "music_muy_v6"
    }

    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            isActive = true
        }
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
        
        try {
            mediaPlayer = MediaPlayer.create(this, uri).apply {
                start()
                setOnCompletionListener { playNext() }
            }
            
            updateMetadata(title, artist, uri)
            showNotification(title, artist, true)
            updateActivityUI(true, title, uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNext() {
        if (songList.isNotEmpty()) playMusic((currentIndex + 1) % songList.size)
    }

    fun togglePlay(): Boolean {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            val (t, a, u) = songList[currentIndex]
            
            val state = if (it.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(state, it.currentPosition.toLong(), 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
                .build())

            showNotification(t, a, it.isPlaying)
            updateActivityUI(it.isPlaying, t, u)
            return it.isPlaying
        }
        return false
    }

    private fun updateMetadata(title: String, artist: String, uri: Uri) {
        val mmr = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        try {
            mmr.setDataSource(this, uri)
            val art = mmr.embeddedPicture
            if (art != null) {
                bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
            }
        } catch (e: Exception) { } finally { mmr.release() }

        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .build())
    }

    private fun showNotification(title: String, artist: String, isPlaying: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pToggle = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_TOGGLE }, flag)
        val pNext = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, flag)
        val pStop = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).apply { action = ACTION_STOP }, flag)

        // Indeks Action: 0 = Toggle, 1 = Next, 2 = Stop
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1) // Munculin Toggle dan Next di bar kecil

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            // Small Icon wajib pakai yang simple (bisa pake ic_play lu tapi Android bakal bikin jadi siluet putih)
            .setSmallIcon(R.drawable.ic_play) 
            .setContentTitle(title)
            .setContentText(artist)
            // Large Icon bakal nampilin Cover Album lagu lu
            .setLargeIcon(mediaSession.controller.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
            .setOngoing(isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            // ACTION 0: Toggle Play/Pause (PAKE ICON PNG LU)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, "Toggle", pToggle)
            // ACTION 1: Next (PAKE ICON PNG LU)
            .addAction(R.drawable.ic_next, "Next", pNext)
            // ACTION 2: Stop (Pake bawaan biar gampang)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStop)
            .build()

        startForeground(1, notification)
    }

    private fun updateActivityUI(isPlaying: Boolean, title: String, uri: Uri) {
        sendBroadcast(Intent("UPDATE_GUI").apply {
            putExtra("isPlaying", isPlaying)
            putExtra("title", title)
            putExtra("uri", uri.toString())
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> togglePlay()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> {
                mediaPlayer?.stop()
                mediaSession.isActive = false
                stopForeground(true)
                stopSelf()
                sendBroadcast(Intent("FINISH_APP"))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }
}
