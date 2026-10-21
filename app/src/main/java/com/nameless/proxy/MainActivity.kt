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
import androidx.webkit.ProxyConfig
import androidx.webkit.WebViewFeature
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import org.json.JSONObject
import java.io.File
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

private val ProxyType.displayTitle: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "SOCKS5"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "Shadowsocks"
        ProxyType.VLESS -> "VLESS"
        ProxyType.VMESS -> "VMess"
        ProxyType.TROJAN -> "Trojan"
        ProxyType.HYSTERIA2 -> "Hysteria 2"
        ProxyType.TUIC -> "TUIC v5"
        ProxyType.SOCKS4 -> "SOCKS4"
    }

private val ProxyType.shortBadge: String
    get() = when (this) {
        ProxyType.SOCKS5 -> "SOCKS5"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "SS"
        ProxyType.VLESS -> "VLESS"
        ProxyType.VMESS -> "VMESS"
        ProxyType.TROJAN -> "TROJAN"
        ProxyType.HYSTERIA2 -> "HY2"
        ProxyType.TUIC -> "TUIC"
        ProxyType.SOCKS4 -> "SOCKS4"
    }

private fun ProxyType.smartEngineSummary(host: String): String {
    val stackLabel = if (IptablesManager.isValidIpv6(host)) "Auto IPv6 Stack" else "Auto IPv4 Zero-Leak"
    return when (this) {
        ProxyType.SOCKS5,
        ProxyType.SHADOWSOCKS,
        ProxyType.VLESS,
        ProxyType.VMESS,
        ProxyType.TROJAN -> "$displayTitle • TCP+UDP • $stackLabel • FakeIP+STUN"
        ProxyType.HYSTERIA2,
        ProxyType.TUIC -> "$displayTitle • QUIC/UDP • $stackLabel • FakeIP+STUN"
        ProxyType.HTTP,
        ProxyType.SOCKS4 -> "$displayTitle • TCP • $stackLabel • FakeIP+STUN"
    }
}

private fun slotLabel(slotIndex: Int): String = "P${slotIndex + 1}"

// -------------------------------------------------------------
// 120FPS iOS 26 / KYANT0 BUBBLY LIQUID GLASS ENGINE
// Zero Per-Card Infinite Redraws (100% GPU Hardware Layer Cached) +
// 3D Convex Pillow Glass Lenses + Underdamped Jelly Bounce Physics
// -------------------------------------------------------------

private val ZuanDeepSpace = Color(0xFF030712)
private val ZuanGlassBase = Color(0x240A162C)
private val ZuanGlassElevated = Color(0x36122444)
private val ZuanAqua = Color(0xFF00E5FF)
private val ZuanMint = Color(0xFF00F5A0)
private val ZuanEmerald = Color(0xFF10B981)
private val ZuanViolet = Color(0xFFA855F7)
private val ZuanIndigo = Color(0xFF6366F1)
private val ZuanAmber = Color(0xFFFFB800)
private val ZuanRose = Color(0xFFFF3366)
private val ZuanTextPrimary = Color(0xFFF8FAFC)
private val ZuanTextSecondary = Color(0xFFC2D6F0)
private val ZuanTextMuted = Color(0xFF7A92B2)

private const val LIQUID_AURORA_AGSL = """
    uniform float2 resolution;
    uniform float time;
    uniform float activeFactor;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(resolution, float2(1.0, 1.0));
        float2 p = uv * 2.0 - 1.0;
        p.x *= resolution.x / max(resolution.y, 1.0);

        float w1 = sin(p.x * 2.1 + time * 0.85) * cos(p.y * 1.8 - time * 0.70);
        float w2 = sin((p.x * 1.3 - p.y * 1.7) * 2.0 - time * 1.05) * 0.58;
        float w3 = cos(length(p - float2(sin(time * 0.50) * 0.38, cos(time * 0.42) * 0.38)) * 3.4 - time * 1.25);
        float fluid = (w1 + w2 + w3) * 0.34;

        half3 baseCol = half3(0.014, 0.028, 0.072);
        half3 idleA = half3(0.0, 0.88, 1.0);
        half3 idleB = half3(0.38, 0.35, 0.98);
        half3 idleC = half3(0.64, 0.33, 0.97);

        half3 actA = half3(0.0, 0.96, 0.64);
        half3 actB = half3(0.0, 0.90, 1.0);
        half3 actC = half3(0.32, 0.44, 0.98);

        half3 colA = mix(idleA, actA, half(activeFactor));
        half3 colB = mix(idleB, actB, half(activeFactor));
        half3 colC = mix(idleC, actC, half(activeFactor));

        float2 c1 = float2(0.22 + 0.22 * cos(time * 0.55), 0.16 + 0.14 * sin(time * 0.48));
        float2 c2 = float2(0.80 + 0.18 * sin(time * 0.45), 0.50 + 0.16 * cos(time * 0.52));
        float2 c3 = float2(0.28 + 0.22 * sin(time * 0.40), 0.84 + 0.12 * cos(time * 0.44));

        float d1 = exp(-2.35 * length(uv - c1));
        float d2 = exp(-2.65 * length(uv - c2));
        float d3 = exp(-2.75 * length(uv - c3));

        half3 glow = colA * half(d1 * 0.44) + colB * half(d2 * 0.40) + colC * half(d3 * 0.34);
        float caustic = smoothstep(0.15, 0.85, 0.5 + 0.5 * fluid);
        glow += mix(colB, colA, half(caustic)) * half(caustic * 0.16);

        return half4(clamp(baseCol + glow, half3(0.0), half3(1.0)), 1.0);
    }
"""

@Composable
fun ZuanLiquidMeshBackground(
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "liquidBg")
    val timeSec by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 62.83185f,
        animationSpec = infiniteRepeatable(tween(48000, easing = LinearEasing)),
        label = "shaderTime"
    )
    val activeFactor by animateFloatAsState(
        targetValue = if (isProxyActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 140f),
        label = "activeFactor"
    )

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(LIQUID_AURORA_AGSL)
        } else null
    }

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.99f }) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            shader.setFloatUniform("resolution", w, h)
            shader.setFloatUniform("time", timeSec)
            shader.setFloatUniform("activeFactor", activeFactor)
            drawRect(brush = ShaderBrush(shader))
        } else {
            val c1 = Offset(w * (0.25f + 0.20f * cos(timeSec * 0.5f)), h * (0.18f + 0.12f * sin(timeSec * 0.5f)))
            val c2 = Offset(w * (0.78f + 0.18f * sin(timeSec * 0.45f)), h * (0.52f + 0.14f * cos(timeSec * 0.45f)))
            val pCol = if (isProxyActive) ZuanMint else ZuanAqua
            val sCol = if (isProxyActive) ZuanAqua else ZuanIndigo
            drawCircle(
                brush = Brush.radialGradient(listOf(pCol.copy(alpha = 0.34f), Color.Transparent), c1, w * 0.90f),
                radius = w * 0.90f,
                center = c1
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(sCol.copy(alpha = 0.28f), Color.Transparent), c2, w * 0.85f),
                radius = w * 0.85f,
                center = c2
            )
        }
    }
}

/**
 * Staggered iOS 26 Bubbly Jelly-Bounce Entrance Animation for Every Tile.
 * Executes 100% on the GPU RenderThread via graphicsLayer!
 */
@Composable
fun IosBubblyTileEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 42L).coerceAtMost(210L))
        appeared = true
    }
    val scaleX by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 340f),
        label = "tileScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 320f),
        label = "tileScaleY"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 42f,
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 320f),
        label = "tileOffsetY"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tileAlpha"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.scaleX = scaleX
            this.scaleY = scaleY
            this.translationY = offsetY
            this.alpha = alpha
        }
    ) {
        content()
    }
}

/**
 * iOS 26 / Kyant0 Tactile Bubbly Jelly-Squish, 3D Perspective Tilt & Refractive Bloom Modifier.
 */
@Composable
fun Modifier.liquidInteractive(
    enabled: Boolean = true,
    pressScale: Float = 1.065f,
    highlightColor: Color = ZuanAqua,
    onClick: (() -> Unit)? = null
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Unspecified) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(Size.Zero) }

    // Genuine underdamped iOS jelly spring (dampingRatio = 0.34f gives a visible bubbly bounce!)
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.34f, stiffness = 430f),
        label = "liquidPress"
    )
    val animDragX by animateFloatAsState(
        targetValue = if (isPressed) 16f * tanh(0.055f * dragDelta.x / 16f) else 0f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 360f),
        label = "dragX"
    )
    val animDragY by animateFloatAsState(
        targetValue = if (isPressed) 16f * tanh(0.055f * dragDelta.y / 16f) else 0f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 360f),
        label = "dragY"
    )

    val pointerMod = if (enabled) {
        Modifier.pointerInput(onClick) {
            boxSize = Size(size.width.toFloat(), size.height.toFloat())
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
            // Anisotropic iOS jelly squish: widens slightly more horizontally while blooming
            val deltaScale = (pressScale - 1f) * pressProgress
            val stretchX = kotlin.math.abs(animDragX) * 0.005f
            val stretchY = kotlin.math.abs(animDragY) * 0.005f
            scaleX = 1f + (deltaScale * 1.12f) + stretchX - (stretchY * 0.4f)
            scaleY = 1f + (deltaScale * 0.88f) + stretchY - (stretchX * 0.4f)
            translationX = animDragX
            translationY = animDragY

            // Subtle 3D Liquid Glass lens tilt toward the finger
            if (boxSize.width > 0f && boxSize.height > 0f && touchPos != Offset.Unspecified) {
                val normX = ((touchPos.x / boxSize.width) - 0.5f).coerceIn(-0.5f, 0.5f)
                val normY = ((touchPos.y / boxSize.height) - 0.5f).coerceIn(-0.5f, 0.5f)
                rotationY = normX * 5.5f * pressProgress
                rotationX = -normY * 5.5f * pressProgress
                cameraDistance = 14f * density
            }
        }
        .then(pointerMod)
        .then(clickMod)
        .drawWithContent {
            drawContent()
            if (pressProgress > 0.01f) {
                val center = if (touchPos != Offset.Unspecified) touchPos else Offset(size.width / 2f, size.height / 2f)
                val radius = size.maxDimension * 0.88f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.34f * pressProgress),
                            highlightColor.copy(alpha = 0.22f * pressProgress),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    ),
                    radius = radius.coerceAtLeast(1f),
                    center = center
                )
            }
        }
}

/**
 * 120fps iOS 26 Convex Pillow Liquid Glass Tile:
 * - Zero infinite redraws when idle (100% GPU hardware layer cached!)
 * - 3D inner convex cushion bevel + bright top specular meniscus + bottom counter-glow
 * - Underdamped iOS jelly-bounce spring when tapped or highlighted
 */
@Composable
fun ZuanLiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    accentGlow: Color = ZuanAqua,
    isHighlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val highlightScaleX by animateFloatAsState(
        targetValue = if (isHighlighted) 1.016f else 1f,
        animationSpec = spring(dampingRatio = 0.36f, stiffness = 360f),
        label = "cardHighlightScaleX"
    )
    val highlightScaleY by animateFloatAsState(
        targetValue = if (isHighlighted) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.36f, stiffness = 360f),
        label = "cardHighlightScaleY"
    )

    val rimBrush = Brush.linearGradient(
        colors = if (isHighlighted) {
            listOf(
                Color.White.copy(alpha = 0.90f),
                accentGlow.copy(alpha = 0.78f),
                ZuanIndigo.copy(alpha = 0.42f),
                accentGlow.copy(alpha = 0.65f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.60f),
                accentGlow.copy(alpha = 0.36f),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.32f)
            )
        }
    )

    val surfaceBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isHighlighted) 0.18f else 0.10f),
            ZuanGlassBase,
            accentGlow.copy(alpha = if (isHighlighted) 0.16f else 0.07f)
        )
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = highlightScaleX
                scaleY = highlightScaleY
            }
            .liquidInteractive(
                enabled = true,
                pressScale = if (onClick != null) 1.058f else 1.028f,
                highlightColor = accentGlow,
                onClick = onClick
            )
            .clip(shape)
            .background(brush = surfaceBrush, shape = shape)
            .border(width = 1.35.dp, brush = rimBrush, shape = shape)
            .drawWithContent {
                drawContent()
                val crPx = cornerRadius.toPx()
                val padPx = 2.dp.toPx()
                // 3D Inner Convex Pillow Bevel Rim (gives every tile an iOS 26 curved glass lens cushion!)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isHighlighted) 0.34f else 0.20f),
                            Color.Transparent,
                            accentGlow.copy(alpha = if (isHighlighted) 0.22f else 0.10f)
                        )
                    ),
                    topLeft = Offset(padPx, padPx),
                    size = Size(
                        (size.width - padPx * 2f).coerceAtLeast(1f),
                        (size.height - padPx * 2f).coerceAtLeast(1f)
                    ),
                    cornerRadius = CornerRadius((crPx - padPx).coerceAtLeast(4f)),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                // Top Specular Meniscus Light Bar
                val inset = crPx * 0.50f
                if (size.width > inset * 2) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isHighlighted) 0.85f else 0.55f),
                                accentGlow.copy(alpha = if (isHighlighted) 0.68f else 0.36f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(inset, 1.5.dp.toPx()),
                        end = Offset(size.width - inset, 1.5.dp.toPx()),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
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

/**
 * Custom iOS 26 Bubbly Liquid Glass Toggle Switch with Elastic Thumb Stretch & Bounce.
 */
@Composable
fun ZuanBubblySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color = ZuanMint
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 400f),
        label = "bubblySwitchProgress"
    )
    // Mid-flight horizontal jelly stretch (peaks at progress = 0.5)
    val midStretch = 1f + 0.28f * (1f - kotlin.math.abs(progress * 2f - 1f))
    val trackColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.38f) else ZuanGlassElevated,
        animationSpec = tween(200),
        label = "switchTrackCol"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.26f),
        animationSpec = tween(200),
        label = "switchBorderCol"
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .liquidInteractive(
                pressScale = 1.12f,
                highlightColor = accent,
                onClick = { onCheckedChange(!checked) }
            )
            .clip(CircleShape)
            .background(trackColor)
            .border(1.2.dp, borderColor, CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (22.dp * progress))
                .size(22.dp)
                .graphicsLayer {
                    scaleX = midStretch
                    scaleY = 1f / (midStretch * 0.92f).coerceAtLeast(1f)
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            if (checked) accent else Color(0xFFD0DFEE)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.90f), CircleShape)
        )
    }
}

/**
 * Custom iOS 26 Bubbly Liquid Glass Capsule Chip.
 */
@Composable
fun ZuanBubblyChip(
    selected: Boolean,
    label: String,
    accent: Color = ZuanIndigo,
    onClick: () -> Unit
) {
    val popScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.36f, stiffness = 400f),
        label = "chipPop_$label"
    )
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = popScale
                scaleY = popScale
            }
            .liquidInteractive(
                pressScale = 1.10f,
                highlightColor = if (selected) ZuanAqua else accent,
                onClick = onClick
            )
            .clip(shape)
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.52f),
                            ZuanAqua.copy(alpha = 0.28f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            ZuanGlassElevated
                        )
                    )
                }
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = if (selected) {
                        listOf(Color.White.copy(alpha = 0.85f), ZuanAqua.copy(alpha = 0.70f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.12f))
                    }
                ),
                shape = shape
            )
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Color.White else ZuanTextSecondary,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Custom iOS 26 Bubbly Liquid Glass Action Pill Button.
 */
@Composable
fun ZuanBubblyButton(
    text: String,
    icon: IconType,
    accent: Color,
    isFilled: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .liquidInteractive(
                enabled = enabled,
                pressScale = 1.075f,
                highlightColor = accent,
                onClick = onClick
            )
            .clip(shape)
            .background(
                if (isFilled) {
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.55f),
                            ZuanIndigo.copy(alpha = 0.42f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            ZuanGlassElevated
                        )
                    )
                }
            )
            .border(
                width = 1.25.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isFilled) 0.78f else 0.45f),
                        accent.copy(alpha = if (isFilled) 0.70f else 0.38f)
                    )
                ),
                shape = shape
            )
            .padding(vertical = 11.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            VectorIcon(icon, size = 15.dp, tint = if (isFilled) Color.White else accent)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZuanTextPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
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
    size: Dp = 34.dp,
    iconSize: Dp = 17.dp
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), accent.copy(alpha = 0.42f))
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
        if (line.lowercase().startsWith("vmess://")) {
            val body = line.substring(8).substringBefore("#").trim()
            if (!body.contains("@")) {
                val decoded = String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.DEFAULT))
                val obj = JSONObject(decoded)
                val h = obj.optString("add", "").trim()
                if (h.isEmpty()) return null
                val p = obj.optString("port", "443").toIntOrNull() ?: obj.optInt("port", 443)
                val uuid = obj.optString("id", "").trim()
                val sni = obj.optString("sni", obj.optString("host", "")).trim()
                val scy = obj.optString("scy", "auto").trim()
                return ProxySettings(
                    type = ProxyType.VMESS,
                    host = h,
                    port = p,
                    password = uuid,
                    sni = sni,
                    ssMethod = scy.ifEmpty { "auto" }
                )
            }
        }

        if (!line.contains("://")) {
            val parts = line.split(":")
            if (parts.size == 4) {
                val p = parts[1].toIntOrNull()
                if (p != null) {
                    return ProxySettings(
                        type = ProxyType.SOCKS5,
                        host = parts[0].trim(),
                        port = p,
                        username = parts[2].trim(),
                        password = parts[3].trim()
                    )
                }
            } else if (parts.size == 2 && !line.contains("@")) {
                val p = parts[1].toIntOrNull()
                if (p != null) {
                    return ProxySettings(
                        type = ProxyType.SOCKS5,
                        host = parts[0].trim(),
                        port = p
                    )
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
            "vmess" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 443
                val uuid = uri.userInfo ?: ""
                val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
                ProxySettings(type = ProxyType.VMESS, host = h, port = p, password = uuid, sni = sni)
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
            "tuic" -> {
                val h = uri.host ?: return null
                val p = if (uri.port > 0) uri.port else 443
                val userInfo = uri.userInfo ?: ""
                val uuid = userInfo.substringBefore(":", "")
                val pw = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
                val sni = uri.getQueryParameter("sni") ?: ""
                ProxySettings(type = ProxyType.TUIC, host = h, port = p, username = Uri.decode(uuid), password = Uri.decode(pw), sni = sni)
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
    private var handoverNonce by mutableIntStateOf(0)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var lastHandoverInterface: String = ""
    @Volatile
    private var lastHandoverTimeMs: Long = 0L

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

        probeActiveNetwork()
        lastHandoverInterface = liveNetState.interfaceName
        registerNetworkHandoverWatcher()

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
                    handoverNonce = handoverNonce,
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

    /**
     * Only refreshes netfilter rules when the physical interface actually changes (e.g. wlan0 <-> rmnet_data0),
     * NEVER on routine LTE signal-strength / bandwidth onCapabilitiesChanged events!
     */
    private fun registerNetworkHandoverWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = checkRealHandover()
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = checkRealHandover()
                override fun onLost(network: Network) = probeActiveNetwork()
            }
            networkCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {}
    }

    private fun checkRealHandover() {
        val prevIface = lastHandoverInterface
        probeActiveNetwork()
        val newIface = liveNetState.interfaceName
        val now = System.currentTimeMillis()
        if (newIface.isNotEmpty() && newIface != prevIface && (now - lastHandoverTimeMs) > 3000L) {
            lastHandoverInterface = newIface
            lastHandoverTimeMs = now
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
            val autoIp = if (IptablesManager.isValidIpv6(h)) IpMode.IPV6_ONLY else IpMode.IPV4_ONLY
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
                pTransport = TransportMode.TCP_AND_UDP,
                pIpMode = autoIp,
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
                        val bHost = b.optString("host", "")
                        val autoIp = if (IptablesManager.isValidIpv6(bHost)) IpMode.IPV6_ONLY else IpMode.IPV4_ONLY
                        applySettingsToState(
                            pHost = bHost,
                            pPort = b.optString("port", "1080"),
                            pUser = b.optString("username", ""),
                            pPass = b.optString("password", ""),
                            pSni = b.optString("sni", ""),
                            pSsMethod = b.optString("ss_method", "2022-blake3-aes-128-gcm"),
                            pRpk = b.optString("reality_pk", ""),
                            pRsid = b.optString("reality_sid", ""),
                            pType = runCatching { ProxyType.valueOf(b.optString("proxy_type", "SOCKS5")) }.getOrDefault(ProxyType.SOCKS5),
                            pTransport = TransportMode.TCP_AND_UDP,
                            pIpMode = autoIp,
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

    fun getCurrentSettings(): ProxySettings {
        val cleanH = host.trim()
        val autoIp = if (IptablesManager.isValidIpv6(cleanH)) IpMode.IPV6_ONLY else IpMode.IPV4_ONLY
        return ProxySettings(
            type = proxyType,
            transportMode = TransportMode.TCP_AND_UDP,
            ipMode = autoIp,
            host = cleanH,
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
    }

    fun syncLiveKernelIfRunning() {
        if (isProxyActive) {
            val snap = getCurrentSettings()
            val uids = buildTargetUids()
            scope.launch(Dispatchers.IO) {
                ProxyController.applyLiveSettingsUpdate(snap, uids)
            }
        }
    }

    var saveJob by remember { mutableStateOf<Job?>(null) }
    fun saveConfigForSlot(slot: Int, immediate: Boolean = false) {
        val autoIp = if (IptablesManager.isValidIpv6(host.trim())) IpMode.IPV6_ONLY else IpMode.IPV4_ONLY
        ipMode = autoIp
        getSlotPrefs(slot).edit()
            .putString("proxy_type", proxyType.name)
            .putString("transport_mode", TransportMode.TCP_AND_UDP.name)
            .putString("ip_mode", autoIp.name)
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
                delay(if (attempt == 1) 150L else 800L)
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

    // Only update WebView proxy override when isProxyActive or runningSlot actually changes!
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

            // Main Animated Content Viewport with Underdamped iOS Bubbly Spring Physics
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
                        (fadeIn(animationSpec = tween(180)) +
                            scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = 0.44f, stiffness = 360f)) +
                            slideInVertically(animationSpec = spring(dampingRatio = 0.48f, stiffness = 360f)) { if (forward) it / 10 else -it / 10 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(130)) +
                                    scaleOut(targetScale = 0.91f, animationSpec = tween(130))
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
                                syncLiveKernelIfRunning()
                            },
                            onToggleHotspot = {
                                routeHotspot = !routeHotspot
                                saveConfigForSlot(activeSlot, true)
                                syncLiveKernelIfRunning()
                            },
                            onPingNode = {
                                if (host.trim().isEmpty()) {
                                    selectedTab = 2
                                    return@ZuanDashboardTab
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testLatency = "Probing..."
                                scope.launch {
                                    val res = ProxyTester.testProxy(getCurrentSettings())
                                    if (res is TestResult.Success) {
                                        if (res.detectedType != proxyType) {
                                            proxyType = res.detectedType
                                            saveConfigForSlot(activeSlot, true)
                                        }
                                        testLatency = "${res.latencyMs} ms"
                                    } else {
                                        testLatency = "Timeout"
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
                            strictWebRtcShield = strictWebRtcShield,
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
                            onAutoDetected = { detType, detIpMode ->
                                proxyType = detType
                                ipMode = detIpMode
                                saveConfigForSlot(activeSlot, true)
                            },
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
                                syncLiveKernelIfRunning()
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
                                syncLiveKernelIfRunning()
                            },
                            onToggleApp = { pkg ->
                                selectedPackages = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg
                                saveConfigForSlot(activeSlot)
                                syncLiveKernelIfRunning()
                            },
                            onSelectAll = {
                                selectedPackages = installedApps.map { it.packageName }.toSet()
                                saveConfigForSlot(activeSlot)
                                syncLiveKernelIfRunning()
                            },
                            onClearAll = {
                                selectedPackages = emptySet()
                                saveConfigForSlot(activeSlot)
                                syncLiveKernelIfRunning()
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
    val badgePop by animateFloatAsState(
        targetValue = if (isProxyActive) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.36f, stiffness = 360f),
        label = "topBadgePop"
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
                        scaleX = badgePop
                        scaleY = badgePop
                    }
                    .liquidInteractive(pressScale = 1.12f, highlightColor = if (isProxyActive) ZuanMint else ZuanAqua)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isProxyActive) {
                                listOf(ZuanMint.copy(alpha = 0.38f), ZuanAqua.copy(alpha = 0.16f))
                            } else {
                                listOf(ZuanIndigo.copy(alpha = 0.38f), ZuanAqua.copy(alpha = 0.16f))
                            }
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.72f), if (isProxyActive) ZuanMint else ZuanAqua)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                VectorIcon(
                    type = IconType.SHIELD,
                    size = 18.dp,
                    tint = if (isProxyActive) ZuanMint else ZuanAqua
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NAMELESS",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
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
                    .liquidInteractive(pressScale = 1.09f, highlightColor = ZuanAqua)
                    .clip(CircleShape)
                    .background(ZuanGlassElevated)
                    .border(1.1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VectorIcon(
                    type = if (liveNet.isCellular) IconType.CELLULAR else IconType.WIFI,
                    size = 12.dp,
                    tint = ZuanAqua
                )
                Spacer(modifier = Modifier.width(4.dp))
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
                    .liquidInteractive(pressScale = 1.10f, highlightColor = rootCol, onClick = onRecheckRoot)
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
                Spacer(modifier = Modifier.width(4.dp))
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

/**
 * 5-Slot Equal-Width iOS 26 Bubbly Liquid Glass Segmented Strip (`P1`..`P5`).
 */
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
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (slot in 0 until ProfileManager.MAX_SLOTS) {
                val isSelected = slot == activeSlot
                val isRunningThis = isProxyActive && runningSlot == slot
                val isConfigured = configuredSlots.contains(slot)
                val proto = slotProtocolMap[slot]?.shortBadge ?: "SOCKS5"

                val slotPopScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.06f else 1f,
                    animationSpec = spring(dampingRatio = 0.36f, stiffness = 400f),
                    label = "slotPop_$slot"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = slotPopScale
                            scaleY = slotPopScale
                        }
                        .liquidInteractive(
                            pressScale = 1.12f,
                            highlightColor = if (isRunningThis) ZuanMint else ZuanAqua,
                            onClick = { onSelectSlot(slot) }
                        )
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            when {
                                isRunningThis -> Brush.linearGradient(
                                    listOf(ZuanMint.copy(alpha = 0.34f), ZuanEmerald.copy(alpha = 0.16f))
                                )
                                isSelected -> Brush.linearGradient(
                                    listOf(ZuanAqua.copy(alpha = 0.28f), ZuanIndigo.copy(alpha = 0.18f))
                                )
                                else -> Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                                )
                            }
                        )
                        .border(
                            width = 1.1.dp,
                            color = when {
                                isRunningThis -> ZuanMint.copy(alpha = 0.82f)
                                isSelected -> ZuanAqua.copy(alpha = 0.70f)
                                else -> Color.White.copy(alpha = 0.12f)
                            },
                            shape = RoundedCornerShape(17.dp)
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isRunningThis || isConfigured) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(
                                            when {
                                                isRunningThis -> ZuanMint
                                                isSelected -> ZuanAqua
                                                else -> ZuanTextSecondary
                                            },
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
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
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (idx, icon, label) ->
                    val isSelected = selectedTab == idx
                    val activeAccent = if (isProxyActive && idx == 0) ZuanMint else ZuanAqua
                    val tabPopScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.07f else 1f,
                        animationSpec = spring(dampingRatio = 0.35f, stiffness = 400f),
                        label = "tabPop_$idx"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = tabPopScale
                                scaleY = tabPopScale
                            }
                            .liquidInteractive(
                                pressScale = 1.14f,
                                highlightColor = activeAccent,
                                onClick = { onSelectTab(idx) }
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (isSelected) {
                                    Brush.linearGradient(
                                        listOf(
                                            activeAccent.copy(alpha = 0.32f),
                                            ZuanIndigo.copy(alpha = 0.20f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .border(
                                width = if (isSelected) 1.15.dp else 0.dp,
                                color = if (isSelected) activeAccent.copy(alpha = 0.72f) else Color.Transparent,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            VectorIcon(
                                type = icon,
                                size = 17.dp,
                                tint = if (isSelected) activeAccent else ZuanTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) ZuanTextPrimary else ZuanTextSecondary,
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

// -------------------------------------------------------------
// TAB 0: 120FPS iOS 26 BUBBLY LIQUID GLASS DASHBOARD
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

    val reactorScale by animateFloatAsState(
        targetValue = if (isSlotRunning) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 360f),
        label = "reactorScale"
    )

    val stunIp: String = remember(publicIpInfo?.ip, host, isProxyActive, strictWebRtcShield) {
        when {
            !isProxyActive -> "Standby"
            !strictWebRtcShield -> "Direct (Off)"
            else -> publicIpInfo?.ip ?: StunResponder.getExitIp().ifEmpty { host.ifEmpty { "Syncing..." } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. HERO REACTOR & TUNNEL CONTROL BUBBLY PILLOW TILE
        IosBubblyTileEntrance(index = 0) {
            ZuanLiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
                accentGlow = heroAccent,
                isHighlighted = isSlotRunning,
                contentPadding = PaddingValues(15.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(heroAccent.copy(alpha = 0.18f))
                            .border(1.1.dp, heroAccent.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(heroAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isTransitioning -> "SWITCHING KERNEL..."
                                isSlotRunning -> "CONNECTED • ${slotLabel(activeSlot)}"
                                isProxyActive && runningSlot != null -> "${slotLabel(runningSlot)} RUNNING"
                                else -> "READY • ${slotLabel(activeSlot)}"
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
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isProxyActive) ZuanMint else ZuanTextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(ZuanIndigo.copy(alpha = 0.28f))
                                .border(1.dp, ZuanIndigo.copy(alpha = 0.60f), RoundedCornerShape(11.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = proxyType.shortBadge,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZuanTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isTransitioning -> "Updating Netfilter..."
                                isSlotRunning -> "Kernel Tunnel Active"
                                else -> "Tap Reactor to Start"
                            },
                            fontSize = 18.5.sp,
                            fontWeight = FontWeight.Black,
                            color = ZuanTextPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (host.isNotBlank()) "$host:$port" else "No node configured — tap to setup",
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (host.isNotBlank()) ZuanAqua else ZuanAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onOpenNodeTab() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${liveNet.transportLabel} (${liveNet.interfaceName}) • PID: ${activePid ?: "—"}",
                            fontSize = 10.5.sp,
                            color = ZuanTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .graphicsLayer {
                                scaleX = reactorScale
                                scaleY = reactorScale
                            }
                            .liquidInteractive(
                                pressScale = 1.18f,
                                highlightColor = heroAccent,
                                onClick = onToggleProxy
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.minDimension / 2f

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(heroAccent.copy(alpha = 0.36f), Color.Transparent),
                                    center = c,
                                    radius = r
                                ),
                                radius = r,
                                center = c
                            )

                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Color.Transparent, heroAccent, Color.White, heroAccent, Color.Transparent)
                                ),
                                startAngle = -45f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(c.x - r * 0.90f, c.y - r * 0.90f),
                                size = Size(r * 1.80f, r * 1.80f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Color.Transparent, ZuanIndigo, heroAccent.copy(alpha = 0.80f), Color.Transparent)
                                ),
                                startAngle = 120f,
                                sweepAngle = 200f,
                                useCenter = false,
                                topLeft = Offset(c.x - r * 0.74f, c.y - r * 0.74f),
                                size = Size(r * 1.48f, r * 1.48f),
                                style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.24f),
                                            heroAccent.copy(alpha = 0.42f),
                                            ZuanGlassElevated
                                        )
                                    )
                                )
                                .border(1.6.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            VectorIcon(
                                type = IconType.POWER,
                                size = 22.dp,
                                tint = Color.White,
                                strokeWidth = 2.4f
                            )
                        }
                    }
                }
            }
        }

        // 2. EXIT IDENTITY & WEBRTC/DNS VERIFICATION BUBBLY PILLOW TILE
        IosBubblyTileEntrance(index = 1) {
            ZuanLiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                accentGlow = if (isProxyActive) ZuanAqua else ZuanIndigo,
                contentPadding = PaddingValues(14.dp)
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
                                .size(42.dp)
                                .liquidInteractive(pressScale = 1.12f, highlightColor = ZuanAqua, onClick = onRefreshIp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(ZuanGlassElevated)
                                .border(1.1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = publicIpInfo?.flagEmoji ?: if (isProxyActive) "🇺🇸" else "🛡️",
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    !isProxyActive -> "Direct Connection"
                                    isFetchingIp && publicIpInfo == null -> "Verifying Exit IP..."
                                    publicIpInfo != null -> publicIpInfo.ip
                                    else -> host.ifEmpty { "Awaiting Node" }
                                },
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZuanTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .liquidInteractive(pressScale = 1.14f, highlightColor = ZuanAqua, onClick = onRefreshIp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(ZuanGlassElevated)
                                .border(1.1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            VectorIcon(IconType.REFRESH, size = 15.dp, tint = ZuanAqua)
                        }
                        Box(
                            modifier = Modifier
                                .liquidInteractive(pressScale = 1.12f, highlightColor = ZuanMint, onClick = onOpenIpFighterTab)
                                .clip(RoundedCornerShape(13.dp))
                                .background(ZuanMint.copy(alpha = 0.20f))
                                .border(1.1.dp, ZuanMint.copy(alpha = 0.58f), RoundedCornerShape(13.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "IPFighter",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZuanMint,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ZuanMetricPill(
                        modifier = Modifier.weight(1f),
                        label = "DNS TUNNEL",
                        value = if (isProxyActive) "US FakeIP" else "System",
                        accent = if (isProxyActive) ZuanMint else ZuanTextSecondary
                    )
                    ZuanMetricPill(
                        modifier = Modifier.weight(1.18f),
                        label = "WEBRTC STUN",
                        value = stunIp,
                        accent = if (isProxyActive && strictWebRtcShield) ZuanAqua else ZuanTextSecondary
                    )
                    ZuanMetricPill(
                        modifier = Modifier.weight(0.92f),
                        label = "LATENCY",
                        value = if (isTesting) "Probing..." else (testLatency ?: "Tap Ping"),
                        accent = ZuanAmber,
                        onClick = onPingNode
                    )
                }
            }
        }

        // 3. LIVE BANDWIDTH SPARKLINE BUBBLY BENTO TILES
        IosBubblyTileEntrance(index = 2) {
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
                    accent = ZuanAqua
                )
                ZuanSparklineBentoCard(
                    modifier = Modifier.weight(1f),
                    title = "Upload",
                    icon = IconType.UPLOAD,
                    rate = currentTxRate,
                    total = totalTxBytes,
                    history = txHistory,
                    accent = ZuanMint
                )
            }
        }

        // 4. INTERACTIVE SHIELD & HOTSPOT QUICK-TOGGLE BUBBLY BENTO TILES
        IosBubblyTileEntrance(index = 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZuanQuickToggleBento(
                    modifier = Modifier.weight(1f),
                    title = "WebRTC & DNS",
                    subtitle = if (strictWebRtcShield) "US STUN + FakeIP" else "Direct WebRTC (Off)",
                    icon = IconType.SHIELD,
                    active = strictWebRtcShield,
                    accent = ZuanMint,
                    onClick = onToggleWebRtc
                )
                ZuanQuickToggleBento(
                    modifier = Modifier.weight(1f),
                    title = "Wi-Fi Hotspot",
                    subtitle = if (routeHotspot) "Laptop STUN + TCP" else "Phone Only (Off)",
                    icon = IconType.HOTSPOT,
                    active = routeHotspot,
                    accent = ZuanAqua,
                    onClick = onToggleHotspot
                )
            }
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
                enabled = true,
                pressScale = 1.08f,
                highlightColor = accent,
                onClick = onClick
            )
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(15.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
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
fun ZuanQuickToggleBento(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: IconType,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    ZuanLiquidGlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        accentGlow = if (active) accent else ZuanIndigo,
        isHighlighted = active,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZuanIconBadge(icon = icon, accent = if (active) accent else ZuanTextSecondary, size = 30.dp, iconSize = 15.dp)
            ZuanBubblySwitch(
                checked = active,
                onCheckedChange = { onClick() },
                accent = accent
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ZuanTextPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            fontSize = 10.5.sp,
            color = if (active) accent else ZuanTextSecondary,
            maxLines = 1,
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
    accent: Color
) {
    ZuanLiquidGlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        accentGlow = accent,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuanIconBadge(icon = icon, accent = accent, size = 28.dp, iconSize = 14.dp)
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZuanTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = splitSpeedValue(rate),
                fontSize = 18.sp,
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
            fontSize = 10.sp,
            color = ZuanTextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
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
                        colors = listOf(accent.copy(alpha = 0.36f), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = accent,
                    style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: iOS 26 BUBBLY LIQUID GLASS IPFIGHTER & LEAK INSPECTOR
// -------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveEphemeralIpFighterTab(
    isProxyActive: Boolean,
    strictWebRtcShield: Boolean,
    runningSlot: Int?,
    publicIpInfo: GeoIpResult?,
    configuredHost: String
) {
    var reloadNonce by remember { mutableIntStateOf(0) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var targetSiteIndex by remember { mutableIntStateOf(0) }

    val targets = listOf(
        Triple(IconType.GLOBE, "IPFighter", "https://ipfighter.com/"),
        Triple(IconType.SHIELD, "WebRTC Test", "https://browserleaks.com/webrtc"),
        Triple(IconType.SERVER, "DNS Test", "https://browserleaks.com/dns")
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

    val verifiedExitIp: String = remember(isProxyActive, strictWebRtcShield, publicIpInfo?.ip, configuredHost) {
        if (!isProxyActive || !strictWebRtcShield) ""
        else publicIpInfo?.ip ?: StunResponder.getExitIp().ifEmpty { configuredHost.trim() }
    }

    val shouldSpoofWebRtc = isProxyActive && strictWebRtcShield

    val shieldScript = remember(targetTz, verifiedExitIp, isProxyActive, shouldSpoofWebRtc) {
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
                if ($shouldSpoofWebRtc && window.RTCPeerConnection && '$verifiedExitIp' !== '') {
                    const OrigPeerConnection = window.RTCPeerConnection;
                    const rewriteSdpOrCand = function(str) {
                        if (!str) return str;
                        return str
                            .replace(/([0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}/g, '$verifiedExitIp')
                            .replace(/(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/g, function(match) {
                                if (match === '0.0.0.0' || match.indexOf('127.') === 0) return match;
                                return '$verifiedExitIp';
                            })
                            .replace('typ host', 'typ srflx');
                    };
                    const wrapCandidateEvent = function(ev) {
                        if (!ev || !ev.candidate || !ev.candidate.candidate) return ev;
                        try {
                            const mod = rewriteSdpOrCand(ev.candidate.candidate);
                            const wrapped = Object.create(ev);
                            Object.defineProperty(wrapped, 'candidate', {
                                value: {
                                    candidate: mod,
                                    sdpMid: ev.candidate.sdpMid,
                                    sdpMLineIndex: ev.candidate.sdpMLineIndex,
                                    address: '$verifiedExitIp',
                                    ip: '$verifiedExitIp',
                                    type: 'srflx',
                                    toJSON: function() {
                                        return {
                                            candidate: mod,
                                            sdpMid: ev.candidate.sdpMid,
                                            sdpMLineIndex: ev.candidate.sdpMLineIndex
                                        };
                                    }
                                }
                            });
                            return wrapped;
                        } catch (e) {
                            return ev;
                        }
                    };
                    window.RTCPeerConnection = function(config, constraints) {
                        const pc = new OrigPeerConnection(config, constraints);
                        const origAddEventListener = pc.addEventListener.bind(pc);
                        pc.addEventListener = function(type, listener, options) {
                            if (type === 'icecandidate' && typeof listener === 'function') {
                                return origAddEventListener(type, function(ev) {
                                    return listener.call(this, wrapCandidateEvent(ev));
                                }, options);
                            }
                            return origAddEventListener(type, listener, options);
                        };
                        let userOnIce = null;
                        Object.defineProperty(pc, 'onicecandidate', {
                            get: function() { return userOnIce; },
                            set: function(fn) {
                                userOnIce = fn;
                                origAddEventListener('icecandidate', function(ev) {
                                    if (typeof userOnIce === 'function') {
                                        userOnIce.call(pc, wrapCandidateEvent(ev));
                                    }
                                });
                            }
                        });
                        return pc;
                    };
                    window.RTCPeerConnection.prototype = OrigPeerConnection.prototype;
                    window.webkitRTCPeerConnection = window.RTCPeerConnection;
                }
            } catch (e) {}
        })();
        """.trimIndent()
    }

    IosBubblyTileEntrance(index = 0, modifier = Modifier.fillMaxSize()) {
        ZuanLiquidGlassCard(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 26.dp,
            contentPadding = PaddingValues(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                targets.forEachIndexed { idx, (icon, label, _) ->
                    val sel = targetSiteIndex == idx
                    val pillScale by animateFloatAsState(
                        targetValue = if (sel) 1.055f else 1f,
                        animationSpec = spring(dampingRatio = 0.36f, stiffness = 400f),
                        label = "sitePill_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = pillScale
                                scaleY = pillScale
                            }
                            .liquidInteractive(
                                pressScale = 1.10f,
                                highlightColor = ZuanAqua,
                                onClick = {
                                    targetSiteIndex = idx
                                    reloadNonce++
                                }
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) ZuanAqua.copy(alpha = 0.24f) else ZuanGlassElevated)
                            .border(1.1.dp, if (sel) ZuanAqua else Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VectorIcon(icon, size = 13.dp, tint = if (sel) ZuanAqua else ZuanTextSecondary)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (sel) ZuanTextPrimary else ZuanTextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .liquidInteractive(
                            pressScale = 1.14f,
                            highlightColor = ZuanMint,
                            onClick = {
                                WebStorage.getInstance().deleteAllData()
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                reloadNonce++
                            }
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(ZuanMint.copy(alpha = 0.22f))
                        .border(1.1.dp, ZuanMint.copy(alpha = 0.60f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    VectorIcon(IconType.REFRESH, size = 15.dp, tint = ZuanMint)
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
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            ) {
                key(isProxyActive, strictWebRtcShield, runningSlot, reloadNonce, targetSiteIndex) {
                    val baseUrl = targets[targetSiteIndex].third
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
                                        if (newProgress in 15..85) {
                                            view?.evaluateJavascript(shieldScript, null)
                                        }
                                    }
                                }
                                var autoRetryCount = 0
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

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true && autoRetryCount < 2) {
                                            autoRetryCount++
                                            view?.postDelayed({ view.loadUrl(baseUrl) }, 650L)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean = false
                                }
                                loadUrl(baseUrl)
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
}

// -------------------------------------------------------------
// TAB 2: iOS 26 BUBBLY LIQUID GLASS UNIVERSAL NODE STUDIO
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
    onAutoDetected: (ProxyType, IpMode) -> Unit,
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
        IosBubblyTileEntrance(index = 0) {
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
                            Text("Universal URI Import & Auto-Verify", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZuanTextPrimary)
                            Text("Auto-detects protocol, transport & IP stack built-in", fontSize = 10.5.sp, color = ZuanTextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZuanBubblyButton(
                        text = "Paste URI",
                        icon = IconType.PASTE,
                        accent = ZuanAqua,
                        isFilled = false,
                        modifier = Modifier.weight(1f),
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
                        }
                    )

                    ZuanBubblyButton(
                        text = if (isCheckingAlive) "Probing..." else "Verify & Tune",
                        icon = IconType.BOLT,
                        accent = ZuanAqua,
                        isFilled = true,
                        enabled = !isCheckingAlive,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (host.trim().isEmpty()) {
                                aliveResult = TestResult.Failure("Host is empty")
                                return@ZuanBubblyButton
                            }
                            isCheckingAlive = true
                            aliveResult = null
                            scope.launch {
                                val res = ProxyTester.testProxy(getCurrentSettings())
                                if (res is TestResult.Success) {
                                    onAutoDetected(res.detectedType, res.detectedIpMode)
                                }
                                aliveResult = res
                                isCheckingAlive = false
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = aliveResult != null,
                    enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = 0.45f, stiffness = 360f)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        when (val res = aliveResult) {
                            is TestResult.Success -> Text(
                                text = "VERIFIED • ${res.latencyMs} ms • ${res.detectedType.smartEngineSummary(host)}",
                                color = ZuanMint,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            is TestResult.Failure -> Text(
                                text = "FAILED • ${res.error}",
                                color = ZuanRose,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            null -> {}
                        }
                    }
                }
            }
        }

        IosBubblyTileEntrance(index = 1) {
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
                            .liquidInteractive(pressScale = 1.12f, highlightColor = ZuanRose, onClick = onResetSlot)
                            .clip(CircleShape)
                            .background(ZuanRose.copy(alpha = 0.18f))
                            .border(1.1.dp, ZuanRose.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VectorIcon(IconType.TRASH, size = 13.dp, tint = ZuanRose)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset ${slotLabel(activeSlot)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZuanRose)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Sing-Box Protocol Engine", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZuanTextSecondary)
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProxyType.values().forEach { t ->
                        ZuanBubblyChip(
                            selected = proxyType == t,
                            label = t.displayTitle,
                            accent = ZuanIndigo,
                            onClick = { onProxyTypeChange(t); onConfigChanged() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(11.dp))

                // Built-In Automatic Transport & IP Stack Engine Bubbly Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidInteractive(pressScale = 1.045f, highlightColor = ZuanMint)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ZuanMint.copy(alpha = 0.18f), ZuanAqua.copy(alpha = 0.14f))
                            )
                        )
                        .border(1.1.dp, ZuanMint.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VectorIcon(IconType.BOLT, size = 14.dp, tint = ZuanMint)
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = proxyType.smartEngineSummary(host),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZuanMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

                    ProxyType.VMESS -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { onPasswordChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.KEY, size = 16.dp, tint = ZuanAqua) },
                            label = { Text("VMess UUID") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { onSniChange(it); onConfigChanged() },
                            leadingIcon = { VectorIcon(IconType.GLOBE, size = 16.dp, tint = ZuanTextSecondary) },
                            label = { Text("TLS SNI (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
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

                    ProxyType.TUIC -> {
                        var showPass by remember { mutableStateOf(false) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { onUsernameChange(it); onConfigChanged() },
                                leadingIcon = { VectorIcon(IconType.USER, size = 16.dp, tint = ZuanAqua) },
                                label = { Text("TUIC UUID") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { onPasswordChange(it); onConfigChanged() },
                                leadingIcon = { VectorIcon(IconType.KEY, size = 16.dp, tint = ZuanTextSecondary) },
                                label = { Text("TUIC Password") },
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

                    ProxyType.SOCKS4 -> {}
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: iOS 26 BUBBLY LIQUID GLASS KERNEL ROUTING, BOOT & SPLIT-TUNNEL
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
        IosBubblyTileEntrance(index = 0) {
            ZuanLiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                isHighlighted = startOnBoot,
                accentGlow = ZuanAmber,
                onClick = { onStartOnBootChange(!startOnBoot) }
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
                    ZuanBubblySwitch(
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBootChange,
                        accent = ZuanAmber
                    )
                }

                AnimatedVisibility(
                    visible = startOnBoot,
                    enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = 0.45f, stiffness = 360f)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (s in 0 until ProfileManager.MAX_SLOTS) {
                                ZuanBubblyChip(
                                    selected = bootSlot == s,
                                    label = slotLabel(s),
                                    accent = ZuanAmber,
                                    onClick = { onBootSlotChange(s) }
                                )
                            }
                        }
                    }
                }
            }
        }

        IosBubblyTileEntrance(index = 1) {
            ZuanLiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                isHighlighted = routeHotspot,
                accentGlow = ZuanAqua,
                onClick = { onRouteHotspotChange(!routeHotspot) }
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
                        ZuanIconBadge(icon = IconType.HOTSPOT, accent = ZuanAqua, size = 36.dp, iconSize = 18.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Wi-Fi Hotspot & USB Tethering", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary)
                            Text("Routes Hotspot TCP, FakeIP DNS & US STUN WebRTC", fontSize = 11.sp, color = ZuanTextSecondary)
                        }
                    }
                    ZuanBubblySwitch(
                        checked = routeHotspot,
                        onCheckedChange = onRouteHotspotChange,
                        accent = ZuanAqua
                    )
                }
            }
        }

        IosBubblyTileEntrance(index = 2) {
            ZuanLiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZuanBubblyButton(
                        text = "Export 5 Slots",
                        icon = IconType.UPLOAD,
                        accent = ZuanAqua,
                        isFilled = false,
                        modifier = Modifier.weight(1f),
                        onClick = onExportBackup
                    )

                    ZuanBubblyButton(
                        text = "Import Backup",
                        icon = IconType.DOWNLOAD,
                        accent = ZuanAqua,
                        isFilled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onImportBackup
                    )
                }
            }
        }

        IosBubblyTileEntrance(index = 3) {
            ZuanLiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                isHighlighted = routeWholeProfile,
                accentGlow = ZuanMint
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
                    ZuanBubblySwitch(
                        checked = routeWholeProfile,
                        onCheckedChange = onRouteWholeProfileChange,
                        accent = ZuanMint
                    )
                }

                AnimatedVisibility(
                    visible = !routeWholeProfile,
                    enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = 0.45f, stiffness = 360f)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ZuanBubblyButton(
                                text = "Select All",
                                icon = IconType.CHECK,
                                accent = ZuanMint,
                                isFilled = false,
                                modifier = Modifier.weight(1f),
                                onClick = onSelectAll
                            )
                            ZuanBubblyButton(
                                text = "Clear",
                                icon = IconType.TRASH,
                                accent = ZuanRose,
                                isFilled = false,
                                modifier = Modifier.weight(1f),
                                onClick = onClearAll
                            )
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
                                        .liquidInteractive(
                                            pressScale = 1.045f,
                                            highlightColor = ZuanAqua,
                                            onClick = { onToggleApp(app.packageName) }
                                        )
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(if (isSelected) ZuanAqua.copy(alpha = 0.18f) else ZuanGlassElevated)
                                        .border(
                                            1.1.dp,
                                            if (isSelected) ZuanAqua.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
                                            RoundedCornerShape(15.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZuanTextPrimary, maxLines = 1)
                                        Text("${app.packageName} • UID ${app.uid}", fontSize = 10.sp, color = ZuanTextSecondary, maxLines = 1)
                                    }
                                    ZuanBubblySwitch(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleApp(app.packageName) },
                                        accent = ZuanAqua
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: iOS 26 BUBBLY LIQUID GLASS KERNEL LOGS & NETFILTER CONSOLE
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

    IosBubblyTileEntrance(index = 0, modifier = Modifier.fillMaxSize()) {
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
                            .liquidInteractive(pressScale = 1.12f, highlightColor = ZuanMint, onClick = onToggleAuditMode)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (showKernelAuditMode) ZuanMint.copy(alpha = 0.22f) else ZuanGlassElevated)
                            .border(1.1.dp, if (showKernelAuditMode) ZuanMint else Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
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
                            .liquidInteractive(
                                pressScale = 1.12f,
                                highlightColor = ZuanAqua,
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(android.content.ClipData.newPlainText("nameless_logs", logs))
                                    Toast.makeText(context, "Copied logs", Toast.LENGTH_SHORT).show()
                                }
                            )
                            .clip(RoundedCornerShape(13.dp))
                            .background(ZuanGlassElevated)
                            .border(1.1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
                            .padding(6.dp)
                    ) {
                        VectorIcon(IconType.COPY, size = 15.dp, tint = ZuanAqua)
                    }

                    Box(
                        modifier = Modifier
                            .liquidInteractive(pressScale = 1.12f, highlightColor = ZuanMint, onClick = onRefresh)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ZuanGlassElevated)
                            .border(1.1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
                            .padding(6.dp)
                    ) {
                        VectorIcon(IconType.REFRESH, size = 15.dp, tint = ZuanMint)
                    }

                    Box(
                        modifier = Modifier
                            .liquidInteractive(pressScale = 1.12f, highlightColor = ZuanRose, onClick = onClear)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ZuanRose.copy(alpha = 0.18f))
                            .border(1.1.dp, ZuanRose.copy(alpha = 0.52f), RoundedCornerShape(13.dp))
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
                    .background(Color(0x66030712))
                    .border(1.1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
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
}
