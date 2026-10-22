package com.nameless.proxy

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
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
import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ProxyConfig
import androidx.webkit.WebViewFeature
import com.nameless.proxy.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

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

private val UI_PROTOCOLS = listOf(
    ProxyType.SOCKS5,
    ProxyType.SOCKS4,
    ProxyType.HTTP,
    ProxyType.SHADOWSOCKS,
    ProxyType.VLESS,
    ProxyType.TROJAN,
    ProxyType.HYSTERIA2
)

private val ProxyType.displayTitle: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "SOCKS5"
        ProxyType.SOCKS4 -> "SOCKS4"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "Shadowsocks"
        ProxyType.VLESS -> "VLESS"
        ProxyType.VMESS -> "VMess"
        ProxyType.TROJAN -> "Trojan"
        ProxyType.HYSTERIA2 -> "Hysteria 2"
        ProxyType.TUIC -> "TUIC"
    }

private val ProxyType.shortBadge: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "SOCKS5"
        ProxyType.SOCKS4 -> "SOCKS4"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "SS"
        ProxyType.VLESS -> "VLESS"
        ProxyType.VMESS -> "VMESS"
        ProxyType.TROJAN -> "TROJAN"
        ProxyType.HYSTERIA2 -> "HY2"
        ProxyType.TUIC -> "TUIC"
    }

private fun slotLabel(slotIndex: Int): String = "P${slotIndex + 1}"

// -------------------------------------------------------------
// SIMPLE CALM SLATE PALETTE + ULTRA-SOFT BUBBLY TOUCH PHYSICS (v13)
// Zero Live Background Colors • Extra Pillowy Jelly Bounce • Rich Spring Transitions
// -------------------------------------------------------------

private val ZuanDeepSpace = Color(0xFF0A0E14)
private val ZuanGlassBase = Color(0xFF131B26)
private val ZuanGlassBottom = Color(0xFF0F1620)
private val ZuanGlassElevated = Color(0xFF1A2536)
private val ZuanAqua = Color(0xFF38BDF8)
private val ZuanMint = Color(0xFF2DD4BF)
private val ZuanEmerald = Color(0xFF10B981)
private val ZuanViolet = Color(0xFF64748B)
private val ZuanIndigo = Color(0xFF26354A)
private val ZuanAmber = Color(0xFFFBBF24)
private val ZuanRose = Color(0xFFFB7185)
private val ZuanTextPrimary = Color(0xFFF1F5F9)
private val ZuanTextSecondary = Color(0xFF94A3B8)
private val ZuanTextMuted = Color(0xFF64748B)

@Composable
fun ZuanLiquidMeshBackground(
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    // Clean, simple solid dark slate background — all live background colors removed as requested
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZuanDeepSpace)
    )
}

/**
 * Ultra-Soft Pillowy & Bubbly Touch Physics Modifier:
 * - Plump spring expansion (pressScale = 1.085f, dampingRatio = 0.24f)
 * - Responsive 3D finger tilt (rotationX / rotationY) + soft upward pillow lift
 * - Soft radial touch ripple glow
 */
@Composable
fun Modifier.liquidInteractive(
    enabled: Boolean = true,
    pressScale: Float = 1.085f,
    highlightColor: Color = ZuanAqua,
    onClick: (() -> Unit)? = null
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Unspecified) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf( Offset(1f, 1f) ) }

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.24f, stiffness = 280f),
        label = "liquidPress"
    )
    val animDragX by animateFloatAsState(
        targetValue = if (isPressed) 16f * tanh(0.055f * dragDelta.x / 16f) else 0f,
        animationSpec = spring(dampingRatio = 0.28f, stiffness = 290f),
        label = "dragX"
    )
    val animDragY by animateFloatAsState(
        targetValue = if (isPressed) 16f * tanh(0.055f * dragDelta.y / 16f) else 0f,
        animationSpec = spring(dampingRatio = 0.28f, stiffness = 290f),
        label = "dragY"
    )

    val normX = if (touchPos != Offset.Unspecified && boxSize.x > 1f) {
        ((touchPos.x / boxSize.x) - 0.5f) * 2f
    } else 0f
    val normY = if (touchPos != Offset.Unspecified && boxSize.y > 1f) {
        ((touchPos.y / boxSize.y) - 0.5f) * 2f
    } else 0f

    val tiltX by animateFloatAsState(
        targetValue = if (isPressed && enabled) (-normY * 6.5f).coerceIn(-8f, 8f) else 0f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = 260f),
        label = "tiltX"
    )
    val tiltY by animateFloatAsState(
        targetValue = if (isPressed && enabled) (normX * 6.5f).coerceIn(-8f, 8f) else 0f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = 260f),
        label = "tiltY"
    )

    val pointerMod = if (enabled) {
        Modifier.pointerInput(onClick) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                boxSize = Offset(size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
                isPressed = true
                touchPos = down.position
                val startPos = down.position
                dragDelta = Offset.Zero
                var upOrCancelled = false
                while (!upOrCancelled) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change == null || !change.pressed) {
                        upOrCancelled = true
                    } else {
                        touchPos = change.position
                        dragDelta = change.position - startPos
                    }
                }
                isPressed = false
                dragDelta = Offset.Zero
            }
        }
    } else Modifier

    val clickMod = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    return this
        .graphicsLayer {
            val s = 1f + (pressScale - 1f) * pressProgress
            scaleX = s + (abs(animDragX) * 0.0052f)
            scaleY = s - (abs(animDragX) * 0.0024f) + (abs(animDragY) * 0.0036f)
            rotationX = tiltX
            rotationY = tiltY
            translationX = animDragX
            translationY = animDragY - (4.5f * pressProgress)
            cameraDistance = 12f * density
        }
        .then(pointerMod)
        .then(clickMod)
        .drawWithContent {
            drawContent()
            if (pressProgress > 0.01f) {
                val center = if (touchPos != Offset.Unspecified) touchPos else Offset(size.width / 2f, size.height / 2f)
                val radius = (size.maxDimension * (0.45f + 0.55f * pressProgress)).coerceAtLeast(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f * pressProgress),
                            highlightColor.copy(alpha = 0.16f * pressProgress),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
}

/**
 * Simple Matte & Plump 3D Pillow Card (Zero Rainbow Shimmer, Extra Tactile Cushion Bevel)
 */
@Composable
fun ZuanLiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    accentGlow: Color = ZuanAqua,
    isHighlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(15.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) accentGlow.copy(alpha = 0.62f) else Color(0xFF243246),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
        label = "cardBorderColor"
    )
    val surfaceBrush = Brush.verticalGradient(
        colors = listOf(
            if (isHighlighted) ZuanGlassElevated else ZuanGlassBase,
            ZuanGlassBottom
        )
    )

    Box(
        modifier = modifier
            .liquidInteractive(
                enabled = true,
                pressScale = if (onClick != null) 1.058f else 1.032f,
                highlightColor = accentGlow,
                onClick = onClick
            )
            .clip(shape)
            .background(brush = surfaceBrush, shape = shape)
            .border(width = if (isHighlighted) 1.4.dp else 1.dp, color = borderColor, shape = shape)
            .drawWithContent {
                // Subtle top pillow highlight bevel for a soft tactile cushion feel
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isHighlighted) 0.08f else 0.045f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.38f
                    )
                )
                drawContent()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

// -------------------------------------------------------------
// CRISP VECTOR ICON ENGINE
// -------------------------------------------------------------

enum class IconType {
    POWER, SHIELD, GLOBE, SERVER, APPS, TERMINAL, ROCKET, HOTSPOT,
    PASTE, BOLT, REFRESH, TRASH, CHECK, COPY, EYE, EYE_OFF,
    DOWNLOAD, UPLOAD, WIFI, CELLULAR, SLIDERS, LOCK, PORT, USER, KEY
}

@Composable
fun VectorIcon(
    type: IconType,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = ZuanTextPrimary,
    strokeWidth: Float = 2.1f
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (type) {
            IconType.POWER -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.48f), strokeWidth = strokeWidth.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.20f),
                    size = Size(w * 0.64f, h * 0.64f),
                    style = s
                )
            }
            IconType.SHIELD -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.84f, h * 0.26f)
                    cubicTo(w * 0.84f, h * 0.58f, w * 0.72f, h * 0.76f, w * 0.5f, h * 0.88f)
                    cubicTo(w * 0.28f, h * 0.76f, w * 0.16f, h * 0.58f, w * 0.16f, h * 0.26f)
                    close()
                }
                drawPath(p, tint, style = s)
                val ck = Path().apply {
                    moveTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.46f, h * 0.60f)
                    lineTo(w * 0.66f, h * 0.40f)
                }
                drawPath(ck, tint, style = s)
            }
            IconType.GLOBE -> {
                drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = s)
                drawOval(tint, topLeft = Offset(w * 0.31f, h * 0.12f), size = Size(w * 0.38f, h * 0.76f), style = s)
                drawLine(tint, Offset(w * 0.14f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), strokeWidth = s.width)
            }
            IconType.SERVER -> {
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.18f), Size(w * 0.68f, h * 0.26f), CornerRadius(w * 0.08f), style = s)
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.56f), Size(w * 0.68f, h * 0.26f), CornerRadius(w * 0.08f), style = s)
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.30f, h * 0.31f))
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.30f, h * 0.69f))
            }
            IconType.APPS -> {
                val r = CornerRadius(w * 0.07f)
                val b = w * 0.28f
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.16f), Size(b, b), r, style = s)
                drawRoundRect(tint, Offset(w * 0.56f, h * 0.16f), Size(b, b), r, style = s)
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.56f), Size(b, b), r, style = s)
                drawRoundRect(tint, Offset(w * 0.56f, h * 0.56f), Size(b, b), r, style = s)
            }
            IconType.TERMINAL -> {
                drawRoundRect(tint, Offset(w * 0.12f, h * 0.18f), Size(w * 0.76f, h * 0.64f), CornerRadius(w * 0.10f), style = s)
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.38f)
                    lineTo(w * 0.40f, h * 0.50f)
                    lineTo(w * 0.26f, h * 0.62f)
                }
                drawPath(p, tint, style = s)
                drawLine(tint, Offset(w * 0.48f, h * 0.62f), Offset(w * 0.66f, h * 0.62f), strokeWidth = s.width, cap = StrokeCap.Round)
            }
            IconType.ROCKET -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    cubicTo(w * 0.74f, h * 0.28f, w * 0.74f, h * 0.56f, w * 0.64f, h * 0.74f)
                    lineTo(w * 0.36f, h * 0.74f)
                    cubicTo(w * 0.26f, h * 0.56f, w * 0.26f, h * 0.28f, w * 0.5f, h * 0.12f)
                    close()
                }
                drawPath(p, tint, style = s)
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.42f))
            }
            IconType.HOTSPOT -> {
                drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.56f))
                drawArc(tint, -145f, 110f, false, Offset(w * 0.26f, h * 0.32f), Size(w * 0.48f, h * 0.48f), style = s)
                drawArc(tint, -145f, 110f, false, Offset(w * 0.12f, h * 0.16f), Size(w * 0.76f, h * 0.76f), style = s)
            }
            IconType.PASTE -> {
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.22f), Size(w * 0.60f, h * 0.64f), CornerRadius(w * 0.08f), style = s)
                drawRoundRect(tint, Offset(w * 0.35f, h * 0.12f), Size(w * 0.30f, h * 0.14f), CornerRadius(w * 0.04f), style = s)
            }
            IconType.BOLT -> {
                val p = Path().apply {
                    moveTo(w * 0.56f, h * 0.12f)
                    lineTo(w * 0.24f, h * 0.52f)
                    lineTo(w * 0.48f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.88f)
                    lineTo(w * 0.76f, h * 0.46f)
                    lineTo(w * 0.52f, h * 0.46f)
                    close()
                }
                drawPath(p, tint, style = s)
            }
            IconType.REFRESH -> {
                drawArc(tint, -60f, 285f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f), style = s)
            }
            IconType.TRASH -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.26f), Offset(w * 0.82f, h * 0.26f), strokeWidth = s.width, cap = StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.26f, h * 0.26f), Size(w * 0.48f, h * 0.60f), CornerRadius(w * 0.06f), style = s)
            }
            IconType.CHECK -> {
                val p = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.82f, h * 0.28f)
                }
                drawPath(p, tint, style = s)
            }
            IconType.COPY -> {
                drawRoundRect(tint, Offset(w * 0.32f, h * 0.30f), Size(w * 0.50f, h * 0.54f), CornerRadius(w * 0.07f), style = s)
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.16f), Size(w * 0.48f, h * 0.52f), CornerRadius(w * 0.07f), style = s)
            }
            IconType.EYE -> {
                drawOval(tint, Offset(w * 0.14f, h * 0.28f), Size(w * 0.72f, h * 0.44f), style = s)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.5f))
            }
            IconType.EYE_OFF -> {
                drawOval(tint, Offset(w * 0.14f, h * 0.28f), Size(w * 0.72f, h * 0.44f), style = s)
                drawLine(tint, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.82f, h * 0.18f), strokeWidth = s.width, cap = StrokeCap.Round)
            }
            IconType.DOWNLOAD -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.76f), strokeWidth = s.width, cap = StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.78f)
                    lineTo(w * 0.74f, h * 0.54f)
                }
                drawPath(p, tint, style = s)
            }
            IconType.UPLOAD -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.24f), strokeWidth = s.width, cap = StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.46f)
                    lineTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.74f, h * 0.46f)
                }
                drawPath(p, tint, style = s)
            }
            IconType.WIFI -> {
                drawArc(tint, -135f, 90f, false, Offset(w * 0.14f, h * 0.24f), Size(w * 0.72f, h * 0.72f), style = s)
                drawArc(tint, -135f, 90f, false, Offset(w * 0.28f, h * 0.42f), Size(w * 0.44f, h * 0.44f), style = s)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.76f))
            }
            IconType.CELLULAR -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.22f, h * 0.60f), strokeWidth = s.width * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.40f, h * 0.78f), Offset(w * 0.40f, h * 0.46f), strokeWidth = s.width * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.58f, h * 0.78f), Offset(w * 0.58f, h * 0.32f), strokeWidth = s.width * 1.2f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.78f), Offset(w * 0.76f, h * 0.18f), strokeWidth = s.width * 1.2f, cap = StrokeCap.Round)
            }
            IconType.SLIDERS -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.30f), Offset(w * 0.82f, h * 0.30f), strokeWidth = s.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.70f), Offset(w * 0.82f, h * 0.70f), strokeWidth = s.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.38f, h * 0.30f))
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.64f, h * 0.70f))
            }
            IconType.LOCK -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.44f), Size(w * 0.56f, h * 0.42f), CornerRadius(w * 0.08f), style = s)
                drawArc(tint, 180f, 180f, false, Offset(w * 0.32f, h * 0.18f), Size(w * 0.36f, h * 0.44f), style = s)
            }
            IconType.PORT -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = s)
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.5f, h * 0.5f))
            }
            IconType.USER -> {
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.34f), style = s)
                drawArc(tint, 195f, 150f, false, Offset(w * 0.20f, h * 0.56f), Size(w * 0.60f, h * 0.44f), style = s)
            }
            IconType.KEY -> {
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.34f, h * 0.5f), style = s)
                drawLine(tint, Offset(w * 0.52f, h * 0.5f), Offset(w * 0.84f, h * 0.5f), strokeWidth = s.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.74f, h * 0.5f), Offset(w * 0.74f, h * 0.66f), strokeWidth = s.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun ZuanIconBadge(
    icon: IconType,
    accent: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 17.dp
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.10f))
                )
            )
            .border(
                width = 1.1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), accent.copy(alpha = 0.45f))
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        VectorIcon(icon, size = iconSize, tint = accent)
    }
}

private fun parseProxyUri(rawInput: String): ProxySettings? {
    val line = rawInput.trim().lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    return try {
        if (!line.contains("://")) {
            val parts = line.split(":")
            if (parts.size == 2 || parts.size == 4) {
                val h = parts[0].trim()
                val p = parts[1].trim().toIntOrNull() ?: return null
                val u = if (parts.size == 4) parts[2].trim() else ""
                val pw = if (parts.size == 4) parts[3].trim() else ""
                if (h.isNotEmpty()) {
                    return ProxySettings(type = ProxyType.SOCKS5, host = h, port = p, username = u, password = pw)
                }
            }
        }
        val uri = Uri.parse(line)
        val scheme = uri.scheme?.lowercase() ?: return null
        when (scheme) {
            "socks5", "socks", "socks5h" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 1080
                val userInfo = uri.userInfo ?: ""
                val u = userInfo.substringBefore(":", "")
                val pw = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
                ProxySettings(type = ProxyType.SOCKS5, host = h, port = p, username = Uri.decode(u), password = Uri.decode(pw))
            }
            "socks4" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 1080
                ProxySettings(type = ProxyType.SOCKS4, host = h, port = p)
            }
            "http", "https" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 8080
                val userInfo = uri.userInfo ?: ""
                val u = userInfo.substringBefore(":", "")
                val pw = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
                ProxySettings(type = ProxyType.HTTP, host = h, port = p, username = Uri.decode(u), password = Uri.decode(pw))
            }
            "vless" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 443
                val uuid = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
                val pbk = uri.getQueryParameter("pbk") ?: ""
                val sid = uri.getQueryParameter("sid") ?: ""
                ProxySettings(type = ProxyType.VLESS, host = h, port = p, password = uuid, sni = sni, realityPublicKey = pbk, realityShortId = sid)
            }
            "trojan" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 443
                val pw = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: ""
                ProxySettings(type = ProxyType.TROJAN, host = h, port = p, password = Uri.decode(pw), sni = sni)
            }
            "hy2", "hysteria2" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 443
                val pw = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: ""
                ProxySettings(type = ProxyType.HYSTERIA2, host = h, port = p, password = Uri.decode(pw), sni = sni)
            }
            "ss" -> {
                val body = line.removePrefix("ss://").substringBefore("#")
                if (body.contains("@")) {
                    val userPart = body.substringBefore("@")
                    val hostPart = body.substringAfter("@")
                    val decoded = try {
                        String(Base64.decode(userPart, Base64.URL_SAFE or Base64.NO_WRAP or Base64.DEFAULT))
                    } catch (_: Exception) {
                        Uri.decode(userPart)
                    }
                    val method = decoded.substringBefore(":")
                    val pass = decoded.substringAfter(":")
                    val h = hostPart.substringBeforeLast(":")
                    val p = hostPart.substringAfterLast(":").toIntOrNull() ?: 8388
                    ProxySettings(type = ProxyType.SHADOWSOCKS, host = h, port = p, ssMethod = method, password = pass)
                } else null
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatUptime(startMs: Long): String {
    if (startMs <= 0L) return "00:00:00"
    val sec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b >= 1_073_741_824L -> String.format("%.2f GB", b / 1_073_741_824.0)
        b >= 1_048_576L -> String.format("%.1f MB", b / 1_048_576.0)
        b >= 1024L -> String.format("%.1f KB", b / 1024.0)
        else -> "$b B"
    }
}

private fun splitSpeedValue(bytesPerSec: Long): String {
    val b = bytesPerSec.coerceAtLeast(0L)
    return when {
        b >= 1_048_576L -> String.format("%.2f", b / 1_048_576.0)
        b >= 1024L -> String.format("%.1f", b / 1024.0)
        else -> "$b"
    }
}

private fun splitSpeedUnit(bytesPerSec: Long): String {
    val b = bytesPerSec.coerceAtLeast(0L)
    return when {
        b >= 1_048_576L -> "MB/s"
        b >= 1024L -> "KB/s"
        else -> "B/s"
    }
}

class MainActivity : ComponentActivity() {

    private var rootState by mutableStateOf(RootState.CHECKING)
    private var isProxyRunningState by mutableStateOf(false)
    private var isTransitioningState by mutableStateOf(false)
    private var runningSlotState by mutableStateOf<Int?>(null)
    private var activePidState by mutableStateOf<String?>(null)
    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var liveNetState by mutableStateOf(LiveNetworkState())

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastActiveIfaceName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val modes = display?.supportedModes
                val bestMode = modes?.maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Exception) {}

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncDaemonStatus()
                probeActiveNetwork()
            }
        })

        registerNetworkHandoverWatcher()
        probeActiveNetwork()

        lifecycleScope.launch(Dispatchers.IO) {
            val (state, _) = RootChecker.verifyRoot()
            withContext(Dispatchers.Main) { rootState = state }

            if (state == RootState.GRANTED) {
                if (!ProxyController.isBinaryExecutable()) {
                    ProxyController.extractBinaryDirectly(applicationContext)
                }
                val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)
                val pid = if (rSlot != null) ProxyController.getActivePid(ProfileManager.androidUserId, rSlot) else null
                withContext(Dispatchers.Main) {
                    if (rSlot != null) {
                        ProfileManager.activeSlot = rSlot
                        runningSlotState = rSlot
                        isProxyRunningState = true
                        activePidState = pid
                    } else {
                        runningSlotState = null
                        isProxyRunningState = false
                        activePidState = null
                    }
                }
            }
            loadInstalledApps()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ZuanAqua,
                    secondary = ZuanMint,
                    tertiary = ZuanIndigo,
                    background = ZuanDeepSpace,
                    surface = ZuanGlassBase,
                    onBackground = ZuanTextPrimary,
                    onSurface = ZuanTextPrimary
                )
            ) {
                MainScreen(
                    rootState = rootState,
                    isProxyActive = isProxyRunningState,
                    isTransitioning = isTransitioningState,
                    runningSlot = runningSlotState,
                    activePid = activePidState,
                    liveNet = liveNetState,
                    onRecheckRoot = { refreshRootStatus() },
                    installedApps = installedApps,
                    onSyncStatus = { syncDaemonStatus() },
                    onStartProxy = { settings, uids, onResult ->
                        isTransitioningState = true
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = ProxyController.startProxy(applicationContext, settings, uids)
                            val pid = if (result.success) ProxyController.getActivePid() else null
                            withContext(Dispatchers.Main) {
                                isTransitioningState = false
                                isProxyRunningState = result.success
                                runningSlotState = if (result.success) ProfileManager.activeSlot else null
                                activePidState = pid
                                onResult(result.success, result.errorMessage)
                            }
                        }
                    },
                    onStopProxy = { onResult ->
                        isTransitioningState = true
                        isProxyRunningState = false
                        runningSlotState = null
                        activePidState = null
                        lifecycleScope.launch(Dispatchers.IO) {
                            val success = ProxyController.stopProxy(applicationContext, ProfileManager.androidUserId)
                            withContext(Dispatchers.Main) {
                                isTransitioningState = false
                                onResult(success)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { cb ->
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
    }

    private fun registerNetworkHandoverWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = handleInterfaceHandoverIfNeeded()
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = handleInterfaceHandoverIfNeeded()
                override fun onLost(network: Network) = handleInterfaceHandoverIfNeeded()
            }
            networkCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {}
    }

    private fun handleInterfaceHandoverIfNeeded() {
        val prevIface = lastActiveIfaceName
        probeActiveNetwork()
        val newIface = liveNetState.interfaceName
        if (prevIface.isNotEmpty() && newIface.isNotEmpty() && prevIface != newIface) {
            lastActiveIfaceName = newIface
            lifecycleScope.launch(Dispatchers.IO) {
                val user = ProfileManager.androidUserId
                val rSlot = ProxyController.getRunningSlot(user)
                if (rSlot != null) {
                    ProxyController.refreshHandoverRules(user, rSlot)
                }
            }
        } else if (newIface.isNotEmpty()) {
            lastActiveIfaceName = newIface
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
        pRouteWhole: Boolean, pPkgs: Set<String>
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
                            pRouteWhole = b.optBoolean("route_whole_profile", true),
                            pPkgs = bPkgs
                        )
                    } else {
                        applySettingsToState(
                            "", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "",
                            ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, emptySet()
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
        strictWebRtcShield = true
    )

    var saveJob by remember { mutableStateOf<Job?>(null) }
    fun saveConfigForSlot(slot: Int, immediate: Boolean = false) {
        getSlotPrefs(slot).edit()
            .putString("proxy_type", proxyType.name)
            .putString("transport_mode", transportMode.name)
            .putString("ip_mode", ipMode.name)
            .putBoolean("strict_webrtc_shield", true)
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
                delay(if (attempt == 1) 180L else 800L)
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

    LaunchedEffect(isProxyActive, runningSlot) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                if (isProxyActive) {
                    val rSlot = runningSlot ?: activeSlot
                    val mixedPort = 10800 + (ProfileManager.androidUserId * 100) + (rSlot * 10) + 1
                    val proxyCfg = ProxyConfig.Builder()
                        .addProxyRule("127.0.0.1:$mixedPort")
                        .build()
                    androidx.webkit.ProxyController.getInstance().setProxyOverride(
                        proxyCfg,
                        { r -> r.run() },
                        {}
                    )
                } else {
                    androidx.webkit.ProxyController.getInstance().clearProxyOverride(
                        { r -> r.run() },
                        {}
                    )
                }
            }
        } catch (_: Exception) {}
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (fadeIn(animationSpec = tween(220)) +
                            scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.44f, stiffness = 300f)) +
                            slideInHorizontally(animationSpec = spring<IntOffset>(dampingRatio = 0.54f, stiffness = 310f)) { w: Int -> if (forward) w / 4 else -w / 4 } +
                            slideInVertically(animationSpec = spring<IntOffset>(dampingRatio = 0.54f, stiffness = 310f)) { h: Int -> h / 16 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(150)) +
                                    scaleOut(targetScale = 0.92f, animationSpec = tween(160)) +
                                    slideOutHorizontally(animationSpec = tween<IntOffset>(160)) { w: Int -> if (forward) -w / 6 else w / 6 }
                            )
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
                                        is TestResult.Success -> "${res.latencyMs} ms"
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
                            configuredHost = host
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
                                Toast.makeText(context, "Slot ${slotLabel(activeSlot)} cleared", Toast.LENGTH_SHORT).show()
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
                                if (isProxyActive) {
                                    val snap = getCurrentSettings()
                                    val uids = buildTargetUids()
                                    scope.launch(Dispatchers.IO) {
                                        ProxyController.applyLiveSettingsUpdate(snap, uids)
                                    }
                                }
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
    val uid = ProfileManager.androidUserId
    val infinite = rememberInfiniteTransition(label = "orbPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = if (isProxyActive) pulse else 1f
                        scaleY = if (isProxyActive) pulse else 1f
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isProxyActive) {
                                listOf(ZuanMint.copy(alpha = 0.38f), ZuanAqua.copy(alpha = 0.18f))
                            } else {
                                listOf(ZuanIndigo.copy(alpha = 0.38f), ZuanAqua.copy(alpha = 0.18f))
                            }
                        )
                    )
                    .border(
                        width = 1.3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.65f), if (isProxyActive) ZuanMint else ZuanAqua)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                VectorIcon(
                    type = IconType.SHIELD,
                    size = 19.dp,
                    tint = if (isProxyActive) ZuanMint else ZuanAqua
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NAMELESS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = ZuanTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = if (isProxyActive && runningSlot != null) {
                        "U#$uid • ${slotLabel(runningSlot)} ONLINE"
                    } else {
                        "U#$uid • ${slotLabel(activeSlot)} STANDBY"
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isProxyActive) ZuanMint else ZuanTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ZuanGlassElevated)
                    .border(1.1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(
                    type = if (liveNet.isCellular) IconType.CELLULAR else IconType.WIFI,
                    size = 12.dp,
                    tint = ZuanAqua
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = liveNet.transportLabel.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZuanTextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            val rootOk = rootState == RootState.GRANTED
            val rootCol = if (rootOk) ZuanMint else ZuanRose
            Row(
                modifier = Modifier
                    .liquidInteractive(pressScale = 1.08f, highlightColor = rootCol, onClick = onRecheckRoot)
                    .clip(CircleShape)
                    .background(rootCol.copy(alpha = 0.18f))
                    .border(1.1.dp, rootCol.copy(alpha = 0.58f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(rootCol, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (rootOk) "ROOT" else "NO ROOT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = rootCol,
                    maxLines = 1,
                    softWrap = false
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
    ZuanLiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (slot in 0 until ProfileManager.MAX_SLOTS) {
                val isSelected = slot == activeSlot
                val isRunningThis = isProxyActive && runningSlot == slot
                val isConfigured = configuredSlots.contains(slot)
                val proto = slotProtocolMap[slot]?.shortBadge ?: "SOCKS5"
                val slotWeight by animateFloatAsState(
                    targetValue = if (isSelected) 1.38f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f),
                    label = "slotWeight_$slot"
                )
                val slotBg by animateColorAsState(
                    targetValue = when {
                        isRunningThis -> ZuanMint.copy(alpha = 0.20f)
                        isSelected -> ZuanGlassElevated
                        else -> Color.Transparent
                    },
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
                    label = "slotBg_$slot"
                )
                val slotBorder by animateColorAsState(
                    targetValue = when {
                        isRunningThis -> ZuanMint.copy(alpha = 0.75f)
                        isSelected -> ZuanAqua.copy(alpha = 0.65f)
                        else -> Color(0xFF223044)
                    },
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
                    label = "slotBorder_$slot"
                )

                Box(
                    modifier = Modifier
                        .weight(slotWeight)
                        .liquidInteractive(
                            pressScale = 1.12f,
                            highlightColor = if (isRunningThis) ZuanMint else ZuanAqua,
                            onClick = { onSelectSlot(slot) }
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(slotBg)
                        .border(
                            width = if (isSelected || isRunningThis) 1.3.dp else 1.dp,
                            color = slotBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 7.dp else 5.dp)
                                .background(
                                    when {
                                        isRunningThis -> ZuanMint
                                        isSelected -> ZuanAqua
                                        isConfigured -> ZuanTextSecondary
                                        else -> ZuanTextMuted.copy(alpha = 0.45f)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = slotLabel(slot),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected || isRunningThis) ZuanTextPrimary else ZuanTextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (startOnBoot && bootSlot == slot) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    VectorIcon(IconType.ROCKET, size = 9.dp, tint = ZuanAmber)
                                }
                            }
                            Text(
                                text = if (isConfigured) proto else "EMPTY",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isRunningThis -> ZuanMint
                                    isSelected -> ZuanAqua
                                    else -> ZuanTextMuted
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZuanCapsuleBottomDock(
    selectedTab: Int,
    isProxyActive: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        Triple(0, IconType.POWER, "Tunnel"),
        Triple(1, IconType.GLOBE, "IPFighter"),
        Triple(2, IconType.SLIDERS, "Node"),
        Triple(3, IconType.APPS, "Routing"),
        Triple(4, IconType.TERMINAL, "Logs")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        ZuanLiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            accentGlow = if (isProxyActive) ZuanMint else ZuanAqua,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (idx, icon, label) ->
                    val isSelected = selectedTab == idx
                    val activeAccent = if (isProxyActive && idx == 0) ZuanMint else ZuanAqua
                    val tabWeight by animateFloatAsState(
                        targetValue = if (isSelected) 1.42f else 1f,
                        animationSpec = spring(dampingRatio = 0.46f, stiffness = 310f),
                        label = "navWeight_$idx"
                    )
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) ZuanGlassElevated else Color.Transparent,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
                        label = "navBg_$idx"
                    )
                    val pillBorder by animateColorAsState(
                        targetValue = if (isSelected) activeAccent.copy(alpha = 0.65f) else Color.Transparent,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
                        label = "navBorder_$idx"
                    )

                    Box(
                        modifier = Modifier
                            .weight(tabWeight)
                            .liquidInteractive(
                                pressScale = 1.14f,
                                highlightColor = activeAccent,
                                onClick = { onSelectTab(idx) }
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBg)
                            .border(
                                width = if (isSelected) 1.2.dp else 0.dp,
                                color = pillBorder,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            VectorIcon(
                                type = icon,
                                size = 17.dp,
                                tint = if (isSelected) activeAccent else ZuanTextSecondary
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ZuanTextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: REDESIGNED CENTERED BUBBLE REACTOR DASHBOARD (v13)
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
    onPingNode: () -> Unit,
    onToggleProxy: () -> Unit
) {
    val isSlotRunning = isProxyActive && runningSlot == activeSlot
    val heroAccent = when {
        isTransitioning -> ZuanAmber
        isSlotRunning -> ZuanMint
        else -> ZuanAqua
    }

    var uptimeText by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(isProxyActive, sessionStartMs) {
        while (isActive && isProxyActive) {
            uptimeText = formatUptime(sessionStartMs)
            delay(1000)
        }
        if (!isProxyActive) uptimeText = "00:00:00"
    }

    val infinite = rememberInfiniteTransition(label = "reactorRings")
    val ringAngleCw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (isSlotRunning) 6500 else 14000, easing = LinearEasing)),
        label = "cw"
    )
    val ringAngleCcw by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(if (isSlotRunning) 9500 else 18000, easing = LinearEasing)),
        label = "ccw"
    )
    val coreBreath by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val sonarWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Restart),
        label = "sonarWave"
    )

    val stunIp: String = remember(publicIpInfo?.ip, host, isProxyActive) {
        if (!isProxyActive) "Standby"
        else publicIpInfo?.ip ?: StunResponder.getExitIp().ifEmpty { host.ifEmpty { "Syncing..." } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. REDESIGNED CENTERED BUBBLE REACTOR HERO CARD
        ZuanLiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 30.dp,
            accentGlow = heroAccent,
            isHighlighted = isSlotRunning,
            contentPadding = PaddingValues(18.dp)
        ) {
            // Top Status & Uptime Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ZuanGlassElevated)
                        .border(1.1.dp, heroAccent.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer {
                                scaleX = if (isSlotRunning) coreBreath else 1f
                                scaleY = if (isSlotRunning) coreBreath else 1f
                            }
                            .background(heroAccent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isTransitioning -> "SWITCHING KERNEL..."
                            isSlotRunning -> "TUNNEL LIVE • ${slotLabel(activeSlot)}"
                            isProxyActive && runningSlot != null -> "${slotLabel(runningSlot)} ACTIVE"
                            else -> "STANDBY • ${slotLabel(activeSlot)}"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = heroAccent,
                        maxLines = 1
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uptimeText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isProxyActive) ZuanMint else ZuanTextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ZuanGlassElevated)
                            .border(1.dp, Color(0xFF2E4059), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = proxyType.shortBadge,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZuanAqua
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Centered Plump Squishy Bubble Power Reactor
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .liquidInteractive(
                            pressScale = 1.16f,
                            highlightColor = heroAccent,
                            onClick = onToggleProxy
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f

                        // Live expanding sonar waves when active or transitioning
                        if (isSlotRunning || isTransitioning) {
                            val r1 = r * (0.60f + 0.38f * sonarWave)
                            drawCircle(
                                color = heroAccent.copy(alpha = (1f - sonarWave) * 0.36f),
                                radius = r1,
                                center = c,
                                style = Stroke(width = 2.2.dp.toPx())
                            )
                            val wave2 = (sonarWave + 0.5f) % 1f
                            val r2 = r * (0.60f + 0.38f * wave2)
                            drawCircle(
                                color = heroAccent.copy(alpha = (1f - wave2) * 0.26f),
                                radius = r2,
                                center = c,
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }

                        rotate(ringAngleCw, pivot = c) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Color.Transparent, heroAccent, Color.White.copy(alpha = 0.8f), Color.Transparent)
                                ),
                                startAngle = 0f,
                                sweepAngle = 240f,
                                useCenter = false,
                                topLeft = Offset(c.x - r * 0.88f, c.y - r * 0.88f),
                                size = Size(r * 1.76f, r * 1.76f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        rotate(ringAngleCcw, pivot = c) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Color.Transparent, ZuanAqua.copy(alpha = 0.7f), heroAccent, Color.Transparent)
                                ),
                                startAngle = 45f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(c.x - r * 0.74f, c.y - r * 0.74f),
                                size = Size(r * 1.48f, r * 1.48f),
                                style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .graphicsLayer {
                                scaleX = coreBreath
                                scaleY = coreBreath
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isSlotRunning) {
                                        listOf(Color(0xFF1A3A3A), Color(0xFF112629))
                                    } else {
                                        listOf(ZuanGlassElevated, ZuanGlassBase)
                                    }
                                )
                            )
                            .border(2.dp, heroAccent.copy(alpha = if (isSlotRunning) 0.85f else 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            VectorIcon(
                                type = IconType.POWER,
                                size = 28.dp,
                                tint = heroAccent,
                                strokeWidth = 2.6f
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isTransitioning -> "WAIT"
                                    isSlotRunning -> "ACTIVE"
                                    else -> "START"
                                },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZuanTextPrimary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        isTransitioning -> "Updating Netfilter & Policy Routing..."
                        isSlotRunning -> "Zero-Leak Root Tunnel Active"
                        else -> "Tap Bubble Reactor to Connect"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = ZuanTextPrimary
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = if (host.isNotBlank()) "$host:$port • ${liveNet.transportLabel} (${liveNet.interfaceName})"
                    else "No node configured in ${slotLabel(activeSlot)} — tap to configure",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (host.isNotBlank()) ZuanAqua else ZuanAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenNodeTab() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3 Bouncy Quick-Action Bubble Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardBubbleActionButton(
                        modifier = Modifier.weight(1f),
                        icon = IconType.GLOBE,
                        label = "IP & Leaks",
                        accent = ZuanMint,
                        onClick = onOpenIpFighterTab
                    )
                    DashboardBubbleActionButton(
                        modifier = Modifier.weight(1f),
                        icon = IconType.BOLT,
                        label = if (isTesting) "Pinging..." else (testLatency ?: "Ping Node"),
                        accent = ZuanAqua,
                        onClick = onPingNode
                    )
                    DashboardBubbleActionButton(
                        modifier = Modifier.weight(1f),
                        icon = IconType.SLIDERS,
                        label = "Edit Node",
                        accent = ZuanAmber,
                        onClick = onOpenNodeTab
                    )
                }
            }
        }

        // 2. EXIT IDENTITY & WEBRTC/DNS SHIELD CARD
        ZuanLiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 26.dp,
            accentGlow = if (isProxyActive) ZuanMint else ZuanAqua,
            contentPadding = PaddingValues(15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(ZuanGlassElevated)
                            .border(1.dp, Color(0xFF2B3C54), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = publicIpInfo?.flagEmoji ?: if (isProxyActive) "🇺🇸" else "🛡️",
                            fontSize = 21.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(11.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = when {
                                !isProxyActive -> "Direct Connection"
                                isFetchingIp && publicIpInfo == null -> "Verifying Exit IP..."
                                publicIpInfo != null -> publicIpInfo.ip
                                else -> host.ifEmpty { "Awaiting Node" }
                            },
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(180)) +
                                    slideInVertically(animationSpec = spring<IntOffset>(dampingRatio = 0.60f, stiffness = 340f)) { h: Int -> h / 2 })
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(130)) +
                                            slideOutVertically(animationSpec = tween<IntOffset>(130)) { h: Int -> -h / 2 }
                                    )
                            },
                            label = "exitIpTransition"
                        ) { ipLabel ->
                            Text(
                                text = ipLabel,
                                fontSize = 17.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isProxyActive) ZuanMint else ZuanTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = when {
                                !isProxyActive -> "Start tunnel to mask IP, DNS & WebRTC"
                                publicIpInfo != null -> {
                                    val ispPart = if (publicIpInfo.isp.isNotBlank()) " • ${publicIpInfo.isp}" else ""
                                    "${publicIpInfo.country}$ispPart"
                                }
                                else -> "Resolving exit geo-location..."
                            },
                            fontSize = 11.sp,
                            color = ZuanTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .liquidInteractive(pressScale = 1.15f, highlightColor = ZuanAqua, onClick = onRefreshIp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(ZuanGlassElevated)
                        .border(1.dp, Color(0xFF2B3C54), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    VectorIcon(IconType.REFRESH, size = 16.dp, tint = ZuanAqua)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ZuanMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "DNS TUNNEL",
                    value = if (isProxyActive) "FakeIP 0% Leak" else "System",
                    accent = if (isProxyActive) ZuanMint else ZuanTextSecondary
                )
                ZuanMetricPill(
                    modifier = Modifier.weight(1.15f),
                    label = "WEBRTC STUN",
                    value = stunIp,
                    accent = if (isProxyActive) ZuanAqua else ZuanTextSecondary
                )
                ZuanMetricPill(
                    modifier = Modifier.weight(0.90f),
                    label = "PID / IFACE",
                    value = activePid ?: liveNet.interfaceName,
                    accent = ZuanAmber
                )
            }
        }

        // 3. LIVE ANIMATED BANDWIDTH SPARKLINE BENTO ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZuanSparklineBentoCard(
                modifier = Modifier.weight(1f),
                title = "Download",
                icon = IconType.DOWNLOAD,
                rate = currentRxRate,
                total = totalRxBytes,
                history = rxHistory,
                accent = ZuanMint,
                isProxyActive = isProxyActive
            )
            ZuanSparklineBentoCard(
                modifier = Modifier.weight(1f),
                title = "Upload",
                icon = IconType.UPLOAD,
                rate = currentTxRate,
                total = totalTxBytes,
                history = txHistory,
                accent = ZuanAqua,
                isProxyActive = isProxyActive
            )
        }
    }
}

@Composable
private fun DashboardBubbleActionButton(
    modifier: Modifier = Modifier,
    icon: IconType,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .liquidInteractive(
                pressScale = 1.12f,
                highlightColor = accent,
                onClick = onClick
            )
            .clip(RoundedCornerShape(16.dp))
            .background(ZuanGlassElevated)
            .border(1.dp, Color(0xFF28384E), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            VectorIcon(type = icon, size = 14.dp, tint = accent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZuanTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ZuanMetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .liquidInteractive(
                enabled = onClick != null,
                pressScale = 1.09f,
                highlightColor = accent,
                onClick = onClick
            )
            .clip(RoundedCornerShape(14.dp))
            .background(ZuanGlassElevated)
            .border(1.dp, Color(0xFF253449), RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = ZuanTextMuted,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ZuanSparklineBentoCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: IconType,
    rate: Long,
    total: Long,
    history: List<Long>,
    accent: Color,
    isProxyActive: Boolean
) {
    val infiniteWave = rememberInfiniteTransition(label = "waveAnim")
    val wavePhase by infiniteWave.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
        label = "wavePhase"
    )

    ZuanLiquidGlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        accentGlow = accent,
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
                fontSize = 19.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = ZuanTextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = splitSpeedUnit(rate),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Text(
            text = "Total: ${formatBytes(total)}",
            fontSize = 10.5.sp,
            color = ZuanTextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            val w = size.width
            val h = size.height
            val maxVal = (history.maxOrNull() ?: 1024L).coerceAtLeast(1024L)
            val pts = history.mapIndexed { idx, v ->
                val x = idx * (w / (history.size - 1).coerceAtLeast(1))
                val baseNorm = (v.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val liveRipple = if (isProxyActive) {
                    0.08f * (0.5f + 0.5f * sin(wavePhase + idx * 0.45f))
                } else 0.03f * (0.5f + 0.5f * sin(wavePhase + idx * 0.35f))
                val norm = (baseNorm + liveRipple).coerceIn(0.05f, 0.96f)
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
                        colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = accent,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val lastPt = pts.last()
                drawCircle(
                    color = accent,
                    radius = 3.2.dp.toPx(),
                    center = lastPt
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: LIVE IPFIGHTER + RESTORED WEBRTC & DNS LEAK CHECKER
// -------------------------------------------------------------

private enum class InspectorTarget(val title: String, val url: String) {
    IP_FIGHTER("IPFighter", "https://ipfighter.com/"),
    WEBRTC_LEAK("WebRTC Check", "https://browserleaks.com/webrtc"),
    DNS_LEAK("DNS Check", "https://browserleaks.com/dns")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveEphemeralIpFighterTab(
    isProxyActive: Boolean,
    runningSlot: Int?,
    publicIpInfo: GeoIpResult?,
    configuredHost: String
) {
    var selectedTarget by remember { mutableStateOf(InspectorTarget.IP_FIGHTER) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var loadProgress by remember { mutableIntStateOf(0) }

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

    val verifiedExitIp: String = remember(isProxyActive, publicIpInfo?.ip, configuredHost) {
        if (!isProxyActive) ""
        else {
            val cleanCfg = ConfigGenerator.cleanHost(configuredHost)
            publicIpInfo?.ip?.takeIf { it.isNotBlank() }
                ?: StunResponder.getExitIp().takeIf { it.isNotBlank() }
                ?: (if (IptablesManager.isValidIpv4(cleanCfg)) cleanCfg else "")
        }
    }

    val shieldScript = remember(targetTz, verifiedExitIp, isProxyActive) {
        """
        (function() {
            try {
                if ($isProxyActive && '$targetTz' !== '') {
                    const origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
                    Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                        const res = origResolved.call(this);
                        res.timeZone = '$targetTz';
                        return res;
                    };
                }
                if ($isProxyActive && '$verifiedExitIp' !== '' && window.RTCPeerConnection) {
                    const exitIp = '$verifiedExitIp';
                    const synthLine = 'candidate:2999745851 1 udp 1686052607 ' + exitIp + ' 54832 typ srflx raddr 0.0.0.0 rport 0 generation 0';
                    const rewriteLine = function(s) {
                        if (!s || typeof s !== 'string') return s;
                        return s.replace(/(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/g, exitIp)
                                .replace(/typ host/g, 'typ srflx');
                    };
                    if (window.RTCIceCandidate && window.RTCIceCandidate.prototype) {
                        const cDesc = Object.getOwnPropertyDescriptor(window.RTCIceCandidate.prototype, 'candidate');
                        if (cDesc && cDesc.get) {
                            Object.defineProperty(window.RTCIceCandidate.prototype, 'candidate', {
                                get: function() { return rewriteLine(cDesc.get.call(this)); }
                            });
                        }
                        const aDesc = Object.getOwnPropertyDescriptor(window.RTCIceCandidate.prototype, 'address');
                        if (aDesc && aDesc.get) {
                            Object.defineProperty(window.RTCIceCandidate.prototype, 'address', {
                                get: function() { return exitIp; }
                            });
                        }
                    }
                    const OrigPC = window.RTCPeerConnection;
                    window.RTCPeerConnection = function(config, constraints) {
                        const pc = new OrigPC(config, constraints);
                        let emitted = false;
                        const makeSynthEvent = function() {
                            let candObj = {
                                candidate: synthLine,
                                sdpMid: '0',
                                sdpMLineIndex: 0,
                                address: exitIp,
                                ip: exitIp,
                                type: 'srflx',
                                toJSON: function() { return { candidate: synthLine, sdpMid: '0', sdpMLineIndex: 0 }; }
                            };
                            try {
                                candObj = new window.RTCIceCandidate({ candidate: synthLine, sdpMid: '0', sdpMLineIndex: 0 });
                            } catch (e) {}
                            return { candidate: candObj, target: pc, currentTarget: pc, type: 'icecandidate' };
                        };
                        const origAdd = pc.addEventListener.bind(pc);
                        pc.addEventListener = function(type, listener, options) {
                            if (type === 'icecandidate' && typeof listener === 'function') {
                                return origAdd(type, function(ev) {
                                    if (ev && ev.candidate && ev.candidate.candidate) {
                                        emitted = true;
                                        return listener.call(this, ev);
                                    }
                                    if (ev && !ev.candidate && !emitted) {
                                        emitted = true;
                                        try { listener.call(this, makeSynthEvent()); } catch (e) {}
                                    }
                                    return listener.call(this, ev);
                                }, options);
                            }
                            return origAdd(type, listener, options);
                        };
                        const origDesc = Object.getOwnPropertyDescriptor(OrigPC.prototype, 'onicecandidate');
                        if (origDesc && origDesc.set) {
                            Object.defineProperty(pc, 'onicecandidate', {
                                configurable: true,
                                enumerable: true,
                                get: function() { return origDesc.get ? origDesc.get.call(this) : null; },
                                set: function(fn) {
                                    if (typeof fn !== 'function') {
                                        origDesc.set.call(this, fn);
                                        return;
                                    }
                                    origDesc.set.call(this, function(ev) {
                                        if (ev && ev.candidate && ev.candidate.candidate) {
                                            emitted = true;
                                            return fn.call(this, ev);
                                        }
                                        if (ev && !ev.candidate && !emitted) {
                                            emitted = true;
                                            try { fn.call(this, makeSynthEvent()); } catch (e) {}
                                        }
                                        return fn.call(this, ev);
                                    });
                                }
                            });
                        }
                        return pc;
                    };
                    window.RTCPeerConnection.prototype = OrigPC.prototype;
                    window.webkitRTCPeerConnection = window.RTCPeerConnection;
                }
            } catch (e) {}
        })();
        """.trimIndent()
    }

    ZuanLiquidGlassCard(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(10.dp)
    ) {
        // Top status + Fresh Check Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(ZuanGlassElevated)
                    .border(1.1.dp, ZuanAqua.copy(alpha = 0.50f), CircleShape)
                    .padding(vertical = 8.dp, horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(IconType.GLOBE, size = 14.dp, tint = ZuanAqua)
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (isProxyActive && verifiedExitIp.isNotEmpty()) {
                        "${selectedTarget.title} • $verifiedExitIp"
                    } else {
                        "Live ${selectedTarget.title} Inspector"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZuanTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .liquidInteractive(
                        pressScale = 1.13f,
                        highlightColor = ZuanMint,
                        onClick = {
                            WebStorage.getInstance().deleteAllData()
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            reloadNonce++
                        }
                    )
                    .clip(CircleShape)
                    .background(ZuanMint.copy(alpha = 0.20f))
                    .border(1.1.dp, ZuanMint.copy(alpha = 0.65f), CircleShape)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(IconType.REFRESH, size = 14.dp, tint = ZuanMint)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Fresh Check",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZuanMint,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Restored 3-Mode Selector Bar: IPFighter | WebRTC Check | DNS Check
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            InspectorTarget.entries.forEach { target ->
                val isSelected = selectedTarget == target
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) ZuanGlassElevated else ZuanGlassBottom,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
                    label = "inspectorTabBg_${target.name}"
                )
                val pillBorder by animateColorAsState(
                    targetValue = if (isSelected) ZuanMint else Color(0xFF243246),
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 280f),
                    label = "inspectorTabBorder_${target.name}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .liquidInteractive(
                            pressScale = 1.11f,
                            highlightColor = if (isSelected) ZuanMint else ZuanAqua,
                            onClick = {
                                if (selectedTarget != target) {
                                    selectedTarget = target
                                    WebStorage.getInstance().deleteAllData()
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                    reloadNonce++
                                }
                            }
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(if (isSelected) 1.3.dp else 1.dp, pillBorder, RoundedCornerShape(14.dp))
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = target.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) ZuanMint else ZuanTextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 4.dp),
                color = ZuanMint,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF243246), RoundedCornerShape(18.dp))
        ) {
            var webkitProxySynced by remember(isProxyActive, runningSlot) { mutableStateOf(false) }
            LaunchedEffect(isProxyActive, runningSlot) {
                try {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                        if (isProxyActive) {
                            val rSlot = runningSlot ?: ProfileManager.activeSlot
                            val mixedPort = 10800 + (ProfileManager.androidUserId * 100) + (rSlot * 10) + 1
                            val proxyCfg = ProxyConfig.Builder()
                                .addProxyRule("127.0.0.1:$mixedPort")
                                .build()
                            androidx.webkit.ProxyController.getInstance().setProxyOverride(
                                proxyCfg,
                                { r -> r.run() },
                                { webkitProxySynced = true }
                            )
                        } else {
                            androidx.webkit.ProxyController.getInstance().clearProxyOverride(
                                { r -> r.run() },
                                { webkitProxySynced = true }
                            )
                        }
                    } else {
                        webkitProxySynced = true
                    }
                } catch (_: Exception) {
                    webkitProxySynced = true
                }
            }

            key(isProxyActive, runningSlot, webkitProxySynced, reloadNonce, selectedTarget) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            clearCache(true)
                            clearHistory()
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
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
                                    view?.evaluateJavascript(shieldScript, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    loadProgress = 100
                                    view?.evaluateJavascript(shieldScript, null)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }
                            loadUrl(selectedTarget.url)
                        }
                    },
                    onRelease = { wv ->
                        wv.stopLoading()
                        wv.clearCache(true)
                        wv.destroy()
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: CLEAN BUBBLY NODE STUDIO (NO USELESS TRANSPORT/IP OPTIONS)
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
                        Text("URI Import & Socket Probe", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZuanTextPrimary)
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
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.1.dp, Color.White.copy(alpha = 0.26f))
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
                    shape = RoundedCornerShape(16.dp),
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
                        text = "Slot ${slotLabel(activeSlot)} Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZuanTextPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .liquidInteractive(pressScale = 1.08f, highlightColor = ZuanRose, onClick = onResetSlot)
                        .clip(CircleShape)
                        .background(ZuanRose.copy(alpha = 0.16f))
                        .border(1.dp, ZuanRose.copy(alpha = 0.48f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VectorIcon(IconType.TRASH, size = 13.dp, tint = ZuanRose)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset ${slotLabel(activeSlot)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZuanRose)
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
                UI_PROTOCOLS.forEach { t ->
                    FilterChip(
                        selected = proxyType == t,
                        onClick = { onProxyTypeChange(t); onConfigChanged() },
                        label = { Text(t.displayTitle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape,
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
                    shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.GLOBE, size = 16.dp, tint = ZuanTextSecondary) },
                        label = { Text("TLS SNI") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = realityPublicKey,
                            onValueChange = { onRealityPublicKeyChange(it); onConfigChanged() },
                            label = { Text("REALITY Public Key") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = realityShortId,
                            onValueChange = { onRealityShortIdChange(it); onConfigChanged() },
                            label = { Text("Short ID") },
                            modifier = Modifier.weight(0.55f),
                            shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        leadingIcon = { VectorIcon(IconType.GLOBE, size = 16.dp, tint = ZuanTextSecondary) },
                        label = { Text("TLS SNI") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                else -> {}
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
                            text = if (startOnBoot) "Armed for Slot ${slotLabel(bootSlot)} (/data/adb/service.d)" else "Manual launch only",
                            fontSize = 11.sp,
                            color = ZuanTextSecondary
                        )
                    }
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZuanAmber)
                )
            }

            AnimatedVisibility(visible = startOnBoot) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (s in 0 until ProfileManager.MAX_SLOTS) {
                            FilterChip(
                                selected = bootSlot == s,
                                onClick = { onBootSlotChange(s) },
                                label = { Text(slotLabel(s), fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ZuanAmber,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
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
                    ZuanIconBadge(icon = IconType.HOTSPOT, accent = ZuanAqua, size = 36.dp, iconSize = 18.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Wi-Fi Hotspot & USB Tethering", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                        Text("Routes Hotspot TCP, DNS & US STUN WebRTC", fontSize = 11.sp, color = ZuanTextSecondary)
                    }
                }
                Switch(
                    checked = routeHotspot,
                    onCheckedChange = onRouteHotspotChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZuanAqua)
                )
            }
        }

        ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.1.dp, Color.White.copy(alpha = 0.24f))
                ) {
                    VectorIcon(IconType.UPLOAD, size = 15.dp, tint = ZuanAqua)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export 5 Slots", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                }

                Button(
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZuanIndigo, contentColor = Color.White)
                ) {
                    VectorIcon(IconType.DOWNLOAD, size = 15.dp, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text("Route Entire User #$uid Profile", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                        Text(
                            text = if (routeWholeProfile) "All apps in U#$uid tunnelled" else "Per-app split tunnel (${selectedPackages.size} selected)",
                            fontSize = 11.sp,
                            color = ZuanTextSecondary
                        )
                    }
                }
                Switch(
                    checked = routeWholeProfile,
                    onCheckedChange = onRouteWholeProfileChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZuanMint)
                )
            }

            if (!routeWholeProfile) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Select All", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(installedApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) ZuanAqua.copy(alpha = 0.16f) else ZuanGlassElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) ZuanAqua.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onToggleApp(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary, maxLines = 1)
                                Text("${app.packageName} • UID ${app.uid}", fontSize = 10.sp, color = ZuanTextSecondary, maxLines = 1)
                            }
                            Checkbox(checked = isSelected, onCheckedChange = { onToggleApp(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: ZUAN LIQUID GLASS KERNEL LOGS & NETFILTER CONSOLE
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

    ZuanLiquidGlassCard(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuanIconBadge(
                    icon = if (showKernelAuditMode) IconType.SHIELD else IconType.TERMINAL,
                    accent = if (showKernelAuditMode) ZuanMint else ZuanAqua,
                    size = 34.dp,
                    iconSize = 17.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (showKernelAuditMode) "Kernel Netfilter Audit" else "Sing-Box Kernel Stream",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZuanTextPrimary
                    )
                    Text(
                        text = "Slot ${slotLabel(runningSlot ?: activeSlot)} • ${if (liveLogStreaming) "Live 1.4s" else "Paused"}",
                        fontSize = 10.5.sp,
                        color = ZuanTextSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (showKernelAuditMode) ZuanMint.copy(alpha = 0.20f) else ZuanGlassElevated)
                        .border(1.dp, if (showKernelAuditMode) ZuanMint else Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .clickable { onToggleAuditMode() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (showKernelAuditMode) "Logs" else "Audit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showKernelAuditMode) ZuanMint else ZuanTextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZuanGlassElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .clickable {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("nameless_logs", logs))
                            Toast.makeText(context, "Copied logs", Toast.LENGTH_SHORT).show()
                        }
                        .padding(6.dp)
                ) {
                    VectorIcon(IconType.COPY, size = 15.dp, tint = ZuanAqua)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZuanGlassElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .clickable { onRefresh() }
                        .padding(6.dp)
                ) {
                    VectorIcon(IconType.REFRESH, size = 15.dp, tint = ZuanMint)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZuanRose.copy(alpha = 0.16f))
                        .border(1.dp, ZuanRose.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
                        .clickable { onClear() }
                        .padding(6.dp)
                ) {
                    VectorIcon(IconType.TRASH, size = 15.dp, tint = ZuanRose)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF04070E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ZuanTextPrimary
            )
        }
    }
}
