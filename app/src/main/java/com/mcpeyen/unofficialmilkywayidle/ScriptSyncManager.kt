package com.mcpeyen.unofficialmilkywayidle

import android.util.Log
import at.pardus.android.webview.gm.model.Script
import at.pardus.android.webview.gm.model.ScriptId
import at.pardus.android.webview.gm.store.ScriptStoreSQLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges UserScriptManager (file-based source of truth) with ScriptStoreSQLite (execution engine).
 * Syncs enabled scripts into the SQLite store so GmScriptInjector can inject them with
 * proper GM API, @require support, and IIFE wrapping.
 */
class ScriptSyncManager(
    private val userScriptManager: UserScriptManager,
    private val scriptStore: ScriptStoreSQLite
) {
    companion object {
        private const val TAG = "ScriptSyncManager"
        private const val DEFAULT_NAMESPACE = "com.mcpeyen.unofficialmilkywayidle"
        private const val MWI_MATCH = "*://*.milkywayidle.com/*"
        private const val MWI_TEST_MATCH = "*://test.milkywayidle.com/*"
    }

    /**
     * Syncs all scripts from UserScriptManager into ScriptStoreSQLite.
     * - Enabled scripts are added/updated in the store
     * - Disabled/removed scripts are deleted from the store
     *
     * Must be called off the UI thread (downloads @require dependencies).
     */
    suspend fun syncScripts() = withContext(Dispatchers.IO) {
        try {
            val allScripts = userScriptManager.getAllScripts()

            // Track all ScriptIds we've synced so we can clean up orphans
            val syncedScriptIds = mutableSetOf<Pair<String, String>>() // (name, namespace)

            for (scriptInfo in allScripts) {
                val content = userScriptManager.loadScriptContent(scriptInfo.filename)
                if (content == null) {
                    Log.w(TAG, "Could not load content for ${scriptInfo.filename}, skipping")
                    continue
                }

                if (!scriptInfo.isEnabled) {
                    // For disabled scripts, try to find and delete them from the store
                    // They could be stored under the parsed name or the config name
                    tryDeleteScript(scriptInfo.name, content, scriptInfo.url)
                    continue
                }

                // Try to parse the script with its UserScript metadata
                val parsedScript = try {
                    Script.parse(content, scriptInfo.url.ifEmpty { null })
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing script ${scriptInfo.name}", e)
                    null
                }

                if (parsedScript != null) {
                    // Script has valid UserScript metadata — add with full metadata
                    Log.d(TAG, "Syncing script with metadata: ${parsedScript.name} (namespace: ${parsedScript.namespace})")
                    scriptStore.add(parsedScript)
                    scriptStore.enable(ScriptId(parsedScript.name, parsedScript.namespace))
                    syncedScriptIds.add(Pair(parsedScript.name, parsedScript.namespace))
                } else {
                    // No UserScript header (e.g., raw library script) — create synthetic Script
                    Log.d(TAG, "Syncing raw script (no metadata): ${scriptInfo.name}")
                    val syntheticScript = Script(
                        scriptInfo.name,           // name
                        DEFAULT_NAMESPACE,         // namespace
                        null,                      // exclude
                        null,                      // include
                        arrayOf(MWI_MATCH, MWI_TEST_MATCH), // match
                        null,                      // description
                        scriptInfo.url.ifEmpty { null }, // downloadurl
                        null,                      // updateurl
                        null,                      // installurl
                        null,                      // icon
                        null,                      // runAt (default = document-end)
                        true,                      // unwrap (global scope for libraries)
                        null,                      // version
                        null,                      // requires
                        null,                      // resources
                        content                    // content
                    )
                    scriptStore.add(syntheticScript)
                    scriptStore.enable(ScriptId(scriptInfo.name, DEFAULT_NAMESPACE))
                    syncedScriptIds.add(Pair(scriptInfo.name, DEFAULT_NAMESPACE))
                }
            }

            // Clean up scripts in the store that are no longer in UserScriptManager
            val storedScripts = scriptStore.all
            if (storedScripts != null) {
                for (stored in storedScripts) {
                    val key = Pair(stored.name, stored.namespace)
                    if (key !in syncedScriptIds) {
                        Log.d(TAG, "Removing orphaned script from store: ${stored.name} (${stored.namespace})")
                        scriptStore.delete(ScriptId(stored.name, stored.namespace))
                    }
                }
            }

            Log.d(TAG, "Script sync completed. ${syncedScriptIds.size} scripts synced.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during script sync", e)
        }
    }

    /**
     * Tries to delete a script from the store, checking both the config name
     * and the parsed metadata name (which may differ).
     */
    private fun tryDeleteScript(configName: String, content: String, url: String) {
        // Try deleting by config name with default namespace
        scriptStore.delete(ScriptId(configName, DEFAULT_NAMESPACE))

        // Also try parsing metadata to find the actual stored name/namespace
        try {
            val parsed = Script.parse(content, url.ifEmpty { null })
            if (parsed != null) {
                scriptStore.delete(ScriptId(parsed.name, parsed.namespace))
            }
        } catch (_: Exception) {
            // Parsing may fail for scripts without headers, which is fine
        }
    }
}
