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
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
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
        
        // 1. Setup WebView
        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        // 2. Setup Progress Bar (Loadbar) di atas
        progressBar = LinearProgressIndicator(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 10)
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
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebShareBridge(requireContext()), "AndroidShare")
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val shareScript = """
                    if (navigator.share === undefined) {
                        navigator.share = function(data) {
                            AndroidShare.share(data.title || 'Share', data.text || '', data.url || '');
                        };
                    }
                """.trimIndent()
                view?.evaluateJavascript(shareScript, null)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
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
            val filters = listOf("(?i)spotidown\\.app\\s*-\\s*", "(?i)y2mate\\.com\\s*-\\s*", "(?i)y2mate\\.bz\\s*-\\s*")
            filters.forEach { pattern -> fileName = fileName.replace(pattern.toRegex(), "").trim() }

            if (fileName.endsWith(".bin") || fileName.length < 5) fileName = "Muy_DL_${System.currentTimeMillis()}.mp3"
            downloadWithProgress(url, mimetype, fileName)
        }
    }

    private fun downloadWithProgress(fileUrl: String, mimeType: String, fileName: String) {
        val context = requireContext()
        val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE).getString("last_folder", null)

        if (treeUriStr == null) {
            Toast.makeText(context, "Folder musik belum diatur!", Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("Mengunduh $fileName")
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
                    val url = URL(fileUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    
                    // HEADER SAKTI: Biar server nggak ngeblokir
                    val cookies = CookieManager.getInstance().getCookie(fileUrl)
                    if (cookies != null) connection.setRequestProperty("Cookie", cookies)
                    connection.setRequestProperty("User-Agent", webView.settings.userAgentString)
                    connection.setRequestProperty("Referer", webView.url ?: fileUrl)
                    connection.setRequestProperty("Origin", "https://${URL(webView.url ?: fileUrl).host}")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    
                    connection.connect()

                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        while (inputStream.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)
                            if (fileLength > 0) {
                                val progress = (total * 100 / fileLength).toInt()
                                builder.setProgress(100, progress, false).setContentText("$progress%")
                                notificationManager.notify(notificationId, builder.build())
                            }
                        }
                    }
                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        builder.setContentText("Selesai").setProgress(0, 0, false).setOngoing(false)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        notificationManager.notify(notificationId, builder.build())
                        Toast.makeText(context, "Selesai: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    notificationManager.cancel(notificationId)
                    Toast.makeText(context, "Gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
