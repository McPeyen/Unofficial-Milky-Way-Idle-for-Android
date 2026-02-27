package com.mcpeyen.unofficialmilkywayidle

import android.content.Context
import android.util.Log
import android.webkit.WebView

class SystemScriptManager(private val context: Context, private val webView: WebView) {

    fun injectProfileButtons() {
        val selector = ".NavigationBar_navigationLink__3eAHA"
        val callback = """
        (function() {
            const handleRefresh = () => {
                window.Android.refreshPage();
            };

            const handleManager = () => {
                window.Android.openScriptManager();
            };

            const injectManagerButton = (menu) => {
                if (menu.querySelector('.manager-button-injected')) return;

                const managerBtn = document.createElement('button');
                managerBtn.className = 'Button_button__1Fe9z Button_fullWidth__17pVU manager-button-injected';
                managerBtn.innerHTML = '<span>Script Manager</span>';

                managerBtn.onclick = handleManager;

                const title = menu.querySelector('.Header_menuTitle__3NUq1');
                if (title) {
                    title.insertAdjacentElement('afterend', managerBtn);
                }
            };

            const injectRefreshButton = (menu) => {
                if (menu.querySelector('.refresh-button-injected')) return;

                const refreshBtn = document.createElement('button');
                refreshBtn.className = 'Button_button__1Fe9z Button_fullWidth__17pVU refresh-button-injected';
                refreshBtn.innerHTML = '<span>Reload Game</span>';

                refreshBtn.onclick = handleRefresh;

                const title = menu.querySelector('.Header_menuTitle__3NUq1');
                if (title) {
                    title.insertAdjacentElement('afterend', refreshBtn);
                }
            };

            const observer = new MutationObserver((mutations) => {
                const menu = document.querySelector('.Header_avatarMenu__1I5qH');
                if (menu) {
                    injectManagerButton(menu);
                    injectRefreshButton(menu);
                }
            });

            observer.observe(document.body, { childList: true, subtree: true });
        })();
        """
        waitForElement(selector, callback)
    }

    fun injectSettings() {
        val selector = "div.SettingsPanel_gameTab__n2hAG"
        val callback = """
        const existingButton = targetNode.querySelector('[data-script-manager="true"]');
        if (!existingButton) {
            let container = document.createElement("div");
            container.setAttribute("data-script-manager", "true");
            container.style.cssText = "display: flex; align-items: center; margin: 10px 0;"; // Added some margin

            let label = document.createElement("span");
            label.innerHTML = "Script Manager: "; // Simplified text
            label.style.marginRight = "10px";

            let button = document.createElement("button");
            button.style.cssText = "background-color: #4357af; color: white; border: none; border-radius: 4px; padding: 5px 10px; cursor: pointer; display: flex; align-items: center; justify-content: center;";

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

            button.addEventListener("click", () => window.Android.openScriptManager());

            container.appendChild(label);
            container.appendChild(button);
            targetNode.insertAdjacentElement("beforeend", container);
        }
        """
        waitForElement(selector, callback)
    }

    fun disableLongClick() {
        val jsCode = """
        document.addEventListener('contextmenu', (e) => {
            const container = document.querySelector("div.Chat_tabsComponentContainer__3ZoKe");
            if (container && container.contains(e.target)) {
                return true;
            } else {
                e.preventDefault();
                return false;
            }
        }, true);
        document.addEventListener('touchstart', (e) => {
            const container = document.querySelector("div.Chat_tabsComponentContainer__3ZoKe");
            if (!(container && container.contains(e.target))) {
                e.target.style.webkitTouchCallout = 'none';
                e.target.style.webkitUserSelect = 'none';
            }
        }, true);
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    fun injectLZString() {
        try {
            val lzStringScript =
                context.assets.open("js/lz-string.min.js").bufferedReader().use { it.readText() }
            webView.evaluateJavascript(lzStringScript, null)
        } catch (e: Exception) {
            Log.e("SystemScriptManager", "Failed to inject LZ-String", e)
        }
    }

    private fun waitForElement(selector: String, callback: String) {
        val jsCode = """
        (function() {
            const selector = `${selector.replace("`", "\\`")}`;
            const callback = function(targetNode) {
                ${callback}
            };

            const observer = new MutationObserver((mutations, obs) => {
                const targetNode = document.querySelector(selector);
                if (targetNode) {
                    // Element found, execute the callback and stop observing.
                    callback(targetNode);
                    obs.disconnect();
                }
            });

            // Start observing the entire document body for additions.
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });

            // Also check if the element already exists, in case the script is injected late.
            const existingNode = document.querySelector(selector);
            if (existingNode) {
                callback(existingNode);
                observer.disconnect();
            }
        })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }
}
