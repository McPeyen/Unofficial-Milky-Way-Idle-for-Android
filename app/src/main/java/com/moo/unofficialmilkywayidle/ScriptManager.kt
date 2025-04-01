package com.moo.unofficialmilkywayidle

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.util.Log
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

class ScriptManager(private val context: Context) {
    private val preferences: SharedPreferences

    init {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initializeScriptsDirectory()
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
                mwiDependencies.put("enabled", true)
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
                mwitools.put("enabled", true)
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

    fun updateAllScripts(onComplete: Runnable?) {
        object : AsyncTask<Void?, Void?, Void?>() {

            override fun onPostExecute(result: Void?) {
                onComplete?.run()
            }

            override fun doInBackground(vararg params: Void?): Void? {
                try {
                    val config = loadConfig()
                    val scripts = config.getJSONArray("scripts")

                    for (i in 0..<scripts.length()) {
                        val script = scripts.getJSONObject(i)
                        if (script.getBoolean("autoUpdate")) {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating scripts", e)
                }
                return null
            }

        }.execute()
    }

    fun addScriptFromUrl(
        name: String,
        url: String,
        enabled: Boolean,
        autoUpdate: Boolean,
        callback: (Boolean) -> Unit
    ) {
        object : AsyncTask<Void?, Void?, Boolean>() {
            override fun doInBackground(vararg params: Void?): Boolean {
                try {
                    // Generate filename from name
                    val filename = name.lowercase(Locale.getDefault())
                        .replace("[^a-z0-9]".toRegex(), "_") + ".js"

                    // Download the script
                    val success = downloadScript(url, filename)
                    if (!success) {
                        return false
                    }

                    // Add to config
                    val config = loadConfig()
                    val scripts = config.getJSONArray("scripts")

                    // Check if script already exists
                    for (i in 0..<scripts.length()) {
                        val script = scripts.getJSONObject(i)
                        if (script.getString("filename") == filename) {
                            script.put("url", url)
                            script.put("enabled", enabled)
                            script.put("autoUpdate", autoUpdate)
                            script.put("lastUpdated", System.currentTimeMillis())
                            saveConfig(config)
                            return true
                        }
                    }

                    // Add new script
                    val script = JSONObject()
                    script.put("name", name)
                    script.put("url", url)
                    script.put("filename", filename)
                    script.put("enabled", enabled)
                    script.put("autoUpdate", autoUpdate)
                    script.put("lastUpdated", System.currentTimeMillis())
                    scripts.put(script)

                    saveConfig(config)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding script from URL", e)
                    return false
                }
            }

            override fun onPostExecute(success: Boolean) {
                callback(success)  // Note: now calling directly instead of callback?.onComplete()
            }


        }.execute()
    }

    fun addCustomScript(
        name: String,
        content: String,
        enabled: Boolean,
        callback: (Boolean) -> Unit  // Now accepts a lambda directly
    ) {
        object : AsyncTask<Void?, Void?, Boolean>() {
            override fun doInBackground(vararg voids: Void?): Boolean {
                try {
                    // Generate filename from name
                    val filename = name.lowercase(Locale.getDefault())
                        .replace("[^a-z0-9]".toRegex(), "_") + ".js"

                    // Save the script content
                    val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
                    val fos = FileOutputStream(file)
                    fos.write(content.toByteArray())
                    fos.close()

                    // Add to config
                    val config = loadConfig()
                    val scripts = config.getJSONArray("scripts")

                    // Check if script already exists
                    for (i in 0..<scripts.length()) {
                        val script = scripts.getJSONObject(i)
                        if (script.getString("filename") == filename) {
                            script.put("enabled", enabled)
                            script.put("custom", true)
                            script.put("lastUpdated", System.currentTimeMillis())
                            saveConfig(config)
                            return true
                        }
                    }

                    // Add new script
                    val script = JSONObject()
                    script.put("name", name)
                    script.put("filename", filename)
                    script.put("enabled", enabled)
                    script.put("custom", true)
                    script.put("lastUpdated", System.currentTimeMillis())
                    scripts.put(script)

                    saveConfig(config)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding custom script", e)
                    return false
                }
            }

            override fun onPostExecute(success: Boolean) {
                callback(success)  // Call the lambda directly
            }
        }.execute()
    }

    private fun downloadScript(scriptUrl: String, filename: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(scriptUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection!!.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(
                    TAG,
                    "Failed to download script: $responseCode"
                )
                return false
            }

            val inputStream = connection.inputStream
            val file = File(File(context.filesDir, SCRIPTS_DIR), filename)

            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var length: Int
            while ((inputStream.read(buffer).also { length = it }) > 0) {
                outputStream.write(buffer, 0, length)
            }
            outputStream.close()
            inputStream.close()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading script", e)
            return false
        } finally {
            connection?.disconnect()
        }
    }

    fun loadScriptContent(filename: String): String {
        try {
            val file = File(File(context.filesDir, SCRIPTS_DIR), filename)
            val stringBuilder = StringBuilder()
            val reader = BufferedReader(FileReader(file))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                stringBuilder.append(line).append('\n')
            }
            reader.close()
            return stringBuilder.toString()
        } catch (e: IOException) {
            Log.e(TAG, "Error loading script content", e)
            return ""
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
            injectGreasemonkeyAPI(webView)
            val config = loadConfig()
            val scripts = config.getJSONArray("scripts")

            for (i in 0..<scripts.length()) {
                val script = scripts.getJSONObject(i)
                if (script.getBoolean("enabled")) {
                    val filename = script.getString("filename")
                    Log.i("Starting Script Load", filename)
                    val content = loadScriptContent(filename)
                    if (!content.isEmpty()) {
                        webView.evaluateJavascript(content, null)
                        Log.i("Finished Script Load", filename)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting enabled scripts", e)
        }
    }

    private fun injectGreasemonkeyAPI(webView: WebView) {
        val gmFunctions = """
        (function() {
            const gmValues = {};

            window.GM_setValue = function(key, value) {
                gmValues[key] = value;
                localStorage.setItem('GM_' + key, JSON.stringify(value));
            };

            window.GM_getValue = function(key, defaultValue) {
                if (key in gmValues) return gmValues[key];

                const storedValue = localStorage.getItem('GM_' + key);
                if (storedValue !== null) {
                    try {
                        const value = JSON.parse(storedValue);
                        gmValues[key] = value;
                        return value;
                    } catch(e) {}
                }
                return defaultValue;
            };

            window.GM_addStyle = function(css) {
                const style = document.createElement('style');
                style.textContent = css;
                document.head.appendChild(style);
                return style;
            };

            window.GM_xmlhttpRequest = function(details) {
                const xhr = new XMLHttpRequest();
                xhr.open(details.method || 'GET', details.url);

                if (details.headers) {
                    for (const header in details.headers) {
                        xhr.setRequestHeader(header, details.headers[header]);
                    }
                }

                if (details.onload) xhr.onload = details.onload;
                if (details.onerror) xhr.onerror = details.onerror;

                xhr.send(details.data);
            };

            window.GM_notification = function(details, ondone) {
                if (typeof details === 'string') {
                    details = { text: details };
                }

                const notificationDiv = document.createElement('div');
                notificationDiv.style.cssText = `
                    position: fixed;
                    top: 20px;
                    right: 20px;
                    max-width: 300px;
                    background-color: #333;
                    color: white;
                    padding: 10px 15px;
                    border-radius: 5px;
                    z-index: 9999;
                    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
                    transition: opacity 0.3s;
                    opacity: 0;
                `;

                let notificationHTML = '';
                if (details.title) {
                    notificationHTML += `<div style="font-weight: bold; margin-bottom: 5px;">{"${'$'}"}{details.title}</div>`;
                }
                notificationHTML += `<div>{"${'$'}"}{details.text || ''}</div>`;

                if (details.image) {
                    notificationHTML = `<div style="display: flex; align-items: center;">
                        <img src="{"${'$'}"}{details.image}" style="max-width: 50px; max-height: 50px; margin-right: 10px;">
                        <div>{"${'$'}"}{notificationHTML}</div>
                    </div>`;
                }

                notificationDiv.innerHTML = notificationHTML;
                document.body.appendChild(notificationDiv);

                setTimeout(() => {
                    notificationDiv.style.opacity = '1';
                }, 10);

                if (details.onclick) {
                    notificationDiv.style.cursor = 'pointer';
                    notificationDiv.addEventListener('click', () => {
                        details.onclick();
                        if (details.clickToClose !== false) {
                            document.body.removeChild(notificationDiv);
                        }
                    });
                }

                const timeout = details.timeout || 5000;
                setTimeout(() => {
                    notificationDiv.style.opacity = '0';
                    setTimeout(() => {
                        if (document.body.contains(notificationDiv)) {
                            document.body.removeChild(notificationDiv);
                            if (typeof ondone === 'function') {
                                ondone();
                            }
                        }
                    }, 300);
                }, timeout);

                return function() {
                    if (document.body.contains(notificationDiv)) {
                        document.body.removeChild(notificationDiv);
                        if (typeof ondone === 'function') {
                            ondone();
                        }
                    }
                };
            };

            console.log('Greasemonkey API has been initialized with notification support');
        })();
    """.trimIndent()

        webView.evaluateJavascript(gmFunctions, null)
    }


    @Throws(JSONException::class, IOException::class)
    private fun loadConfig(): JSONObject {
        val configFile = File(context.filesDir, SCRIPTS_CONFIG)
        val stringBuilder = StringBuilder()
        val reader = BufferedReader(FileReader(configFile))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            stringBuilder.append(line)
        }
        reader.close()
        return JSONObject(stringBuilder.toString())
    }

    @Throws(IOException::class)
    private fun saveConfig(config: JSONObject) {
        val configFile = File(context.filesDir, SCRIPTS_CONFIG)
        val fos = FileOutputStream(configFile)
        fos.write(config.toString().toByteArray())
        fos.close()
    }

    interface ScriptCallback {
        fun onComplete(success: Boolean)
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