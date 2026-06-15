package com.mytm.darrbi.presentation.topup

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import org.json.JSONObject

/**
 * Full-screen in-app WebView for the ClickPay hosted payment page (mirrors ride-android's WebViewActivity).
 * On each page load it asks the page for its body text via the `Android` JS bridge; once that text parses
 * as JSON with a `statusCode`, the payment is complete and [onResult] fires (success = statusCode ≤ 300).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebViewDialog(url: String, onResult: (Boolean) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_cancel),
                        tint = DarrbiTheme.colors.onSurface,
                        modifier = Modifier.size(24.dp).clickable(onClick = onCancel),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.addbalance_add_balance),
                        style = DarrbiTheme.typography.title,
                        color = DarrbiTheme.colors.onSurface,
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            addJavascriptInterface(PaymentResultBridge(onResult), "Android")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    // Ask the page for its body text; the bridge decides if it's the result page.
                                    view?.loadUrl(
                                        "javascript:window.Android.processContent(" +
                                            "document.getElementsByTagName('body')[0].innerText)",
                                    )
                                }
                            }
                            loadUrl(url)
                        }
                    },
                )
            }
        }
    }
}

/** Receives each page's body text; fires [onResult] once (on the main thread) when a status JSON appears. */
private class PaymentResultBridge(private val onResult: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var delivered = false

    @JavascriptInterface
    fun processContent(body: String) {
        if (delivered) return
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return
        if (!json.has("statusCode")) return
        delivered = true
        val success = json.optInt("statusCode") in 1..300
        main.post { onResult(success) }
    }
}
