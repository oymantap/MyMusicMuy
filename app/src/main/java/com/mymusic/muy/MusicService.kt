package com.mymusic.muy

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    var songList = mutableListOf<Triple<String, String, Uri>>()
    var currentIndex = -1
    private var currentAlbumArt: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> if (mediaPlayer?.isPlaying == true) togglePlay()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
            AudioManager.AUDIOFOCUS_GAIN -> if (mediaPlayer != null && !mediaPlayer!!.isPlaying) mediaPlayer?.start()
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { togglePlay() }
        override fun onPause() { togglePlay() }
        override fun onSkipToNext() { playNext() }
        override fun onSkipToPrevious() { playPrevious() }
        override fun onSeekTo(pos: Long) {
            mediaPlayer?.seekTo(pos.toInt())
            updatePlaybackState(mediaPlayer?.isPlaying ?: false)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "action_toggle"
        const val ACTION_STOP = "action_stop"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        const val ACTION_REFRESH_WIDGET = "action_refresh_widget"
        const val CHANNEL_ID = "music_muy_v6"
    }

    inner class MusicBinder : Binder() { 
        fun getService(): MusicService = this@MusicService 
    }
    
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            isActive = true
            setCallback(mediaSessionCallback)
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPos(): Int = mediaPlayer?.currentPosition ?: 0
    fun getAlbumArt(): Bitmap? = currentAlbumArt
    fun getCurrentTitle(): String? = if (currentIndex != -1) songList[currentIndex].first else null
    fun seekTo(pos: Int) { 
        mediaPlayer?.seekTo(pos) 
        updatePlaybackState(mediaPlayer?.isPlaying ?: false)
    }

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
                extractMetadataAndNotify(title, artist, uri)
                updatePlaybackState(true)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun extractMetadataAndNotify(title: String, artist: String, uri: Uri) {
        // Pindahkan ekstraksi gambar utama ke background thread agar seek bar notifikasi lancar
        Thread {
            val mmr = MediaMetadataRetriever()
            var artBitmap: Bitmap? = null
            try {
                mmr.setDataSource(this, uri)
                val art = mmr.embeddedPicture
                if (art != null) {
                    artBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                }
            } catch (e: Exception) { 
                e.printStackTrace() 
            } finally { 
                mmr.release() 
            }

            mainHandler.post {
                currentAlbumArt = artBitmap
                mediaSession.setMetadata(MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0L)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt)
                    .build())

                showNotification(title, artist, true)
                pushDataToWidget(title, artist, currentAlbumArt, true)
                updateActivityUI()
            }
        }.start()
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
            pushDataToWidget(t, a, currentAlbumArt, it.isPlaying)
            updateActivityUI()
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
                       PlaybackStateCompat.ACTION_SEEK_TO or
                       PlaybackStateCompat.ACTION_PLAY or
                       PlaybackStateCompat.ACTION_PAUSE)
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
            .setSmallIcon(R.drawable.ic_play) 
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(currentAlbumArt)
            .setOngoing(isPlaying) 
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(R.drawable.ic_prev, "Prev", pPrev) 
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, "Toggle", pToggle) 
            .addAction(R.drawable.ic_next, "Next", pNext) 
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

    private fun pushDataToWidget(title: String, artist: String, albumArt: Bitmap?, isPlaying: Boolean) {
        // BUNGKUS SELURUH PROSES AMBIL DATA WIDGET KE BACKGROUND THREAD BIAR NOTIFIKASI TETAP SEHAT WAL AFIAT
        Thread {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val thisWidget = ComponentName(this, MusicWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(thisWidget)

                val size52px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52f, resources.displayMetrics).toInt()
                val size36px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics).toInt()
                val coverToRender = albumArt ?: BitmapFactory.decodeResource(resources, R.drawable.logo)

                for (id in ids) {
                    val options = appWidgetManager.getAppWidgetOptions(id)
                    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                    // Pilih file XML fisik yang tepat berdasarkan deteksi dari ChatGPT kemarin
                    val layoutId = when {
                        minWidth <= 110 -> R.layout.widget_music_1x1
                        minHeight >= 160 -> R.layout.widget_music_2x4
                        minWidth <= 180 -> R.layout.widget_music_1x2
                        minWidth <= 250 -> R.layout.widget_music_1x3
                        else -> R.layout.widget_music_complex
                    }

                    val views = RemoteViews(packageName, layoutId)

                    // Suntik data teks dasar ke file layout yang aktif
                    try { views.setTextViewText(R.id.widget_title, title) } catch (e: Exception) {}
                    try { views.setTextViewText(R.id.widget_artist, artist) } catch (e: Exception) {}
                    try { views.setImageViewResource(R.id.btn_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play) } catch (e: Exception) {}

                    if (coverToRender != null) {
                        try {
                            val scaledMain = Bitmap.createScaledBitmap(coverToRender, size52px, size52px, true)
                            views.setImageViewBitmap(R.id.widget_cover, getRoundedBitmap(scaledMain, 24f))
                        } catch (e: Exception) {}
                    }

                    // Hanya proses Recent Covers jika layout aktif adalah 2x4
                    if (layoutId == R.layout.widget_music_2x4) {
                        val recentViewsIds = arrayOf(
                            R.id.widget_recent_cover_1, R.id.widget_recent_cover_2,
                            R.id.widget_recent_cover_3, R.id.widget_recent_cover_4, R.id.widget_recent_cover_5
                        )
                        for (rid in recentViewsIds) { views.setViewVisibility(rid, View.GONE) }

                        var slotIndex = 0
                        for (i in (currentIndex - 1) downTo 0) {
                            if (slotIndex >= recentViewsIds.size) break
                            val uriRecent = songList[i].third
                            val mmr = MediaMetadataRetriever()
                            var recentBitmap: Bitmap? = null
                            try {
                                mmr.setDataSource(this, uriRecent)
                                val art = mmr.embeddedPicture
                                if (art != null) {
                                    recentBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                                }
                            } catch (e: Exception) {} finally { mmr.release() }

                            if (recentBitmap != null) {
                                val scaledRecent = Bitmap.createScaledBitmap(recentBitmap, size36px, size36px, true)
                                views.setImageViewBitmap(recentViewsIds[slotIndex], getRoundedBitmap(scaledRecent, 14f))
                                views.setViewVisibility(recentViewsIds[slotIndex], View.VISIBLE)
                                slotIndex++
                            }
                        }
                    }

                    // Pasang Intent Pending
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val mainPI = PendingIntent.getActivity(this, 99, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    views.setOnClickPendingIntent(R.id.widget_click_area, mainPI)

                    val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    try { views.setOnClickPendingIntent(R.id.btn_prev, PendingIntent.getService(this, 10, Intent(this, MusicService::class.java).apply { action = ACTION_PREV }, flag)) } catch (e: Exception) {}
                    try { views.setOnClickPendingIntent(R.id.btn_play_pause, PendingIntent.getService(this, 11, Intent(this, MusicService::class.java).apply { action = ACTION_TOGGLE }, flag)) } catch (e: Exception) {}
                    try { views.setOnClickPendingIntent(R.id.btn_next, PendingIntent.getService(this, 12, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, flag)) } catch (e: Exception) {}

                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun getRoundedBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }
        val rectF = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rectF, pixels, pixels, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun updateActivityUI() {
        sendBroadcast(Intent("UPDATE_GUI"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> playPrevious()
            ACTION_TOGGLE -> togglePlay()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> stopEverything()
            ACTION_REFRESH_WIDGET -> {
                if (currentIndex != -1) {
                    val (t, a, _) = songList[currentIndex]
                    pushDataToWidget(t, a, currentAlbumArt, isPlaying())
                }
            }
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
        currentIndex = -1 
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisWidget = ComponentName(this, MusicWidgetProvider::class.java)
        val views = RemoteViews(packageName, R.layout.widget_music_complex)
        views.setTextViewText(R.id.widget_title, "Not Playing")
        views.setTextViewText(R.id.widget_artist, "No Artist")
        views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
        appWidgetManager.updateAppWidget(thisWidget, views)

        stopSelf()
        sendBroadcast(Intent("HIDE_MINI_PLAYER"))
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaPlayer?.release()
        super.onDestroy()
    }
}