package com.xaibxero.namelessproxy.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

data class LeakTabPreset(
    val title: String,
    val url: String
)

val LeakTabPresets = listOf(
    LeakTabPreset("IPFighter", "https://ipfighter.com"),
    LeakTabPreset("DNS Leak", "https://www.dnsleaktest.com"),
    LeakTabPreset("WebRTC", "https://browserleaks.com/webrtc")
)

private fun destroyWebViewCleanly(wv: WebView?) {
    if (wv == null) return
    try {
        wv.stopLoading()
        wv.loadUrl("about:blank")
        wv.clearHistory()
        wv.removeAllViews()
        wv.destroy()
    } catch (_: Exception) {
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SingleAutoLeakWebTab(
    preset: LeakTabPreset,
    gpuAccelerated: Boolean,
    onCloseTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var addressInput by remember(preset.url) { mutableStateOf(preset.url) }
    var pageProgress by remember(preset.url) { mutableIntStateOf(0) }
    var activeWebView by remember(preset.url) { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = activeWebView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onCloseTab()
        }
    }

    // Auto-close and release WebView memory immediately when switching tabs or leaving app
    DisposableEffect(preset.url, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                destroyWebViewCleanly(activeWebView)
                activeWebView = null
                onCloseTab()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyWebViewCleanly(activeWebView)
            activeWebView = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0A0D14),
            border = BorderStroke(1.dp, Color(0xFF1E2634)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                SmallCircleBtn("<") {
                    activeWebView?.let { if (it.canGoBack()) it.goBack() }
                }
                SmallCircleBtn("↻") {
                    activeWebView?.reload()
                }
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        val target = if (addressInput.startsWith("http://") || addressInput.startsWith("https://")) {
                            addressInput.trim()
                        } else {
                            "https://${addressInput.trim()}"
                        }
                        activeWebView?.loadUrl(target)
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F59B),
                        unfocusedBorderColor = Color(0xFF1E2634),
                        cursorColor = Color(0xFF00F59B)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
                SmallCircleBtn("✕", tint = Color(0xFFFF4D6D)) {
                    onCloseTab()
                }
            }
        }

        if (pageProgress in 1..99) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                color = Color(0xFF00F59B),
                trackColor = Color(0xFF141923),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0D14),
            border = BorderStroke(1.dp, Color(0xFF1E2634)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            key(preset.url) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setLayerType(
                                if (gpuAccelerated) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE,
                                null
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!url.isNullOrBlank()) addressInput = url
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                }
                            }
                            loadUrl(preset.url)
                            activeWebView = this
                        }
                    },
                    update = { wv ->
                        wv.setLayerType(
                            if (gpuAccelerated) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE,
                            null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SmallCircleBtn(symbol: String, tint: Color = Color(0xFF00F59B), onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF131822),
        border = BorderStroke(1.dp, Color(0xFF232C3D)),
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = tint,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
