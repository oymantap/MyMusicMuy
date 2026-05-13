package com.mymusic.muy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

class WebFragment : Fragment() {

    private lateinit var webView: WebView
    private var targetUrl: String? = null
    private val CHANNEL_ID = "download_channel"

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
        webView = WebView(requireContext())
        setupWebView()
        setupDownloadLogic()
        targetUrl?.let { webView.loadUrl(it) }
        return webView
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.databaseEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        // 1. DUKUNGAN COOKIE (Biar download kaga gagal mulu)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 2. JEMBATAN SHARE API (Biar tombol Share di Spotify/Web jalan)
        webView.addJavascriptInterface(WebShareBridge(requireContext()), "AndroidShare")
        
        // Inject script buat nge-redirect navigator.share ke jembatan kita
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val shareScript = """
                    if (navigator.share === undefined) {
                        navigator.share = function(data) {
                            if (data.url || data.text) {
                                AndroidShare.share(data.title || 'Share', data.text || '', data.url || '');
                            }
                        };
                    }
                """.trimIndent()
                view?.evaluateJavascript(shareScript, null)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
        }

        // 3. LONG PRESS UNTUK SALIN LINK (Cadangan)
        webView.setOnLongClickListener {
            val url = webView.url
            if (url != null) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Link disalin!", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    // Class Internal untuk menangani Share dari JavaScript
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
            
            val filters = listOf(
                "(?i)spotidown\\.app\\s*-\\s*",
                "(?i)y2mate\\.com\\s*-\\s*",
                "(?i)y2mate\\.bz\\s*-\\s*",
                "(?i)spotifydown\\.com\\s*-\\s*"
            )
            filters.forEach { pattern ->
                fileName = fileName.replace(pattern.toRegex(), "").trim()
            }

            if (fileName.endsWith(".bin") || fileName.length < 5) {
                fileName = "Muy_DL_${System.currentTimeMillis()}.mp3"
            }
            
            downloadWithProgress(url, mimetype, fileName)
        }
    }

    private fun downloadWithProgress(fileUrl: String, mimeType: String, fileName: String) {
        val context = requireContext()
        val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
            .getString("last_folder", null)

        if (treeUriStr == null) {
            Toast.makeText(context, "Folder musik belum diatur!", Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("Mengunduh $fileName")
            setContentText("Sedang mengunduh...")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folderUri = Uri.parse(treeUriStr)
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
                val newFile = rootFolder?.createFile(mimeType, fileName)

                if (newFile != null) {
                    val url = java.net.URL(fileUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    
                    // KIRIM COOKIE & USER AGENT (Anti Gagal Download)
                    val cookies = CookieManager.getInstance().getCookie(fileUrl)
                    if (cookies != null) connection.setRequestProperty("Cookie", cookies)
                    connection.setRequestProperty("User-Agent", webView.settings.userAgentString)
                    connection.setRequestProperty("Referer", webView.url)
                    
                    connection.connect()

                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream
                    val outputStream = context.contentResolver.openOutputStream(newFile.uri)

                    if (outputStream != null) {
                        val buffer = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (inputStream.read(buffer).also { count = it } != -1) {
                            total += count
                            outputStream.write(buffer, 0, count)
                            if (fileLength > 0) {
                                val progress = (total * 100 / fileLength).toInt()
                                builder.setProgress(100, progress, false)
                                builder.setContentText("$progress%")
                                notificationManager.notify(notificationId, builder.build())
                            }
                        }
                        outputStream.close()
                    }
                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        builder.setContentText("Unduhan Selesai").setProgress(0, 0, false).setOngoing(false)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        notificationManager.notify(notificationId, builder.build())
                        Toast.makeText(context, "Selesai: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    notificationManager.cancel(notificationId)
                    Toast.makeText(context, "Download Gagal!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Download Progress", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
