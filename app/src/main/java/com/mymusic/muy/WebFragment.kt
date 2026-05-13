package com.mymusic.muy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment

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
        targetUrl?.let { webView.loadUrl(it) }
        return webView
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        
        // Agar ringan
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // --- PROSES SUNTIK / INJEKSI ---
                // Kita hapus header/footer asli web mereka biar gak kelihatan promosi gratis
                
                val jsHideElements = """
                    javascript:(function() { 
                        // Hapus elemen header atau logo yang sering muncul
                        var selectors = [
                            'header', '.navbar', 'footer', '#header', '.logo', 
                            '.ads-container', '.banner-ad'
                        ];
                        selectors.forEach(function(selector) {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) { el.style.display = 'none'; });
                        });
                        
                        // Khusus buat dark mode paksa biar masuk ke tema Muy
                        document.body.style.backgroundColor = '#000000';
                        document.body.style.color = '#FFFFFF';
                    })()
                """.trimIndent()
                
                view?.loadUrl(jsHideElements)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Biar tetep di dalam aplikasi, gak loncat ke Chrome
                return false 
            }
        }
    }
}
