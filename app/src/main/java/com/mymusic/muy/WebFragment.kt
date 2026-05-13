package com.mymusic.muy

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebFragment : Fragment() {

    private lateinit var webView: WebView
    private var targetUrl: String? = null

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
        
        // 1. TEMBAK DARK MODE (Native & JS Backup)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // 2. TEMBAK PENGGANTI NAMA (SP Down / YT Down) & MATIKAN IKLAN
                val jsClean = """
                    (function() {
                        // Ganti Title Berdasarkan URL
                        var currentUrl = window.location.href;
                        if (currentUrl.includes('spotifydown')) { document.title = 'SP Downloader'; }
                        if (currentUrl.includes('y2mate')) { document.title = 'YT Downloader'; }

                        // Matikan Iklan & Elemen Sampah
                        var badElements = [
                            'header', '.navbar', 'footer', '.logo', 
                            '.ads-container', '#ad-slot', '.ad-box', 
                            'iframe[src*="googleads"]', 'div[id*="ai_widget"]'
                        ];
                        badElements.forEach(function(selector) {
                            document.querySelectorAll(selector).forEach(function(el) {
                                el.remove(); 
                            });
                        });

                        // Paksa Background Hitam jika native dark mode gagal
                        document.body.style.backgroundColor = '#000000';
                        document.body.style.color = '#FFFFFF';
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(jsClean, null)
            }

            // Mencegah iklan buka tab baru (pop-up)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                // Hanya izinkan navigasi jika masih di domain yang sama atau domain downloader
                return if (url.contains("spotifydown") || url.contains("y2mate") || url.contains("google.com")) {
                    false 
                } else {
                    true // Blokir navigasi ke situs iklan luar
                }
            }
        }
    }

    private fun setupDownloadLogic() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            // Ambil nama file asli dari Content-Disposition jika ada
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            if (fileName.endsWith(".bin")) fileName = "Muy_Download_${System.currentTimeMillis()}.mp3"
            
            downloadToUserFolder(url, mimetype, fileName)
        }
    }

    private fun downloadToUserFolder(fileUrl: String, mimeType: String, fileName: String) {
        val context = requireContext()
        val treeUriStr = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
            .getString("last_folder", null)

        if (treeUriStr == null) {
            Toast.makeText(context, "Pilih folder musik dulu!", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Mengunduh: $fileName", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folderUri = Uri.parse(treeUriStr)
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
                val newFile = rootFolder?.createFile(mimeType, fileName)

                if (newFile != null) {
                    val url = java.net.URL(fileUrl)
                    val connection = url.openConnection()
                    connection.setRequestProperty("User-Agent", webView.settings.userAgentString)
                    connection.connect()

                    val inputStream = connection.getInputStream()
                    context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Berhasil Simpan ke Folder Musik", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
