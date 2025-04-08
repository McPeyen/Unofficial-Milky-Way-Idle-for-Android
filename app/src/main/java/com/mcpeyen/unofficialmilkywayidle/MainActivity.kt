package com.mcpeyen.unofficialmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var userScriptManager: UserScriptManager? = null
    private var systemScriptManager: SystemScriptManager? = null

    @SuppressLint("SetJavaScriptEnabled")
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView!!.settings.javaScriptEnabled = true
        webView!!.settings.domStorageEnabled = true
        webView!!.settings.cacheMode = WebSettings.LOAD_DEFAULT

        userScriptManager = UserScriptManager(this)
        systemScriptManager = SystemScriptManager(this, webView!!)

        webView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                updateScripts()
            }
        }

        webView!!.addJavascriptInterface(WebAppInterface(), "Android")
        webView!!.loadUrl("https://www.milkywayidle.com/")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView!!.canGoBack()) {
                    webView!!.goBack()
                } else {
                    finish() // Close the activity
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
            // Call your existing openScriptManager method
            this@MainActivity.openScriptManager()
        }

        @JavascriptInterface
        fun refreshPage() {
            // Call your existing openScriptManager method
            this@MainActivity.refreshPage()
        }
    }

    @JavascriptInterface
    fun refreshPage() {
        runOnUiThread {
            webView!!.reload()
            webView!!.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    updateScripts()
                }
            }
        }
    }

    private fun updateScripts() {
        lifecycleScope.launch {
            val enabledCount = userScriptManager!!.getEnabledScriptCount()
            if (enabledCount > 0) {
                userScriptManager!!.updateEnabledScripts {
                    runOnUiThread {
                        systemScriptManager!!.injectGreasemonkeyAPI()
                        systemScriptManager!!.injectRefreshButton()
                        systemScriptManager!!.injectSettings()
                        systemScriptManager!!.disableLongClick()
                        userScriptManager!!.injectEnabledScripts(webView!!)

                        Toast.makeText(
                            this@MainActivity,
                            "Injecting $enabledCount script(s)...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                systemScriptManager!!.injectGreasemonkeyAPI()
                systemScriptManager!!.injectRefreshButton()
                systemScriptManager!!.injectSettings()
                systemScriptManager!!.disableLongClick()
            }
        }
    }
}