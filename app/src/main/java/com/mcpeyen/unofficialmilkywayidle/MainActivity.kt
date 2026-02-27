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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

    // Tracks the current script sync job so document-start injection can await it
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

        // Initialize script store (SQLite) and sync scripts eagerly
        // so they're available for document-start injection
        scriptStore = ScriptStoreSQLite(this)
        userScriptManager = UserScriptManager(this, lifecycleScope)
        scriptSyncManager = ScriptSyncManager(userScriptManager, scriptStore)
        gmScriptInjector = GmScriptInjector(scriptStore, jsBridgeName, secret)

        scriptSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            scriptStore.open()
            // Download any pending scripts, then sync to SQLite
            val updateJob = userScriptManager.updateEnabledScripts {
                scriptSyncManager.syncScripts()
            }
            updateJob.join()
        }

        systemScriptManager = SystemScriptManager(this, webView)

        // Register the GM API bridge (handles GM_getValue, GM_setValue, etc. from JS)
        webView.addJavascriptInterface(
            WebViewGmApi(webView, scriptStore, secret),
            jsBridgeName
        )

        // Keep the Android interface for openScriptManager/refreshPage
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        webView.webChromeClient = setupWebChromeClient()
        webView.webViewClient = setupWebViewClient()
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

    override fun onResume() {
        super.onResume()
        resumeCount++
        if (resumeCount <= 1) return // Skip first onResume (onCreate already synced)
        // Re-sync scripts when returning from ScriptManager (add/remove/enable/disable)
        scriptSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            val updateJob = userScriptManager.updateEnabledScripts {
                scriptSyncManager.syncScripts()
            }
            updateJob.join()
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
                    injectDocumentStartScripts(url)
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

    private fun injectDocumentStartScripts(url: String) {
        lifecycleScope.launch {
            // LZ-String is still needed by some scripts
            systemScriptManager.injectLZString()

            // Wait for script sync to finish so @require libraries are in SQLite
            scriptSyncJob?.join()

            // Inject document-start scripts via the GM injector
            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, false)
            }
        }
    }

    private fun injectDocumentEndScripts(url: String) {
        lifecycleScope.launch {
            delay(1500L)

            // Stage 1 done: page loaded
            runOnUiThread {
                setStageState(1, StageState.DONE)
                setStageState(2, StageState.ACTIVE)
            }

            // Show the count early so user sees it immediately
            val enabledCount = userScriptManager.getEnabledScriptCount()
            if (enabledCount > 0) {
                runOnUiThread {
                    findViewById<TextView>(R.id.stage2_text).text =
                        "Preparing $enabledCount script(s)..."
                }
            }

            // Ensure the eager sync from onCreate/onResume is done,
            // then re-sync to pick up any changes
            scriptSyncJob?.join()
            scriptSyncManager.syncScripts()

            // Inject all document-end scripts via the library
            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, true)
            }

            // Stage 2 done: scripts injected
            runOnUiThread {
                setStageState(2, StageState.DONE)
                setStageState(3, StageState.ACTIVE)
            }

            // These stay the same - they're UI features, not GM API
            systemScriptManager.injectProfileButtons()
            systemScriptManager.injectSettings()
            systemScriptManager.disableLongClick()

            // Allow scripts time to initialize before hiding the loading screen
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
        // Reset stage 2 text to default
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
