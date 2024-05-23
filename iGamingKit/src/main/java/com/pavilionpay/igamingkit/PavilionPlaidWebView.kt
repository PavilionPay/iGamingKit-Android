package com.pavilionpay.igamingkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.plaid.link.OpenPlaidLink
import com.plaid.link.Plaid
import com.plaid.link.event.LinkEventName
import com.plaid.link.linkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess


/**
 * A composable function that displays a WebView and handles Plaid link events.
 *
 * @param url The URL to load in the WebView.
 * @param redirectUrl The redirect URL used after completing a deposit or withdrawal. This is needed to help ensure security and
 * only allow the expected redirect.
 * @param onClose A callback function to be invoked when the WebView is closed.
 */
@Composable
fun PavilionPlaidWebView(
        url: String,
        redirectUrl: String,
        onFullScreenRequested: () -> Unit,
        onClose: () -> Unit,
) {
    require(url.isNotEmpty()) { stringResource(R.string.url_cannot_be_empty) }

    var isLoading by remember { mutableStateOf(true) }
    var linkTokenState by remember { mutableStateOf("") }
    var jsNativeInterface by remember { mutableStateOf<JavaScriptInterface?>(null) }

    val context = LocalContext.current
    val webView = remember {
        setupWebView(
            url = url,
            redirectUrl = redirectUrl,
            context = context,
            onLinkTokenStateChange = { linkTokenState = it },
            onLoadingChange = { isLoading = it },
            onJsNativeInterfaceChange = { jsNativeInterface = it },
            onFullScreenRequested = onFullScreenRequested,
            onClose = onClose,
        )
    }

    // This shows the WebView and a progress indicator while the WebView is loading
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView },
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(1f),
            )
        }
    }

    // Dispose the WebView when the composable is disposed
    DisposableEffect(key1 = webView) {
        onDispose {
            webView.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                removeAllViews()
                // Remove the JavaScript interface
                removeJavascriptInterface("Android")
                // Finally, destroy the WebView itself
                destroy()
            }
        }
    }

    Plaid.setLinkEventListener { event ->
        if (event.eventName == LinkEventName.HANDOFF) {
            linkTokenState = ""
        }
    }

    val plaidLink = remember {
        OpenPlaidLink()
    }

    val plaidActivityResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when (val plaidResult = plaidLink.parseResult(result.resultCode, result.data)) {
            is LinkSuccess -> {
                jsNativeInterface?.callOnAndroidSuccess(
                    metadata = plaidResult.metadata.metadataJson,
                )
            }

            is LinkExit -> {
                jsNativeInterface?.callOnAndroidSuccess(
                    metadata = plaidResult.error?.errorJson,
                )
            }

            else -> throw IllegalStateException("Unexpected result $plaidResult")
        }
    }

    // Wait for the link token to be set from the WebView before launching the Plaid Link
    if (linkTokenState.isNotEmpty()) {
        plaidActivityResultLauncher.launch(
            plaidLink.createIntent(
                context = context,
                linkConfiguration = linkTokenConfiguration {
                    token = linkTokenState
                },
            ),
        )
    }
}

/**
 * Sets up a WebView with the specified parameters. This WebView is used to load the Plaid Link. We setup a WebViewClient
 * to handle the URL loading and inject JavaScript code to handle the communication between the WebView and the Android.
 * A JavaScript interface is also injected to handle the communication between the WebView and the Android. The JavaScript
 * interface is used to call Android functions from the WebView.
 *
 * @param url The URL to load in the WebView.
 * @param redirectUrl The URL the pavilion pay process will redirect to. This is used to help ensure security and only a valid
 * redirect is allowed.
 * @param context The context to use for creating the WebView.
 * @param onLinkTokenStateChange A callback function to be invoked when the link token state changes.
 * @param onLoadingChange A callback function to be invoked when the loading state changes.
 * @param onJsNativeInterfaceChange A callback function to be invoked when the JavaScript native interface changes.
 * @param onClose A callback function to be invoked when the WebView is closed.
 * @return A WebView set up with the specified parameters.
 */
private fun setupWebView(
        url: String,
        redirectUrl: String,
        context: Context,
        onLinkTokenStateChange: (String) -> Unit,
        onLoadingChange: (Boolean) -> Unit,
        onJsNativeInterfaceChange: (JavaScriptInterface?) -> Unit,
        onFullScreenRequested: () -> Unit,
        onClose: () -> Unit,
) = WebView(context).apply {
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return request?.let {
                if (request.url.toString().startsWith("tel:")) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, request.url))
                    return true
                }

                val comparisonUri = java.net.URI(redirectUrl)
                !(
                        request.url.scheme == comparisonUri.scheme &&
                                request.url.host == comparisonUri.host &&
                                request.url.path == comparisonUri.path
                        )
            } ?: true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Inject JavaScript code
            evaluateJavascript(
                context.getString(R.string.event_listener_script),
                null,
            )
            onLoadingChange(false)
        }
    }
    settings.setSupportMultipleWindows(true)

    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            val popup = WebView(context)
            view?.addView(popup)
            (resultMsg?.obj as? WebView.WebViewTransport)?.webView = popup
            resultMsg?.sendToTarget()
            return true
        }
    }

    WebView.setWebContentsDebuggingEnabled(true)
    @SuppressLint("SetJavaScriptEnabled")
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true

    val jsNativeInterface = JavaScriptInterface(
        webView = this,
        tokenHandler = onLinkTokenStateChange,
        fullScreenHandler = onFullScreenRequested,
        closeHandler = {
            onLinkTokenStateChange("")
            onClose()
        },
    )
    addJavascriptInterface(jsNativeInterface, "Android")
    onJsNativeInterfaceChange(jsNativeInterface)
    this.loadUrl(url)
}
