package com.mcpeyen.unofficialmilkywayidle

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

class UserScriptManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) {

    private val configMutex = Mutex()

    init {
        externalScope.launch(Dispatchers.IO) {
            initializeScriptsDirectory()
        }
    }

    suspend fun getEnabledScriptCount(): Int = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig()
            val scripts = config.optJSONArray("scripts") ?: return@withContext 0
            var count = 0
            for (i in 0 until scripts.length()) {
                if (scripts.getJSONObject(i).optBoolean("enabled")) {
                    count++
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enabled script count", e)
            0
        }
    }

    suspend fun updateEnabledScripts(onComplete: suspend () -> Unit): Job = externalScope.launch {
        try {
            configMutex.withLock { // 3. PROTECT READ-MODIFY-WRITE WITH MUTEX
                val config = loadConfig()
                val scripts = config.getJSONArray("scripts")
                var configChanged = false

                for (i in 0 until scripts.length()) {
                    val script = scripts.getJSONObject(i)
                    if (script.getBoolean("enabled")) {
                        val lastUpdated = script.getLong("lastUpdated")
                        val currentTime = System.currentTimeMillis()

                        if (lastUpdated == 0L) {
                            val url = script.getString("url")
                            val filename = script.getString("filename")
                            Log.d(TAG, "Updating script: $filename")
                            if (downloadScript(url, filename)) {
                                script.put("lastUpdated", currentTime)
                                configChanged = true
                            }
                        }
                    }
                }
                if (configChanged) saveConfig(config)
            }

            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scripts", e)
        }
    }

    suspend fun addScriptFromUrl(name: String, url: String, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val filename = generateFilename(name)
                if (!downloadScript(url, filename)) return@withContext false

                configMutex.withLock {
                    val config = loadConfig()
                    val scripts = config.getJSONArray("scripts")
                    val existingScript = findScriptByFilename(scripts, filename)

                    if (existingScript != null) {
                        existingScript.put("url", url)
                        existingScript.put("enabled", enabled)
                        existingScript.put("lastUpdated", System.currentTimeMillis())
                    } else {
                        val newScript = JSONObject().apply {
                            put("name", name)
                            put("url", url)
                            put("filename", filename)
                            put("enabled", enabled)
                            put("lastUpdated", System.currentTimeMillis())
                        }
                        scripts.put(newScript)
                    }
                    saveConfig(config)
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding script from URL", e)
                false
            }
        }

    suspend fun addCustomScript(name: String, content: String, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val filename = generateFilename(name)
                val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
                file.writeText(content) // Simpler way to write string to file

                configMutex.withLock {
                    val config = loadConfig()
                    val scripts = config.getJSONArray("scripts")
                    val existingScript = findScriptByFilename(scripts, filename)

                    if (existingScript != null) {
                        existingScript.put("enabled", enabled)
                        existingScript.put("custom", true)
                        existingScript.put("lastUpdated", System.currentTimeMillis())
                    } else {
                        val newScript = JSONObject().apply {
                            put("name", name)
                            put("filename", filename)
                            put("enabled", enabled)
                            put("custom", true)
                            put("lastUpdated", System.currentTimeMillis())
                        }
                        scripts.put(newScript)
                    }
                    saveConfig(config)
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding custom script", e)
                false
            }
        }


    suspend fun loadScriptContent(filename: String): String? = withContext(Dispatchers.IO) {
        val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
        if (!file.exists()) return@withContext null
        try {
            file.readText()
        } catch (e: IOException) {
            Log.e(TAG, "Error loading script content for $filename", e)
            null
        }
    }

    suspend fun setScriptEnabled(filename: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            configMutex.withLock {
                val config = loadConfig()
                val scripts = config.getJSONArray("scripts")
                findScriptByFilename(scripts, filename)?.put("enabled", enabled)
                saveConfig(config)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting script enabled state", e)
        }
    }

    suspend fun removeScript(filename: String) = withContext(Dispatchers.IO) {
        try {
            configMutex.withLock {
                val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
                if (file.exists()) file.delete()

                val config = loadConfig()
                val scripts = config.getJSONArray("scripts")
                val newScripts = JSONArray()
                for (i in 0 until scripts.length()) {
                    val script = scripts.getJSONObject(i)
                    if (script.getString("filename") != filename) {
                        newScripts.put(script)
                    }
                }
                config.put("scripts", newScripts)
                saveConfig(config)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing script", e)
        }
    }

    suspend fun saveScriptOrder(scripts: List<ScriptInfo>) = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig()

            val jsonArray = JSONArray()
            for (script in scripts) {
                val scriptJson = JSONObject()
                scriptJson.put("name", script.name)
                scriptJson.put("filename", script.filename)
                scriptJson.put("enabled", script.isEnabled)
                scriptJson.put("custom", script.isCustom)
                scriptJson.put("url", script.url)
                scriptJson.put("lastUpdated", script.lastUpdated)
                jsonArray.put(scriptJson)
            }

            config.put("scripts", jsonArray)
            saveConfig(config)
        } catch (e: Exception) {
            android.util.Log.e("UserScriptManager", "Error saving script order", e)
        }
    }

    suspend fun getAllScripts(): List<ScriptInfo> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig()
            val scripts = config.optJSONArray("scripts") ?: return@withContext emptyList()
            List(scripts.length()) { i ->
                val script = scripts.getJSONObject(i)
                ScriptInfo(
                    name = script.getString("name"),
                    filename = script.getString("filename"),
                    isEnabled = script.getBoolean("enabled"),
                    isCustom = script.optBoolean("custom", false),
                    url = script.optString("url", ""),
                    lastUpdated = script.getLong("lastUpdated")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all scripts", e)
            emptyList()
        }
    }

    suspend fun injectEnabledScripts(webView: WebView) {
        val enabledScriptContents = withContext(Dispatchers.IO) {
            try {
                val config = loadConfig()
                val scripts =
                    config.optJSONArray("scripts") ?: return@withContext emptyList<String>()

                val contentList = mutableListOf<String>()
                for (i in 0 until scripts.length()) {
                    val script = scripts.optJSONObject(i)
                    if (script?.optBoolean("enabled", false) == true) {
                        val filename = script.optString("filename")
                        if (filename.isNotEmpty()) {
                            loadScriptContent(filename)?.let { content ->
                                contentList.add(content)
                                Log.d(TAG, "Queued script for injection: $filename")
                            }
                        }
                    }
                }
                contentList // Return the list of script contents
            } catch (e: Exception) {
                Log.e(TAG, "Error loading script files", e)
                emptyList<String>() // Return an empty list on error
            }
        }

        // Now, if we have scripts, switch to the Main thread to interact with the WebView.
        if (enabledScriptContents.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                Log.d(
                    TAG,
                    "Creating and injecting master loader for ${enabledScriptContents.size} scripts."
                )
                val masterLoaderScript = createMasterLoaderScript(enabledScriptContents)

                webView.evaluateJavascript(masterLoaderScript) {
                    // This callback also runs on the Main thread.
                    Log.d(TAG, "Master loader has been successfully dispatched to the WebView.")
                }
            }
        } else {
            Log.d(TAG, "No enabled scripts to inject.")
        }
    }

    private fun createMasterLoaderScript(scriptContents: List<String>): String {
        val scriptsJsonArray = JSONArray(scriptContents).toString()

        return """
            const pollForGameReady_MASTER_$$ = setInterval(() => {
                // We will use the more reliable DOM check
                    const targetNode = document.querySelector("div.GamePage_mainPanel__2njyb");
                    if (true) {
                        clearInterval(pollForGameReady_MASTER_$$);
                        console.log('Wrapper: Game UI is ready. Injecting all ${scriptContents.size} enabled scripts.');

                        const scriptsToInject = ${scriptsJsonArray};

                        scriptsToInject.forEach((scriptContent, index) => {
                            try {
                                const scriptElement = document.createElement('script');
                                scriptElement.type = 'text/javascript';
                                scriptElement.textContent = scriptContent;
                                document.head.appendChild(scriptElement);
                                console.log('Wrapper: Successfully injected script #' + (index + 1) + '.');
                            } catch (e) {
                                console.error('Wrapper: Error injecting script #' + (index + 1) + ':', e);
                            }
                        });
                    } else {
                        console.log('Wrapper: Waiting for game UI to be ready...');
                    }
                }, 300);
                """
    }

    private suspend fun initializeScriptsDirectory() {
        val scriptsDir = File(context.filesDir, SCRIPTS_DIR)
        if (!scriptsDir.exists()) {
            scriptsDir.mkdir()
        }

        val configFile = File(context.filesDir, SCRIPTS_CONFIG)
        if (!configFile.exists()) {
            try {
                val scripts = JSONArray()

                val mwiDependencies = JSONObject()
                mwiDependencies.put("name", "MWITools-Dependencies")
                mwiDependencies.put(
                    "url",
                    "https://raw.githubusercontent.com/YangLeda/Userscripts-For-MilkyWayIdle/refs/heads/main/MWITools%20addon%20for%20Steam%20version.js"
                )
                mwiDependencies.put("filename", "mwitools_dependencies.js")
                mwiDependencies.put("enabled", false)
                mwiDependencies.put("lastUpdated", 0)
                scripts.put(mwiDependencies)

                val mwitools = JSONObject()
                mwitools.put("name", "MWITools")
                mwitools.put(
                    "url",
                    "https://greasyfork.org/en/scripts/494467-mwitools/code/script.user.js"
                )
                mwitools.put("filename", "mwitools.js")
                mwitools.put("enabled", false)
                mwitools.put("lastUpdated", 0)
                scripts.put(mwitools)

                val config = JSONObject()
                config.put("scripts", scripts)

                val fos = FileOutputStream(configFile)
                fos.write(config.toString().toByteArray())
                fos.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating initial config", e)
            }
        }
    }

    private suspend fun downloadScript(scriptUrl: String, filename: String): Boolean =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            return@withContext try {
                val file = File(File(context.filesDir, SCRIPTS_DIR), filename)

                val url = URL(scriptUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "*/*")
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.e(
                        TAG,
                        "Failed to download script: HTTP $responseCode - ${connection.responseMessage}"
                    )
                    false
                }

                val contentLength = connection.contentLength
                if (contentLength > 0) {
                    Log.d(TAG, "Downloading script ($contentLength bytes): $filename")
                } else {
                    Log.d(TAG, "Downloading script (unknown size): $filename")
                }

                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                        }

                        Log.d(
                            TAG,
                            "Successfully downloaded script ($totalBytesRead bytes): $filename"
                        )
                    }
                }

                true
            } catch (e: IOException) {
                Log.e(TAG, "Network error downloading script $filename: ${e.message}", e)
                false
            } catch (e: SecurityException) {
                Log.e(TAG, "Security error downloading script $filename: ${e.message}", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error downloading script $filename: ${e.message}", e)
                false
            } finally {
                connection?.disconnect()
            }
        }

    private fun findScriptByFilename(scripts: JSONArray, filename: String): JSONObject? {
        for (i in 0 until scripts.length()) {
            val script = scripts.getJSONObject(i)
            if (script.getString("filename") == filename) {
                return script
            }
        }
        return null
    }

    private fun generateFilename(name: String): String {
        return name.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]"), "_") + ".js"
    }

    @Throws(JSONException::class)
    private fun loadConfig(): JSONObject {
        val configFile = File(context.filesDir, SCRIPTS_CONFIG)
        if (!configFile.exists()) {
            Log.w(TAG, "Config file does not exist: ${configFile.absolutePath}")
            return JSONObject()
        }

        return try {
            val jsonString = configFile.bufferedReader().use { it.readText() }
            JSONObject(jsonString)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading config file", e)
            JSONObject()
        }
    }

    @Throws(IOException::class)
    private fun saveConfig(config: JSONObject) {
        val configFile = File(context.filesDir, SCRIPTS_CONFIG)
        val fos = FileOutputStream(configFile)
        fos.write(config.toString().toByteArray())
        fos.close()
    }

    suspend fun updateScriptContentFromUrl(script: ScriptInfo, index: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            downloadScript(script.url, script.filename)

            val config = loadConfig()
            val scriptsArray = config.getJSONArray("scripts")
            val scriptJson = scriptsArray.getJSONObject(index)

            scriptJson.put("lastUpdated", System.currentTimeMillis())

            scriptsArray.put(index, scriptJson)
            config.put("scripts", scriptsArray)
            saveConfig(config)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update script at index $index", e)
            false
        }
    }

    data class ScriptInfo(
        var name: String,
        var filename: String,
        var isEnabled: Boolean,
        var isCustom: Boolean,
        var url: String,
        var lastUpdated: Long
    )

    companion object {
        private const val TAG = "ScriptManager"
        private const val PREFS_NAME = "ScriptPrefs"
        private const val SCRIPTS_CONFIG = "scripts_config.json"
        private const val SCRIPTS_DIR = "scripts"
        private val UPDATE_INTERVAL = TimeUnit.HOURS.toMillis(12)
    }
}