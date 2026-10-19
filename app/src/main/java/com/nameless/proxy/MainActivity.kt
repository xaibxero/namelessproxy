package com.nameless.proxy

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.nameless.proxy.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

data class AppItem(
    val name: String,
    val packageName: String,
    val uid: Int
)

data class LiveNetworkState(
    val transportLabel: String = "Network",
    val interfaceName: String = "auto",
    val isCellular: Boolean = false,
    val isWifi: Boolean = false,
    val isConnected: Boolean = true
)

private val ProxyType.displayTitle: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "SOCKS5"
        ProxyType.SOCKS4 -> "SOCKS4"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "Shadowsocks"
        ProxyType.VLESS -> "VLESS"
        ProxyType.TROJAN -> "Trojan"
        ProxyType.HYSTERIA2 -> "Hysteria 2"
    }

private val ProxyType.shortBadge: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "SOCKS5"
        ProxyType.SOCKS4 -> "SOCKS4"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "SS"
        ProxyType.VLESS -> "VLESS"
        ProxyType.TROJAN -> "TROJAN"
        ProxyType.HYSTERIA2 -> "HY2"
    }

// -------------------------------------------------------------
// ZUAN KERNEL MANAGER LIQUID GLASS DESIGN SYSTEM
// Inspired by ZUANVFX01/ZKM & Kyant0/android-liquid-glass
// -------------------------------------------------------------

private val ZuanDeepSpace = Color(0xFF060913)
private val ZuanGlassBase = Color(0xB80E1628)
private val ZuanGlassElevated = Color(0xD416223B)
private val ZuanAqua = Color(0xFF00E5FF)
private val ZuanSky = Color(0xFF38BDF8)
private val ZuanMint = Color(0xFF00F5A0)
private val ZuanEmerald = Color(0xFF10B981)
private val ZuanViolet = Color(0xFF8B5CF6)
private val ZuanIndigo = Color(0xFF6366F1)
private val ZuanAmber = Color(0xFFFFB800)
private val ZuanRose = Color(0xFFFF3366)
private val ZuanTextPrimary = Color(0xFFF8FAFC)
private val ZuanTextSecondary = Color(0xFFA3B8CC)
private val ZuanTextMuted = Color(0xFF64748B)

/**
 * Zuan Kernel Manager's signature animated Liquid Mesh Background.
 * Renders 3 organic drifting plasma blobs with radial specular diffusion.
 */
@Composable
fun ZuanLiquidMeshBackground(
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "zuanMesh")
    val phase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "p1"
    )
    val phase2 by infinite.animateFloat(
        initialValue = 6.283185f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing)),
        label = "p2"
    )

    val blobPrimary by animateColorAsState(
        targetValue = when {
            isTransitioning -> ZuanAmber
            isProxyActive -> ZuanMint
            else -> ZuanIndigo
        },
        animationSpec = tween(800),
        label = "blob1"
    )
    val blobSecondary by animateColorAsState(
        targetValue = when {
            isTransitioning -> ZuanRose
            isProxyActive -> ZuanAqua
            else -> ZuanViolet
        },
        animationSpec = tween(800),
        label = "blob2"
    )
    val blobTertiary by animateColorAsState(
        targetValue = if (isProxyActive) ZuanIndigo else ZuanAqua,
        animationSpec = tween(800),
        label = "blob3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val c1 = Offset(
            x = w * (0.28f + 0.22f * cos(phase1)),
            y = h * (0.18f + 0.10f * sin(phase1))
        )
        val c2 = Offset(
            x = w * (0.76f + 0.18f * sin(phase2)),
            y = h * (0.48f + 0.12f * cos(phase2))
        )
        val c3 = Offset(
            x = w * (0.35f + 0.24f * sin(phase1 * 0.7f)),
            y = h * (0.82f + 0.08f * cos(phase2 * 0.7f))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blobPrimary.copy(alpha = 0.22f), Color.Transparent),
                center = c1,
                radius = w * 0.78f
            ),
            radius = w * 0.78f,
            center = c1
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blobSecondary.copy(alpha = 0.18f), Color.Transparent),
                center = c2,
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = c2
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blobTertiary.copy(alpha = 0.14f), Color.Transparent),
                center = c3,
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = c3
        )
    }
}

/**
 * Zuan Liquid Glass Surface Card with diagonal specular rim lighting,
 * translucent frosted fill, and top-edge inner refraction highlight.
 */
@Composable
fun ZuanLiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    glowColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val rimTopLeft = glowColor?.copy(alpha = 0.62f) ?: Color.White.copy(alpha = 0.34f)
    val rimMid = glowColor?.copy(alpha = 0.22f) ?: Color.White.copy(alpha = 0.10f)
    val rimBottomRight = glowColor?.copy(alpha = 0.35f) ?: Color.White.copy(alpha = 0.05f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(ZuanGlassBase)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.09f),
                        if (glowColor != null) glowColor.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.02f),
                        Color.Black.copy(alpha = 0.18f)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(rimTopLeft, rimMid, rimBottomRight),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 900f)
                ),
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Zuan Squircle Icon Badge used in Bento cards and list headers.
 */
@Composable
fun ZuanIconBadge(
    icon: IconType,
    accent: Color,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.08f))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.65f), accent.copy(alpha = 0.15f))
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        VectorIcon(type = icon, size = iconSize, tint = accent)
    }
}

enum class IconType {
    POWER, BOLT, SHIELD, GLOBE, SLIDERS, HUB, TERMINAL,
    WIFI, CELLULAR, HOTSPOT, ROCKET, COPY, PASTE, REFRESH,
    TRASH, DOWN_ARROW, UP_ARROW, EXPORT, IMPORT, APPS,
    SEARCH, EYE, EYE_OFF, PLAY, PAUSE, CHECK, SERVER, PORT, USER, KEY
}

@Composable
fun VectorIcon(
    type: IconType,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = ZuanTextPrimary,
    strokeWidth: Float = 2.0f
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (type) {
            IconType.POWER -> {
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.18f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.10f),
                    end = Offset(w * 0.5f, h * 0.48f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
            IconType.BOLT -> {
                val p = Path().apply {
                    moveTo(w * 0.55f, h * 0.08f)
                    lineTo(w * 0.20f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.40f, h * 0.92f)
                    lineTo(w * 0.80f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }
                drawPath(p, color = tint)
            }
            IconType.SHIELD -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.52f)
                    cubicTo(w * 0.84f, h * 0.74f, w * 0.5f, h * 0.90f, w * 0.5f, h * 0.90f)
                    cubicTo(w * 0.5f, h * 0.90f, w * 0.16f, h * 0.74f, w * 0.16f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.24f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.46f, h * 0.60f)
                    lineTo(w * 0.66f, h * 0.40f)
                }
                drawPath(check, color = tint, style = stroke)
            }
            IconType.GLOBE -> {
                drawCircle(color = tint, radius = w * 0.38f, style = stroke)
                drawLine(tint, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), sw)
                drawOval(
                    color = tint,
                    topLeft = Offset(w * 0.31f, h * 0.12f),
                    size = Size(w * 0.38f, h * 0.76f),
                    style = stroke
                )
            }
            IconType.SLIDERS -> {
                drawLine(tint, Offset(w * 0.16f, h * 0.28f), Offset(w * 0.84f, h * 0.28f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.16f, h * 0.72f), Offset(w * 0.84f, h * 0.72f), sw, StrokeCap.Round)
                drawCircle(ZuanDeepSpace, radius = w * 0.11f, center = Offset(w * 0.36f, h * 0.28f))
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.36f, h * 0.28f), style = stroke)
                drawCircle(ZuanDeepSpace, radius = w * 0.11f, center = Offset(w * 0.66f, h * 0.50f))
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.66f, h * 0.50f), style = stroke)
                drawCircle(ZuanDeepSpace, radius = w * 0.11f, center = Offset(w * 0.42f, h * 0.72f))
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.42f, h * 0.72f), style = stroke)
            }
            IconType.HUB -> {
                val c = Offset(w * 0.5f, h * 0.5f)
                val p1 = Offset(w * 0.22f, h * 0.24f)
                val p2 = Offset(w * 0.78f, h * 0.24f)
                val p3 = Offset(w * 0.22f, h * 0.76f)
                val p4 = Offset(w * 0.78f, h * 0.76f)
                drawLine(tint, c, p1, sw * 0.85f)
                drawLine(tint, c, p2, sw * 0.85f)
                drawLine(tint, c, p3, sw * 0.85f)
                drawLine(tint, c, p4, sw * 0.85f)
                drawCircle(tint, radius = w * 0.13f, center = c)
                drawCircle(tint, radius = w * 0.09f, center = p1)
                drawCircle(tint, radius = w * 0.09f, center = p2)
                drawCircle(tint, radius = w * 0.09f, center = p3)
                drawCircle(tint, radius = w * 0.09f, center = p4)
            }
            IconType.TERMINAL -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.16f),
                    size = Size(w * 0.76f, h * 0.68f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                    style = stroke
                )
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.38f)
                    lineTo(w * 0.42f, h * 0.50f)
                    lineTo(w * 0.28f, h * 0.62f)
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(w * 0.48f, h * 0.62f), Offset(w * 0.68f, h * 0.62f), sw, StrokeCap.Round)
            }
            IconType.WIFI -> {
                drawArc(tint, -135f, 90f, false, Offset(w * 0.10f, h * 0.20f), Size(w * 0.80f, h * 0.80f), style = stroke)
                drawArc(tint, -135f, 90f, false, Offset(w * 0.24f, h * 0.38f), Size(w * 0.52f, h * 0.52f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.76f))
            }
            IconType.CELLULAR -> {
                val barW = w * 0.11f
                val xs = listOf(0.18f, 0.36f, 0.54f, 0.72f)
                val hs = listOf(0.28f, 0.44f, 0.60f, 0.76f)
                for (i in 0..3) {
                    val bh = h * hs[i]
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * xs[i], h * 0.84f - bh),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(barW * 0.4f, barW * 0.4f)
                    )
                }
            }
            IconType.HOTSPOT -> {
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.54f))
                drawArc(tint, -145f, 110f, false, Offset(w * 0.24f, h * 0.28f), Size(w * 0.52f, h * 0.52f), style = stroke)
                drawArc(tint, -150f, 120f, false, Offset(w * 0.10f, h * 0.14f), Size(w * 0.80f, h * 0.80f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.56f), Offset(w * 0.5f, h * 0.88f), sw, StrokeCap.Round)
            }
            IconType.ROCKET -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    cubicTo(w * 0.74f, h * 0.28f, w * 0.72f, h * 0.58f, w * 0.64f, h * 0.72f)
                    lineTo(w * 0.36f, h * 0.72f)
                    cubicTo(w * 0.28f, h * 0.58f, w * 0.26f, h * 0.28f, w * 0.5f, h * 0.12f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.44f))
                drawLine(tint, Offset(w * 0.38f, h * 0.86f), Offset(w * 0.62f, h * 0.86f), sw, StrokeCap.Round)
            }
            IconType.COPY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.30f, h * 0.28f),
                    size = Size(w * 0.54f, h * 0.58f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke
                )
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.66f)
                    lineTo(w * 0.16f, h * 0.66f)
                    lineTo(w * 0.16f, h * 0.16f)
                    lineTo(w * 0.64f, h * 0.16f)
                }
                drawPath(p, tint, style = stroke)
            }
            IconType.PASTE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.22f),
                    size = Size(w * 0.60f, h * 0.66f),
                    cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.36f, h * 0.12f),
                    size = Size(w * 0.28f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.48f), Offset(w * 0.66f, h * 0.48f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.66f), Offset(w * 0.56f, h * 0.66f), sw, StrokeCap.Round)
            }
            IconType.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = -30f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke
                )
                val arrow = Path().apply {
                    moveTo(w * 0.64f, h * 0.14f)
                    lineTo(w * 0.84f, h * 0.28f)
                    lineTo(w * 0.64f, h * 0.40f)
                }
                drawPath(arrow, tint, style = stroke)
            }
            IconType.TRASH -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.24f), Offset(w * 0.82f, h * 0.24f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.14f), Offset(w * 0.62f, h * 0.14f), sw, StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.26f, h * 0.28f),
                    size = Size(w * 0.48f, h * 0.58f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
            }
            IconType.DOWN_ARROW -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.80f), sw, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.56f)
                    lineTo(w * 0.50f, h * 0.82f)
                    lineTo(w * 0.74f, h * 0.56f)
                }
                drawPath(p, tint, style = stroke)
            }
            IconType.UP_ARROW -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.84f), Offset(w * 0.5f, h * 0.20f), sw, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.44f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.74f, h * 0.44f)
                }
                drawPath(p, tint, style = stroke)
            }
            IconType.EXPORT -> {
                val tray = Path().apply {
                    moveTo(w * 0.16f, h * 0.60f)
                    lineTo(w * 0.16f, h * 0.84f)
                    lineTo(w * 0.84f, h * 0.84f)
                    lineTo(w * 0.84f, h * 0.60f)
                }
                drawPath(tray, tint, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.5f, h * 0.16f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.30f, h * 0.36f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.70f, h * 0.36f)
                }
                drawPath(head, tint, style = stroke)
            }
            IconType.IMPORT -> {
                val tray = Path().apply {
                    moveTo(w * 0.16f, h * 0.60f)
                    lineTo(w * 0.16f, h * 0.84f)
                    lineTo(w * 0.84f, h * 0.84f)
                    lineTo(w * 0.84f, h * 0.60f)
                }
                drawPath(tray, tint, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.64f), sw, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.30f, h * 0.44f)
                    lineTo(w * 0.50f, h * 0.64f)
                    lineTo(w * 0.70f, h * 0.44f)
                }
                drawPath(head, tint, style = stroke)
            }
            IconType.APPS -> {
                val r = CornerRadius(w * 0.08f, w * 0.08f)
                val s = Size(w * 0.32f, h * 0.32f)
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.14f), s, r, style = stroke)
                drawRoundRect(tint, Offset(w * 0.54f, h * 0.14f), s, r, style = stroke)
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.54f), s, r, style = stroke)
                drawRoundRect(tint, Offset(w * 0.54f, h * 0.54f), s, r, style = stroke)
            }
            IconType.SEARCH -> {
                drawCircle(tint, radius = w * 0.28f, center = Offset(w * 0.44f, h * 0.44f), style = stroke)
                drawLine(tint, Offset(w * 0.65f, h * 0.65f), Offset(w * 0.86f, h * 0.86f), sw, StrokeCap.Round)
            }
            IconType.EYE -> {
                val p = Path().apply {
                    moveTo(w * 0.10f, h * 0.50f)
                    cubicTo(w * 0.28f, h * 0.20f, w * 0.72f, h * 0.20f, w * 0.90f, h * 0.50f)
                    cubicTo(w * 0.72f, h * 0.80f, w * 0.28f, h * 0.80f, w * 0.10f, h * 0.50f)
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.5f))
            }
            IconType.EYE_OFF -> {
                val p = Path().apply {
                    moveTo(w * 0.10f, h * 0.50f)
                    cubicTo(w * 0.28f, h * 0.20f, w * 0.72f, h * 0.20f, w * 0.90f, h * 0.50f)
                    cubicTo(w * 0.72f, h * 0.80f, w * 0.28f, h * 0.80f, w * 0.10f, h * 0.50f)
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.82f, h * 0.18f), sw, StrokeCap.Round)
            }
            IconType.PLAY -> {
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.20f)
                    lineTo(w * 0.78f, h * 0.50f)
                    lineTo(w * 0.28f, h * 0.80f)
                    close()
                }
                drawPath(p, tint)
            }
            IconType.PAUSE -> {
                drawLine(tint, Offset(w * 0.34f, h * 0.22f), Offset(w * 0.34f, h * 0.78f), sw * 1.3f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.22f), Offset(w * 0.66f, h * 0.78f), sw * 1.3f, StrokeCap.Round)
            }
            IconType.CHECK -> {
                val p = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.82f, h * 0.28f)
                }
                drawPath(p, tint, style = stroke)
            }
            IconType.SERVER -> {
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.18f), Size(w * 0.72f, h * 0.26f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.56f), Size(w * 0.72f, h * 0.26f), CornerRadius(w * 0.08f, w * 0.08f), style = stroke)
                drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.28f, h * 0.31f))
                drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.28f, h * 0.69f))
            }
            IconType.PORT -> {
                drawLine(tint, Offset(w * 0.36f, h * 0.18f), Offset(w * 0.28f, h * 0.82f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.18f), Offset(w * 0.60f, h * 0.82f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.38f), Offset(w * 0.82f, h * 0.38f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.62f), Offset(w * 0.82f, h * 0.62f), sw, StrokeCap.Round)
            }
            IconType.USER -> {
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.32f), style = stroke)
                drawArc(tint, -165f, 150f, false, Offset(w * 0.18f, h * 0.58f), Size(w * 0.64f, h * 0.50f), style = stroke)
            }
            IconType.KEY -> {
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.32f, h * 0.50f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.74f, h * 0.50f), Offset(w * 0.74f, h * 0.68f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.86f, h * 0.50f), Offset(w * 0.86f, h * 0.64f), sw, StrokeCap.Round)
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var rootState by mutableStateOf(RootState.CHECKING)
    private var isProxyRunningState by mutableStateOf(false)
    private var isTransitioningState by mutableStateOf(false)
    private var runningSlotState by mutableStateOf<Int?>(null)
    private var activePidState by mutableStateOf<String?>(null)
    private var liveNetState by mutableStateOf(LiveNetworkState())
    private var handoverNonce by mutableIntStateOf(0)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()
        registerLiveNetworkMonitor()

        lifecycleScope.launch(Dispatchers.IO) {
            val s0 = getSharedPreferences("nameless_slot_0", Context.MODE_PRIVATE)
            if (s0.getString("host", "").isNullOrBlank()) {
                val restored = PersistentStorage.autoRestoreAllFromRoot(this@MainActivity)
                if (restored) {
                    withContext(Dispatchers.Main) {
                        syncDaemonStatus()
                    }
                }
            }
        }

        val globalPrefs = getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
        if (!globalPrefs.getBoolean("start_on_boot", false)) {
            lifecycleScope.launch(Dispatchers.IO) {
                BootManager.removeBootScript()
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncDaemonStatus()
                probeActiveNetwork()
            }
        })

        setContent {
            val scheme = darkColorScheme(
                primary = ZuanAqua,
                onPrimary = ZuanDeepSpace,
                secondary = ZuanIndigo,
                background = ZuanDeepSpace,
                surface = ZuanGlassBase,
                onBackground = ZuanTextPrimary,
                onSurface = ZuanTextPrimary,
                error = ZuanRose
            )

            MaterialTheme(colorScheme = scheme) {
                MainScreen(
                    rootState = rootState,
                    isProxyActive = isProxyRunningState,
                    isTransitioning = isTransitioningState,
                    runningSlot = runningSlotState,
                    activePid = activePidState,
                    liveNet = liveNetState,
                    handoverNonce = handoverNonce,
                    onRecheckRoot = { refreshRootStatus() },
                    installedApps = installedApps,
                    onSyncStatus = { syncDaemonStatus() },
                    onStartProxy = { settings, uids, onResult ->
                        if (isTransitioningState) return@MainScreen
                        isTransitioningState = true
                        lifecycleScope.launch(Dispatchers.IO) {
                            val res = ProxyController.startProxy(this@MainActivity, settings, uids)
                            val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)
                            val pid = if (rSlot != null) ProxyController.getActivePid(ProfileManager.androidUserId, rSlot) else null
                            withContext(Dispatchers.Main) {
                                isProxyRunningState = rSlot != null
                                runningSlotState = rSlot
                                activePidState = pid
                                isTransitioningState = false
                                onResult(res.success, res.errorMessage)
                            }
                        }
                    },
                    onStopProxy = { onResult ->
                        if (isTransitioningState) return@MainScreen
                        // Optimistic immediate UI response while <120ms stop completes
                        isTransitioningState = true
                        isProxyRunningState = false
                        runningSlotState = null
                        activePidState = null
                        lifecycleScope.launch(Dispatchers.IO) {
                            val ok = ProxyController.stopProxy(this@MainActivity)
                            withContext(Dispatchers.Main) {
                                isTransitioningState = false
                                onResult(ok)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    private fun registerLiveNetworkMonitor() {
        probeActiveNetwork()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = handleNetworkChange()
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = handleNetworkChange()
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = handleNetworkChange()
                override fun onLost(network: Network) = handleNetworkChange()
            }
            networkCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {}
    }

    private fun handleNetworkChange() {
        probeActiveNetwork()
        lifecycleScope.launch(Dispatchers.IO) {
            val user = ProfileManager.androidUserId
            val rSlot = ProxyController.getRunningSlot(user)
            if (rSlot != null) {
                ProxyController.refreshHandoverRules(user, rSlot)
                withContext(Dispatchers.Main) {
                    handoverNonce++
                }
            }
        }
    }

    private fun probeActiveNetwork() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNet = cm.activeNetwork
            val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
            val lp = activeNet?.let { cm.getLinkProperties(it) }
            val iface = lp?.interfaceName ?: detectFallbackInterface()

            if (caps == null) {
                liveNetState = LiveNetworkState("Offline", iface, false, false, false)
                return
            }

            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            liveNetState = LiveNetworkState(
                transportLabel = if (isWifi) "Wi-Fi" else if (isCell) "Cellular" else "Network",
                interfaceName = iface.ifEmpty { if (isWifi) "wlan0" else "rmnet" },
                isCellular = isCell,
                isWifi = isWifi,
                isConnected = true
            )
        } catch (_: Exception) {
            liveNetState = LiveNetworkState("Online", detectFallbackInterface(), false, false, true)
        }
    }

    private fun detectFallbackInterface(): String {
        return try {
            var best = "auto"
            File("/proc/net/dev").forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.contains(":")) {
                    val iface = trimmed.substringBefore(":").trim()
                    val parts = trimmed.substringAfter(":").trim().split(Regex("\\s+"))
                    val rx = parts.firstOrNull()?.toLongOrNull() ?: 0L
                    if (rx > 0) {
                        if (iface.startsWith("wlan")) best = iface
                        else if ((iface.startsWith("rmnet") || iface.startsWith("v4-rmnet") || iface.startsWith("ccmni")) && !best.startsWith("wlan")) {
                            best = iface
                        }
                    }
                }
            }
            best
        } catch (_: Exception) {
            "auto"
        }
    }

    private fun syncDaemonStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)
            val pid = if (rSlot != null) ProxyController.getActivePid(ProfileManager.androidUserId, rSlot) else null
            withContext(Dispatchers.Main) {
                isProxyRunningState = rSlot != null
                runningSlotState = rSlot
                activePidState = pid
            }
        }
    }

    private fun refreshRootStatus() {
        lifecycleScope.launch {
            rootState = RootState.CHECKING
            val (s, _) = RootChecker.verifyRoot()
            rootState = s
        }
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter {
                    it.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                        it.packageName.contains("chrome") ||
                        it.packageName.contains("edge") ||
                        it.packageName.contains("firefox") ||
                        it.packageName.contains("brave") ||
                        it.packageName.contains("youtube")
                }
                .map { AppItem(it.loadLabel(pm).toString(), it.packageName, it.uid) }
                .sortedBy { it.name.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps = apps
            }
        }
    }
}

private fun readPhysicalNetworkBytes(): Pair<Long, Long> {
    return try {
        var rx = 0L
        var tx = 0L
        File("/proc/net/dev").forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.contains(":")) {
                val iface = trimmed.substringBefore(":").trim()
                if (iface.startsWith("wlan") ||
                    iface.startsWith("rmnet") ||
                    iface.startsWith("rev_rmnet") ||
                    iface.startsWith("v4-rmnet") ||
                    iface.startsWith("ccmni") ||
                    iface.startsWith("pdp") ||
                    iface.startsWith("eth") ||
                    iface.startsWith("rndis")
                ) {
                    val parts = trimmed.substringAfter(":").trim().split(Regex("\\s+"))
                    if (parts.size >= 9) {
                        rx += parts[0].toLongOrNull() ?: 0L
                        tx += parts[8].toLongOrNull() ?: 0L
                    }
                }
            }
        }
        if (rx > 0L || tx > 0L) Pair(rx, tx)
        else Pair(TrafficStats.getTotalRxBytes().coerceAtLeast(0L), TrafficStats.getTotalTxBytes().coerceAtLeast(0L))
    } catch (_: Exception) {
        Pair(TrafficStats.getTotalRxBytes().coerceAtLeast(0L), TrafficStats.getTotalTxBytes().coerceAtLeast(0L))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    rootState: RootState,
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activePid: String?,
    liveNet: LiveNetworkState,
    handoverNonce: Int,
    onRecheckRoot: () -> Unit,
    installedApps: List<AppItem>,
    onSyncStatus: () -> Unit,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var currentRxRate by remember { mutableLongStateOf(0L) }
    var currentTxRate by remember { mutableLongStateOf(0L) }
    var totalRxBytes by remember { mutableLongStateOf(0L) }
    var totalTxBytes by remember { mutableLongStateOf(0L) }
    var rxHistory by remember { mutableStateOf(List(24) { 0L }) }
    var txHistory by remember { mutableStateOf(List(24) { 0L }) }

    LaunchedEffect(isProxyActive) {
        if (isProxyActive) {
            if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
            val (initRx, initTx) = withContext(Dispatchers.IO) { readPhysicalNetworkBytes() }
            var prevRx = initRx
            var prevTx = initTx
            val startRx = initRx
            val startTx = initTx
            var prevTime = System.currentTimeMillis()

            while (isActive) {
                delay(1000)
                val (currRxRaw, currTxRaw) = withContext(Dispatchers.IO) { readPhysicalNetworkBytes() }
                val currRx = currRxRaw.coerceAtLeast(prevRx)
                val currTx = currTxRaw.coerceAtLeast(prevTx)
                val now = System.currentTimeMillis()
                val dt = (now - prevTime).coerceAtLeast(1L) / 1000.0

                val rxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)
                val txRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)
                currentRxRate = rxRate
                currentTxRate = txRate
                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)
                rxHistory = (rxHistory.drop(1) + rxRate)
                txHistory = (txHistory.drop(1) + txRate)

                prevRx = currRx
                prevTx = currTx
                prevTime = now
            }
        } else {
            sessionStartMs = 0L
            currentRxRate = 0L
            currentTxRate = 0L
            totalRxBytes = 0L
            totalTxBytes = 0L
            rxHistory = List(24) { 0L }
            txHistory = List(24) { 0L }
        }
    }

    fun getSlotPrefs(slot: Int) = context.getSharedPreferences("nameless_slot_$slot", Context.MODE_PRIVATE)

    var proxyType by remember { mutableStateOf(ProxyType.SOCKS5) }
    var transportMode by remember { mutableStateOf(TransportMode.TCP_AND_UDP) }
    var ipMode by remember { mutableStateOf(IpMode.IPV4_ONLY) }
    var strictWebRtcShield by remember { mutableStateOf(true) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var ssMethod by remember { mutableStateOf("2022-blake3-aes-128-gcm") }
    var realityPublicKey by remember { mutableStateOf("") }
    var realityShortId by remember { mutableStateOf("") }
    var routeWholeProfile by remember { mutableStateOf(true) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var configuredSlots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var slotProtocolMap by remember { mutableStateOf<Map<Int, ProxyType>>(emptyMap()) }

    fun refreshConfiguredSlots() {
        val set = mutableSetOf<Int>()
        val pMap = mutableMapOf<Int, ProxyType>()
        for (i in 0 until ProfileManager.MAX_SLOTS) {
            val sp = getSlotPrefs(i)
            if (!sp.getString("host", "").isNullOrBlank()) set.add(i)
            pMap[i] = runCatching { ProxyType.valueOf(sp.getString("proxy_type", "SOCKS5")!!) }.getOrDefault(ProxyType.SOCKS5)
        }
        configuredSlots = set
        slotProtocolMap = pMap
    }

    fun buildTargetUids(): List<Int>? {
        if (routeWholeProfile) return null
        val uids = installedApps
            .filter { selectedPackages.contains(it.packageName) }
            .map { it.uid }
            .toMutableSet()
        uids.add(context.applicationInfo.uid)
        return uids.toList()
    }

    fun applySettingsToState(
        pHost: String, pPort: String, pUser: String, pPass: String,
        pSni: String, pSsMethod: String, pRpk: String, pRsid: String,
        pType: ProxyType, pTransport: TransportMode, pIpMode: IpMode,
        pStrictWebRtc: Boolean, pRouteWhole: Boolean, pPkgs: Set<String>
    ) {
        host = pHost
        port = pPort
        username = pUser
        password = pPass
        sni = pSni
        ssMethod = pSsMethod
        realityPublicKey = pRpk
        realityShortId = pRsid
        proxyType = pType
        transportMode = pTransport
        ipMode = pIpMode
        strictWebRtcShield = pStrictWebRtc
        routeWholeProfile = pRouteWhole
        selectedPackages = pPkgs
    }

    fun loadSlotData(slot: Int) {
        val sp = getSlotPrefs(slot)
        val h = sp.getString("host", "") ?: ""
        if (h.isNotEmpty()) {
            applySettingsToState(
                pHost = h,
                pPort = sp.getString("port", "1080") ?: "1080",
                pUser = sp.getString("username", "") ?: "",
                pPass = sp.getString("password", "") ?: "",
                pSni = sp.getString("sni", "") ?: "",
                pSsMethod = sp.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm",
                pRpk = sp.getString("reality_pk", "") ?: "",
                pRsid = sp.getString("reality_sid", "") ?: "",
                pType = runCatching { ProxyType.valueOf(sp.getString("proxy_type", "SOCKS5")!!) }.getOrDefault(ProxyType.SOCKS5),
                pTransport = runCatching { TransportMode.valueOf(sp.getString("transport_mode", "TCP_AND_UDP")!!) }.getOrDefault(TransportMode.TCP_AND_UDP),
                pIpMode = runCatching { IpMode.valueOf(sp.getString("ip_mode", "IPV4_ONLY")!!) }.getOrDefault(IpMode.IPV4_ONLY),
                pStrictWebRtc = sp.getBoolean("strict_webrtc_shield", true),
                pRouteWhole = sp.getBoolean("route_whole_profile", true),
                pPkgs = (sp.getStringSet("selected_packages", emptySet()) ?: emptySet()).toSet()
            )
            refreshConfiguredSlots()
        } else {
            scope.launch(Dispatchers.IO) {
                val b = PersistentStorage.loadBackup(slot, ProfileManager.androidUserId)
                withContext(Dispatchers.Main) {
                    if (b != null) {
                        val bPkgs = mutableSetOf<String>()
                        val arr = b.optJSONArray("selected_packages")
                        if (arr != null) {
                            for (i in 0 until arr.length()) bPkgs.add(arr.getString(i))
                        }
                        applySettingsToState(
                            pHost = b.optString("host", ""),
                            pPort = b.optString("port", "1080"),
                            pUser = b.optString("username", ""),
                            pPass = b.optString("password", ""),
                            pSni = b.optString("sni", ""),
                            pSsMethod = b.optString("ss_method", "2022-blake3-aes-128-gcm"),
                            pRpk = b.optString("reality_pk", ""),
                            pRsid = b.optString("reality_sid", ""),
                            pType = runCatching { ProxyType.valueOf(b.optString("proxy_type", "SOCKS5")) }.getOrDefault(ProxyType.SOCKS5),
                            pTransport = runCatching { TransportMode.valueOf(b.optString("transport_mode", "TCP_AND_UDP")) }.getOrDefault(TransportMode.TCP_AND_UDP),
                            pIpMode = runCatching { IpMode.valueOf(b.optString("ip_mode", "IPV4_ONLY")) }.getOrDefault(IpMode.IPV4_ONLY),
                            pStrictWebRtc = b.optBoolean("strict_webrtc_shield", true),
                            pRouteWhole = b.optBoolean("route_whole_profile", true),
                            pPkgs = bPkgs
                        )
                    } else {
                        applySettingsToState(
                            "", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "",
                            ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, true, emptySet()
                        )
                    }
                    refreshConfiguredSlots()
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadSlotData(activeSlot) }

    fun getCurrentSettings() = ProxySettings(
        type = proxyType,
        transportMode = transportMode,
        ipMode = ipMode,
        host = host.trim(),
        port = port.trim().toIntOrNull()?.coerceIn(1, 65535) ?: 1080,
        username = username.trim(),
        password = password.trim(),
        routeHotspot = routeHotspot,
        sni = sni.trim(),
        ssMethod = ssMethod.trim(),
        realityPublicKey = realityPublicKey.trim(),
        realityShortId = realityShortId.trim(),
        strictWebRtcShield = strictWebRtcShield
    )

    var saveJob by remember { mutableStateOf<Job?>(null) }
    fun saveConfigForSlot(slot: Int, immediate: Boolean = false) {
        getSlotPrefs(slot).edit()
            .putString("proxy_type", proxyType.name)
            .putString("transport_mode", transportMode.name)
            .putString("ip_mode", ipMode.name)
            .putBoolean("strict_webrtc_shield", strictWebRtcShield)
            .putString("host", host.trim())
            .putString("port", port.trim())
            .putString("username", username.trim())
            .putString("password", password.trim())
            .putString("sni", sni.trim())
            .putString("ss_method", ssMethod.trim())
            .putString("reality_pk", realityPublicKey.trim())
            .putString("reality_sid", realityShortId.trim())
            .putBoolean("route_whole_profile", routeWholeProfile)
            .putStringSet("selected_packages", selectedPackages.toSet())
            .apply()
        globalPrefs.edit()
            .putBoolean("start_on_boot", startOnBoot)
            .putInt("boot_slot", bootSlot)
            .putBoolean("route_hotspot", routeHotspot)
            .apply()
        refreshConfiguredSlots()

        val snap = getCurrentSettings()
        val uids = buildTargetUids()
        val pkgs = selectedPackages.toSet()
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            if (!immediate) delay(600)
            if (startOnBoot && slot == bootSlot) BootManager.syncBootState(context, true, snap, uids, slot)
            if (snap.host.isNotEmpty()) {
                PersistentStorage.saveBackup(
                    context, slot, snap, startOnBoot && slot == bootSlot,
                    routeWholeProfile, pkgs, ProfileManager.androidUserId
                )
            }
        }
    }

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }
    var ipFetchJob by remember { mutableStateOf<Job?>(null) }

    fun triggerExitNodeCheck() {
        ipFetchJob?.cancel()
        if (!isProxyActive) {
            publicIpInfo = null
            isFetchingIp = false
            return
        }
        isFetchingIp = true
        ipFetchJob = scope.launch(Dispatchers.IO) {
            for (attempt in 1..4) {
                delay(if (attempt == 1) 250L else 900L)
                val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId) ?: activeSlot
                val socksPort = 10800 + (ProfileManager.androidUserId * 100) + (rSlot * 10) + 1
                val res = IpFetcher.getPublicIpInfo(socksPort)
                if (res != null) {
                    withContext(Dispatchers.Main) {
                        publicIpInfo = res
                        isFetchingIp = false
                    }
                    if (res.flagEmoji != "🌐") break
                }
            }
            withContext(Dispatchers.Main) {
                isFetchingIp = false
            }
        }
    }

    LaunchedEffect(isProxyActive, runningSlot, handoverNonce) {
        triggerExitNodeCheck()
    }

    var testLatency by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("Initializing Zuan Kernel Stream...") }
    var liveLogStreaming by remember { mutableStateOf(true) }
    var showKernelAuditMode by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab, runningSlot, activeSlot, liveLogStreaming, showKernelAuditMode) {
        if (selectedTab == 4) {
            while (isActive) {
                val targetSlot = runningSlot ?: activeSlot
                val output = withContext(Dispatchers.IO) {
                    if (showKernelAuditMode) {
                        ProxyController.getKernelAuditReport(ProfileManager.androidUserId, targetSlot)
                    } else {
                        ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                    }
                }
                currentLogs = output.ifEmpty { "No logs recorded." }
                if (!liveLogStreaming) break
                delay(1400)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZuanDeepSpace)
    ) {
        // Zuan Animated Liquid Mesh Background
        ZuanLiquidMeshBackground(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Zuan Top Enterprise Header + 5-Slot Liquid Capsule Strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZuanTopBar(
                    isProxyActive = isProxyActive,
                    runningSlot = runningSlot,
                    activeSlot = activeSlot,
                    rootState = rootState,
                    liveNet = liveNet,
                    onRecheckRoot = onRecheckRoot
                )

                ZuanSlotCapsuleStrip(
                    activeSlot = activeSlot,
                    runningSlot = runningSlot,
                    isProxyActive = isProxyActive,
                    startOnBoot = startOnBoot,
                    bootSlot = bootSlot,
                    configuredSlots = configuredSlots,
                    slotProtocolMap = slotProtocolMap,
                    onSelectSlot = { newSlot ->
                        if (newSlot != activeSlot) {
                            saveConfigForSlot(activeSlot, true)
                            ProfileManager.activeSlot = newSlot
                            activeSlot = newSlot
                            loadSlotData(newSlot)
                            onSyncStatus()
                        }
                    }
                )
            }

            // Main Animated Content Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)))
                    },
                    label = "zuanViewTransition"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> ZuanDashboardTab(
                            isProxyActive = isProxyActive,
                            isTransitioning = isTransitioning,
                            runningSlot = runningSlot,
                            activeSlot = activeSlot,
                            sessionStartMs = sessionStartMs,
                            activePid = activePid,
                            publicIpInfo = publicIpInfo,
                            isFetchingIp = isFetchingIp,
                            liveNet = liveNet,
                            host = host,
                            port = port,
                            proxyType = proxyType,
                            strictWebRtcShield = strictWebRtcShield,
                            routeHotspot = routeHotspot,
                            currentRxRate = currentRxRate,
                            currentTxRate = currentTxRate,
                            totalRxBytes = totalRxBytes,
                            totalTxBytes = totalTxBytes,
                            rxHistory = rxHistory,
                            txHistory = txHistory,
                            isTesting = isTesting,
                            testLatency = testLatency,
                            onRefreshIp = { triggerExitNodeCheck() },
                            onOpenIpFighterTab = { selectedTab = 1 },
                            onOpenNodeTab = { selectedTab = 2 },
                            onToggleWebRtc = {
                                strictWebRtcShield = !strictWebRtcShield
                                saveConfigForSlot(activeSlot, true)
                            },
                            onToggleHotspot = {
                                routeHotspot = !routeHotspot
                                saveConfigForSlot(activeSlot, true)
                            },
                            onPingNode = {
                                if (host.trim().isEmpty()) {
                                    selectedTab = 2
                                    return@ZuanDashboardTab
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testLatency = "Pinging..."
                                scope.launch {
                                    val res = ProxyTester.testProxy(getCurrentSettings())
                                    testLatency = when (res) {
                                        is TestResult.Success -> "${res.latencyMs} ms • Online"
                                        is TestResult.Failure -> "Timeout"
                                    }
                                    isTesting = false
                                }
                            },
                            onToggleProxy = {
                                if (isProxyActive && runningSlot == activeSlot) {
                                    onStopProxy { }
                                } else {
                                    if (host.trim().isEmpty()) {
                                        selectedTab = 2
                                        return@ZuanDashboardTab
                                    }
                                    saveConfigForSlot(activeSlot, true)
                                    val targets = buildTargetUids()
                                    onStartProxy(getCurrentSettings(), targets) { ok, err ->
                                        if (!ok) {
                                            Toast.makeText(
                                                context,
                                                err?.lines()?.lastOrNull() ?: "Start failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )

                        1 -> LiveEphemeralIpFighterTab(
                            isProxyActive = isProxyActive,
                            runningSlot = runningSlot,
                            publicIpInfo = publicIpInfo,
                            liveNet = liveNet
                        )

                        2 -> NodeConfigTab(
                            activeSlot = activeSlot,
                            host = host,
                            port = port,
                            username = username,
                            password = password,
                            sni = sni,
                            ssMethod = ssMethod,
                            realityPublicKey = realityPublicKey,
                            realityShortId = realityShortId,
                            proxyType = proxyType,
                            transportMode = transportMode,
                            ipMode = ipMode,
                            strictWebRtcShield = strictWebRtcShield,
                            onConfigChanged = { saveConfigForSlot(activeSlot) },
                            onHostChange = { host = it },
                            onPortChange = { port = it },
                            onUsernameChange = { username = it },
                            onPasswordChange = { password = it },
                            onSniChange = { sni = it },
                            onSsMethodChange = { ssMethod = it },
                            onRealityPublicKeyChange = { realityPublicKey = it },
                            onRealityShortIdChange = { realityShortId = it },
                            onProxyTypeChange = { proxyType = it },
                            onTransportModeChange = { transportMode = it },
                            onIpModeChange = { ipMode = it },
                            onStrictWebRtcChange = { strictWebRtcShield = it },
                            onImportUri = { imported ->
                                proxyType = imported.type
                                host = imported.host
                                port = imported.port.toString()
                                username = imported.username
                                password = imported.password
                                sni = imported.sni
                                ssMethod = imported.ssMethod
                                realityPublicKey = imported.realityPublicKey
                                realityShortId = imported.realityShortId
                                saveConfigForSlot(activeSlot, true)
                            },
                            onResetSlot = {
                                getSlotPrefs(activeSlot).edit().clear().apply()
                                scope.launch(Dispatchers.IO) {
                                    PersistentStorage.clearBackup(activeSlot, ProfileManager.androidUserId)
                                }
                                loadSlotData(activeSlot)
                                Toast.makeText(context, "Slot P$activeSlot cleared", Toast.LENGTH_SHORT).show()
                            },
                            getCurrentSettings = { getCurrentSettings() }
                        )

                        3 -> RoutingAndBootTab(
                            startOnBoot = startOnBoot,
                            bootSlot = bootSlot,
                            routeHotspot = routeHotspot,
                            routeWholeProfile = routeWholeProfile,
                            selectedPackages = selectedPackages,
                            installedApps = installedApps,
                            onStartOnBootChange = {
                                startOnBoot = it
                                if (it) {
                                    bootSlot = activeSlot
                                    saveConfigForSlot(activeSlot, true)
                                } else {
                                    globalPrefs.edit().putBoolean("start_on_boot", false).apply()
                                    scope.launch(Dispatchers.IO) { BootManager.removeBootScript() }
                                }
                            },
                            onBootSlotChange = {
                                bootSlot = it
                                saveConfigForSlot(activeSlot, true)
                            },
                            onRouteHotspotChange = {
                                routeHotspot = it
                                saveConfigForSlot(activeSlot, true)
                            },
                            onExportBackup = {
                                saveConfigForSlot(activeSlot, true)
                                scope.launch(Dispatchers.IO) {
                                    val ok = PersistentStorage.exportAllToDownloads(context)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            if (ok) "Exported to ${PersistentStorage.PUBLIC_BACKUP_PATH}" else "Export failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onImportBackup = {
                                scope.launch(Dispatchers.IO) {
                                    val ok = PersistentStorage.importAllFromDownloads(context)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            loadSlotData(activeSlot)
                                            Toast.makeText(context, "Imported all 5 slots", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Backup not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onRouteWholeProfileChange = {
                                routeWholeProfile = it
                                saveConfigForSlot(activeSlot, true)
                            },
                            onToggleApp = { pkg ->
                                selectedPackages = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg
                                saveConfigForSlot(activeSlot)
                            },
                            onSelectAll = {
                                selectedPackages = installedApps.map { it.packageName }.toSet()
                                saveConfigForSlot(activeSlot)
                            },
                            onClearAll = {
                                selectedPackages = emptySet()
                                saveConfigForSlot(activeSlot)
                            }
                        )

                        4 -> KernelLogsTab(
                            activeSlot = activeSlot,
                            runningSlot = runningSlot,
                            logs = currentLogs,
                            liveLogStreaming = liveLogStreaming,
                            showKernelAuditMode = showKernelAuditMode,
                            onToggleLiveStream = { liveLogStreaming = !liveLogStreaming },
                            onToggleAuditMode = { showKernelAuditMode = !showKernelAuditMode },
                            onRefresh = {
                                scope.launch(Dispatchers.IO) {
                                    val targetSlot = runningSlot ?: activeSlot
                                    val l = if (showKernelAuditMode) {
                                        ProxyController.getKernelAuditReport(ProfileManager.androidUserId, targetSlot)
                                    } else {
                                        ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                                    }
                                    withContext(Dispatchers.Main) {
                                        currentLogs = l.ifEmpty { "No logs." }
                                    }
                                }
                            },
                            onClear = {
                                currentLogs = "Cleared."
                                scope.launch(Dispatchers.IO) {
                                    ProxyController.clearLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
                                }
                            }
                        )
                    }
                }
            }

            // Zuan Capsule iOS Liquid Glass Navigation Dock
            ZuanCapsuleBottomDock(
                selectedTab = selectedTab,
                isProxyActive = isProxyActive,
                onSelectTab = { selectedTab = it }
            )
        }
    }
}

@Composable
fun ZuanTopBar(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    liveNet: LiveNetworkState,
    onRecheckRoot: () -> Unit
) {
    val rootCol by animateColorAsState(
        targetValue = when (rootState) {
            RootState.GRANTED -> ZuanMint
            RootState.DENIED -> ZuanRose
            RootState.CHECKING -> ZuanAmber
        },
        label = "rootColor"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZuanIconBadge(
                icon = IconType.BOLT,
                accent = if (isProxyActive) ZuanMint else ZuanAqua,
                size = 42.dp,
                iconSize = 22.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Nameless Proxy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZuanTextPrimary
                )
                Text(
                    text = if (isProxyActive) {
                        "Kernel Active • Slot P${runningSlot ?: activeSlot} • User #${ProfileManager.androidUserId}"
                    } else {
                        "Kernel Sing-Box • Standby • User #${ProfileManager.androidUserId}"
                    },
                    fontSize = 11.5.sp,
                    color = if (isProxyActive) ZuanMint else ZuanTextSecondary
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Network Capsule
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(ZuanGlassElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(
                    type = if (liveNet.isWifi) IconType.WIFI else IconType.CELLULAR,
                    size = 14.dp,
                    tint = ZuanAqua
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = liveNet.transportLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZuanTextPrimary
                )
            }

            // Root Status Capsule
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(ZuanGlassElevated)
                    .border(1.dp, rootCol.copy(alpha = 0.55f), CircleShape)
                    .clickable { onRecheckRoot() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(7.dp).background(rootCol, CircleShape))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (rootState == RootState.GRANTED) "ROOT" else "NO ROOT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZuanTextPrimary
                )
            }
        }
    }
}

@Composable
fun ZuanSlotCapsuleStrip(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    configuredSlots: Set<Int>,
    slotProtocolMap: Map<Int, ProxyType>,
    onSelectSlot: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0 until ProfileManager.MAX_SLOTS).forEach { idx ->
            val sel = idx == activeSlot
            val run = isProxyActive && runningSlot == idx
            val isBoot = startOnBoot && bootSlot == idx
            val isConf = configuredSlots.contains(idx)
            val proto = slotProtocolMap[idx]?.shortBadge ?: "SOCKS5"

            val scale by animateFloatAsState(
                targetValue = if (sel || run) 1.03f else 0.98f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "slotScale"
            )
            val accent = when {
                run -> ZuanMint
                sel -> ZuanAqua
                isBoot -> ZuanAmber
                else -> Color.White
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (run) ZuanMint.copy(alpha = 0.16f)
                        else if (sel) ZuanAqua.copy(alpha = 0.14f)
                        else ZuanGlassBase
                    )
                    .border(
                        width = if (sel || run) 1.5.dp else 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = if (sel || run) 0.75f else 0.25f),
                                accent.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelectSlot(idx) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    run -> ZuanMint
                                    isBoot -> ZuanAmber
                                    isConf -> ZuanAqua
                                    else -> ZuanTextMuted.copy(alpha = 0.5f)
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Slot P$idx",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (run) ZuanMint else if (sel) ZuanAqua else ZuanTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        run -> "Active • $proto"
                        isBoot -> "Boot • $proto"
                        isConf -> proto
                        else -> "Empty"
                    },
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (run) ZuanMint else if (isBoot) ZuanAmber else ZuanTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: ZUAN LIQUID GLASS BENTO DASHBOARD
// -------------------------------------------------------------

@Composable
fun ZuanDashboardTab(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    sessionStartMs: Long,
    activePid: String?,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    liveNet: LiveNetworkState,
    host: String,
    port: String,
    proxyType: ProxyType,
    strictWebRtcShield: Boolean,
    routeHotspot: Boolean,
    currentRxRate: Long,
    currentTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxHistory: List<Long>,
    txHistory: List<Long>,
    isTesting: Boolean,
    testLatency: String?,
    onRefreshIp: () -> Unit,
    onOpenIpFighterTab: () -> Unit,
    onOpenNodeTab: () -> Unit,
    onToggleWebRtc: () -> Unit,
    onToggleHotspot: () -> Unit,
    onPingNode: () -> Unit,
    onToggleProxy: () -> Unit
) {
    val context = LocalContext.current
    val isCurrentSlotRunning = isProxyActive && runningSlot == activeSlot

    val infinite = rememberInfiniteTransition(label = "zuanOrb")
    val rot1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing)),
        label = "r1"
    )
    val rot2 by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "r2"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val orbAccent by animateColorAsState(
        targetValue = when {
            isTransitioning -> ZuanAmber
            isProxyActive -> ZuanMint
            else -> ZuanAqua
        },
        animationSpec = tween(500),
        label = "orbAcc"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. HERO ZUAN LIQUID GLASS TUNNEL CONTROLLER CARD
        ZuanLiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = if (isProxyActive) ZuanMint else ZuanAqua
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(orbAccent, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isTransitioning -> "Synchronizing Kernel..."
                            isProxyActive -> "Tunnel Active • Slot P${runningSlot ?: activeSlot}"
                            else -> "Tunnel Standby • Slot P$activeSlot"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProxyActive) ZuanMint else ZuanTextSecondary
                    )
                }

                if (isProxyActive && sessionStartMs > 0L) {
                    UptimeBadge(sessionStartMs)
                } else if (activePid != null) {
                    Text(
                        text = "PID $activePid",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ZuanTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated Liquid Glass Plasma Power Orb
                Box(
                    modifier = Modifier
                        .size(106.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !isTransitioning
                        ) { onToggleProxy() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val c = Offset(w / 2f, h / 2f)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(orbAccent.copy(alpha = 0.28f), Color.Transparent),
                                center = c,
                                radius = w * 0.48f * pulse
                            ),
                            radius = w * 0.48f * pulse,
                            center = c
                        )

                        rotate(rot1, c) {
                            drawCircle(
                                color = orbAccent.copy(alpha = 0.65f),
                                radius = w * 0.43f,
                                style = Stroke(
                                    width = 2.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                                )
                            )
                        }

                        rotate(rot2, c) {
                            drawArc(
                                color = if (isProxyActive) ZuanAqua else ZuanViolet,
                                startAngle = 15f,
                                sweepAngle = 125f,
                                useCenter = false,
                                topLeft = Offset(w * 0.13f, h * 0.13f),
                                size = Size(w * 0.74f, h * 0.74f),
                                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = if (isProxyActive) ZuanAqua else ZuanViolet,
                                startAngle = 195f,
                                sweepAngle = 125f,
                                useCenter = false,
                                topLeft = Offset(w * 0.13f, h * 0.13f),
                                size = Size(w * 0.74f, h * 0.74f),
                                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        drawCircle(
                            color = ZuanGlassElevated,
                            radius = w * 0.30f * pulse,
                            center = c
                        )
                        drawCircle(
                            color = orbAccent,
                            radius = w * 0.30f * pulse,
                            center = c,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                    }

                    VectorIcon(
                        type = if (isProxyActive) IconType.BOLT else IconType.POWER,
                        size = 30.dp,
                        tint = orbAccent,
                        strokeWidth = 2.4f,
                        modifier = Modifier.scale(pulse)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right Column: Exit Node IP, Location & Quick Actions
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isProxyActive) "EXIT NODE IP" else "CONFIGURED ENDPOINT",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ZuanAqua
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when {
                            isProxyActive && publicIpInfo != null -> "${publicIpInfo.flagEmoji} ${publicIpInfo.ip}"
                            isProxyActive && isFetchingIp -> "Verifying Tunnel IP..."
                            host.isNotBlank() -> "${host.trim()}:${port.trim()}"
                            else -> "Tap to Configure P$activeSlot"
                        },
                        fontSize = 17.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZuanTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            val ipToCopy = publicIpInfo?.ip ?: host.trim()
                            if (ipToCopy.isNotEmpty()) {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("ExitIP", ipToCopy))
                                Toast.makeText(context, "Copied: $ipToCopy", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when {
                            isProxyActive && publicIpInfo != null -> buildString {
                                append(publicIpInfo.country)
                                if (publicIpInfo.timezone.isNotEmpty()) append(" • ${publicIpInfo.timezone}")
                            }
                            else -> "${proxyType.displayTitle} • ${liveNet.transportLabel} (${liveNet.interfaceName})"
                        },
                        fontSize = 11.5.sp,
                        color = ZuanTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ZuanGlassElevated)
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable { onRefreshIp() }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VectorIcon(IconType.REFRESH, size = 13.dp, tint = ZuanAqua)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isFetchingIp) "Checking..." else "Verify IP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZuanTextPrimary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ZuanAqua.copy(alpha = 0.16f))
                                .border(1.dp, ZuanAqua.copy(alpha = 0.55f), CircleShape)
                                .clickable { onOpenIpFighterTab() }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VectorIcon(IconType.GLOBE, size = 13.dp, tint = ZuanAqua)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "IPFighter",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZuanAqua
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Start/Stop + Ping Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPingNode,
                    enabled = !isTesting && !isTransitioning,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ZuanGlassElevated,
                        contentColor = ZuanTextPrimary
                    ),
                    modifier = Modifier
                        .weight(0.38f)
                        .height(48.dp)
                ) {
                    VectorIcon(IconType.BOLT, size = 15.dp, tint = ZuanAqua)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = testLatency ?: "Ping P$activeSlot",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onToggleProxy,
                    enabled = !isTransitioning,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentSlotRunning) ZuanRose else ZuanEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(0.62f)
                        .height(48.dp)
                ) {
                    VectorIcon(
                        type = if (isCurrentSlotRunning) IconType.POWER else IconType.BOLT,
                        size = 16.dp,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isTransitioning -> "Syncing..."
                            !isProxyActive -> "Start Proxy (P$activeSlot)"
                            isCurrentSlotRunning -> "Stop Proxy (P$activeSlot)"
                            else -> "Switch to Slot P$activeSlot"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 2. ZUAN BENTO ROW 1: LIVE DOWNLOAD & UPLOAD WAVEFORM TELEMETRY CARDS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZuanBentoSpeedCard(
                modifier = Modifier.weight(1f),
                title = "Download",
                icon = IconType.DOWN_ARROW,
                rate = currentRxRate,
                total = totalRxBytes,
                history = rxHistory,
                accent = ZuanMint
            )
            ZuanBentoSpeedCard(
                modifier = Modifier.weight(1f),
                title = "Upload",
                icon = IconType.UP_ARROW,
                rate = currentTxRate,
                total = totalTxBytes,
                history = txHistory,
                accent = ZuanAqua
            )
        }

        // 3. ZUAN BENTO ROW 2: WEBRTC/DNS SHIELD & HOTSPOT BRIDGE CARDS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZuanLiquidGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleWebRtc() },
                glowColor = if (strictWebRtcShield) ZuanMint else null,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZuanIconBadge(
                        icon = IconType.SHIELD,
                        accent = if (strictWebRtcShield) ZuanMint else ZuanTextSecondary,
                        size = 34.dp,
                        iconSize = 17.dp
                    )
                    Text(
                        text = if (strictWebRtcShield) "LOCKED" else "OFF",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (strictWebRtcShield) ZuanMint else ZuanTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "WebRTC & DNS Shield",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZuanTextPrimary
                )
                Text(
                    text = "Zero-Leak FIB & NAT Lock",
                    fontSize = 11.sp,
                    color = ZuanTextSecondary
                )
            }

            ZuanLiquidGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleHotspot() },
                glowColor = if (routeHotspot) ZuanAqua else null,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZuanIconBadge(
                        icon = IconType.HOTSPOT,
                        accent = if (routeHotspot) ZuanAqua else ZuanTextSecondary,
                        size = 34.dp,
                        iconSize = 17.dp
                    )
                    Text(
                        text = if (routeHotspot) "ACTIVE" else "OFF",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (routeHotspot) ZuanAqua else ZuanTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Hotspot Bridge",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZuanTextPrimary
                )
                Text(
                    text = "Wi-Fi & USB Tethering",
                    fontSize = 11.sp,
                    color = ZuanTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ZuanBentoSpeedCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: IconType,
    rate: Long,
    total: Long,
    history: List<Long>,
    accent: Color
) {
    ZuanLiquidGlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuanIconBadge(icon = icon, accent = accent, size = 30.dp, iconSize = 15.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZuanTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = splitSpeedValue(rate),
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = ZuanTextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = splitSpeedUnit(rate),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Text(
            text = "Session: ${formatBytes(total)}",
            fontSize = 10.5.sp,
            color = ZuanTextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val w = size.width
            val h = size.height
            val maxVal = (history.maxOrNull() ?: 1024L).coerceAtLeast(1024L)
            val pts = history.mapIndexed { idx, v ->
                val x = idx * (w / (history.size - 1).coerceAtLeast(1))
                val norm = (v.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val y = h - (norm * (h * 0.85f)) - (h * 0.08f)
                Offset(x, y)
            }

            if (pts.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = accent,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: ZUAN LIQUID GLASS IPFIGHTER & LEAK INSPECTOR
// -------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveEphemeralIpFighterTab(
    isProxyActive: Boolean,
    runningSlot: Int?,
    publicIpInfo: GeoIpResult?,
    liveNet: LiveNetworkState
) {
    var reloadNonce by remember { mutableIntStateOf(0) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var targetSiteIndex by remember { mutableIntStateOf(0) }

    val targets = listOf(
        Triple(IconType.GLOBE, "IPFighter", "https://ipfighter.com/"),
        Triple(IconType.EYE_OFF, "WebRTC Test", "https://browserleaks.com/webrtc"),
        Triple(IconType.SHIELD, "DNS Test", "https://browserleaks.com/dns")
    )

    val targetTz = remember(isProxyActive, publicIpInfo?.timezone, publicIpInfo?.country) {
        if (!isProxyActive) ""
        else if (!publicIpInfo?.timezone.isNullOrBlank()) {
            publicIpInfo!!.timezone
        } else {
            val c = publicIpInfo?.country?.lowercase() ?: ""
            when {
                c.contains("united kingdom") || c.contains("london") -> "Europe/London"
                c.contains("germany") || c.contains("frankfurt") -> "Europe/Berlin"
                c.contains("singapore") -> "Asia/Singapore"
                c.contains("japan") || c.contains("tokyo") -> "Asia/Tokyo"
                else -> "America/New_York"
            }
        }
    }

    val tzSyncScript = remember(targetTz, isProxyActive) {
        """
        (function() {
            try {
                if (${isProxyActive} && '$targetTz' !== '') {
                    const origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
                    Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                        const res = origResolved.call(this);
                        res.timeZone = '$targetTz';
                        return res;
                    };
                }
            } catch (e) {}
        })();
        """.trimIndent()
    }

    LaunchedEffect(isProxyActive, runningSlot) {
        reloadNonce++
    }

    DisposableEffect(Unit) {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        onDispose {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    ZuanLiquidGlassCard(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isProxyActive) ZuanMint else ZuanAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyActive) {
                            "Live IPFighter • ${publicIpInfo?.ip ?: "Tunnel Active"}"
                        } else {
                            "Live IPFighter • Direct (${liveNet.transportLabel})"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZuanTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.forEachIndexed { idx, (icon, label, _) ->
                        val sel = targetSiteIndex == idx
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (sel) ZuanAqua.copy(alpha = 0.18f) else ZuanGlassElevated)
                                .border(1.dp, if (sel) ZuanAqua else Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    targetSiteIndex = idx
                                    reloadNonce++
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VectorIcon(icon, size = 13.dp, tint = if (sel) ZuanAqua else ZuanTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) ZuanTextPrimary else ZuanTextSecondary
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    reloadNonce++
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZuanEmerald,
                    contentColor = Color.White
                )
            ) {
                VectorIcon(IconType.REFRESH, size = 14.dp, tint = Color.White)
                Spacer(modifier = Modifier.width(5.dp))
                Text("Fresh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 4.dp),
                color = ZuanAqua,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
        ) {
            key(reloadNonce, targetSiteIndex) {
                val baseUrl = targets[targetSiteIndex].third
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            clearCache(true)
                            clearHistory()
                            clearFormData()
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = false
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                setSupportZoom(true)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    view?.evaluateJavascript(tzSyncScript, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    loadProgress = 100
                                    view?.evaluateJavascript(tzSyncScript, null)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }
                            val noCacheHeaders = mapOf(
                                "Cache-Control" to "no-cache, no-store, must-revalidate",
                                "Pragma" to "no-cache",
                                "Expires" to "0"
                            )
                            loadUrl("$baseUrl?_t=${System.currentTimeMillis()}", noCacheHeaders)
                        }
                    },
                    onRelease = { wv ->
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.clearHistory()
                        wv.clearCache(true)
                        wv.removeAllViews()
                        wv.destroy()
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: ZUAN LIQUID GLASS NODE STUDIO
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeConfigTab(
    activeSlot: Int,
    host: String,
    port: String,
    username: String,
    password: String,
    sni: String,
    ssMethod: String,
    realityPublicKey: String,
    realityShortId: String,
    proxyType: ProxyType,
    transportMode: TransportMode,
    ipMode: IpMode,
    strictWebRtcShield: Boolean,
    onConfigChanged: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSniChange: (String) -> Unit,
    onSsMethodChange: (String) -> Unit,
    onRealityPublicKeyChange: (String) -> Unit,
    onRealityShortIdChange: (String) -> Unit,
    onProxyTypeChange: (ProxyType) -> Unit,
    onTransportModeChange: (TransportMode) -> Unit,
    onIpModeChange: (IpMode) -> Unit,
    onStrictWebRtcChange: (Boolean) -> Unit,
    onImportUri: (ProxySettings) -> Unit,
    onResetSlot: () -> Unit,
    getCurrentSettings: () -> ProxySettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCheckingAlive by remember { mutableStateOf(false) }
    var aliveResult by remember { mutableStateOf<TestResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZuanIconBadge(icon = IconType.PASTE, accent = ZuanAqua, size = 34.dp, iconSize = 17.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("URI Import & Socket Probe", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                        Text("Import clipboard URI or test node latency", fontSize = 11.sp, color = ZuanTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val rawClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                        val parsed = parseProxyUri(rawClip)
                        if (parsed != null) {
                            onImportUri(parsed)
                            Toast.makeText(context, "Imported ${parsed.type.displayTitle}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No valid proxy URI in clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    VectorIcon(IconType.PASTE, size = 15.dp, tint = ZuanAqua)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste URI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                }

                Button(
                    onClick = {
                        if (host.trim().isEmpty()) {
                            aliveResult = TestResult.Failure("Host is empty")
                            return@Button
                        }
                        isCheckingAlive = true
                        aliveResult = null
                        scope.launch {
                            aliveResult = ProxyTester.testProxy(getCurrentSettings())
                            isCheckingAlive = false
                        }
                    },
                    enabled = !isCheckingAlive,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZuanIndigo,
                        contentColor = Color.White
                    )
                ) {
                    VectorIcon(IconType.BOLT, size = 15.dp, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCheckingAlive) "Testing..." else "Test Socket",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (aliveResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                when (val res = aliveResult) {
                    is TestResult.Success -> Text(
                        text = "ONLINE • ${res.latencyMs} ms",
                        color = ZuanMint,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    is TestResult.Failure -> Text(
                        text = "FAILED • ${res.error}",
                        color = ZuanRose,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    null -> {}
                }
            }
        }

        ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZuanIconBadge(icon = IconType.SLIDERS, accent = ZuanMint, size = 34.dp, iconSize = 17.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Slot P$activeSlot Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZuanTextPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ZuanRose.copy(alpha = 0.15f))
                        .border(1.dp, ZuanRose.copy(alpha = 0.45f), CircleShape)
                        .clickable { onResetSlot() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VectorIcon(IconType.TRASH, size = 13.dp, tint = ZuanRose)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset P$activeSlot", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZuanRose)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Protocol", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZuanTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProxyType.values().forEach { t ->
                    FilterChip(
                        selected = proxyType == t,
                        onClick = { onProxyTypeChange(t); onConfigChanged() },
                        label = { Text(t.displayTitle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ZuanIndigo,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Transport & IP Mode", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZuanTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = transportMode == TransportMode.TCP_AND_UDP,
                    onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                    label = { Text("TCP + UDP", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZuanEmerald,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = transportMode == TransportMode.TCP_ONLY,
                    onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                    label = { Text("TCP Only", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZuanEmerald,
                        selectedLabelColor = Color.White
                    )
                )
                listOf(
                    Pair(IpMode.IPV4_ONLY, "IPv4 Only"),
                    Pair(IpMode.DUAL_STACK, "Dual-Stack"),
                    Pair(IpMode.IPV6_ONLY, "IPv6 Only")
                ).forEach { (mode, lbl) ->
                    FilterChip(
                        selected = ipMode == mode,
                        onClick = { onIpModeChange(mode); onConfigChanged() },
                        label = { Text(lbl, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ZuanIndigo,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { onHostChange(it); onConfigChanged() },
                    leadingIcon = { VectorIcon(IconType.SERVER, size = 16.dp, tint = ZuanAqua) },
                    label = { Text("Server Host / IP") },
                    placeholder = { Text("48.45.153.215") },
                    modifier = Modifier.weight(0.68f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { onPortChange(it); onConfigChanged() },
                    leadingIcon = { VectorIcon(IconType.PORT, size = 15.dp, tint = ZuanAqua) },
                    label = { Text("Port") },
                    placeholder = { Text("1080") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.32f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (proxyType) {
                ProxyType.SOCKS5, ProxyType.HTTP -> {
                    var showPass by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { onUsernameChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.USER, size = 16.dp, tint = ZuanTextSecondary) },
                            label = { Text("Username") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { onPasswordChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.KEY, size = 16.dp, tint = ZuanTextSecondary) },
                            label = { Text("Password") },
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Box(modifier = Modifier.clickable { showPass = !showPass }.padding(8.dp)) {
                                    VectorIcon(if (showPass) IconType.EYE_OFF else IconType.EYE, size = 16.dp, tint = ZuanAqua)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                    }
                }

                ProxyType.SHADOWSOCKS -> {
                    var showPass by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = ssMethod,
                        onValueChange = { onSsMethodChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.SHIELD, size = 16.dp, tint = ZuanAqua) },
                        label = { Text("Cipher Method") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.KEY, size = 16.dp, tint = ZuanTextSecondary) },
                        label = { Text("Password / PSK") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(modifier = Modifier.clickable { showPass = !showPass }.padding(8.dp)) {
                                VectorIcon(if (showPass) IconType.EYE_OFF else IconType.EYE, size = 16.dp, tint = ZuanAqua)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                ProxyType.VLESS -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.KEY, size = 16.dp, tint = ZuanAqua) },
                        label = { Text("VLESS UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.GLOBE, size = 16.dp, tint = ZuanTextSecondary) },
                        label = { Text("TLS SNI") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = realityPublicKey,
                            onValueChange = { onRealityPublicKeyChange(it); onConfigChanged() },
                            label = { Text("REALITY Public Key") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = realityShortId,
                            onValueChange = { onRealityShortIdChange(it); onConfigChanged() },
                            label = { Text("Short ID") },
                            modifier = Modifier.weight(0.55f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                    }
                }

                ProxyType.TROJAN, ProxyType.HYSTERIA2 -> {
                    var showPass by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.KEY, size = 16.dp, tint = ZuanAqua) },
                        label = { Text("${proxyType.displayTitle} Password") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(modifier = Modifier.clickable { showPass = !showPass }.padding(8.dp)) {
                                VectorIcon(if (showPass) IconType.EYE_OFF else IconType.EYE, size = 16.dp, tint = ZuanAqua)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.GLOBE, size = 16.dp, tint = ZuanTextSecondary) },
                        label = { Text("TLS SNI") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                ProxyType.SOCKS4 -> {}
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: ZUAN LIQUID GLASS KERNEL ROUTING, BOOT & SPLIT-TUNNEL
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingAndBootTab(
    startOnBoot: Boolean,
    bootSlot: Int,
    routeHotspot: Boolean,
    routeWholeProfile: Boolean,
    selectedPackages: Set<String>,
    installedApps: List<AppItem>,
    onStartOnBootChange: (Boolean) -> Unit,
    onBootSlotChange: (Int) -> Unit,
    onRouteHotspotChange: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onRouteWholeProfileChange: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    val uid = ProfileManager.androidUserId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZuanIconBadge(icon = IconType.ROCKET, accent = ZuanAmber, size = 36.dp, iconSize = 18.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Auto-Start on System Boot", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                        Text(
                            text = if (startOnBoot) "Armed for Slot P$bootSlot (/data/adb/service.d)" else "Manual launch only",
                            fontSize = 11.sp,
                            color = ZuanTextSecondary
                        )
                    }
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ZuanEmerald
                    )
                )
            }

            AnimatedVisibility(visible = startOnBoot) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0 until ProfileManager.MAX_SLOTS).forEach { s ->
                        FilterChip(
                            selected = bootSlot == s,
                            onClick = { onBootSlotChange(s) },
                            label = { Text("Slot P$s", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZuanAmber,
                                selectedLabelColor = ZuanDeepSpace
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZuanIconBadge(icon = IconType.HOTSPOT, accent = ZuanAqua, size = 36.dp, iconSize = 18.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Hotspot & Tethering Bridge", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                        Text("Share proxy tunnel with Wi-Fi / USB clients", fontSize = 11.sp, color = ZuanTextSecondary)
                    }
                }
                Switch(
                    checked = routeHotspot,
                    onCheckedChange = onRouteHotspotChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ZuanEmerald
                    )
                )
            }
        }

        ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuanIconBadge(icon = IconType.EXPORT, accent = ZuanIndigo, size = 34.dp, iconSize = 17.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("5-Slot Config Backup", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                    Text("/sdcard/Download/nameless_backup.json", fontSize = 11.sp, color = ZuanTextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    VectorIcon(IconType.EXPORT, size = 15.dp, tint = ZuanAqua)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                }
                Button(
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZuanIndigo,
                        contentColor = Color.White
                    )
                ) {
                    VectorIcon(IconType.IMPORT, size = 15.dp, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Backup", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZuanIconBadge(icon = IconType.APPS, accent = ZuanMint, size = 36.dp, iconSize = 18.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Tunnel All Apps (User #$uid)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                        Text(
                            text = if (routeWholeProfile) "Global Zero-Leak Profile Lock" else "${selectedPackages.size} apps selected",
                            fontSize = 11.sp,
                            color = ZuanTextSecondary
                        )
                    }
                }
                Switch(
                    checked = routeWholeProfile,
                    onCheckedChange = onRouteWholeProfileChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ZuanEmerald
                    )
                )
            }

            if (!routeWholeProfile) {
                Spacer(modifier = Modifier.height(10.dp))
                var query by remember { mutableStateOf("") }
                val filtered = remember(query, installedApps, selectedPackages) {
                    val base = if (query.isEmpty()) installedApps else installedApps.filter {
                        it.name.contains(query, true) || it.packageName.contains(query, true)
                    }
                    base.sortedWith(
                        compareByDescending<AppItem> { selectedPackages.contains(it.packageName) }
                            .thenBy { it.name.lowercase() }
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { VectorIcon(IconType.SEARCH, size = 16.dp, tint = ZuanAqua) },
                    placeholder = { Text("Search installed apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedPackages.size} selected", fontSize = 11.5.sp, color = ZuanTextSecondary)
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZuanAqua)
                        }
                        TextButton(onClick = onClearAll) {
                            Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZuanRose)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleApp(app.packageName) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZuanTextPrimary, maxLines = 1)
                                Text("${app.packageName} • UID ${app.uid}", fontSize = 10.5.sp, color = ZuanTextSecondary, maxLines = 1)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ZuanEmerald,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: ZUAN LIQUID GLASS KERNEL LOGS & NETFILTER AUDIT
// -------------------------------------------------------------

@Composable
fun KernelLogsTab(
    activeSlot: Int,
    runningSlot: Int?,
    logs: String,
    liveLogStreaming: Boolean,
    showKernelAuditMode: Boolean,
    onToggleLiveStream: () -> Unit,
    onToggleAuditMode: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    ZuanLiquidGlassCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuanIconBadge(
                    icon = if (showKernelAuditMode) IconType.SHIELD else IconType.TERMINAL,
                    accent = ZuanAqua,
                    size = 34.dp,
                    iconSize = 17.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (showKernelAuditMode) "Netfilter Rule Audit" else "Kernel Logs (Slot P${runningSlot ?: activeSlot})",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZuanTextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (liveLogStreaming) "● Live Stream" else "○ Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (liveLogStreaming) ZuanMint else ZuanTextSecondary,
                            modifier = Modifier.clickable { onToggleLiveStream() }
                        )
                        Text("•", fontSize = 11.sp, color = ZuanTextMuted)
                        Text(
                            text = if (showKernelAuditMode) "Switch to Sing-Box Logs" else "Audit Iptables Rules",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZuanAqua,
                            modifier = Modifier.clickable { onToggleAuditMode() }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", logs))
                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = ZuanTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onClear) { Text("Clear", color = ZuanRose, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onRefresh) { Text("Refresh", color = ZuanAqua, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(ZuanDeepSpace.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ZuanTextPrimary,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

// -------------------------------------------------------------
// ZUAN CAPSULE LIQUID GLASS NAVIGATION DOCK
// -------------------------------------------------------------

@Composable
fun ZuanCapsuleBottomDock(
    selectedTab: Int,
    isProxyActive: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val navItems = listOf(
        Pair(IconType.BOLT, "Dashboard"),
        Pair(IconType.GLOBE, "IPFighter"),
        Pair(IconType.SLIDERS, "Nodes"),
        Pair(IconType.HUB, "Routing"),
        Pair(IconType.TERMINAL, "Logs")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(ZuanGlassElevated)
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { idx, (icon, label) ->
                val sel = selectedTab == idx
                val activeAccent = if (idx == 0 && isProxyActive) ZuanMint else ZuanAqua

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (sel) activeAccent.copy(alpha = 0.18f) else Color.Transparent
                        )
                        .then(
                            if (sel) Modifier.border(1.dp, activeAccent.copy(alpha = 0.55f), CircleShape)
                            else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTab(idx) }
                        .padding(horizontal = if (sel) 14.dp else 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    VectorIcon(
                        type = icon,
                        size = 18.dp,
                        tint = if (sel) activeAccent else ZuanTextSecondary
                    )
                    AnimatedVisibility(visible = sel) {
                        Row {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZuanTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UptimeBadge(sessionStartMs: Long) {
    var sec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sessionStartMs) {
        while (isActive) {
            sec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            delay(1000)
        }
    }
    val hrs = sec / 3600
    val mins = (sec % 3600) / 60
    val s = sec % 60
    Text(
        text = if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, s) else String.format("%02d:%02d", mins, s),
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        color = ZuanMint
    )
}

private fun parseProxyUri(rawUri: String): ProxySettings? {
    if (rawUri.isBlank() || !rawUri.contains("://")) return null
    return try {
        val scheme = rawUri.substringBefore("://").lowercase()
        val uri = Uri.parse(rawUri)
        when (scheme) {
            "vless" -> {
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 443
                val uuid = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
                val pbk = uri.getQueryParameter("pbk") ?: ""
                val sid = uri.getQueryParameter("sid") ?: ""
                ProxySettings(
                    type = ProxyType.VLESS,
                    host = host,
                    port = port,
                    password = uuid,
                    sni = sni,
                    realityPublicKey = pbk,
                    realityShortId = sid
                )
            }
            "trojan" -> {
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 443
                val pass = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: ""
                ProxySettings(
                    type = ProxyType.TROJAN,
                    host = host,
                    port = port,
                    password = pass,
                    sni = sni
                )
            }
            "hy2", "hysteria2" -> {
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 443
                val pass = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: ""
                ProxySettings(
                    type = ProxyType.HYSTERIA2,
                    host = host,
                    port = port,
                    password = pass,
                    sni = sni
                )
            }
            "socks5", "socks" -> {
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 1080
                val userInfo = uri.userInfo ?: ""
                val user = userInfo.substringBefore(":", "")
                val pass = userInfo.substringAfter(":", "")
                ProxySettings(
                    type = ProxyType.SOCKS5,
                    host = host,
                    port = port,
                    username = user,
                    password = pass
                )
            }
            "http", "https" -> {
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 8080
                val userInfo = uri.userInfo ?: ""
                val user = userInfo.substringBefore(":", "")
                val pass = userInfo.substringAfter(":", "")
                ProxySettings(
                    type = ProxyType.HTTP,
                    host = host,
                    port = port,
                    username = user,
                    password = pass
                )
            }
            "ss" -> {
                val body = rawUri.substringAfter("ss://").substringBefore("#")
                if (body.contains("@")) {
                    val userInfoPart = body.substringBefore("@")
                    val hostPortPart = body.substringAfter("@").substringBefore("?")
                    val decodedUser = try {
                        String(Base64.decode(userInfoPart, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (_: Exception) {
                        Uri.decode(userInfoPart)
                    }
                    val method = decodedUser.substringBefore(":")
                    val pass = decodedUser.substringAfter(":")
                    val host = hostPortPart.substringBeforeLast(":")
                    val port = hostPortPart.substringAfterLast(":").toIntOrNull() ?: 8388
                    ProxySettings(
                        type = ProxyType.SHADOWSOCKS,
                        host = host,
                        port = port,
                        ssMethod = method,
                        password = pass
                    )
                } else {
                    val decoded = try {
                        String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (_: Exception) { "" }
                    if (decoded.contains("@") && decoded.contains(":")) {
                        val method = decoded.substringBefore(":")
                        val rest = decoded.substringAfter(":")
                        val pass = rest.substringBefore("@")
                        val hostPort = rest.substringAfter("@")
                        val host = hostPort.substringBeforeLast(":")
                        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
                        ProxySettings(
                            type = ProxyType.SHADOWSOCKS,
                            host = host,
                            port = port,
                            ssMethod = method,
                            password = pass
                        )
                    } else null
                }
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun splitSpeedValue(b: Long) = when {
    b >= 1024 * 1024 -> String.format("%.1f", b / (1024.0 * 1024.0))
    b >= 1024 -> String.format("%.1f", b / 1024.0)
    else -> "$b"
}

private fun splitSpeedUnit(b: Long) = when {
    b >= 1024 * 1024 -> "MB/s"
    b >= 1024 -> "KB/s"
    else -> "B/s"
}

private fun formatBytes(b: Long) = when {
    b >= 1024L * 1024L * 1024L -> String.format("%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    b >= 1024L * 1024L -> String.format("%.1f MB", b / (1024.0 * 1024.0))
    b >= 1024L -> String.format("%.1f KB", b / 1024.0)
    else -> "$b B"
}
