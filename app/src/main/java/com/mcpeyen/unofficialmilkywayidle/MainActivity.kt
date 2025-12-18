package com.mcpeyen.unofficialmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var systemScriptManager: SystemScriptManager
    private var pageFinishedLoading = false
    private var pendingCharacterId: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        loadingOverlay = findViewById(R.id.loading_overlay)

        setupWebViewSettings()

        userScriptManager = UserScriptManager(this, lifecycleScope)
        systemScriptManager = SystemScriptManager(this, webView)

        webView.webChromeClient = setupWebChromeClient()
        webView.webViewClient = setupWebViewClient()

        webView.addJavascriptInterface(WebAppInterface(), "Android")
        webView.loadUrl("https://www.milkywayidle.com/")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    private fun setupWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }
        }
    }

    private fun setupWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                if (url?.startsWith("https://www.milkywayidle.com") == true) {
                    showLoadingScreen()
                    pageFinishedLoading = false
                    injectDocumentStartScripts()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val isMwi = url?.startsWith("https://www.milkywayidle.com") == true

                if (isMwi && !pageFinishedLoading) {
                    injectDocumentEndScripts()
                    pageFinishedLoading = true
                } else if (!isMwi) {
                    hideLoadingScreen()
                }
            }
        }
    }

    private fun injectDocumentStartScripts() {
        lifecycleScope.launch {
            systemScriptManager.injectLZString()
            systemScriptManager.injectGreasemonkeyAPI()
        }
    }

    private fun injectDocumentEndScripts() {
        lifecycleScope.launch {
            delay(1500L)

            val enabledCount = userScriptManager.getEnabledScriptCount()
            if (enabledCount > 0) {
                runOnUiThread {
                    findViewById<TextView>(R.id.loading_text).text =
                        "Loading Milky Way...\nInjecting $enabledCount script(s)..."
                }
                userScriptManager.updateEnabledScripts {
                    userScriptManager.injectEnabledScripts(webView)
                }
            }

            systemScriptManager.injectRefreshButton()
            systemScriptManager.injectSettings()
            systemScriptManager.disableLongClick()

            pendingCharacterId?.let { id ->
                runOnUiThread {
                    val js = """
                    (function() {
                        const charLink = document.querySelector('a[href*="characterId=$id"]');
                        if (charLink) {
                            charLink.click();
                        }
                    })();
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
                pendingCharacterId = null

                delay(1000L)
            }

            delay(500L)
            runOnUiThread {
                hideLoadingScreen()
            }
        }
    }

    private fun showLoadingScreen() {
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
        findViewById<TextView>(R.id.loading_text).text = "Loading Milky Way..."
    }

    private fun hideLoadingScreen() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun openScriptManager() {
        val intent = Intent(this, ScriptManagerActivity::class.java)
        startActivity(intent)
    }

    @JavascriptInterface
    fun refreshPage() {
        runOnUiThread {
            val currentUrl = webView.url
            pendingCharacterId = currentUrl?.substringAfter("characterId=", "")?.substringBefore("&")

            if (pendingCharacterId?.isEmpty() == true) {
                pendingCharacterId = null
            }

            webView.loadUrl("https://www.milkywayidle.com/characterSelect")
        }
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun openScriptManager() {
            this@MainActivity.openScriptManager()
        }

        @JavascriptInterface
        fun refreshPage() {
            this@MainActivity.refreshPage()
        }
    }
}
