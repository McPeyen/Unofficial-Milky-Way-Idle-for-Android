package com.mcpeyen.unofficialmilkywayidle

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import at.pardus.android.webview.gm.run.WebViewGmApi
import at.pardus.android.webview.gm.store.ScriptStoreSQLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var systemScriptManager: SystemScriptManager
    private lateinit var scriptStore: ScriptStoreSQLite
    private lateinit var scriptSyncManager: ScriptSyncManager
    private lateinit var gmScriptInjector: GmScriptInjector
    private var pageFinishedLoading = false
    private var currentUrl: String = "https://www.milkywayidle.com/"
    private var resumeCount = 0

    // Tracks the current script sync job
    private var scriptSyncJob: Job? = null

    private val jsBridgeName = "WebViewGM"
    private val secret = UUID.randomUUID().toString()

    enum class StageState { PENDING, ACTIVE, DONE }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        loadingOverlay = findViewById(R.id.loading_overlay)

        setupWebViewSettings()

        scriptStore = ScriptStoreSQLite(this)
        userScriptManager = UserScriptManager(this, lifecycleScope)
        scriptSyncManager = ScriptSyncManager(userScriptManager, scriptStore)
        gmScriptInjector = GmScriptInjector(scriptStore, jsBridgeName, secret)

        systemScriptManager = SystemScriptManager(this, webView)

        webView.addJavascriptInterface(
            WebViewGmApi(webView, scriptStore, secret),
            jsBridgeName
        )
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        webView.webChromeClient = setupWebChromeClient()
        webView.webViewClient = setupWebViewClient()

        // Initial sync and load
        scriptSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            scriptStore.open()
            val updateJob = userScriptManager.updateEnabledScripts {
                scriptSyncManager.syncScripts()
            }
            updateJob.join()

            withContext(Dispatchers.Main) {
                applyDocumentStartScripts()
                webView.loadUrl("https://www.milkywayidle.com/")
            }
        }

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

    override fun onResume() {
        super.onResume()
        resumeCount++
        if (resumeCount <= 1) return
        
        scriptSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            val updateJob = userScriptManager.updateEnabledScripts {
                scriptSyncManager.syncScripts()
            }
            updateJob.join()
            
            withContext(Dispatchers.Main) {
                // If scripts changed while in manager, we re-apply them.
                // Note: addDocumentStartJavaScript scripts are cumulative per session 
                // unless the page is reloaded or the WebView is recreated.
                applyDocumentStartScripts()
            }
        }
    }

    private fun applyDocumentStartScripts() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val scripts = gmScriptInjector.getDocumentStartScripts("https://www.milkywayidle.com/")
            for (scriptJs in scripts) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    scriptJs,
                    setOf("https://www.milkywayidle.com", "https://*.milkywayidle.com")
                )
            }
            Log.i("MainActivity", "Registered ${scripts.size} document-start scripts via WebViewCompat")
        } else {
            Log.w("MainActivity", "DOCUMENT_START_SCRIPT feature not supported on this device")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            scriptStore.close()
        } catch (_: Exception) {}
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
                    currentUrl = url
                    showLoadingScreen()
                    pageFinishedLoading = false
                    
                    // We only inject manually if the modern API is NOT supported
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        injectDocumentStartScriptsLegacy(url)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val isMwi = url?.startsWith("https://www.milkywayidle.com") == true

                if (isMwi && !pageFinishedLoading) {
                    currentUrl = url ?: currentUrl
                    injectDocumentEndScripts(currentUrl)
                    pageFinishedLoading = true
                } else if (!isMwi) {
                    hideLoadingScreen()
                }
            }
        }
    }

    private fun injectDocumentStartScriptsLegacy(url: String) {
        lifecycleScope.launch {
            systemScriptManager.injectLZString()
            scriptSyncJob?.join()
            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, false)
            }
        }
    }

    private fun injectDocumentEndScripts(url: String) {
        lifecycleScope.launch {
            delay(1500L)

            runOnUiThread {
                setStageState(1, StageState.DONE)
                setStageState(2, StageState.ACTIVE)
            }

            val enabledCount = userScriptManager.getEnabledScriptCount()
            if (enabledCount > 0) {
                runOnUiThread {
                    findViewById<TextView>(R.id.stage2_text).text =
                        "Preparing $enabledCount script(s)..."
                }
            }

            scriptSyncJob?.join()
            scriptSyncManager.syncScripts()

            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, true)
            }

            runOnUiThread {
                setStageState(2, StageState.DONE)
                setStageState(3, StageState.ACTIVE)
            }

            systemScriptManager.injectProfileButtons()
            systemScriptManager.injectSettings()
            systemScriptManager.disableLongClick()

            delay(2000L)

            runOnUiThread {
                setStageState(3, StageState.DONE)
            }

            delay(500L)

            runOnUiThread {
                hideLoadingScreen()
            }
        }
    }

    private fun setStageState(stage: Int, state: StageState) {
        val spinnerId: Int
        val iconId: Int
        val textId: Int

        when (stage) {
            1 -> { spinnerId = R.id.stage1_spinner; iconId = R.id.stage1_icon; textId = R.id.stage1_text }
            2 -> { spinnerId = R.id.stage2_spinner; iconId = R.id.stage2_icon; textId = R.id.stage2_text }
            3 -> { spinnerId = R.id.stage3_spinner; iconId = R.id.stage3_icon; textId = R.id.stage3_text }
            else -> return
        }

        val spinner = findViewById<ProgressBar>(spinnerId)
        val icon = findViewById<ImageView>(iconId)
        val text = findViewById<TextView>(textId)

        when (state) {
            StageState.PENDING -> {
                spinner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_stage_pending)
                text.alpha = 0.5f
            }
            StageState.ACTIVE -> {
                spinner.visibility = View.VISIBLE
                icon.visibility = View.GONE
                text.alpha = 1.0f
            }
            StageState.DONE -> {
                spinner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_stage_done)
                text.alpha = 1.0f
            }
        }
    }

    private fun showLoadingScreen() {
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
        setStageState(1, StageState.ACTIVE)
        setStageState(2, StageState.PENDING)
        setStageState(3, StageState.PENDING)
        findViewById<TextView>(R.id.stage2_text).text = "Preparing scripts..."
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
