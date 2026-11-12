package com.xaibxero.namelessproxy.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private data class LeakWebTab(
    val title: String,
    val defaultUrl: String
)

private val PresetTabs = listOf(
    LeakWebTab("IPFighter", "https://ipfighter.com"),
    LeakWebTab("DNS Leak", "https://www.dnsleaktest.com"),
    LeakWebTab("WebRTC", "https://browserleaks.com/webrtc")
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BuiltInWebTabs(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabUrls = remember {
        mutableStateListOf(
            PresetTabs[0].defaultUrl,
            PresetTabs[1].defaultUrl,
            PresetTabs[2].defaultUrl
        )
    }
    var addressInput by remember { mutableStateOf(tabUrls[0]) }
    var pageProgress by remember { mutableIntStateOf(0) }
    val webViews = remember { mutableStateMapOf<Int, WebView>() }

    LaunchedEffect(selectedTab) {
        addressInput = tabUrls[selectedTab]
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 3 Preset Browser Tabs (IPFighter / DNS Leak / WebRTC)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetTabs.forEachIndexed { idx, tab ->
                val isSelected = idx == selectedTab
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color(0xFF00F59B) else Color(0xFF0A0D14),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00F59B) else Color(0xFF1F2736)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable {
                            selectedTab = idx
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 9.dp)
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Compact Browser Address & Navigation Bar
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0A0D14),
            border = BorderStroke(1.dp, Color(0xFF1F2736)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                NavPill("<") {
                    webViews[selectedTab]?.let { if (it.canGoBack()) it.goBack() }
                }
                NavPill("↻") {
                    webViews[selectedTab]?.reload()
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
                        tabUrls[selectedTab] = target
                        webViews[selectedTab]?.loadUrl(target)
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F59B),
                        unfocusedBorderColor = Color(0xFF1F2736),
                        cursorColor = Color(0xFF00F59B)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
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

        // Live WebView Surface (All HTTP/DNS/WebRTC goes through kernel TProxy)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0D14),
            border = BorderStroke(1.dp, Color(0xFF1F2736)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            key(selectedTab) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        webViews[selectedTab] ?: WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!url.isNullOrBlank()) {
                                        tabUrls[selectedTab] = url
                                        addressInput = url
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                }
                            }
                            loadUrl(tabUrls[selectedTab])
                            webViews[selectedTab] = this
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavPill(symbol: String, onClick: () -> Unit) {
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
                color = Color(0xFF00F59B),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
