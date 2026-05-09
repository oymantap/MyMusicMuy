package com.mymusic.muy

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import java.io.ByteArrayOutputStream

class MusicService : Service() {
    var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    var songList = mutableListOf<Triple<String, String, Uri>>()
    var currentIndex = -1
    private var currentAlbumArt: Bitmap? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> if (mediaPlayer?.isPlaying == true) togglePlay()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
            AudioManager.AUDIOFOCUS_GAIN -> if (mediaPlayer != null && !mediaPlayer!!.isPlaying) mediaPlayer?.start()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "action_toggle"
        const val ACTION_STOP = "action_stop"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        const val CHANNEL_ID = "music_muy_v6"
    }

    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            isActive = true
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPos(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(pos: Int) { mediaPlayer?.seekTo(pos) }

    fun setList(newList: List<Triple<String, String, Uri>>) {
        songList.clear()
        songList.addAll(newList)
    }

    fun playMusic(index: Int) {
        if (index < 0 || index >= songList.size) return
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentIndex = index
            val (title, artist, uri) = songList[index]
            
            mediaPlayer?.stop()
            mediaPlayer?.release()
            
            try {
                mediaPlayer = MediaPlayer.create(this, uri).apply {
                    start()
                    setOnCompletionListener { playNext() }
                }
                // Step 1: Ambil Metadata & Cover
                extractMetadataAndNotify(title, artist, uri)
                updatePlaybackState(true)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Step 2: Fungsi maut buat ambil info & sebar ke mana-mana
    private fun extractMetadataAndNotify(title: String, artist: String, uri: Uri) {
        val mmr = MediaMetadataRetriever()
        currentAlbumArt = null
        try {
            mmr.setDataSource(this, uri)
            val art = mmr.embeddedPicture
            if (art != null) {
                currentAlbumArt = BitmapFactory.decodeByteArray(art, 0, art.size)
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        } finally { 
            mmr.release() 
        }

        // Update MediaSession (Buat Lockscreen)
        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0L)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt)
            .build())

        // Kirim ke Notifikasi
        showNotification(title, artist, true)
        
        // Kirim ke MainActivity
        updateActivityUI(true, title, uri)
    }

    fun playNext() { if (songList.isNotEmpty()) playMusic((currentIndex + 1) % songList.size) }
    
    fun playPrevious() {
        if (songList.isNotEmpty()) {
            val next = if (currentIndex - 1 < 0) songList.size - 1 else currentIndex - 1
            playMusic(next)
        }
    }

    fun togglePlay(): Boolean {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            val (t, a, u) = songList[currentIndex]
            updatePlaybackState(it.isPlaying)
            showNotification(t, a, it.isPlaying)
            updateActivityUI(it.isPlaying, t, u)
            return it.isPlaying
        }
        return false
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1f)
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or 
                       PlaybackStateCompat.ACTION_SKIP_TO_NEXT or 
                       PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                       PlaybackStateCompat.ACTION_SEEK_TO)
            .build())
    }

    private fun showNotification(title: String, artist: String, isPlaying: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Music Muy", NotificationManager.IMPORTANCE_LOW))
        }

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pPrev = PendingIntent.getService(this, 3, Intent(this, MusicService::class.java).apply { action = ACTION_PREV }, flag)
        val pToggle = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_TOGGLE }, flag)
        val pNext = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, flag)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(currentAlbumArt) // Ambil dari variabel global yang udah di-extract
            .setOngoing(isPlaying) 
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Prev", pPrev)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Toggle", pToggle)
            .addAction(android.R.drawable.ic_media_next, "Next", pNext)
            .build()

        if (isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            nm.notify(1, notification)
        }
    }

    private fun updateActivityUI(isPlaying: Boolean, title: String, uri: Uri) {
        val intent = Intent("UPDATE_GUI")
        intent.putExtra("isPlaying", isPlaying)
        intent.putExtra("title", title)
        intent.putExtra("uri", uri.toString())
        
        // Bonus: Kalau mau kirim Bitmap langsung via broadcast emang berat, 
        // tapi mending UI ambil dari URI aja karena Glide udah punya cache super kenceng.
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> playPrevious()
            ACTION_TOGGLE -> togglePlay()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopEverything()
    }

    private fun stopEverything() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
        stopSelf()
        sendBroadcast(Intent("HIDE_MINI_PLAYER"))
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaPlayer?.release()
        super.onDestroy()
    }
}
