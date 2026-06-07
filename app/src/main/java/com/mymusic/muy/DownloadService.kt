package com.mymusic.muy

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeJobs = mutableMapOf<Int, Job>()
    private val CHANNEL_ID = "muy_background_downloader_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val notificationId = intent?.getIntExtra("NOTIF_ID", -1) ?: -1

        // Handler kalau user klik tombol "Batal" di Notifikasi
        if (action?.startsWith("CANCEL_DOWNLOAD_") == true && notificationId != -1) {
            activeJobs[notificationId]?.cancel()
            activeJobs.remove(notificationId)
            stopSelfIfNoJobs()
            return START_NOT_STICKY
        }

        val fileUrl = intent?.getStringExtra("URL")
        val mimeType = intent?.getStringExtra("MIME")
        val fileName = intent?.getStringExtra("NAME") ?: "Muy_Song.mp3"
        val ua = intent?.getStringExtra("UA") ?: ""
        val ref = intent?.getStringExtra("REF") ?: ""
        val cookies = intent?.getStringExtra("COOKIES")
        val treeUriStr = intent?.getStringExtra("TREE_URI")

        if (fileUrl != null && treeUriStr != null && notificationId != -1) {
            startForegroundDownload(notificationId, fileUrl, mimeType ?: "audio/mpeg", fileName, ua, ref, cookies, treeUriStr)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundDownload(
        notifId: Int, fileUrl: String, mimeType: String, fileName: String,
        ua: String, ref: String, cookies: String?, treeUriStr: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = "CANCEL_DOWNLOAD_$notifId"
            putExtra("NOTIF_ID", notifId)
        }
        val pendingCancel = PendingIntent.getService(
            this, notifId, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Potong nama file di memori lokal jika terlalu panjang agar tidak merusak layout notifikasi
        val shortName = if (fileName.length > 40) fileName.substring(0, 37) + "..." else fileName

        val builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setContentTitle(shortName)
            setContentText("Menghubungkan ke server...")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", pendingCancel)
            setProgress(100, 0, true)
        }

        // Paksa sistem Android mengenali ini sebagai Foreground Service agar aman di latar belakang
        startForeground(notifId, builder.build())

        val job = serviceScope.launch {
            var connection: HttpURLConnection? = null
            var input: InputStream? = null
            var output: OutputStream? = null
            var targetDoc: DocumentFile? = null

            try {
                val folderUri = Uri.parse(treeUriStr)
                val rootFolder = DocumentFile.fromTreeUri(this@DownloadService, folderUri)
                targetDoc = rootFolder?.createFile(mimeType, fileName) ?: throw Exception("Gagal membuat file di SAF")

                val urlObj = URL(fileUrl)
                connection = (urlObj.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Cookie", cookies ?: "")
                    setRequestProperty("User-Agent", ua)
                    setRequestProperty("Referer", ref)
                    connectTimeout = 30000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw Exception("Server Error (HTTP ${connection.responseCode})")
                }

                val fileLength = connection.contentLength.toLong()
                input = connection.inputStream
                output = contentResolver.openOutputStream(targetDoc.uri) ?: throw Exception("Gagal membuat Stream")

                val buffer = ByteArray(16384)
                var bytesDownloaded: Long = 0
                var bytesInBatch: Int
                var lastUpdateMillis = 0L
                var bytesAtLastUpdate: Long = 0

                while (isActive) {
                    bytesInBatch = input.read(buffer)
                    if (bytesInBatch == -1) break

                    output.write(buffer, 0, bytesInBatch)
                    bytesDownloaded += bytesInBatch

                    val currentMillis = System.currentTimeMillis()
                    // Update tampilan teks notifikasi tiap 900ms biar akurat & hemat resource
                    if (currentMillis - lastUpdateMillis > 900) {
                        val progressPercent = if (fileLength > 0) (bytesDownloaded * 100 / fileLength).toInt() else 0
                        val timeDiff = (currentMillis - lastUpdateMillis).coerceAtLeast(1)
                        val bytesDiff = bytesDownloaded - bytesAtLastUpdate
                        val kbps = (bytesDiff * 1000.0 / timeDiff) / 1024.0

                        // Format teks ala Google Chrome: 10.6mb/100mb (45%) • 256 KB/s
                        val currentMb = bytesDownloaded.toDouble() / (1024 * 1024)
                        val totalMb = fileLength.toDouble() / (1024 * 1024)

                        val infoText = if (fileLength > 0) {
                            String.format(Locale.US, "%.1fMB / %.1fMB (%d%%) • %.0f KB/s", currentMb, totalMb, progressPercent, kbps)
                        } else {
                            String.format(Locale.US, "%.1fMB • %.0f KB/s", currentMb, kbps)
                        }

                        builder.setProgress(100, progressPercent, fileLength <= 0)
                            .setContentText(infoText)
                        notificationManager.notify(notifId, builder.build())

                        lastUpdateMillis = currentMillis
                        bytesAtLastUpdate = bytesDownloaded
                    }
                }

                if (!isActive) {
                    targetDoc.delete()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Selesai diunduh!", Toast.LENGTH_SHORT).show()
                        
                        notificationManager.cancel(notifId)
                        
                        val finishNotif = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("Download Selesai! 🎉")
                            .setContentText(shortName)
                            .setOngoing(false)
                            .setAutoCancel(true)
                            .build()
                        
                        notificationManager.notify(notifId, finishNotif)
                    }
                }

            } catch (e: Exception) {
                targetDoc?.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                try { input?.close() } catch (e: Exception) {}
                try { output?.close() } catch (e: Exception) {}
                connection?.disconnect()
                activeJobs.remove(notifId)
                stopSelfIfNoJobs()
            }
        }

        activeJobs[notifId] = job
    }

    private fun stopSelfIfNoJobs() {
        if (activeJobs.isEmpty()) {
            // 🌟 STOP_FOREGROUND_REMOVE bakal otomatis ngehapus semua notifikasi yang nempel di foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Background Engine Downloader",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi mesin pengunduh latar belakang Muy"
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
