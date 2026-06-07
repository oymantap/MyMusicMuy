package com.mymusic.muy

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator
    private var targetUrl: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    companion object {
        fun newInstance(url: String): WebFragment {
            val fragment = WebFragment()
            val args = Bundle().apply { putString("url", url) }
            fragment.arguments = args
            return fragment
        }
    }

    fun canGoBack(): Boolean = ::webView.isInitialized && webView.canGoBack()
    fun goBack() { if (::webView.isInitialized && webView.canGoBack()) webView.goBack() }
    fun resetToHome() { if (::webView.isInitialized) targetUrl?.let { webView.loadUrl(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUrl = arguments?.getString("url")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = FrameLayout(context)
        
        webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        progressBar = LinearProgressIndicator(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 12)
            trackColor = android.graphics.Color.TRANSPARENT
            setIndicatorColor(android.graphics.Color.parseColor("#00E5FF"))
            visibility = View.GONE
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
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                return try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    if (requireContext().packageManager.resolveActivity(intent, 0) != null) {
                        startActivity(intent)
                        true
                    } else {
                        intent.getStringExtra("browser_fallback_url")?.let { view?.loadUrl(it) }
                        true
                    }
                } catch (e: Exception) { true }
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(wv: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = cb
                val intent = p?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try { fileChooserLauncher.launch(intent); true } catch (e: Exception) { filePathCallback?.onReceiveValue(null); filePathCallback = null; false }
            }
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun saveBlob(base64Data: String, mimeType: String, fileName: String) {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                saveBlobDirectly(decodedBytes, mimeType, fileName)
            }
        }, "BlobBridge")
    }

    private fun setupDownloadLogic() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            var rawName = ""
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                rawName = contentDisposition.substringAfter("filename=").substringBefore(";").replace("\"", "")
                if (rawName.startsWith("UTF-8''")) {
                    try { rawName = java.net.URLDecoder.decode(rawName.substring(7), "UTF-8") } catch (e: Exception) { rawName = rawName.substring(7) }
                }
            }
            if (rawName.isEmpty()) rawName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)

            val domainTrash = "(?i)(spotidown\\.app|y2mate\\.com|y2mate\\.bz|y2mate|spotidown)".toRegex()
            var cleanName = rawName.replace(domainTrash, "").trim()
            cleanName = cleanName.replace("[\\/:*?\"<>|]".toRegex(), "").replace("[\\s\\-_/]+".toRegex(), "_").removePrefix("_").removeSuffix("_")

            val extFromMime = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?: "mp3"
            if (cleanName.endsWith(".bin", true) || !cleanName.contains(".")) {
                val base = if (cleanName.contains(".")) cleanName.substringBeforeLast(".") else cleanName
                cleanName = "$base.$extFromMime"
            }

            // 🌟 LOGIC PEMOTONG NAMA FILE (MAKSIMAL 20 HURUF + EKSTENSI)
            val extension = if (cleanName.contains(".")) cleanName.substringAfterLast(".") else "mp3"
            val nameWithoutExt = if (cleanName.contains(".")) cleanName.substringBeforeLast(".") else cleanName

            val shortName = if (nameWithoutExt.length > 20) {
                nameWithoutExt.take(20) + "..."
            } else {
                nameWithoutExt
            }

            val finalFileName = "Muy_${shortName}.$extension"
            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
            val currentRef = webView.url ?: url

            val appContext = context?.applicationContext ?: return@setDownloadListener
            val sharedPrefs = appContext.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
            val treeUriStr = sharedPrefs.getString("last_folder", null)

            if (treeUriStr == null) {
                Toast.makeText(appContext, "Folder penyimpanan belum ditentukan!", Toast.LENGTH_LONG).show()
                return@setDownloadListener
            }

            val serviceIntent = Intent(appContext, DownloadService::class.java).apply {
                putExtra("URL", url)
                putExtra("MIME", mimetype)
                putExtra("NAME", finalFileName)
                putExtra("UA", userAgent)
                putExtra("REF", currentRef)
                putExtra("COOKIES", cookies)
                putExtra("TREE_URI", treeUriStr)
                putExtra("NOTIF_ID", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
            Toast.makeText(appContext, "Mengunduh di latar belakang...", Toast.LENGTH_SHORT).show()
        }
    } // Tanda kurung penutup aman di sini!

    private fun saveBlobDirectly(data: ByteArray, mimeType: String, fileName: String) {
        val ctx = context?.applicationContext ?: return
        val treeUri = ctx.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE).getString("last_folder", null) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri))
                val file = folder?.createFile(mimeType, "Muy_$fileName")
                file?.uri?.let { uri ->
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    withContext(Dispatchers.Main) { Toast.makeText(ctx, "Blob tersimpan!", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) { Log.e("MuyWeb", "Blob Fail") }
        }
    }
}
