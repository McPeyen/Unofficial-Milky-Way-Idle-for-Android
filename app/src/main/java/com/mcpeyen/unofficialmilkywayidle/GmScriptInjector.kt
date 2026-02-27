package com.mcpeyen.unofficialmilkywayidle

import android.util.Log
import android.webkit.WebView
import at.pardus.android.webview.gm.model.Script
import at.pardus.android.webview.gm.store.ScriptStore
import java.util.UUID

/**
 * Injects userscripts into a WebView with full Greasemonkey API support.
 * Refined to ensure @require order is preserved.
 */
class GmScriptInjector(
    private val scriptStore: ScriptStore,
    private val jsBridgeName: String,
    private val secret: String
) {
    companion object {
        private const val TAG = "GmScriptInjector"

        private const val JS_UNSAFE_WINDOW_INIT = """
            var unsafeWindow = (function() {
                try {
                    var el = document.createElement('p');
                    el.setAttribute('onclick', 'return window;');
                    return el.onclick();
                } catch (e) {
                    return window;
                }
            })();
            window.wrappedJSObject = unsafeWindow;
        """

        private const val JSMISSINGFUNCTION =
            "function() { GM_log(\"Called function not yet implemented\"); };\n"
    }

    fun injectScripts(webView: WebView, url: String, pageFinished: Boolean) {
        val matchingScripts = scriptStore.get(url) ?: return

        for (script in matchingScripts) {
            val shouldRun = if (!pageFinished) {
                Script.RUNATSTART == script.runAt
            } else {
                script.runAt == null || Script.RUNATEND == script.runAt
            }

            if (!shouldRun) continue

            Log.i(TAG, "Injecting script \"${script.name}\" on $url")
            val jsCode = buildScriptJs(script)
            webView.evaluateJavascript(jsCode, null)
        }
    }

    fun getDocumentStartScripts(url: String): List<String> {
        val matchingScripts = scriptStore.get(url) ?: return emptyList()
        return matchingScripts.filter { it.runAt == Script.RUNATSTART }.map {
            Log.i(TAG, "Preparing document-start script \"${it.name}\" for $url")
            buildScriptJs(it)
        }
    }

    private fun buildScriptJs(script: Script): String {
        val escapedName = script.name.replace("\"", "\\\"")
        val escapedNamespace = script.namespace.replace("\"", "\\\"")
        val defaultSignature = "\"$escapedName\", \"$escapedNamespace\", \"$secret\""

        val callbackPrefix = ("GM_" + script.name + script.namespace + UUID.randomUUID().toString())
            .replace(Regex("[^0-9a-zA-Z_]"), "")

        val jsApi = buildString {
            append("var GM_listValues = function() { return $jsBridgeName.listValues($defaultSignature).split(\",\"); };\n")
            append("var GM_getValue = function(name, defaultValue) { return $jsBridgeName.getValue($defaultSignature, name, defaultValue); };\n")
            append("var GM_setValue = function(name, value) { $jsBridgeName.setValue($defaultSignature, name, value); };\n")
            append("var GM_deleteValue = function(name) { $jsBridgeName.deleteValue($defaultSignature, name); };\n")
            append("var GM_addStyle = function(css) { var style = document.createElement(\"style\"); style.type = \"text/css\"; style.innerHTML = css; (document.head || document.documentElement).appendChild(style); return style; };\n")
            append("var GM_log = function(message) { $jsBridgeName.log($defaultSignature, message); };\n")
            append("var GM_getResourceURL = function(resourceName) { return $jsBridgeName.getResourceURL($defaultSignature, resourceName); };\n")
            append("var GM_getResourceText = function(resourceName) { return $jsBridgeName.getResourceText($defaultSignature, resourceName); };\n")
            append(buildXmlHttpRequestJs(defaultSignature, callbackPrefix))
            append("var GM_notification = function(text, title, image, onclick) { $jsBridgeName.log($defaultSignature, 'Notification: ' + (title || '') + ' - ' + (typeof text === 'string' ? text : (text.text || ''))); };\n")
            append(buildGmInfoJs(script))
            append("var GM_openInTab = $JSMISSINGFUNCTION")
            append("var GM_registerMenuCommand = $JSMISSINGFUNCTION")
            append("var GM_setClipboard = $JSMISSINGFUNCTION")
            append(buildGmAsyncApi(script))
        }

        // IMPORTANT: Greasemonkey requires order is critical.
        // We need to re-sort based on the original metadata block if possible,
        // but since we updated Script.java to use LinkedHashSet, new scripts
        // should stay in order. For existing scripts, we match against the source.
        val requires = script.requires
        val originalScript = script.content
        
        val sortedRequires = if (requires != null && originalScript != null) {
            requires.sortedBy { originalScript.indexOf(it.url) }
        } else {
            requires?.toList() ?: emptyList()
        }

        Log.d(TAG, "Script ${script.name} bundling ${sortedRequires.size} @requires")

        val jsRequires = buildString {
            sortedRequires.forEach { require ->
                Log.v(TAG, "Adding @require: ${require.url} (length: ${require.content?.length ?: 0})")
                append("\n/* --- Start Require: ${require.url} --- */\n")
                append(require.content ?: "// Library content was null")
                append("\n/* --- End Require --- */\n")
            }
        }

        val mainContent = script.content

        return if (!script.isUnwrap) {
            """
            (function() {
                $JS_UNSAFE_WINDOW_INIT
                $jsApi
                
                try {
                    $jsRequires
                } catch (libError) {
                    console.error("Error in @requires for ${escapedName}:", libError);
                    if (typeof GM_log !== 'undefined') GM_log("Error in @requires: " + libError);
                }
                
                try {
                    $mainContent
                } catch (e) {
                    console.error("Error in script ${escapedName}:", e);
                    if (typeof GM_log !== 'undefined') {
                        GM_log("Error in script ${escapedName}: " + e);
                    }
                }
            }).call(window);
            """.trimIndent()
        } else {
            JS_UNSAFE_WINDOW_INIT + jsApi + jsRequires + mainContent
        }
    }

    private fun buildXmlHttpRequestJs(defaultSignature: String, callbackPrefix: String): String {
        return buildString {
            append("var GM_xmlhttpRequest = function(details) { \n")
            for (callback in listOf("onabort", "onerror", "onload", "onprogress", "onreadystatechange", "ontimeout")) {
                val propName = "${callbackPrefix}GM_${callback}Callback"
                append("if (details.$callback) { unsafeWindow.$propName = details.$callback; details.$callback = '$propName'; }\n")
            }
            append("if (details.upload) {\n")
            for (callback in listOf("onabort", "onerror", "onload", "onprogress")) {
                val propName = "${callbackPrefix}GM_upload${callback}Callback"
                append("if (details.upload.$callback) { unsafeWindow.$propName = details.upload.$callback; details.upload.$callback = '$propName'; }\n")
            }
            append("}\n")
            append("return JSON.parse($jsBridgeName.xmlHttpRequest($defaultSignature, JSON.stringify(details))); };\n")
            append("var GM_xmlHttpRequest = GM_xmlhttpRequest;\n")
        }
    }

    private fun buildGmInfoJs(script: Script): String {
        val escapedName = script.name.replace("'", "\\'")
        val escapedVersion = (script.version ?: "1.0.0").replace("'", "\\'")
        val escapedNamespace = script.namespace.replace("'", "\\'")
        val escapedDescription = (script.description ?: "").replace("'", "\\'")

        return """
            var GM_info = {
                script: {
                    name: '$escapedName',
                    version: '$escapedVersion',
                    namespace: '$escapedNamespace',
                    description: '$escapedDescription',
                    handler: 'McMilkyWayIdle'
                },
                scriptHandler: 'McMilkyWayIdle',
                version: '0.51'
            };
        """.trimIndent() + "\n"
    }

    private fun buildGmAsyncApi(script: Script): String {
        return """
            var GM = {
                getValue: function(name, defaultValue) {
                    return Promise.resolve(GM_getValue(name, defaultValue));
                },
                setValue: function(name, value) {
                    GM_setValue(name, value);
                    return Promise.resolve();
                },
                deleteValue: function(name) {
                    GM_deleteValue(name);
                    return Promise.resolve();
                },
                listValues: function() {
                    return Promise.resolve(GM_listValues());
                },
                xmlHttpRequest: GM_xmlhttpRequest,
                xmlhttpRequest: GM_xmlhttpRequest,
                notification: GM_notification,
                addStyle: GM_addStyle,
                getResourceUrl: function(resourceName) {
                    return Promise.resolve(GM_getResourceURL(resourceName));
                },
                info: GM_info,
                log: GM_log
            };
        """.trimIndent() + "\n"
    }
}
