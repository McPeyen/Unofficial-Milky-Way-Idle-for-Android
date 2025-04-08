package com.mcpeyen.unofficialmilkywayidle

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

class UserScriptManager(private val context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        initializeScriptsDirectory()
    }

    suspend fun getUpdateScriptCount(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val config = loadConfig()
                val scripts = config.getJSONArray("scripts")

                var scriptsToUpdate = 0

                for (i in 0..<scripts.length()) {
                    val script = scripts.getJSONObject(i)
                    if (script.getBoolean("autoUpdate") && script.getBoolean("enabled")) {
                        scriptsToUpdate += 1
                    }
                }

                scriptsToUpdate
            } catch (e: Exception) {
                Log.e(TAG, "Error updating scripts", e)
                0
            }
        }
    }

    fun updateAllScripts(onComplete: Runnable?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = loadConfig()
                val scripts = config.getJSONArray("scripts")

                for (i in 0..<scripts.length()) {
                    val script = scripts.getJSONObject(i)
                    if (script.getBoolean("autoUpdate") && script.getBoolean("enabled")) {
                        val lastUpdated = script.getLong("lastUpdated")
                        val currentTime = System.currentTimeMillis()

                        // Update if it's been more than the update interval
                        if (currentTime - lastUpdated > UPDATE_INTERVAL) {
                            val url = script.getString("url")
                            val filename = script.getString("filename")
                            downloadScript(url, filename)

                            // Update last updated time
                            script.put("lastUpdated", currentTime)
                        }
                    }
                }

                // Save updated config
                saveConfig(config)

                // Switch to main thread to run the completion callback
                withContext(Dispatchers.Main) {
                    onComplete?.run()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating scripts", e)
            }
        }
    }

    fun addScriptFromUrl(
        name: String,
        url: String,
        enabled: Boolean,
        autoUpdate: Boolean,
        callback: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = try {
                // Generate filename from name
                val filename = name.lowercase(Locale.getDefault())
                    .replace("[^a-z0-9]".toRegex(), "_") + ".js"

                // Download the script
                val downloadSuccess = downloadScript(url, filename)
                if (!downloadSuccess) {
                    false
                } else {
                    // Add to config
                    val config = loadConfig()
                    val scripts = config.getJSONArray("scripts")

                    // Check if script already exists
                    var scriptExists = false
                    for (i in 0..<scripts.length()) {
                        val script = scripts.getJSONObject(i)
                        if (script.getString("filename") == filename) {
                            script.put("url", url)
                            script.put("enabled", enabled)
                            script.put("autoUpdate", autoUpdate)
                            script.put("lastUpdated", System.currentTimeMillis())
                            scriptExists = true
                            break
                        }
                    }

                    // Add new script if it doesn't exist
                    if (!scriptExists) {
                        val script = JSONObject()
                        script.put("name", name)
                        script.put("url", url)
                        script.put("filename", filename)
                        script.put("enabled", enabled)
                        script.put("autoUpdate", autoUpdate)
                        script.put("lastUpdated", System.currentTimeMillis())
                        scripts.put(script)
                    }

                    saveConfig(config)
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding script from URL", e)
                false
            }

            // Switch to main thread to call the callback
            withContext(Dispatchers.Main) {
                callback(success)
            }
        }
    }

    fun addCustomScript(
        name: String,
        content: String,
        enabled: Boolean,
        callback: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = try {
                // Generate filename from name
                val filename = name.lowercase(Locale.getDefault())
                    .replace("[^a-z0-9]".toRegex(), "_") + ".js"

                // Save the script content
                val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
                FileOutputStream(file).use { fos ->
                    fos.write(content.toByteArray())
                }

                // Add to config
                val config = loadConfig()
                val scripts = config.getJSONArray("scripts")

                // Check if script already exists
                var scriptExists = false
                for (i in 0..<scripts.length()) {
                    val script = scripts.getJSONObject(i)
                    if (script.getString("filename") == filename) {
                        script.put("enabled", enabled)
                        script.put("custom", true)
                        script.put("lastUpdated", System.currentTimeMillis())
                        scriptExists = true
                        break
                    }
                }

                // Add new script if it doesn't exist
                if (!scriptExists) {
                    val script = JSONObject()
                    script.put("name", name)
                    script.put("filename", filename)
                    script.put("enabled", enabled)
                    script.put("custom", true)
                    script.put("lastUpdated", System.currentTimeMillis())
                    scripts.put(script)
                }

                saveConfig(config)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding custom script", e)
                false
            }

            // Switch to main thread to call the callback
            withContext(Dispatchers.Main) {
                callback(success)
            }
        }
    }


    fun loadScriptContent(filename: String): String? {
        val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "Script file does not exist or is not a file: $filename")
            return null
        }

        return try {
            file.bufferedReader().use { reader ->
                reader.readText()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading script content for $filename", e)
            null
        }
    }

    fun setScriptEnabled(filename: String, enabled: Boolean) {
        try {
            val config = loadConfig()
            val scripts = config.getJSONArray("scripts")

            for (i in 0..<scripts.length()) {
                val script = scripts.getJSONObject(i)
                if (script.getString("filename") == filename) {
                    script.put("enabled", enabled)
                    break
                }
            }

            saveConfig(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting script enabled state", e)
        }
    }

    fun removeScript(filename: String) {
        try {
            // Delete the file
            val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
            if (file.exists()) {
                file.delete()
            }

            // Remove from config
            val config = loadConfig()
            val scripts = config.getJSONArray("scripts")
            val newScripts = JSONArray()

            for (i in 0..<scripts.length()) {
                val script = scripts.getJSONObject(i)
                if (script.getString("filename") != filename) {
                    newScripts.put(script)
                }
            }

            config.put("scripts", newScripts)
            saveConfig(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing script", e)
        }
    }

    fun allScripts(): List<ScriptInfo> {

        val scriptList: MutableList<ScriptInfo> = ArrayList()

        try {
            val config = loadConfig()
            val scripts = config.getJSONArray("scripts")

            for (i in 0..<scripts.length()) {
                val script = scripts.getJSONObject(i)

                val name = script.getString("name")
                val filename = script.getString("filename")
                val enabled = script.getBoolean("enabled")
                val custom = script.optBoolean("custom", false)
                val url = script.optString("url", "")
                val autoUpdate = script.optBoolean("autoUpdate", false)

                scriptList.add(ScriptInfo(name, filename, enabled, custom, url, autoUpdate))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all scripts", e)
        }

        return scriptList
    }

    fun injectEnabledScripts(webView: WebView) {
        try {
            val config = loadConfig()
            val scripts = config.optJSONArray("scripts") ?: return

            for (i in 0 until scripts.length()) {
                val script = scripts.optJSONObject(i) ?: continue
                if (script.optBoolean("enabled", false)) {
                    val filename = script.optString("filename")
                    if (filename.isNotEmpty()) {
                        Log.d(TAG, "Starting Script Load: $filename")
                        loadScriptContent(filename)?.let { content ->
                            webView.evaluateJavascript(content) { result ->
                                Log.d(TAG, "Script execution result for $filename: $result")
                            }
                            Log.d(TAG, "Finished Script Load: $filename")
                        } ?: Log.w(TAG, "Script content was empty: $filename")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting enabled scripts", e)
        }
    }

    private fun initializeScriptsDirectory() {
        val scriptsDir = File(context.filesDir, SCRIPTS_DIR)
        if (!scriptsDir.exists()) {
            scriptsDir.mkdir()
        }

        // Create initial config file if it doesn't exist
        val configFile = File(context.filesDir, SCRIPTS_CONFIG)
        if (!configFile.exists()) {
            try {
                // Initialize with default MWITools script
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
                mwiDependencies.put("autoUpdate", true)
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
                mwitools.put("autoUpdate", true)
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

    private fun downloadScript(scriptUrl: String, filename: String): Boolean {
        var connection: HttpURLConnection? = null

        return try {
            val file = File(File(context.filesDir, SCRIPTS_DIR), filename)

            // Setup connection
            val url = URL(scriptUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "*/*")
                instanceFollowRedirects = true
            }

            // Handle response
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.e(TAG, "Failed to download script: HTTP $responseCode - ${connection.responseMessage}"                )
                return false
            }

            // Get content length for progress tracking (if available)
            val contentLength = connection.contentLength
            if (contentLength > 0) {
                Log.d(TAG, "Downloading script ($contentLength bytes): $filename")
            } else {
                Log.d(TAG, "Downloading script (unknown size): $filename")
            }

            // Use Kotlin's extension functions to handle streams safely
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192) // Larger buffer for better performance
                    var bytesRead: Int
                    var totalBytesRead = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }

                    Log.d(TAG, "Successfully downloaded script ($totalBytesRead bytes): $filename")
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

    class ScriptInfo(
        val name: String, val filename: String, val isEnabled: Boolean, val isCustom: Boolean,
        val url: String, val isAutoUpdate: Boolean
    )

    companion object {
        private const val TAG = "ScriptManager"
        private const val PREFS_NAME = "ScriptPrefs"
        private const val SCRIPTS_CONFIG = "scripts_config.json"
        private const val SCRIPTS_DIR = "scripts"
        private val UPDATE_INTERVAL = TimeUnit.HOURS.toMillis(12) // Update scripts every 12 hours
    }
}