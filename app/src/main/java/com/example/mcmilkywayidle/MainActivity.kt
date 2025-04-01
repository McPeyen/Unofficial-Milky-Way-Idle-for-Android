package com.example.mcmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var scriptManager: ScriptManager? = null

    @SuppressLint("SetJavaScriptEnabled")
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)


        // Initialize ScriptManager
        scriptManager = ScriptManager(this)


        // Initialize WebView
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
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT


        // Load Milky Way Idle website
        webView!!.loadUrl("https://www.milkywayidle.com/")


        // Check for script updates
        updateScripts()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_scripts) {
            openScriptManager()
            return true
        } else if (id == R.id.action_refresh) {
            webView!!.reload()
            return true
        } else if (id == R.id.action_update_scripts) {
            updateScripts()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (webView!!.canGoBack()) {
            webView!!.goBack()
        } else {
            super.onBackPressed()
        }
    }

    protected override fun onResume() {
        super.onResume()
        // Re-inject scripts when returning to the app (e.g., after changing script settings)
        injectScripts()
    }

    private fun openScriptManager() {
        val intent = Intent(this, ScriptManagerActivity::class.java)
        ContextCompat.startActivity(this, intent, null) // Null for options
    }

    private fun updateScripts() {
        Toast.makeText(this, "Updating scripts...", Toast.LENGTH_SHORT).show()
        scriptManager!!.updateAllScripts {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Scripts updated",
                    Toast.LENGTH_SHORT
                )
                    .show()
                injectScripts()
            }
        }
    }

    private fun injectScripts() {
        scriptManager!!.injectEnabledScripts(webView!!)
    }
}