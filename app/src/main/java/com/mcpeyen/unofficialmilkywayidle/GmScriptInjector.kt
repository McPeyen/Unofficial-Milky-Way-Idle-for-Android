package com.mcpeyen.unofficialmilkywayidle

import android.util.Log
import android.webkit.WebView
import at.pardus.android.webview.gm.model.Script
import at.pardus.android.webview.gm.store.ScriptStore
import java.util.UUID

/**
 * Injects userscripts into a WebView with full Greasemonkey API support.
 * Extracted from webview-gm-lib's WebViewClientGm.runMatchingScripts() with
 * additional GM.* async wrappers and GM_notification stub.
 */
class GmScriptInjector(
    private val scriptStore: ScriptStore,
    private val jsBridgeName: String,
    private val secret: String
) {
    companion object {
        private const val TAG = "GmScriptInjector"

        private const val JSCONTAINERSTART = "(function() {\n"
        private const val JSCONTAINEREND = "\n})()"

        private const val JSUNSAFEWINDOW =
            "unsafeWindow = (function() { var el = document.createElement('p'); el.setAttribute('onclick', 'return window;'); return el.onclick(); }()); window.wrappedJSObject = unsafeWindow;\n"

        private const val JSMISSINGFUNCTION =
            "function() { GM_log(\"Called function not yet implemented\"); };\n"
    }

    /**
     * Injects all scripts matching the given URL into the WebView.
     *
     * @param webView the WebView to inject scripts into
     * @param url the current page URL to match scripts against
     * @param pageFinished true for document-end scripts, false for document-start
     */
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

    /**
     * Builds the complete JavaScript for a single script, including:
     * - GM API variable declarations with secret
     * - @require script content prepended
     * - IIFE wrapping (unless @unwrap)
     * - GM.* async wrappers
     */
    private fun buildScriptJs(script: Script): String {
        Log.d(TAG, "Script ${script.name} has ${script.requires?.size ?: 0} requires")

        val escapedName = script.name.replace("\"", "\\\"")
        val escapedNamespace = script.namespace.replace("\"", "\\\"")
        val defaultSignature = "\"$escapedName\", \"$escapedNamespace\", \"$secret\""

        val callbackPrefix = ("GM_" + script.name + script.namespace + UUID.randomUUID().toString())
            .replace(Regex("[^0-9a-zA-Z_]"), "")

        val jsApi = buildString {
            // unsafeWindow
            append(JSUNSAFEWINDOW)

            // GM_listValues
            append("var GM_listValues = function() { return $jsBridgeName.listValues($defaultSignature).split(\",\"); };\n")

            // GM_getValue
            append("var GM_getValue = function(name, defaultValue) { return $jsBridgeName.getValue($defaultSignature, name, defaultValue); };\n")

            // GM_setValue
            append("var GM_setValue = function(name, value) { $jsBridgeName.setValue($defaultSignature, name, value); };\n")

            // GM_deleteValue
            append("var GM_deleteValue = function(name) { $jsBridgeName.deleteValue($defaultSignature, name); };\n")

            // GM_addStyle
            append("""var GM_addStyle = function(css) { var style = document.createElement("style"); style.type = "text/css"; style.innerHTML = css; document.getElementsByTagName('head')[0].appendChild(style); return style; };""")
            append("\n")

            // GM_log
            append("var GM_log = function(message) { $jsBridgeName.log($defaultSignature, message); };\n")

            // GM_getResourceURL
            append("var GM_getResourceURL = function(resourceName) { return $jsBridgeName.getResourceURL($defaultSignature, resourceName); };\n")

            // GM_getResourceText
            append("var GM_getResourceText = function(resourceName) { return $jsBridgeName.getResourceText($defaultSignature, resourceName); };\n")

            // GM_xmlhttpRequest with callback handling
            append(buildXmlHttpRequestJs(defaultSignature, callbackPrefix))

            // GM_notification (stub)
            append("var GM_notification = function(text, title, image, onclick) { GM_log('Notification: ' + (title || '') + ' - ' + (typeof text === 'string' ? text : (text.text || ''))); };\n")

            // GM_info
            append(buildGmInfoJs(script))

            // GM_openInTab (stub)
            append("var GM_openInTab = $JSMISSINGFUNCTION")

            // GM_registerMenuCommand (stub)
            append("var GM_registerMenuCommand = $JSMISSINGFUNCTION")

            // GM_setClipboard (stub)
            append("var GM_setClipboard = $JSMISSINGFUNCTION")

            // GM.* async API wrappers
            append(buildGmAsyncApi(script))
        }

        val jsRequires = buildString {
            script.requires?.forEach { require ->
                append("\n/* --- Start Require: ${require.url} --- */\n")
                append(require.content)
                append("\n/* --- End Require --- */\n")
            }
        }

        val mainContent = script.content

        return if (!script.isUnwrap) {
            """
    $jsApi
    (function(window, unsafeWindow) {
        try {
            // Injecting Libraries
            $jsRequires
            
            // Injecting Main Script
            $mainContent
        } catch (e) {
            console.error("Error in script ${escapedName}:", e);
            if (typeof GM_log !== 'undefined') {
                GM_log("Error in script ${escapedName}: " + e);
            }
        }
    })(window, unsafeWindow);
    """.trimIndent()
        } else {
            jsApi + jsRequires + mainContent
        }
    }

    /**
     * Builds the GM_xmlhttpRequest function with callback proxying through unsafeWindow.
     */
    private fun buildXmlHttpRequestJs(defaultSignature: String, callbackPrefix: String): String {
        return buildString {
            append("var GM_xmlhttpRequest = function(details) { \n")

            // Map each callback to an unsafeWindow property
            for (callback in listOf(
                "onabort",
                "onerror",
                "onload",
                "onprogress",
                "onreadystatechange",
                "ontimeout"
            )) {
                val propName = "${callbackPrefix}GM_${callback}Callback"
                append("if (details.$callback) { unsafeWindow.$propName = details.$callback;\n")
                append("details.$callback = '$propName'; }\n")
            }

            // Upload callbacks
            append("if (details.upload) {\n")
            for (callback in listOf("onabort", "onerror", "onload", "onprogress")) {
                val propName = "${callbackPrefix}GM_upload${callback}Callback"
                append("if (details.upload.$callback) { unsafeWindow.$propName = details.upload.$callback;\n")
                append("details.upload.$callback = '$propName'; }\n")
            }
            append("}\n")

            append("return JSON.parse($jsBridgeName.xmlHttpRequest($defaultSignature, JSON.stringify(details))); };\n")
        }
    }

    /**
     * Builds the GM_info object with script metadata.
     */
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

    /**
     * Builds GM.* async wrappers (Greasemonkey 4+ API).
     */
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
