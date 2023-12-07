package com.pavilionpay.igamingkit

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
import com.plaid.link.Plaid
import com.plaid.link.OpenPlaidLink
import com.plaid.link.event.LinkEventName
import com.plaid.link.linkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess

/**
 * A composable function that displays a WebView and handles Plaid link events.
 *
 * @param url The URL to load in the WebView.
 * @param onClose A callback function to be invoked when the WebView is closed.
 */
@Composable
fun PavilionPlaidWebView(
    url: String,
    onClose: () -> Unit,
) {
    require(url.isNotEmpty()) { stringResource(R.string.url_cannot_be_empty) }

    var isLoading by remember { mutableStateOf(true) }
    var linkTokenState by remember { mutableStateOf("") }
    var successName by remember { mutableStateOf("") }
    var errorName by remember { mutableStateOf("") }
    var jsNativeInterface by remember { mutableStateOf<JavaScriptInterface?>(null) }

    val context = LocalContext.current
    val webView = remember {
        setupWebView(
            url = url,
            context = context,
            onLinkTokenStateChange = { linkTokenState = it },
            onSuccessNameChange = { successName = it },
            onErrorName = { errorName = it },
            onLoadingChange = { isLoading = it },
            onJsNativeInterfaceChange = { jsNativeInterface = it },
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
        Log.d("PPI", "Event $event")
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
                Log.d("PPI", plaidResult.publicToken)
                Log.d("PPI", plaidResult.metadata.metadataJson)
                jsNativeInterface?.callJavascriptFunctionWithName(
                    name = successName,
                    metadata = plaidResult.metadata.metadataJson,
                )
            }
            is LinkExit -> {
                Log.d("PPI", plaidResult.error?.errorJson.toString())
                plaidResult.error?.errorMessage?.let { Log.d("PPI", it) }
                jsNativeInterface?.callJavascriptFunctionWithName(
                    name = errorName,
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
 * @param context The context to use for creating the WebView.
 * @param onLinkTokenStateChange A callback function to be invoked when the link token state changes.
 * @param onSuccessNameChange A callback function to be invoked when the success name changes.
 * @param onErrorName A callback function to be invoked when the error name changes.
 * @param onLoadingChange A callback function to be invoked when the loading state changes.
 * @param onJsNativeInterfaceChange A callback function to be invoked when the JavaScript native interface changes.
 * @param onClose A callback function to be invoked when the WebView is closed.
 * @return A WebView set up with the specified parameters.
 */
private fun setupWebView(
    url: String,
    context: Context,
    onLinkTokenStateChange: (String) -> Unit,
    onSuccessNameChange: (String) -> Unit,
    onErrorName: (String) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onJsNativeInterfaceChange: (JavaScriptInterface?) -> Unit,
    onClose: () -> Unit,
) = WebView(context).apply {
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            Log.d("PPI", "shouldOverrideUrlLoading ${request?.url}")
            return false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Inject JavaScript code
            evaluateJavascript("""
                window.addEventListener("message", (e) => {
                    window.Android.consumeWindowMessage(e.name, e.data);
                });
            """, null
            )
            onLoadingChange(false)
        }
    }
    WebView.setWebContentsDebuggingEnabled(true)
    @SuppressLint("SetJavaScriptEnabled")
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true

    val jsNativeInterface = JavaScriptInterface(
        webView = this,
        tokenHandler = { token, successCallback, errorCallBack ->
            onLinkTokenStateChange(token)
            onSuccessNameChange(successCallback)
            onErrorName(errorCallBack)
        },
        closeHandler = {
            onLinkTokenStateChange("")
            onClose()
        }
    )
    addJavascriptInterface(jsNativeInterface, "Android")
    onJsNativeInterfaceChange(jsNativeInterface)
    this.loadUrl(url)
}