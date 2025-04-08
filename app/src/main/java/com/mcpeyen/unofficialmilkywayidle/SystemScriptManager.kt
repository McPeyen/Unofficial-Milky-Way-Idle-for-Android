package com.mcpeyen.unofficialmilkywayidle

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat

class SystemScriptManager(private val context: Context, private val webView: WebView) {
    fun injectSettings() {

        val jsCode = """
        const mcSettings = () => {
            const targetNode = document.querySelector("div.SettingsPanel_gameTab__n2hAG");
            if (targetNode) {
                const existingButton = targetNode.querySelector('[data-script-manager="true"]');
                if (!existingButton) {
                    // Create container div to hold text and button
                    let container = document.createElement("div");
                    container.setAttribute("data-script-manager", "true"); // Add a data attribute to identify our element
                    container.style.display = "flex";
                    container.style.alignItems = "center";
                    container.style.margin = "10px 0";
                
                    // Create text label
                    let label = document.createElement("span");
                    label.innerHTML = "Open Script Manager: ";
                    label.style.marginRight = "10px";
            
                    // Create button with icon
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
            
                    // Add icon (using a simple SVG code icon)
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
            
                    // Add click event listener to call openScriptManager()
                    button.addEventListener("click", function() {
                        window.Android.openScriptManager();
                    });
            
                    // Assemble and add to the page
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
            // First disable long-click events on the entire document
            document.addEventListener('contextmenu', (e) => {
                // Check if the clicked element is inside the TabsComponent_tabPanelsContainer
                const container = document.querySelector("div.Chat_tabsComponentContainer__3ZoKe");
                if (container && container.contains(e.target)) {
                    // Allow default action for elements in the container
                    return true;
                } else {
                    // Prevent default context menu for all other elements
                    e.preventDefault();
                    return false;
                }
            }, true);
        
            // Also disable long touch events which might trigger selection on mobile
            document.addEventListener('touchstart', (e) => {
                const container = document.querySelector("div.Chat_tabsComponentContainer__3ZoKe");
                if (!(container && container.contains(e.target))) {
                    e.target.style.webkitTouchCallout = 'none';
                    e.target.style.webkitUserSelect = 'none';
                }
            }, true);
        };
    
        // Execute the function immediately
        mcLongClickDisable();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    fun injectGreasemonkeyAPI() {
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
                return new Promise((resolve, reject) => {
                    const xhr = new XMLHttpRequest();
        
                    xhr.open(details.method || 'GET', details.url, !details.synchronous);
        
                    if (details.headers) {
                        for (const header in details.headers) {
                            xhr.setRequestHeader(header, details.headers[header]);
                        }
                    }
        
                    // Set responseType if specified
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

        webView.evaluateJavascript(gmFunctions, null)
    }
}