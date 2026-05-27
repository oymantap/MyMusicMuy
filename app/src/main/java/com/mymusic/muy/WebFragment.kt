package com.mymusic.muy

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * WEBFRAGMENT - MUY MUSIC DOWNLOADER ENGINE
 * -------------------------------------------------------------------------
 * VERSI: 2.5 (SUPER-EXTENDED / NO CORRUPTION)
 * FITUR UTAMA: 
 * 1. Smart Filename Purge (Hapus Spotidown, y2mate, dll)
 * 2. Anti-Bin System (MimeType Mapping Terpadu)
 * 3. High-Responsiveness Klik Download
 * 4. ETA Realtime reaktif terhadap lag jaringan
 * -------------------------------------------------------------------------
 */
class WebFragment : Fragment() {

    // --- VARIABEL GLOBAL UI ---
    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator
    private var targetUrl: String? = null
    private val CHANNEL_ID = "download_channel_muy_v2"
    
    // --- VARIABEL KONTROL DOWNLOAD ---
    private val activeJobs = mutableMapOf<Int, Job>()
    // --- HANDLER PEMILIH FILE ---
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val results = WebChromeClient.FileChooserParams.parseResult(
                    result.resultCode, 
                    data
                )
                filePathCallback?.onReceiveValue(results)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // LAUNCHER MODERN UNTUK PILIH FOLDER ULANG (TARUH DI SINI)
    private val folderChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val treeUri = result.data?.data
            if (treeUri != null) {
                // Mengunci izin akses agar persisten (tidak gampang hangus)
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                
                // Update SharedPreferences dengan folder baru
                val sharedPrefs = requireContext().getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("last_folder", treeUri.toString()).apply()
                
                Toast.makeText(requireContext(), "Folder berhasil diperbarui! Silahkan download ulang.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        /**
         * Instance factory untuk fragment
         */
        fun newInstance(url: String): WebFragment {
            val fragment = WebFragment()
            val args = Bundle()
            args.putString("url", url)
            fragment.arguments = args
            return fragment
        }
    }

    // --- FUNGSI KONTROL NAVIGASI ---
    
    fun canGoBack(): Boolean {
        return ::webView.isInitialized && webView.canGoBack()
    }

    fun goBack() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        }
    }

    fun resetToHome() {
        if (::webView.isInitialized) {
            targetUrl?.let { 
                webView.loadUrl(it) 
            }
        }
    }

    // --- LIFECYCLE FRAGMENT ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load URL dari bundle
        targetUrl = arguments?.getString("url")
        // Registrasi channel notifikasi
        createNotificationChannel()
    }

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val root = FrameLayout(context)
        
        // Inisialisasi WebView dengan Layout Match Parent
        webView = WebView(context)
        val webParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.layoutParams = webParams
        
        // Inisialisasi Progress Bar untuk loading page (Top position)
        progressBar = LinearProgressIndicator(context)
        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            20 
        )
        progressBar.layoutParams = progressParams
        progressBar.visibility = View.GONE
        
        // Konfigurasi mendalam
        setupWebView()
        setupDownloadLogic()
        
        // Membangun View
        root.addView(webView)
        root.addView(progressBar)
        
        // Jalankan URL
        targetUrl?.let { 
            webView.loadUrl(it) 
        }
        
        return root
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        // SETTING JAVASCRIPT & STORAGE
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        
        // SETTING VIEWPORT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        
        // SETTING NETWORK & CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(true)
        
        // IDENTITAS BROWSER (Chrome Mobile Terbaru)
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        
        // WEBVIEW CLIENT HANDLING
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, 
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                
                // Bypass untuk link web biasa
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false 
                }
                
                // Penanganan Skema Intent (Spotify, WA, dll)
                return try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    val resolveInfo = requireContext().packageManager.resolveActivity(intent, 0)
                    
                    if (resolveInfo != null) {
                        startActivity(intent)
                        true
                    } else {
                        val fallback = intent.getStringExtra("browser_fallback_url")
                        if (fallback != null) {
                            view?.loadUrl(fallback)
                            true
                        } else {
                            false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MuyWeb", "Intent Error: ${e.message}")
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
        
        // WEB CHROME CLIENT (Progress & File Chooser)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                wv: WebView?, 
                cb: ValueCallback<Array<Uri>>?, 
                p: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = cb
                
                val intent = p?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    false
                }
            }
        }

        // JS BRIDGE UNTUK BLOB DOWNLOAD
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun saveBlob(base64Data: String, mimeType: String, fileName: String) {
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        saveFileDirectly(decodedBytes, mimeType, fileName)
                    } catch (e: Exception) {
                        Log.e("MuyWeb", "Blob Fail")
                    }
                }
            }
        }, "BlobBridge")
    }

    /**
     * SETUP DOWNLOAD LOGIC - BAGIAN KRUSIAL PENAMAAN FILE
     * FIX: Unresolved reference 'rawName' & Anti-Bin System
     */
    private fun setupDownloadLogic() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            
            var rawName = ""
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                rawName = contentDisposition.substringAfter("filename=").substringBefore(";").replace("\"", "")
                if (rawName.startsWith("UTF-8''")) {
                    try { 
                        rawName = java.net.URLDecoder.decode(rawName.substring(7), "UTF-8") 
                    } catch (e: Exception) {
                        rawName = rawName.substring(7)
                    }
                }
            }
            
            if (rawName.isEmpty()) {
                rawName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
            }

            // PEMBERSIHAN DOMAIN
            val domainTrash = "(?i)(spotidown\\.app|y2mate\\.com|y2mate\\.bz|y2mate|spotidown)".toRegex()
            var cleanName = rawName.replace(domainTrash, "").trim()
            
            // FIX STORAGE I/O: Hapus total karakter ilegal Android/Linux (\/:*?"<>|) sebelum diubah ke underscore
            cleanName = cleanName.replace("[\\/:*?\"<>|]".toRegex(), "")
            
            // Mengubah spasi dan sisa simbol menjadi satu underscore
            cleanName = cleanName.replace("[\\s\\-_/]+".toRegex(), "_")
                                 .removePrefix("_")
                                 .removeSuffix("_")

            val mimeMap = android.webkit.MimeTypeMap.getSingleton()
            val extFromMime = mimeMap.getExtensionFromMimeType(mimetype) ?: "mp3"

            if (cleanName.endsWith(".bin", true) || !cleanName.contains(".")) {
                val base = if (cleanName.contains(".")) cleanName.substringBeforeLast(".") else cleanName
                cleanName = "$base.$extFromMime"
            }

            val finalFileName = "Muy_$cleanName"
            
            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
            val currentRef = webView.url ?: url
            
            downloadWithProgress(
                url, 
                mimetype, 
                finalFileName, 
                userAgent, 
                currentRef, 
                cookies
            )
        }
    }

    /**
     * DOWNLOAD CORE ENGINE - PROSES BACKGROUND & NOTIFIKASI
     */
    private fun downloadWithProgress(
        fileUrl: String, 
        mimeType: String, 
        fileName: String, 
        ua: String, 
        ref: String, 
        cookies: String?
    ) {
        val appContext = context?.applicationContext ?: return
        val sharedPrefs = appContext.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val treeUriStr = sharedPrefs.getString("last_folder", null)

        if (treeUriStr == null) {
            Toast.makeText(appContext, "Folder belum dipilih!", Toast.LENGTH_LONG).show()
            return
        }

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        
        // SETUP TOMBOL BATAL
        val cancelIntent = Intent("CANCEL_DOWNLOAD_$notificationId")
        val pendingCancel = PendingIntent.getBroadcast(
            appContext, 
            notificationId, 
            cancelIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // BUILDER NOTIFIKASI AWAL
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID).apply {
            setContentTitle(fileName)
            setContentText("Menyiapkan koneksi...")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", pendingCancel)
            setProgress(100, 0, true)
        }

        notificationManager.notify(notificationId, builder.build())
        Toast.makeText(appContext, "Mengunduh: $fileName", Toast.LENGTH_SHORT).show()

        // PROSES ASINKRON (COROUTINE)
        val downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            var input: InputStream? = null
            var output: OutputStream? = null
            var targetDoc: DocumentFile? = null

            try {
                // Inisialisasi Storage SAF
                val folderUri = Uri.parse(treeUriStr)
                val rootFolder = DocumentFile.fromTreeUri(appContext, folderUri)
                targetDoc = rootFolder?.createFile(mimeType, fileName) 
                    ?: throw Exception("Storage I/O Error")

                // Inisialisasi Koneksi Network
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
                
                // Validasi HTTP Response
                if (connection.responseCode !in 200..299) {
                    throw Exception("Server Error: ${connection.responseCode}")
                }

                val fileLength = connection.contentLength.toLong()
                input = connection.inputStream
                output = appContext.contentResolver.openOutputStream(targetDoc.uri) 
                    ?: throw Exception("Stream Error")

                // VARIABEL TRACKING PROGRESS
                val buffer = ByteArray(16384)
                var bytesDownloaded: Long = 0
                var bytesInBatch: Int
                var lastUpdateMillis = 0L
                var bytesAtLastUpdate: Long = 0
                
                // LOOPING DOWNLOAD DATA
                while (isActive) {
                    bytesInBatch = input.read(buffer)
                    if (bytesInBatch == -1) {
                        break 
                    }
                    
                    output.write(buffer, 0, bytesInBatch)
                    bytesDownloaded += bytesInBatch

                    val currentMillis = System.currentTimeMillis()
                    
                    // UPDATE NOTIFIKASI TIAP 800MS (Agar Akurat & Tidak Lag)
                    if (currentMillis - lastUpdateMillis > 800) {
                        val progressPercent = if (fileLength > 0) {
                            (bytesDownloaded * 100 / fileLength).toInt()
                        } else {
                            0
                        }
                        
                        // Hitung Speed (KB/s)
                        val timeDiff = (currentMillis - lastUpdateMillis).coerceAtLeast(1)
                        val bytesDiff = bytesDownloaded - bytesAtLastUpdate
                        val kbps = (bytesDiff * 1000.0 / timeDiff) / 1024.0
                        
                        // Hitung Sisa Waktu (ETA)
                        val infoText = if (fileLength > 0) {
                            val bytesLeft = fileLength - bytesDownloaded
                            val etaSeconds = if (kbps > 0.1) {
                                (bytesLeft / 1024.0 / kbps).toLong()
                            } else {
                                9999999L 
                            }
                            
                            val etaStr = if (etaSeconds > 86400) {
                                "1 abad+" 
                            } else {
                                String.format("%02d:%02d", etaSeconds / 60, etaSeconds % 60)
                            }
                            
                            "$progressPercent% • ${String.format("%.1f", kbps)} KB/s • $etaStr"
                        } else {
                            "${String.format("%.1f", kbps)} KB/s • Sedang Mengambil..."
                        }

                        // Kirim Update ke UI Notifikasi
                        builder.setProgress(100, progressPercent, fileLength <= 0)
                               .setContentText(infoText)
                        notificationManager.notify(notificationId, builder.build())
                        
                        lastUpdateMillis = currentMillis
                        bytesAtLastUpdate = bytesDownloaded
                    }
                }

                // VALIDASI AKHIR DOWNLOAD
                if (!isActive) {
                    targetDoc.delete()
                    notificationManager.cancel(notificationId)
                } else {
                    withContext(Dispatchers.Main) {
                        notificationManager.cancel(notificationId)
                        
                        val successNotif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("Download Selesai")
                            .setContentText(fileName)
                            .setAutoCancel(true)
                            .build()
                            
                        notificationManager.notify(notificationId + 1, successNotif)
                        Toast.makeText(appContext, "Tersimpan: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                targetDoc?.delete()
                withContext(Dispatchers.Main) {
                    notificationManager.cancel(notificationId)
                    
                    // PENANGANAN OTOMATIS JIKA FOLDER SAF EXPIRED / ERROR I/O
                    if (e.message?.contains("Storage", ignoreCase = true) == true || 
                        e.message?.contains("Disk", ignoreCase = true) == true || 
                        e.message?.contains("Stream", ignoreCase = true) == true) {
                        
                        Toast.makeText(appContext, "Izin folder habis, mohon pilih ulang folder penyimpanan!", Toast.LENGTH_LONG).show()
                        
                        try {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                            // Memanggil launcher modern yang berada di bagian atas fragment
                            folderChooserLauncher.launch(intent)
                        } catch (ex: Exception) {
                            Log.e("MuyWeb", "Gagal membuka SAF Picker otomatis")
                        }
                        
                    } else {
                        Toast.makeText(appContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                // CLEANUP RESOURCE
                try {
                    input?.close()
                    output?.close()
                    connection?.disconnect()
                } catch (ex: Exception) {
                    // Silent close
                }
                activeJobs.remove(notificationId)
            }
        }

        // SIMPAN JOB KE MAP
        activeJobs[notificationId] = downloadJob

        // RECEIVER UNTUK PEMBATALAN
        val cancelReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                activeJobs[notificationId]?.cancel()
                try {
                    appContext.unregisterReceiver(this)
                } catch (ex: Exception) { }
            }
        }
        
        val filter = IntentFilter("CANCEL_DOWNLOAD_$notificationId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(cancelReceiver, filter)
        }
    }


    private fun saveFileDirectly(data: ByteArray, mimeType: String, fileName: String) {
        val ctx = context?.applicationContext ?: return
        val prefs = ctx.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val treeUri = prefs.getString("last_folder", null) ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri))
                val file = folder?.createFile(mimeType, "Muy_$fileName")
                file?.uri?.let { uri ->
                    ctx.contentResolver.openOutputStream(uri)?.use { 
                        it.write(data) 
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Blob tersimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MuyWeb", "Save Fail")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Muy Download Service"
            val importance = NotificationManager.IMPORTANCE_LOW 
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Notifikasi progress download musik"
                setShowBadge(false)
                enableVibration(false)
            }
            
            val manager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        // Matikan semua job yang masih jalan sebelum fragment hancur
        activeJobs.forEach { (_, job) -> 
            if (job.isActive) {
                job.cancel()
            }
        }
        super.onDestroy()
    }
}