package com.mcpeyen.unofficialmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var systemScriptManager: SystemScriptManager
    private var pageFinishedLoading = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
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
            databaseEnabled = true // Important for some web games
            cacheMode = WebSettings.LOAD_DEFAULT

            allowFileAccess = true
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // Helps if any assets are on http
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
            /**
             * This method gives the host app a chance to take over control when a new URL is about to be loaded.
             * Returning false allows the WebView to handle the URL loading itself, which is what we want.
             * This is crucial for handling redirects and in-page navigation correctly.
             */
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Returning false ensures that all navigation stays within your WebView.
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageFinishedLoading = false
                injectDocumentStartScripts()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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
                userScriptManager.updateEnabledScripts {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Injecting $enabledCount script(s)...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    userScriptManager.injectEnabledScripts(webView)
                }
            }

            systemScriptManager.injectRefreshButton()
            systemScriptManager.injectSettings()
            systemScriptManager.disableLongClick()
        }
    }

    private fun openScriptManager() {
        val intent = Intent(this, ScriptManagerActivity::class.java)
        startActivity(intent)
    }

    @JavascriptInterface
    fun refreshPage() {
        runOnUiThread {
            webView.reload()
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
