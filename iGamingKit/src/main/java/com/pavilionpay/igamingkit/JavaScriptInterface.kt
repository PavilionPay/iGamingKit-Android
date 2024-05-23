package com.pavilionpay.igamingkit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * A class that provides a JavaScript interface for a WebView.
 *
 * @param webView The WebView to which this interface is attached.
 * @param tokenHandler A function to handle token events.
 * @param closeHandler A function to handle close events.
 */
internal class JavaScriptInterface(
        private val webView: WebView,
        private val tokenHandler: (String) -> Unit,
        private val fullScreenHandler: () -> Unit,
        private val closeHandler: () -> Unit,
) {
    /**
     * This functions triggers the onAndroidSuccess function in the WebView to indicate the Plaid process has completed. The metadata
     * contains the bank details.
     */
    fun callOnAndroidSuccess(metadata: String?) {
        webView.post {
            webView.evaluateJavascript("\$onAndroidSuccess('$metadata')", null)
        }
    }

    /**
     * A function to handle close events. This is called from JavaScript within in the WebView to close the WebView.
     */
    @JavascriptInterface
    fun consumeWindowMessage(name: String?, payload: String?) {
        if (payload == "close") {
            webView.context.findActivity()?.runOnUiThread {
                closeHandler()
            }
        } else if (payload == "opensdk") {
            webView.context.findActivity()?.runOnUiThread {
                fullScreenHandler()
            }
        }
    }

    /**
     * A function to handle token events. This is called from JavaScript within in the WebView to set the linkToken.
     *
     * @param linkToken The link token.
     */
    @JavascriptInterface
    fun openLinkNative(linkToken: String, unused: String, unused2: String) {
        tokenHandler(linkToken)
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
