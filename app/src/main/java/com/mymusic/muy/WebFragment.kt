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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        webView = WebView(requireContext())
        setupWebView()
        setupDownloadLogic() // Aktifkan pendeteksi download
        targetUrl?.let { webView.loadUrl(it) }
        return webView
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // INJEKSI: Samarkan branding & Paksa Dark Mode
                val jsHide = """
                    javascript:(function() { 
                        var selectors = ['header', '.navbar', 'footer', '.logo', '.ads-container'];
                        selectors.forEach(function(s) {
                            var el = document.querySelectorAll(s);
                            el.forEach(function(e) { e.style.display = 'none'; });
                        });
                        document.body.style.backgroundColor = '#000000';
                        document.body.style.color = '#FFFFFF';
                    })()
                """.trimIndent()
                view?.loadUrl(jsHide)
            }
        }
    }

    private fun setupDownloadLogic() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            // Saat user klik tombol download di web, fungsi ini jalan
            downloadToUserFolder(url, mimetype)
        }
    }

    private fun downloadToUserFolder(fileUrl: String, mimeType: String) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)
        val treeUriStr = prefs.getString("last_folder", null)

        if (treeUriStr == null) {
            Toast.makeText(context, "Pilih folder musik dulu di halaman utama!", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Mulai mengunduh ke folder pilihan...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folderUri = Uri.parse(treeUriStr)
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
                
                // Nama file otomatis pake timestamp biar gak bentrok
                val fileName = "Muy_DL_${System.currentTimeMillis()}.mp3"
                val newFile = rootFolder?.createFile(mimeType, fileName)

                if (newFile != null) {
                    val url = java.net.URL(fileUrl)
                    val connection = url.openConnection()
                    connection.connect()

                    val inputStream = connection.getInputStream()
                    context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Selesai: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal simpan file!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
