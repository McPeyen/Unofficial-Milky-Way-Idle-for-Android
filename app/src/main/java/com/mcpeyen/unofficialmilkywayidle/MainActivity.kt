package com.mcpeyen.unofficialmilkywayidle

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var userScriptManager: UserScriptManager? = null
    private var systemScriptManager: SystemScriptManager? = null

    @SuppressLint("SetJavaScriptEnabled")
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Inject scripts when page is loaded
                injectScripts()
            }
        }

        // Configure WebView settings
        val webSettings = webView!!.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        userScriptManager = UserScriptManager(this)
        systemScriptManager = SystemScriptManager(this, webView!!)

        // Load Milky Way Idle website
        webView!!.loadUrl("https://www.milkywayidle.com/")

        // Check for script updates
        updateScripts()
        injectScripts()

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


    private fun updateScripts() {
        Toast.makeText(this, "Updating scripts...", Toast.LENGTH_SHORT).show()
        userScriptManager!!.updateAllScripts {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Scripts updated",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    private fun injectScripts() {
        systemScriptManager!!.injectGreasemonkeyAPI()
        systemScriptManager!!.injectSettings()
        systemScriptManager!!.disableLongClick()
        userScriptManager!!.injectEnabledScripts(webView!!)
    }
}