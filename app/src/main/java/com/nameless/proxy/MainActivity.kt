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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
    val transportLabel: String = "NET",
    val interfaceName: String = "auto",
    val isCellular: Boolean = false,
    val isWifi: Boolean = false,
    val isConnected: Boolean = true
)

private val ProxyType.shortBadge: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "S5"
        ProxyType.SOCKS4 -> "S4"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "SS"
        ProxyType.VLESS -> "VLESS"
        ProxyType.TROJAN -> "TRJ"
        ProxyType.HYSTERIA2 -> "HY2"
    }

// Neo-Glass Void & Neon Palette
private val VoidBg = Color(0xFF05070D)
private val GlassCardBg = Color(0xCC0D121F)
private val GlassPodBg = Color(0xE6131B2E)
private val GlassBorder = Color(0xFF1E2B45)
private val NeonEmerald = Color(0xFF00F5A0)
private val NeonCyan = Color(0xFF00D9F5)
private val NeonViolet = Color(0xFF8B5CF6)
private val NeonIndigo = Color(0xFF6366F1)
private val NeonAmber = Color(0xFFFFB800)
private val NeonRose = Color(0xFFFF3366)
private val TextCrisp = Color(0xFFF8FAFC)
private val TextSoft = Color(0xFF94A3B8)
private val TextDim = Color(0xFF475569)

// -------------------------------------------------------------
// CUSTOM VECTOR ICON ENGINE (Zero Text-Clutter, 100% Canvas Drawn)
// -------------------------------------------------------------

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
    tint: Color = TextCrisp,
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
                drawCircle(VoidBg, radius = w * 0.11f, center = Offset(w * 0.36f, h * 0.28f))
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.36f, h * 0.28f), style = stroke)
                drawCircle(VoidBg, radius = w * 0.11f, center = Offset(w * 0.66f, h * 0.50f))
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.66f, h * 0.50f), style = stroke)
                drawCircle(VoidBg, radius = w * 0.11f, center = Offset(w * 0.42f, h * 0.72f))
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

@Composable
fun GlassIconButton(
    icon: IconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 19.dp,
    tint: Color = TextCrisp,
    bgColor: Color = GlassPodBg,
    borderColor: Color = GlassBorder,
    spinning: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "iconSpin")
    val spinDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin"
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        VectorIcon(
            type = icon,
            size = iconSize,
            tint = tint,
            modifier = if (spinning) Modifier.graphicsLayer(rotationZ = spinDeg) else Modifier
        )
    }
}

@Composable
fun DynamicAmbientBackdrop(
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "ambient")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "drift"
    )
    val primaryGlow by animateColorAsState(
        targetValue = when {
            isTransitioning -> NeonAmber
            isProxyActive -> NeonEmerald
            else -> NeonIndigo
        },
        animationSpec = tween(700),
        label = "glow1"
    )
    val secondaryGlow by animateColorAsState(
        targetValue = when {
            isTransitioning -> NeonRose
            isProxyActive -> NeonCyan
            else -> NeonViolet
        },
        animationSpec = tween(700),
        label = "glow2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val c1 = Offset(
            x = w * (0.5f + 0.22f * cos(drift)),
            y = h * (0.28f + 0.08f * sin(drift))
        )
        val c2 = Offset(
            x = w * (0.5f - 0.25f * sin(drift * 0.8f)),
            y = h * (0.72f + 0.08f * cos(drift * 0.8f))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow.copy(alpha = 0.14f), Color.Transparent),
                center = c1,
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = c1
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryGlow.copy(alpha = 0.11f), Color.Transparent),
                center = c2,
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = c2
        )
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
                primary = NeonCyan,
                onPrimary = VoidBg,
                secondary = NeonIndigo,
                background = VoidBg,
                surface = GlassCardBg,
                onBackground = TextCrisp,
                onSurface = TextCrisp,
                error = NeonRose
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
                        isTransitioningState = true
                        lifecycleScope.launch(Dispatchers.IO) {
                            val ok = ProxyController.stopProxy(this@MainActivity)
                            withContext(Dispatchers.Main) {
                                isProxyRunningState = false
                                runningSlotState = null
                                activePidState = null
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
                liveNetState = LiveNetworkState("OFF", iface, false, false, false)
                return
            }

            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            liveNetState = LiveNetworkState(
                transportLabel = if (isWifi) "WIFI" else if (isCell) "CELL" else "NET",
                interfaceName = iface.ifEmpty { if (isWifi) "wlan0" else "rmnet" },
                isCellular = isCell,
                isWifi = isWifi,
                isConnected = true
            )
        } catch (_: Exception) {
            liveNetState = LiveNetworkState("NET", detectFallbackInterface(), false, false, true)
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

    fun refreshConfiguredSlots() {
        val set = mutableSetOf<Int>()
        for (i in 0 until ProfileManager.MAX_SLOTS) {
            if (!getSlotPrefs(i).getString("host", "").isNullOrBlank()) set.add(i)
        }
        configuredSlots = set
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
                delay(if (attempt == 1) 300L else 950L)
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
    var currentLogs by remember { mutableStateOf("...") }
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
                currentLogs = output.ifEmpty { "Empty." }
                if (!liveLogStreaming) break
                delay(1500)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBg)
    ) {
        // Animated Live Ambient Nebula Backdrop
        DynamicAmbientBackdrop(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Compact Icon-First Top HUD with Integrated 5-Slot Orbs
            FloatingHudBar(
                rootState = rootState,
                liveNet = liveNet,
                activeSlot = activeSlot,
                runningSlot = runningSlot,
                isProxyActive = isProxyActive,
                startOnBoot = startOnBoot,
                bootSlot = bootSlot,
                configuredSlots = configuredSlots,
                onRecheckRoot = onRecheckRoot,
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

            // Smooth Animated View Viewport
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
                            .togetherWith(fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)))
                    },
                    label = "tabTransition"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> QuantumReactorHubTab(
                            isProxyActive = isProxyActive,
                            isTransitioning = isTransitioning,
                            runningSlot = runningSlot,
                            activeSlot = activeSlot,
                            sessionStartMs = sessionStartMs,
                            activePid = activePid,
                            publicIpInfo = publicIpInfo,
                            isFetchingIp = isFetchingIp,
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
                                    return@QuantumReactorHubTab
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testLatency = "..."
                                scope.launch {
                                    val res = ProxyTester.testProxy(getCurrentSettings())
                                    testLatency = when (res) {
                                        is TestResult.Success -> "${res.latencyMs}ms"
                                        is TestResult.Failure -> "ERR"
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
                                        return@QuantumReactorHubTab
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
                            publicIpInfo = publicIpInfo
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
                                Toast.makeText(context, "P$activeSlot Cleared", Toast.LENGTH_SHORT).show()
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
                                            if (ok) "Exported" else "Failed",
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
                                            Toast.makeText(context, "Imported", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No backup found", Toast.LENGTH_SHORT).show()
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
                                        currentLogs = l.ifEmpty { "Empty." }
                                    }
                                }
                            },
                            onClear = {
                                currentLogs = ""
                                scope.launch(Dispatchers.IO) {
                                    ProxyController.clearLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
                                }
                            }
                        )
                    }
                }
            }

            // Icon-Only Floating Glass Bottom Dock
            FloatingIconDock(
                selectedTab = selectedTab,
                isProxyActive = isProxyActive,
                onSelectTab = { selectedTab = it }
            )
        }
    }
}

@Composable
fun FloatingHudBar(
    rootState: RootState,
    liveNet: LiveNetworkState,
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    configuredSlots: Set<Int>,
    onRecheckRoot: () -> Unit,
    onSelectSlot: (Int) -> Unit
) {
    val rootCol by animateColorAsState(
        targetValue = when (rootState) {
            RootState.GRANTED -> NeonEmerald
            RootState.DENIED -> NeonRose
            RootState.CHECKING -> NeonAmber
        },
        label = "rootCol"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Status Pills (Root Icon + Live Wi-Fi/Cellular Icon)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Root Shield Pill
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(GlassPodBg)
                    .border(1.dp, rootCol.copy(alpha = 0.5f), CircleShape)
                    .clickable { onRecheckRoot() }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(IconType.SHIELD, size = 16.dp, tint = rootCol)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "U${ProfileManager.androidUserId}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextCrisp
                )
            }

            // Live Wi-Fi / Cellular Signal Pill
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(GlassPodBg)
                    .border(1.dp, GlassBorder, CircleShape)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(
                    type = if (liveNet.isWifi) IconType.WIFI else IconType.CELLULAR,
                    size = 15.dp,
                    tint = if (liveNet.isConnected) NeonCyan else NeonAmber
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = liveNet.transportLabel,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextSoft
                )
            }
        }

        // Right: Interactive 5-Slot Circular Orbs (P0..P4)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (0 until ProfileManager.MAX_SLOTS).forEach { idx ->
                val sel = idx == activeSlot
                val run = isProxyActive && runningSlot == idx
                val isBoot = startOnBoot && bootSlot == idx
                val isConf = configuredSlots.contains(idx)

                val orbScale by animateFloatAsState(
                    targetValue = if (sel || run) 1.08f else 0.95f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "orbScale"
                )
                val borderCol by animateColorAsState(
                    targetValue = when {
                        run -> NeonEmerald
                        sel -> NeonCyan
                        isBoot -> NeonAmber
                        isConf -> GlassBorder
                        else -> GlassBorder.copy(alpha = 0.4f)
                    },
                    label = "orbBorder"
                )
                val bgCol by animateColorAsState(
                    targetValue = when {
                        run -> NeonEmerald.copy(alpha = 0.20f)
                        sel -> NeonCyan.copy(alpha = 0.18f)
                        else -> GlassPodBg
                    },
                    label = "orbBg"
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(bgCol)
                        .border(if (sel || run) 1.8.dp else 1.dp, borderCol, CircleShape)
                        .clickable { onSelectSlot(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$idx",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            run -> NeonEmerald
                            sel -> NeonCyan
                            isConf -> TextCrisp
                            else -> TextDim
                        }
                    )
                    if (run || isBoot) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(5.dp)
                                .background(if (run) NeonEmerald else NeonAmber, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: QUANTUM REACTOR HUB (Animated, Icon-First Home)
// -------------------------------------------------------------

@Composable
fun QuantumReactorHubTab(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    sessionStartMs: Long,
    activePid: String?,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
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

    val infinite = rememberInfiniteTransition(label = "quantumReactor")
    val outerRot by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "outerRot"
    )
    val innerRot by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "innerRot"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val primaryColor by animateColorAsState(
        targetValue = when {
            isTransitioning -> NeonAmber
            isProxyActive -> NeonEmerald
            else -> NeonCyan
        },
        animationSpec = tween(500),
        label = "reactorColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. QUANTUM REACTOR CENTERPIECE + FLOATING ORBIT PILLS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp),
            contentAlignment = Alignment.Center
        ) {
            // Top-Left Floating Uptime / PID Pill
            if (isProxyActive && sessionStartMs > 0L) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .background(GlassPodBg)
                        .border(1.dp, NeonEmerald.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(NeonEmerald, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    UptimeBadge(sessionStartMs)
                }
            } else if (activePid != null) {
                Text(
                    text = "#$activePid",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSoft,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp)
                )
            }

            // Top-Right Floating Ping Probe Pill (Icon-First)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(GlassPodBg)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable(enabled = !isTesting) { onPingNode() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(
                    type = IconType.BOLT,
                    size = 14.dp,
                    tint = if (testLatency?.endsWith("ms") == true) NeonEmerald else NeonCyan
                )
                if (testLatency != null) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = testLatency,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (testLatency.endsWith("ms")) NeonEmerald else NeonRose
                    )
                }
            }

            // Central Interactive Multi-Ring Reactor Core
            Box(
                modifier = Modifier
                    .size(204.dp)
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
                    val center = Offset(w / 2f, h / 2f)

                    // Soft radial aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = if (isProxyActive) 0.28f else 0.12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = w * 0.49f * breathe
                        ),
                        radius = w * 0.49f * breathe,
                        center = center
                    )

                    // Outer segmented telemetry track
                    rotate(degrees = outerRot, pivot = center) {
                        drawCircle(
                            color = primaryColor.copy(alpha = if (isProxyActive) 0.7f else 0.28f),
                            radius = w * 0.44f,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 16f), 0f)
                            )
                        )
                    }

                    // Counter-rotating dual plasma arcs
                    rotate(degrees = innerRot, pivot = center) {
                        val arcRectOffset = Offset(w * 0.14f, h * 0.14f)
                        val arcSize = Size(w * 0.72f, h * 0.72f)
                        drawArc(
                            color = if (isProxyActive) NeonCyan else NeonViolet.copy(alpha = 0.55f),
                            startAngle = 0f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = arcRectOffset,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (isProxyActive) NeonCyan else NeonViolet.copy(alpha = 0.55f),
                            startAngle = 180f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = arcRectOffset,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Core sphere
                    drawCircle(
                        color = GlassPodBg,
                        radius = w * 0.29f * (if (isProxyActive) breathe else 1f),
                        center = center
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = if (isProxyActive) 0.9f else 0.45f),
                        radius = w * 0.29f * (if (isProxyActive) breathe else 1f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Core Power / Bolt Vector Icon
                VectorIcon(
                    type = if (isProxyActive) IconType.BOLT else IconType.POWER,
                    size = 44.dp,
                    tint = primaryColor,
                    strokeWidth = 2.6f,
                    modifier = Modifier.scale(if (isProxyActive) breathe else 1f)
                )
            }
        }

        // 2. COMPACT EXIT IP CAPSULE + 3 ICON ACTION BUTTONS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GlassCardBg)
                .border(
                    1.dp,
                    if (isProxyActive) NeonEmerald.copy(alpha = 0.45f) else GlassBorder,
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isProxyActive && publicIpInfo != null -> "${publicIpInfo.flagEmoji} ${publicIpInfo.ip}"
                        isProxyActive && isFetchingIp -> "•••.•••.•••.•••"
                        host.isNotBlank() -> "${host.trim()}:${port.trim()}"
                        else -> "P$activeSlot —"
                    },
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextCrisp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isProxyActive && publicIpInfo != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${publicIpInfo.country} • ${publicIpInfo.timezone.ifEmpty { "P${runningSlot ?: activeSlot}" }}",
                        fontSize = 11.sp,
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(
                    icon = IconType.COPY,
                    size = 38.dp,
                    iconSize = 16.dp,
                    onClick = {
                        val ipToCopy = publicIpInfo?.ip ?: host.trim()
                        if (ipToCopy.isNotEmpty()) {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("IP", ipToCopy))
                            Toast.makeText(context, ipToCopy, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                GlassIconButton(
                    icon = IconType.REFRESH,
                    size = 38.dp,
                    iconSize = 16.dp,
                    tint = NeonCyan,
                    spinning = isFetchingIp,
                    onClick = onRefreshIp
                )
                GlassIconButton(
                    icon = IconType.GLOBE,
                    size = 38.dp,
                    iconSize = 17.dp,
                    tint = NeonEmerald,
                    borderColor = NeonEmerald.copy(alpha = 0.5f),
                    onClick = onOpenIpFighterTab
                )
            }
        }

        // 3. 4-ICON QUICK SHIELD & PROTOCOL MATRIX STRIP (Icon + Short Code Only)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.SLIDERS,
                code = proxyType.shortBadge,
                active = true,
                accent = NeonCyan,
                onClick = onOpenNodeTab
            )
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.SHIELD,
                code = "DNS",
                active = isProxyActive,
                accent = NeonEmerald,
                onClick = {}
            )
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = if (strictWebRtcShield) IconType.EYE_OFF else IconType.EYE,
                code = "RTC",
                active = strictWebRtcShield,
                accent = NeonEmerald,
                onClick = onToggleWebRtc
            )
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.HOTSPOT,
                code = "AP",
                active = routeHotspot,
                accent = NeonCyan,
                onClick = onToggleHotspot
            )
        }

        // 4. TWIN LIVE BEZIER WAVEFORM TELEMETRY PODS (RX & TX)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiveWaveformPod(
                modifier = Modifier.weight(1f),
                icon = IconType.DOWN_ARROW,
                rate = currentRxRate,
                total = totalRxBytes,
                history = rxHistory,
                accent = NeonEmerald
            )
            LiveWaveformPod(
                modifier = Modifier.weight(1f),
                icon = IconType.UP_ARROW,
                rate = currentTxRate,
                total = totalTxBytes,
                history = txHistory,
                accent = NeonCyan
            )
        }
    }
}

@Composable
private fun QuickShieldTile(
    modifier: Modifier = Modifier,
    icon: IconType,
    code: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val borderCol by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.55f) else GlassBorder,
        label = "tileBorder"
    )
    val bgCol by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.12f) else GlassCardBg,
        label = "tileBg"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgCol)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VectorIcon(
            type = icon,
            size = 20.dp,
            tint = if (active) accent else TextSoft
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = code,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) TextCrisp else TextSoft
        )
    }
}

@Composable
private fun LiveWaveformPod(
    modifier: Modifier = Modifier,
    icon: IconType,
    rate: Long,
    total: Long,
    history: List<Long>,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassCardBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                VectorIcon(icon, size = 15.dp, tint = accent)
            }
            Text(
                text = formatBytes(total),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSoft
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = splitSpeedValue(rate),
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = TextCrisp
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

        Spacer(modifier = Modifier.height(8.dp))

        // Smooth Cubic-Bezier Gradient Filled Waveform
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
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
                        colors = listOf(accent.copy(alpha = 0.32f), Color.Transparent)
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
// TAB 1: EPHEMERAL NON-CACHED LIVE IPFIGHTER & LEAK RADAR
// -------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveEphemeralIpFighterTab(
    isProxyActive: Boolean,
    runningSlot: Int?,
    publicIpInfo: GeoIpResult?
) {
    var reloadNonce by remember { mutableIntStateOf(0) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var targetSiteIndex by remember { mutableIntStateOf(0) }

    val targets = listOf(
        Triple(IconType.GLOBE, "IPF", "https://ipfighter.com/"),
        Triple(IconType.EYE_OFF, "RTC", "https://browserleaks.com/webrtc"),
        Triple(IconType.SHIELD, "DNS", "https://browserleaks.com/dns")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(GlassCardBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
            .padding(10.dp)
    ) {
        // Minimal Icon Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEachIndexed { idx, (icon, code, _) ->
                    val sel = targetSiteIndex == idx
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(if (sel) NeonCyan.copy(alpha = 0.18f) else GlassPodBg)
                            .border(1.dp, if (sel) NeonCyan else GlassBorder, CircleShape)
                            .clickable {
                                targetSiteIndex = idx
                                reloadNonce++
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VectorIcon(icon, size = 14.dp, tint = if (sel) NeonCyan else TextSoft)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = code,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (sel) TextCrisp else TextSoft
                        )
                    }
                }
            }

            GlassIconButton(
                icon = IconType.REFRESH,
                size = 34.dp,
                iconSize = 15.dp,
                tint = NeonEmerald,
                borderColor = NeonEmerald.copy(alpha = 0.5f),
                spinning = loadProgress in 1..95,
                onClick = {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    reloadNonce++
                }
            )
        }

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 4.dp),
                color = NeonCyan,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
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
// TAB 2: ICON-FIRST NODE STUDIO
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
        // 1. Top Icon Action Strip (Paste URI, Test Socket, Reset Slot)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassCardBg)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = IconType.PASTE,
                    tint = NeonCyan,
                    borderColor = NeonCyan.copy(alpha = 0.5f),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val rawClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                        val parsed = parseProxyUri(rawClip)
                        if (parsed != null) {
                            onImportUri(parsed)
                            Toast.makeText(context, parsed.type.shortBadge, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid URI", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                GlassIconButton(
                    icon = IconType.BOLT,
                    tint = NeonEmerald,
                    borderColor = NeonEmerald.copy(alpha = 0.5f),
                    spinning = isCheckingAlive,
                    onClick = {
                        if (host.trim().isEmpty()) {
                            aliveResult = TestResult.Failure("Empty")
                            return@GlassIconButton
                        }
                        isCheckingAlive = true
                        aliveResult = null
                        scope.launch {
                            aliveResult = ProxyTester.testProxy(getCurrentSettings())
                            isCheckingAlive = false
                        }
                    }
                )

                if (aliveResult != null) {
                    when (val res = aliveResult) {
                        is TestResult.Success -> Text(
                            text = "${res.latencyMs} ms",
                            color = NeonEmerald,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                        is TestResult.Failure -> Text(
                            text = "ERR",
                            color = NeonRose,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                        null -> {}
                    }
                } else {
                    Text(
                        text = "P$activeSlot",
                        color = TextSoft,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            GlassIconButton(
                icon = IconType.TRASH,
                tint = NeonRose,
                borderColor = NeonRose.copy(alpha = 0.45f),
                onClick = onResetSlot
            )
        }

        // 2. Protocol Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProxyType.values().forEach { t ->
                val sel = proxyType == t
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) NeonIndigo else GlassCardBg)
                        .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(14.dp))
                        .clickable { onProxyTypeChange(t); onConfigChanged() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.shortBadge,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (sel) Color.White else TextSoft
                    )
                }
            }
        }

        // 3. Compact Segmented Mode Pills (Transport + IP Stack + WebRTC Shield)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Transport Pill
            val isUdp = transportMode == TransportMode.TCP_AND_UDP
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassCardBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        onTransportModeChange(if (isUdp) TransportMode.TCP_ONLY else TransportMode.TCP_AND_UDP)
                        onConfigChanged()
                    }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(IconType.HUB, size = 15.dp, tint = NeonCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isUdp) "TCP+UDP" else "TCP",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextCrisp
                )
            }

            // IP Stack Pill (Cycles v4 -> Dual -> v6)
            Row(
                modifier = Modifier
                    .weight(0.85f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassCardBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        val next = when (ipMode) {
                            IpMode.IPV4_ONLY -> IpMode.DUAL_STACK
                            IpMode.DUAL_STACK -> IpMode.IPV6_ONLY
                            IpMode.IPV6_ONLY -> IpMode.IPV4_ONLY
                        }
                        onIpModeChange(next)
                        onConfigChanged()
                    }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(IconType.GLOBE, size = 15.dp, tint = NeonViolet)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (ipMode) {
                        IpMode.IPV4_ONLY -> "IPv4"
                        IpMode.DUAL_STACK -> "DUAL"
                        IpMode.IPV6_ONLY -> "IPv6"
                    },
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextCrisp
                )
            }

            // WebRTC Lock Pill
            Row(
                modifier = Modifier
                    .weight(0.85f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (strictWebRtcShield) NeonEmerald.copy(alpha = 0.14f) else GlassCardBg)
                    .border(1.dp, if (strictWebRtcShield) NeonEmerald else GlassBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        onStrictWebRtcChange(!strictWebRtcShield)
                        onConfigChanged()
                    }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(
                    type = if (strictWebRtcShield) IconType.EYE_OFF else IconType.EYE,
                    size = 15.dp,
                    tint = if (strictWebRtcShield) NeonEmerald else TextSoft
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RTC",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (strictWebRtcShield) NeonEmerald else TextSoft
                )
            }
        }

        // 4. Icon-First Endpoint Fields
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GlassCardBg)
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { onHostChange(it); onConfigChanged() },
                    leadingIcon = { VectorIcon(IconType.SERVER, size = 17.dp, tint = NeonCyan) },
                    placeholder = { Text("Host / IP", color = TextDim) },
                    modifier = Modifier.weight(0.66f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { onPortChange(it); onConfigChanged() },
                    leadingIcon = { VectorIcon(IconType.PORT, size = 16.dp, tint = NeonCyan) },
                    placeholder = { Text("1080", color = TextDim) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.34f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            when (proxyType) {
                ProxyType.SOCKS5, ProxyType.HTTP -> {
                    var showPass by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { onUsernameChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.USER, size = 17.dp, tint = TextSoft) },
                            placeholder = { Text("User", color = TextDim) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { onPasswordChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.KEY, size = 17.dp, tint = TextSoft) },
                            placeholder = { Text("Pass", color = TextDim) },
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .clickable { showPass = !showPass }
                                        .padding(8.dp)
                                ) {
                                    VectorIcon(
                                        type = if (showPass) IconType.EYE_OFF else IconType.EYE,
                                        size = 17.dp,
                                        tint = NeonCyan
                                    )
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
                        leadingIcon = { VectorIcon(IconType.SHIELD, size = 17.dp, tint = NeonCyan) },
                        placeholder = { Text("Cipher", color = TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.KEY, size = 17.dp, tint = TextSoft) },
                        placeholder = { Text("Password", color = TextDim) },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(modifier = Modifier.clickable { showPass = !showPass }.padding(8.dp)) {
                                VectorIcon(if (showPass) IconType.EYE_OFF else IconType.EYE, size = 17.dp, tint = NeonCyan)
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
                        leadingIcon = { VectorIcon(IconType.KEY, size = 17.dp, tint = NeonCyan) },
                        placeholder = { Text("UUID", color = TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.GLOBE, size = 17.dp, tint = TextSoft) },
                        placeholder = { Text("SNI", color = TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = realityPublicKey,
                            onValueChange = { onRealityPublicKeyChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.SHIELD, size = 17.dp, tint = TextSoft) },
                            placeholder = { Text("Public Key", color = TextDim) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = realityShortId,
                            onValueChange = { onRealityShortIdChange(it); onConfigChanged() },
                            placeholder = { Text("SID", color = TextDim) },
                            modifier = Modifier.weight(0.5f),
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
                        leadingIcon = { VectorIcon(IconType.KEY, size = 17.dp, tint = NeonCyan) },
                        placeholder = { Text("Password", color = TextDim) },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(modifier = Modifier.clickable { showPass = !showPass }.padding(8.dp)) {
                                VectorIcon(if (showPass) IconType.EYE_OFF else IconType.EYE, size = 17.dp, tint = NeonCyan)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.GLOBE, size = 17.dp, tint = TextSoft) },
                        placeholder = { Text("SNI", color = TextDim) },
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
// TAB 3: ICON-FIRST KERNEL MATRIX & APP SPLIT-TUNNEL
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 4-Icon Matrix Control Row (Boot, Hotspot, Export, Import)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.ROCKET,
                code = if (startOnBoot) "BOOT P$bootSlot" else "BOOT",
                active = startOnBoot,
                accent = NeonAmber,
                onClick = { onStartOnBootChange(!startOnBoot) }
            )
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.HOTSPOT,
                code = "HOTSPOT",
                active = routeHotspot,
                accent = NeonCyan,
                onClick = { onRouteHotspotChange(!routeHotspot) }
            )
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.EXPORT,
                code = "SAVE",
                active = false,
                accent = NeonIndigo,
                onClick = onExportBackup
            )
            QuickShieldTile(
                modifier = Modifier.weight(1f),
                icon = IconType.IMPORT,
                code = "LOAD",
                active = false,
                accent = NeonEmerald,
                onClick = onImportBackup
            )
        }

        // Boot Slot Selector Orbs when Boot is Armed
        if (startOnBoot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassCardBg)
                    .border(1.dp, NeonAmber.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(IconType.ROCKET, size = 18.dp, tint = NeonAmber)
                (0 until ProfileManager.MAX_SLOTS).forEach { s ->
                    val sel = bootSlot == s
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (sel) NeonAmber else GlassPodBg)
                            .clickable { onBootSlotChange(s) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P$s",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (sel) VoidBg else TextSoft
                        )
                    }
                }
            }
        }

        // 2. App Split-Tunnel Matrix Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GlassCardBg)
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VectorIcon(IconType.APPS, size = 20.dp, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (routeWholeProfile) "GLOBAL LOCK" else "${selectedPackages.size} APPS",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextCrisp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!routeWholeProfile) {
                        GlassIconButton(
                            icon = IconType.CHECK,
                            size = 34.dp,
                            iconSize = 15.dp,
                            tint = NeonEmerald,
                            onClick = onSelectAll
                        )
                        GlassIconButton(
                            icon = IconType.TRASH,
                            size = 34.dp,
                            iconSize = 15.dp,
                            tint = NeonRose,
                            onClick = onClearAll
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = onRouteWholeProfileChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBg,
                            checkedTrackColor = NeonEmerald
                        )
                    )
                }
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
                    leadingIcon = { VectorIcon(IconType.SEARCH, size = 17.dp, tint = NeonCyan) },
                    placeholder = { Text("Search...", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
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
                                Text(
                                    text = app.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCrisp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${app.packageName} • ${app.uid}",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSoft,
                                    maxLines = 1
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeonEmerald,
                                    checkmarkColor = VoidBg
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
// TAB 4: ICON-ONLY LIVE KERNEL TERMINAL
// -------------------------------------------------------------

@Composable
fun KernelLogsTab(
    logs: String,
    liveLogStreaming: Boolean,
    showKernelAuditMode: Boolean,
    onToggleLiveStream: () -> Unit,
    onToggleAuditMode: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(GlassCardBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(
                    icon = if (liveLogStreaming) IconType.PAUSE else IconType.PLAY,
                    size = 36.dp,
                    iconSize = 15.dp,
                    tint = if (liveLogStreaming) NeonEmerald else TextSoft,
                    borderColor = if (liveLogStreaming) NeonEmerald.copy(alpha = 0.5f) else GlassBorder,
                    onClick = onToggleLiveStream
                )
                GlassIconButton(
                    icon = if (showKernelAuditMode) IconType.TERMINAL else IconType.SHIELD,
                    size = 36.dp,
                    iconSize = 16.dp,
                    tint = if (showKernelAuditMode) NeonCyan else TextSoft,
                    borderColor = if (showKernelAuditMode) NeonCyan.copy(alpha = 0.5f) else GlassBorder,
                    onClick = onToggleAuditMode
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(
                    icon = IconType.COPY,
                    size = 36.dp,
                    iconSize = 15.dp,
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Logs", logs))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                )
                GlassIconButton(
                    icon = IconType.REFRESH,
                    size = 36.dp,
                    iconSize = 15.dp,
                    tint = NeonCyan,
                    onClick = onRefresh
                )
                GlassIconButton(
                    icon = IconType.TRASH,
                    size = 36.dp,
                    iconSize = 15.dp,
                    tint = NeonRose,
                    onClick = onClear
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(VoidBg)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = TextCrisp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

// -------------------------------------------------------------
// FLOATING ICON-ONLY GLASS BOTTOM DOCK
// -------------------------------------------------------------

@Composable
fun FloatingIconDock(
    selectedTab: Int,
    isProxyActive: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val icons = listOf(
        IconType.BOLT,
        IconType.GLOBE,
        IconType.SLIDERS,
        IconType.HUB,
        IconType.TERMINAL
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(GlassCardBg)
                .border(1.dp, GlassBorder, CircleShape)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icons.forEachIndexed { idx, icon ->
                val sel = selectedTab == idx
                val scale by animateFloatAsState(
                    targetValue = if (sel) 1.15f else 0.95f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dockScale"
                )
                val activeColor = if (idx == 0 && isProxyActive) NeonEmerald else NeonCyan

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(if (sel) activeColor.copy(alpha = 0.16f) else Color.Transparent)
                        .border(
                            width = if (sel) 1.2.dp else 0.dp,
                            color = if (sel) activeColor.copy(alpha = 0.65f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTab(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    VectorIcon(
                        type = icon,
                        size = 21.dp,
                        tint = if (sel) activeColor else TextSoft
                    )
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
        color = NeonEmerald
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
