package com.mcpeyen.unofficialmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var systemScriptManager: SystemScriptManager
    private var scriptInjected = false

    @SuppressLint("SetJavaScriptEnabled")
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        userScriptManager = UserScriptManager(this, lifecycleScope)
        systemScriptManager = SystemScriptManager(this, webView)

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress > 70 && !scriptInjected) {
                    injectScripts()
                    scriptInjected = true
                }
            }
        }

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

    private fun openScriptManager() {
        val intent = Intent(this, ScriptManagerActivity::class.java)
        ContextCompat.startActivity(this, intent, null) // Null for options
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

    @JavascriptInterface
    fun refreshPage() {
        runOnUiThread {
            scriptInjected = false
            webView.reload()
        }
    }

    private fun injectScripts() {
        lifecycleScope.launch {
            systemScriptManager.injectGreasemonkeyAPI()
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
            systemScriptManager.injectRefreshGesture()
            systemScriptManager.injectSettings()
            systemScriptManager.disableLongClick()
        }
    }
}