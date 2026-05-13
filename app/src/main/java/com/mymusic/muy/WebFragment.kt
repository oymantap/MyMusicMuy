package com.mymusic.muy

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class WebFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator
    private var targetUrl: String? = null
    private val CHANNEL_ID = "download_channel"
    
    // Callback untuk Upload File
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    companion object {
        fun newInstance(url: String): WebFragment {
            val fragment = WebFragment()
            val args = Bundle()
            args.putString("url", url)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUrl = arguments?.getString("url")
        createNotificationChannel()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = FrameLayout(requireContext())
        
        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        progressBar = LinearProgressIndicator(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                15 // Ukuran sedikit lebih tebal agar terlihat
            )
            visibility = View.GONE
            trackCornerRadius = 5
        }

        setupWebView()
        setupDownloadLogic()

        root.addView(webView)
        root.addView(progressBar)

        targetUrl?.let { webView.loadUrl(it) }
        return root
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // JEMBATAN ANDROID
        webView.addJavascriptInterface(WebShareBridge(requireContext()), "AndroidShare")
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun saveBlob(base64Data: String, mimeType: String, fileName: String) {
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val fileData = Base64.decode(base64Data, Base64.DEFAULT)
                        saveFileDirectly(fileData, mimeType, fileName)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Error dekode data!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }, "BlobBridge")
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                progressBar.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?, 
                callback: ValueCallback<Array<Uri>>?, 
                params: FileChooserParams?
            ): Boolean {
                filePathCallback = callback
                fileChooserLauncher.launch(params?.createIntent())
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Injeksi navigator.share agar web standar bisa memicu Android Share
                view?.evaluateJavascript("""
                    if (navigator.share === undefined) {
                        navigator.share = function(data) {
                            AndroidShare.share(data.title || 'Share', data.text || '', data.url || '');
                        };
                    }
                """.trimIndent(), null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                // Menangani skema luar (intent, tel, whatsapp, dll)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    return false
                }
            }
        }
    }

    private fun saveFileDirectly(data: ByteArray, mimeType: String, fileName: String) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val treeUriStr = prefs.getString("last_folder", null) 

        if (treeUriStr == null) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Folder tujuan belum diatur di setelan!", Toast.LENGTH_LONG).show()
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                val newFile = rootFolder?.createFile(mimeType, fileName)
                newFile?.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Berhasil simpan: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal simpan file ke storage!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    class WebShareBridge(private val context: Context) {
        @JavascriptInterface
        fun share(title: String, text: String, url: String) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$text $url".trim())
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan lewat"))
        }
    }

    private fun setupDownloadLogic() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            
            // Membersihkan nama file dari sampah website downloader
            val filters = listOf(
                "(?i)spotidown\\.app\\s*-\\s*", 
                "(?i)y2mate\\.com\\s*-\\s*", 
                "(?i)y2mate\\.bz\\s*-\\s*"
            )
            filters.forEach { pattern -> fileName = fileName.replace(pattern.toRegex(), "").trim() }
            
            if (fileName.endsWith(".bin") || fileName.length < 5) {
                fileName = "Muy_DL_${System.currentTimeMillis()}.mp3"
            }
            
            val cookies = CookieManager.getInstance().getCookie(url)
            val finalUserAgent = webView.settings.userAgentString
            val referer = webView.url ?: url
            
            Toast.makeText(requireContext(), "Mulai mengunduh file...", Toast.LENGTH_SHORT).show()
            downloadWithProgress(url, mimetype, fileName, finalUserAgent, referer, cookies)
        }
    }

    private fun downloadWithProgress(
        fileUrl: String, 
        mimeType: String, 
        fileName: String, 
        ua: String, 
        ref: String, 
        cookies: String?
    ) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val treeUriStr = prefs.getString("last_folder", null)

        if (treeUriStr == null) {
            Toast.makeText(context, "Atur folder penyimpanan dulu!", Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("Muy Downloader")
            setContentText("Menyiapkan: $fileName")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setProgress(100, 0, true)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folderUri = Uri.parse(treeUriStr)
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
                val newFile = rootFolder?.createFile(mimeType, fileName)

                if (newFile != null) {
                    val url = URL(fileUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    
                    if (!cookies.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookies)
                    connection.setRequestProperty("User-Agent", ua)
                    connection.setRequestProperty("Referer", ref)
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.connect()

                    if (connection.responseCode !in 200..299) {
                        throw Exception("Server merespon error: ${connection.responseCode}")
                    }

                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream
                    
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        var lastProgressUpdate = 0L

                        while (inputStream.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)
                            
                            // Update notifikasi setiap 500ms agar tidak membebani sistem
                            if (System.currentTimeMillis() - lastProgressUpdate > 500) {
                                if (fileLength > 0) {
                                    val progress = (total * 100 / fileLength).toInt()
                                    builder.setProgress(100, progress, false)
                                        .setContentText("Mengunduh: $progress% ($fileName)")
                                } else {
                                    builder.setProgress(100, 0, true)
                                        .setContentText("Mengunduh: ${(total/1024)} KB")
                                }
                                notificationManager.notify(notificationId, builder.build())
                                lastProgressUpdate = System.currentTimeMillis()
                            }
                        }
                    }
                    inputStream.close()
                    connection.disconnect()

                    withContext(Dispatchers.Main) {
                        builder.setContentTitle("Selesai Mengunduh")
                            .setContentText(fileName)
                            .setProgress(0, 0, false)
                            .setOngoing(false)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        notificationManager.notify(notificationId, builder.build())
                        Toast.makeText(context, "Selesai: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    notificationManager.cancel(notificationId)
                    Toast.makeText(context, "Gagal download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Download Progress"
            val descriptionText = "Notifikasi untuk status pengunduhan Muy"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
