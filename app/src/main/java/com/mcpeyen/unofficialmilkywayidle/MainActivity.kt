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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout // Reference to the overlay
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var systemScriptManager: SystemScriptManager
    private var pageFinishedLoading = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        loadingOverlay = findViewById(R.id.loading_overlay) // Find the view

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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // SHOW the loading screen immediately when any page starts loading (including refreshes)
                showLoadingScreen()

                pageFinishedLoading = false
                injectDocumentStartScripts()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Do NOT hide the loading screen here yet.
                // We wait for the scripts to inject in the method below.
                if (!pageFinishedLoading) {
                    injectDocumentEndScripts()
                    pageFinishedLoading = true
                }
            }
        }
    }

    private fun injectDocumentStartScripts() {
        lifecycleScope.launch {
            systemScriptManager.injectGreasemonkeyAPI()
        }
    }

    private fun injectDocumentEndScripts() {
        lifecycleScope.launch {
            delay(1500L)

            val enabledCount = userScriptManager.getEnabledScriptCount()
            if (enabledCount > 0) {
                // Update loading screen text
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

            delay(1500L)
            runOnUiThread {
                hideLoadingScreen()
            }
        }
    }

    private fun showLoadingScreen() {
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
        // Reset text when showing
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
