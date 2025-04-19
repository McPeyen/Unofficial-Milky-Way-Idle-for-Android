package com.mcpeyen.unofficialmilkywayidle

import android.content.Context
import android.webkit.WebView

class SystemScriptManager(private val context: Context, private val webView: WebView) {

    fun injectRefreshGesture() {
        val jsCode = """
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

                // Create progress circle with SVG namespace
                const svgNS = "http://www.w3.org/2000/svg";
                const progressCircle = document.createElementNS(svgNS, 'svg');
                progressCircle.setAttribute('width', '32');
                progressCircle.setAttribute('height', '32');
                progressCircle.setAttribute('viewBox', '0 0 32 32');
                progressCircle.style.cssText = `
                    transform: rotate(-90deg);
                `;

                // Background circle
                const backgroundCircle = document.createElementNS(svgNS, 'circle');
                backgroundCircle.setAttribute('cx', '16');
                backgroundCircle.setAttribute('cy', '16');
                backgroundCircle.setAttribute('r', '12');
                backgroundCircle.setAttribute('stroke', 'rgba(255, 255, 255, 0.3)');
                backgroundCircle.setAttribute('stroke-width', '3');
                backgroundCircle.setAttribute('fill', 'none');

                // Progress circle
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

            const targetNode = document.querySelector("div.GamePage_headerPanel__1T_cA");
            if (targetNode) {
                window.headerHeight = targetNode.offsetHeight;
                const REFRESH_THRESHOLD = 200;  // Pull distance needed to trigger refresh

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
                            refreshIndicator.style.transform = `translateX(-50%) scale({"${'$'}"}{Math.min(0.8 + (pullDistance / 250), 1.2)})`;

                            const progressPercentage = Math.min(pullDistance / REFRESH_THRESHOLD, 1);

                            const progressArc = document.getElementById('mc-progress-arc');
                            const circumference = 2 * Math.PI * 12;  // 2πr
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
            }

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
            setTimeout(mcGestureRefresh, 500);
        };
        mcGestureRefresh();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    fun injectRefreshButton() {
        val jsCode = """
        const mcRefresh = () => {
            const targetNode = document.querySelector("div.SettingsPanel_gameTab__n2hAG");
            if (targetNode) {
                const existingButton = targetNode.querySelector('[data-refresh-button="true"]');            
                if (!existingButton) {
                    let refreshContainer = document.createElement("div");
                    refreshContainer.setAttribute("data-refresh-button", "true");
                    refreshContainer.style.display = "flex";
                    refreshContainer.style.alignItems = "center";
                    refreshContainer.style.margin = "0px 0";

                    let label = document.createElement("span");
                    label.innerHTML = "Reload: ";
                    label.style.marginRight = "10px";

                    let refreshButton = document.createElement("button");
                    refreshButton.style.backgroundColor = "#4357af";
                    refreshButton.style.color = "white";
                    refreshButton.style.border = "none";
                    refreshButton.style.borderRadius = "4px";
                    refreshButton.style.padding = "5px 10px";
                    refreshButton.style.cursor = "pointer";
                    refreshButton.style.display = "flex";
                    refreshButton.style.alignItems = "center";
                    refreshButton.style.justifyContent = "center";

                    refreshButton.innerHTML = `
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;">
                            <path d="M23 4v6h-6"></path>
                            <path d="M1 20v-6h6"></path>
                            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10"></path>
                            <path d="M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
                        </svg>
                        Reload Game
                    `;

                    refreshButton.addEventListener("click", function() {
                        window.Android.refreshPage();
                    });

                    refreshContainer.appendChild(label);
                    refreshContainer.appendChild(refreshButton);
                    targetNode.insertAdjacentElement("beforeend", refreshContainer);
                }
            }
            setTimeout(mcRefresh, 500);
        };

        mcRefresh();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    fun injectSettings() {
        val jsCode = """
        const mcSettings = () => {
            const targetNode = document.querySelector("div.SettingsPanel_gameTab__n2hAG");
            if (targetNode) {
                const existingButton = targetNode.querySelector('[data-script-manager="true"]');
                if (!existingButton) {
                    let container = document.createElement("div");
                    container.setAttribute("data-script-manager", "true"); // Add a data attribute to identify our element
                    container.style.display = "flex";
                    container.style.alignItems = "center";
                    container.style.margin = "0px 0";

                    let label = document.createElement("span");
                    label.innerHTML = "Open Script Manager: ";
                    label.style.marginRight = "10px";

                    let button = document.createElement("button");
                    button.style.backgroundColor = "#4357af";
                    button.style.color = "white";
                    button.style.border = "none";
                    button.style.borderRadius = "4px";
                    button.style.padding = "5px 10px";
                    button.style.cursor = "pointer";
                    button.style.display = "flex";
                    button.style.alignItems = "center";
                    button.style.justifyContent = "center";

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

                    button.addEventListener("click", function() {
                        window.Android.openScriptManager();
                    });

                    container.appendChild(label);
                    container.appendChild(button);
                    targetNode.insertAdjacentElement("beforeend", container);
                }
            }
            setTimeout(mcSettings, 500);
        };
        mcSettings();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }


    fun disableLongClick() {
        val jsCode = """
        const mcLongClickDisable = () => {
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
        };

        mcLongClickDisable();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    fun injectGreasemonkeyAPI() {
        val jsCode = """
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
                return new Promise((resolve, reject) => {
                    const xhr = new XMLHttpRequest();

                    xhr.open(details.method || 'GET', details.url, !details.synchronous);

                    if (details.headers) {
                        for (const header in details.headers) {
                            xhr.setRequestHeader(header, details.headers[header]);
                        }
                    }

                    if (details.responseType) {
                        xhr.responseType = details.responseType;
                    }

                    // Create response object that mimics Greasemonkey's response format
                    const createResponse = () => {
                        return {
                            responseText: xhr.responseText,
                            responseXML: xhr.responseXML,
                            response: xhr.response,
                            status: xhr.status,
                            statusText: xhr.statusText,
                            readyState: xhr.readyState,
                            finalUrl: xhr.responseURL
                        };
                    };

                    xhr.onload = function() {
                        const response = createResponse();
                        if (details.onload) details.onload(response);
                        resolve(response);
                    };

                    xhr.onerror = function() {
                        const response = createResponse();
                        if (details.onerror) details.onerror(response);
                        reject(response);
                    };

                    xhr.onabort = function() {
                        const response = createResponse();
                        if (details.onabort) details.onabort(response);
                        reject(response);
                    };

                    xhr.ontimeout = function() {
                        const response = createResponse();
                        if (details.ontimeout) details.ontimeout(response);
                        reject(response);
                    };

                    if (details.onprogress) {
                        xhr.onprogress = function(e) { 
                            details.onprogress(e); 
                        };
                    }

                    xhr.send(details.data || null);

                    // For compatibility with code that expects a return value with abort method
                    return { 
                        abort: function() { 
                            xhr.abort(); 
                            reject({aborted: true});
                        } 
                    };
                });
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

            // Create the GM object and attach the functions
            window.GM = {
                setValue: GM_setValue,
                getValue: GM_getValue,
                addStyle: GM_addStyle,
                xmlHttpRequest: function(details) {
                    return window.GM_xmlhttpRequest(details);
                },
                notification: GM_notification
            };

            console.log('Greasemonkey API has been initialized with notification support');
        })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }
}