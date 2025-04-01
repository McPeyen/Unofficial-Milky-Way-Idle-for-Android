package com.example.mcmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
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

    private fun injectSettings(webView: WebView) {
        val jsCode = """
        (function mcMwiSettings () {
            const targetNode = document.querySelector("div.SettingsPanel_gameTab__n2hAG");
            if (targetNode) {
                // Create container div to hold text and button
                let container = document.createElement("div");
                container.style.display = "flex";
                container.style.alignItems = "center";
                container.style.margin = "10px 0";
                
                // Create text label
                let label = document.createElement("span");
                label.innerHTML = "Open Script Manager: ";
                label.style.marginRight = "10px";
            
                // Create button with icon
                let button = document.createElement("button");
                button.style.backgroundColor = "#4357af";
                button.style.color = "white";
                button.style.border = "none";
                button.style.borderRadius = "4px";
                button.style.padding = "5px 10px";
                button.style.cursor = "pointer";
                button.style.display = "flex";
                button.style.alignItems = "center";
                button.style.justifyContent = "center";
            
                // Add icon (using a simple SVG code icon)
                button.innerHTML = `
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                        <polyline points="14 2 14 8 20 8"></polyline>
                        <line x1="16" y1="13" x2="8" y2="13"></line>
                        <line x1="16" y1="17" x2="8" y2="17"></line>
                        <polyline points="10 9 9 9 8 9"></polyline>
                    </svg>
                    Script Manager
                `;
            
                // Add click event listener to call openScriptManager()
                button.addEventListener("click", function() {
                    window.Android.openScriptManager();
                });
            
                // Assemble and add to the page
                container.appendChild(label);
                container.appendChild(button);
                targetNode.insertAdjacentElement("beforeend", container);
            
            } else {
                setTimeout(mcMwiSettings, 500);
            }
        })();
    """.trimIndent()

        webView.addJavascriptInterface(WebAppInterface(), "Android")
        webView.evaluateJavascript(jsCode, null)
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun openScriptManager() {
            // Call your existing openScriptManager method
            this@MainActivity.openScriptManager()
        }
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
        injectSettings(webView!!)
        scriptManager!!.injectEnabledScripts(webView!!)
    }
}