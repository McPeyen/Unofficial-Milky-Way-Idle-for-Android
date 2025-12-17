package com.mcpeyen.unofficialmilkywayidle

import android.content.Context
import android.webkit.WebView

class SystemScriptManager(private val context: Context, private val webView: WebView) {

    /**
     * A helper function that injects a script to wait for a specific element to appear in the DOM.
     * Once the element is found, it executes the provided callback code.
     * This is far more reliable than setTimeout loops for Single-Page Applications.
     */
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

    fun injectRefreshGesture() {
        val selector = "div.GamePage_headerPanel__1T_cA"
        val callback = """
        // The rest of your original injectRefreshGesture JS code goes here.
        // It's now guaranteed to run only when 'targetNode' exists.
        const mcGestureRefresh = () => {
            let refreshIndicator = document.getElementById('mc-refresh-indicator');
            if (!refreshIndicator) {
                refreshIndicator = document.createElement('div');
                refreshIndicator.id = 'mc-refresh-indicator';
                refreshIndicator.style.cssText = `
                    position: absolute;
                    top: 0;
                    left: 50%;
                    transform: translateX(-50%);
                    width: 40px;
                    height: 40px;
                    border-radius: 50%;
                    background-color: #4357af;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 9999;
                    opacity: 0;
                    transition: opacity 0.2s;
                    pointer-events: none;
                `;

                const svgNS = "http://www.w3.org/2000/svg";
                const progressCircle = document.createElementNS(svgNS, 'svg');
                progressCircle.setAttribute('width', '32');
                progressCircle.setAttribute('height', '32');
                progressCircle.setAttribute('viewBox', '0 0 32 32');
                progressCircle.style.cssText = `transform: rotate(-90deg);`;

                const backgroundCircle = document.createElementNS(svgNS, 'circle');
                backgroundCircle.setAttribute('cx', '16');
                backgroundCircle.setAttribute('cy', '16');
                backgroundCircle.setAttribute('r', '12');
                backgroundCircle.setAttribute('stroke', 'rgba(255, 255, 255, 0.3)');
                backgroundCircle.setAttribute('stroke-width', '3');
                backgroundCircle.setAttribute('fill', 'none');

                const progressArc = document.createElementNS(svgNS, 'circle');
                progressArc.id = 'mc-progress-arc';
                progressArc.setAttribute('cx', '16');
                progressArc.setAttribute('cy', '16');
                progressArc.setAttribute('r', '12');
                progressArc.setAttribute('stroke', '#ffffff');
                progressArc.setAttribute('stroke-width', '3');
                progressArc.setAttribute('fill', 'none');

                const circumference = 2 * Math.PI * 12;
                progressArc.setAttribute('stroke-dasharray', circumference.toString());
                progressArc.setAttribute('stroke-dashoffset', circumference.toString());

                progressCircle.appendChild(backgroundCircle);
                progressCircle.appendChild(progressArc);
                refreshIndicator.appendChild(progressCircle);
                document.body.appendChild(refreshIndicator);
            }

            // 'targetNode' is passed directly from the MutationObserver callback
            window.headerHeight = targetNode.offsetHeight;
            const REFRESH_THRESHOLD = 200;

            targetNode.addEventListener('touchstart', function(e) {
                window.pullStartY = e.touches[0].clientY;
                refreshIndicator.style.transition = 'none';
                document.getElementById('mc-progress-arc').style.transition = 'none';
            });

            targetNode.addEventListener('touchmove', function(e) {
                if (window.pullStartY) {
                    var pullDistance = e.touches[0].clientY - window.pullStartY;
                    if (pullDistance > 0) {
                        refreshIndicator.style.opacity = Math.min(pullDistance / 100, 1).toString();
                        refreshIndicator.style.transform = `translateX(-50%) scale(${'$'}{Math.min(0.8 + (pullDistance / 250), 1.2)})`;

                        const progressPercentage = Math.min(pullDistance / REFRESH_THRESHOLD, 1);
                        const progressArc = document.getElementById('mc-progress-arc');
                        const circumference = 2 * Math.PI * 12;
                        const dashOffset = circumference * (1 - progressPercentage);
                        progressArc.setAttribute('stroke-dashoffset', dashOffset.toString());

                        if (pullDistance > REFRESH_THRESHOLD) {
                            window.Android.refreshPage();
                            window.pullStartY = null;

                            refreshIndicator.style.opacity = '1';
                            refreshIndicator.style.transition = 'opacity 0.5s';

                            const progressArc = document.getElementById('mc-progress-arc');
                            progressArc.setAttribute('stroke-dashoffset', '0');
                            progressArc.style.transition = 'stroke-dashoffset 0.3s';
                            refreshIndicator.style.animation = 'mc-spin 1s linear infinite';

                            setTimeout(() => {
                                refreshIndicator.style.opacity = '0';
                                refreshIndicator.style.animation = '';
                            }, 1000);
                        }
                    }
                }
            });

            targetNode.addEventListener('touchend', function() {
                if (window.pullStartY) {
                    refreshIndicator.style.transition = 'opacity 0.3s, transform 0.3s';
                    refreshIndicator.style.opacity = '0';
                    refreshIndicator.style.transform = 'translateX(-50%) scale(1)';

                    const progressArc = document.getElementById('mc-progress-arc');
                    progressArc.style.transition = 'stroke-dashoffset 0.3s';
                    const circumference = 2 * Math.PI * 12;
                    progressArc.setAttribute('stroke-dashoffset', circumference.toString());
                    refreshIndicator.style.animation = '';
                    window.pullStartY = null;
                }
            });

            if (!document.getElementById('mc-refresh-styles')) {
                const styleTag = document.createElement('style');
                styleTag.id = 'mc-refresh-styles';
                styleTag.textContent = `
                    @keyframes mc-spin {
                        0% { transform: translateX(-50%) rotate(0deg); }
                        100% { transform: translateX(-50%) rotate(360deg); }
                    }
                `;
                document.head.appendChild(styleTag);
            }
        };
        mcGestureRefresh();
        """
        waitForElement(selector, callback)
    }

    fun injectRefreshButton() {
        val selector = "div.SettingsPanel_gameTab__n2hAG"
        val callback = """
            // No need for a loop or timeout here.
            // 'targetNode' is the element found by the MutationObserver.
            const existingButton = targetNode.querySelector('[data-refresh-button="true"]');
            if (!existingButton) {
                let refreshContainer = document.createElement("div");
                refreshContainer.setAttribute("data-refresh-button", "true");
                refreshContainer.style.cssText = "display: flex; align-items: center; margin: 10px 0;"; // Added some margin

                let label = document.createElement("span");
                label.innerHTML = "Reload: ";
                label.style.marginRight = "10px";

                let refreshButton = document.createElement("button");
                refreshButton.style.cssText = "background-color: #4357af; color: white; border: none; border-radius: 4px; padding: 5px 10px; cursor: pointer; display: flex; align-items: center; justify-content: center;";

                refreshButton.innerHTML = `
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;">
                        <path d="M23 4v6h-6"></path>
                        <path d="M1 20v-6h6"></path>
                        <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10"></path>
                        <path d="M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
                    </svg>
                    Reload Game
                `;

                refreshButton.addEventListener("click", () => window.Android.refreshPage());

                refreshContainer.appendChild(label);
                refreshContainer.appendChild(refreshButton);
                targetNode.insertAdjacentElement("beforeend", refreshContainer);
            }
        """
        waitForElement(selector, callback)
    }

    fun injectSettings() {
        val selector = "div.SettingsPanel_gameTab__n2hAG"
        val callback = """
            // No need for a loop or timeout here.
            // 'targetNode' is the element found by the MutationObserver.
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
        // This can be injected directly as it doesn't depend on specific elements.
        webView.evaluateJavascript(jsCode, null)
    }

    fun injectGreasemonkeyAPI() {
        val jsCode = """
    (function() {
        if (window.GM) return; // Prevent re-injection if GM already exists

        // --- 1. Internal Storage & Legacy API (GM_*) ---
        
        const gmValues = {};

        window.GM_setValue = function(key, value) {
            gmValues[key] = value;
            try {
                localStorage.setItem('GM_' + key, JSON.stringify(value));
            } catch(e) { console.error("GM_setValue error:", e); }
        };

        window.GM_getValue = function(key, defaultValue) {
            if (key in gmValues) return gmValues[key];

            const storedValue = localStorage.getItem('GM_' + key);
            if (storedValue !== null) {
                try {
                    const value = JSON.parse(storedValue);
                    gmValues[key] = value;
                    return value;
                } catch(e) { console.error("GM_getValue error:", e); }
            }
            return defaultValue;
        };
        
        window.GM_deleteValue = function(key) {
            delete gmValues[key];
            localStorage.removeItem('GM_' + key);
        };

        window.GM_addStyle = function(css) {
            const style = document.createElement('style');
            style.textContent = css;
            document.head.appendChild(style);
            return style;
        };

        window.GM_xmlhttpRequest = function(details) {
            return new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                xhr.open(details.method || 'GET', details.url, true);

                if (details.headers) {
                    for (const header in details.headers) {
                        xhr.setRequestHeader(header, details.headers[header]);
                    }
                }
                if (details.responseType) xhr.responseType = details.responseType;
                if (details.timeout) xhr.timeout = details.timeout;

                const createResponse = (xhrInstance) => ({
                    responseText: xhrInstance.responseText,
                    responseXML: xhrInstance.responseXML,
                    response: xhrInstance.response,
                    status: xhrInstance.status,
                    statusText: xhrInstance.statusText,
                    readyState: xhrInstance.readyState,
                    finalUrl: xhrInstance.responseURL,
                    responseHeaders: xhrInstance.getAllResponseHeaders()
                });

                xhr.onload = () => {
                    const response = createResponse(xhr);
                    if (details.onload) details.onload(response);
                    resolve(response);
                };

                xhr.onerror = () => {
                    const response = createResponse(xhr);
                    if (details.onerror) details.onerror(response);
                    reject(response);
                };

                xhr.onabort = () => {
                    const response = createResponse(xhr);
                    if (details.onabort) details.onabort(response);
                    reject({aborted: true});
                };

                xhr.ontimeout = () => {
                    const response = createResponse(xhr);
                    if (details.ontimeout) details.ontimeout(response);
                    reject({timedout: true});
                };

                if (details.onprogress) xhr.onprogress = details.onprogress;

                xhr.send(details.data || null);
            });
        };

        // --- 2. Modern API (GM object) ---
        // This is what MWITools is looking for.
        // The spec requires these to be Async (return Promises).
        
        window.GM = {
            getValue: async function(key, defaultValue) {
                return window.GM_getValue(key, defaultValue);
            },
            setValue: async function(key, value) {
                return window.GM_setValue(key, value);
            },
            deleteValue: async function(key) {
                return window.GM_deleteValue(key);
            },
            xmlHttpRequest: window.GM_xmlhttpRequest,
            info: {
                script: {
                    version: "1.0.0",
                    name: "Android Wrapper",
                    handler: "AndroidWebView"
                }
            }
        };
        
        // Expose unsafeWindow for compatibility
        window.unsafeWindow = window;
        
    })();
    """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }
}
