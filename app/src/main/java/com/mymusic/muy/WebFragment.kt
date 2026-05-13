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

    // FUNGSI PUBLIK UNTUK DIAKSES ACTIVITY
    fun canGoBack(): Boolean = ::webView.isInitialized && webView.canGoBack()
    
    fun goBack() { if (::webView.isInitialized) webView.goBack() }
    
    fun resetToHome() { targetUrl?.let { webView.loadUrl(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUrl = arguments?.getString("url")
        createNotificationChannel()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = FrameLayout(requireContext())
        
        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        progressBar = LinearProgressIndicator(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 15)
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
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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
                            Toast.makeText(requireContext(), "Gagal proses file!", Toast.LENGTH_SHORT).show()
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

            override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                filePathCallback = callback
                fileChooserLauncher.launch(params?.createIntent())
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
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
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                } catch (e: Exception) { return false }
            }
        }
    }

    private fun saveFileDirectly(data: ByteArray, mimeType: String, fileName: String) {
        val context = requireContext()
        val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE).getString("last_folder", null) 

        if (treeUriStr == null) {
            lifecycleScope.launch(Dispatchers.Main) { Toast.makeText(context, "Folder belum diatur!", Toast.LENGTH_SHORT).show() }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                val newFile = rootFolder?.createFile(mimeType, fileName)
                newFile?.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Tersimpan: $fileName", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Gagal simpan file!", Toast.LENGTH_SHORT).show() }
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
            context.startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

    private fun setupDownloadLogic() {
        webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val filters = listOf("(?i)spotidown\\.app\\s*-\\s*", "(?i)y2mate\\.com\\s*-\\s*", "(?i)y2mate\\.bz\\s*-\\s*")
            filters.forEach { pattern -> fileName = fileName.replace(pattern.toRegex(), "").trim() }
            if (fileName.endsWith(".bin") || fileName.length < 5) fileName = "Muy_DL_${System.currentTimeMillis()}.mp3"
            
            val cookies = CookieManager.getInstance().getCookie(url)
            val ua = webView.settings.userAgentString
            val ref = webView.url ?: url
            
            Toast.makeText(requireContext(), "Mulai mengunduh...", Toast.LENGTH_SHORT).show()
            downloadWithProgress(url, mimetype, fileName, ua, ref, cookies)
        }
    }

    private fun downloadWithProgress(fileUrl: String, mimeType: String, fileName: String, ua: String, ref: String, cookies: String?) {
        val context = requireContext()
        val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE).getString("last_folder", null) ?: return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("Muy Downloader")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setOngoing(true)
            setProgress(100, 0, true)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                val newFile = rootFolder?.createFile(mimeType, fileName)

                if (newFile != null) {
                    val connection = (URL(fileUrl).openConnection() as HttpURLConnection).apply {
                        if (!cookies.isNullOrEmpty()) setRequestProperty("Cookie", cookies)
                        setRequestProperty("User-Agent", ua)
                        setRequestProperty("Referer", ref)
                        connectTimeout = 15000
                    }

                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        var lastUpdate = 0L

                        while (inputStream.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)
                            if (System.currentTimeMillis() - lastUpdate > 500) {
                                val progress = if (fileLength > 0) (total * 100 / fileLength).toInt() else 0
                                builder.setProgress(100, progress, fileLength <= 0).setContentText("Proses: $progress% ($fileName)")
                                notificationManager.notify(notificationId, builder.build())
                                lastUpdate = System.currentTimeMillis()
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        builder.setContentTitle("Selesai").setContentText(fileName).setProgress(0, 0, false).setOngoing(false)
                        notificationManager.notify(notificationId, builder.build())
                        Toast.makeText(context, "Selesai: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { notificationManager.cancel(notificationId) }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Download", NotificationManager.IMPORTANCE_LOW)
            (requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
