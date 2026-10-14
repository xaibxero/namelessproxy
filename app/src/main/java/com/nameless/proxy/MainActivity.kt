package com.nameless.proxy

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

data class AppItem(
    val name: String,
    val packageName: String,
    val uid: Int
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

private val SlotPlanetNames = listOf("Mercury", "Venus", "Earth", "Mars", "Saturn")

// -------------------------------------------------------------
// REFINED CELESTIAL OBSIDIAN & ALABASTER PALETTES
// -------------------------------------------------------------

data class CosmicPalette(
    val isDark: Boolean,
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val glassPodFill: Color,
    val glassRimBright: Color,
    val glassRimSubtle: Color,
    val orbitTrack: Color,
    val sunGold: Color,
    val sunOrange: Color,
    val earthCyan: Color,
    val cosmicViolet: Color,
    val marsCoral: Color,
    val emeraldLive: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

private val DarkCosmicPalette = CosmicPalette(
    isDark = true,
    skyTop = Color(0xFF04050C),
    skyMid = Color(0xFF090C1C),
    skyBottom = Color(0xFF0F142C),
    glassFillTop = Color(0x40161B36),
    glassFillBottom = Color(0x280E1226),
    glassPodFill = Color(0x3D1E2548),
    glassRimBright = Color(0x70818CF8),
    glassRimSubtle = Color(0x284F46E5),
    orbitTrack = Color(0x4294A3B8),
    sunGold = Color(0xFFFFBE0B),
    sunOrange = Color(0xFFFF6B35),
    earthCyan = Color(0xFF00E5FF),
    cosmicViolet = Color(0xFF8B5CF6),
    marsCoral = Color(0xFFFF2E63),
    emeraldLive = Color(0xFF00F5A0),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8)
)

private val LightCosmicPalette = CosmicPalette(
    isDark = false,
    skyTop = Color(0xFFEEF2FF),
    skyMid = Color(0xFFE0E7FF),
    skyBottom = Color(0xFFF5F3FF),
    glassFillTop = Color(0x66FFFFFF),
    glassFillBottom = Color(0x42F8FAFC),
    glassPodFill = Color(0x59FFFFFF),
    glassRimBright = Color(0xB3FFFFFF),
    glassRimSubtle = Color(0x386366F1),
    orbitTrack = Color(0x42475569),
    sunGold = Color(0xFFD97706),
    sunOrange = Color(0xFFEA580C),
    earthCyan = Color(0xFF0284C7),
    cosmicViolet = Color(0xFF6366F1),
    marsCoral = Color(0xFFE11D48),
    emeraldLive = Color(0xFF059669),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF1E293B),
    textMuted = Color(0xFF64748B)
)

val LocalCosmicPalette = compositionLocalOf { DarkCosmicPalette }

@Composable
fun CrystalGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 22,
    accentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalCosmicPalette.current
    val shape = RoundedCornerShape(cornerRadius.dp)
    val rimStart = accentColor?.copy(alpha = 0.85f) ?: palette.glassRimBright
    val rimEnd = accentColor?.copy(alpha = 0.25f) ?: palette.glassRimSubtle

    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.glassFillTop, palette.glassFillBottom)
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(colors = listOf(rimStart, rimEnd)),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

// Custom Spring-Animated Switch for Smooth Toggling
@Composable
fun AnimatedCosmicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val palette = LocalCosmicPalette.current
    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchThumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.emeraldLive else palette.glassPodFill,
        animationSpec = tween(260),
        label = "switchTrack"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) palette.emeraldLive else palette.glassRimBright,
        animationSpec = tween(260),
        label = "switchBorder"
    )

    Box(
        modifier = Modifier
            .width(50.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(trackColor.copy(alpha = if (enabled) 0.32f else 0.12f))
            .border(1.2.dp, borderColor.copy(alpha = if (enabled) 0.85f else 0.3f), CircleShape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (22f * thumbProgress).dp)
                .size(20.dp)
                .graphicsLayer {
                    scaleX = 0.92f + 0.12f * thumbProgress
                    scaleY = 0.92f + 0.12f * thumbProgress
                }
                .clip(CircleShape)
                .background(
                    if (checked) {
                        Brush.linearGradient(listOf(palette.emeraldLive, palette.earthCyan))
                    } else {
                        Brush.linearGradient(listOf(palette.textMuted, palette.textSecondary))
                    }
                )
        )
    }
}

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var rootState by mutableStateOf(RootState.CHECKING)
    private var rootLabel by mutableStateOf("Wait")
    private var isProxyRunningState by mutableStateOf(false)
    private var isTransitioningState by mutableStateOf(false)
    private var runningSlotState by mutableStateOf<Int?>(null)
    private var activePidState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()

        val globalPrefs = getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
        if (!globalPrefs.getBoolean("start_on_boot", false)) {
            lifecycleScope.launch(Dispatchers.IO) {
                BootManager.removeBootScript()
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncDaemonStatus()
            }
        })

        setContent {
            val isDark = isSystemInDarkTheme()
            val palette = if (isDark) DarkCosmicPalette else LightCosmicPalette
            val scheme = if (isDark) {
                darkColorScheme(
                    primary = palette.earthCyan,
                    secondary = palette.cosmicViolet,
                    background = palette.skyTop,
                    surface = palette.glassFillTop,
                    onSurface = palette.textPrimary
                )
            } else {
                lightColorScheme(
                    primary = palette.cosmicViolet,
                    secondary = palette.earthCyan,
                    background = palette.skyTop,
                    surface = palette.glassFillTop,
                    onSurface = palette.textPrimary
                )
            }

            CompositionLocalProvider(LocalCosmicPalette provides palette) {
                MaterialTheme(colorScheme = scheme) {
                    MainScreen(
                        rootState = rootState,
                        rootLabel = rootLabel,
                        isProxyActive = isProxyRunningState,
                        isTransitioning = isTransitioningState,
                        runningSlot = runningSlotState,
                        activePid = activePidState,
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
            rootLabel = "Wait"
            val (s, l) = RootChecker.verifyRoot()
            rootState = s
            rootLabel = l
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
                    iface.startsWith("ccmni") ||
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
        if (rx > 0L || tx > 0L) {
            Pair(rx, tx)
        } else {
            Pair(
                TrafficStats.getTotalRxBytes().coerceAtLeast(0L),
                TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            )
        }
    } catch (_: Exception) {
        Pair(
            TrafficStats.getTotalRxBytes().coerceAtLeast(0L),
            TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    rootState: RootState,
    rootLabel: String,
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activePid: String?,
    onRecheckRoot: () -> Unit,
    installedApps: List<AppItem>,
    onSyncStatus: () -> Unit,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val palette = LocalCosmicPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Continuous 120Hz Monotonic Clock for smooth 360° planetary orbits
    var continuousTimeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = System.nanoTime()
        while (isActive) {
            withInfiniteAnimationFrameMillis {
                continuousTimeSec = (System.nanoTime() - startNanos) / 1_000_000_000f
            }
        }
    }

    // Smooth EMA Network Telemetry (Eliminates on/off zero-flickering between packet bursts!)
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var currentRxRate by remember { mutableLongStateOf(0L) }
    var currentTxRate by remember { mutableLongStateOf(0L) }
    var totalRxBytes by remember { mutableLongStateOf(0L) }
    var totalTxBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isProxyActive) {
        if (isProxyActive) {
            if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
            val (initRx, initTx) = withContext(Dispatchers.IO) { readPhysicalNetworkBytes() }
            var prevRx = initRx
            var prevTx = initTx
            val startRx = initRx
            val startTx = initTx
            var prevTime = System.currentTimeMillis()
            var emaRx = 0.0
            var emaTx = 0.0

            while (isActive) {
                delay(550)
                val (currRxRaw, currTxRaw) = withContext(Dispatchers.IO) { readPhysicalNetworkBytes() }
                val currRx = currRxRaw.coerceAtLeast(prevRx)
                val currTx = currTxRaw.coerceAtLeast(prevTx)
                val now = System.currentTimeMillis()
                val dt = (now - prevTime).coerceAtLeast(1L) / 1000.0

                val instantRx = ((currRx - prevRx) / dt).coerceAtLeast(0.0)
                val instantTx = ((currTx - prevTx) / dt).coerceAtLeast(0.0)

                // Smooth rise on burst, gentle decay between bursts so it never flickers on/off
                emaRx = if (instantRx >= emaRx) {
                    instantRx * 0.72 + emaRx * 0.28
                } else {
                    (emaRx * 0.65 + instantRx * 0.35).let { if (it < 180.0) 0.0 else it }
                }

                emaTx = if (instantTx >= emaTx) {
                    instantTx * 0.72 + emaTx * 0.28
                } else {
                    (emaTx * 0.65 + instantTx * 0.35).let { if (it < 180.0) 0.0 else it }
                }

                currentRxRate = emaRx.toLong()
                currentTxRate = emaTx.toLong()
                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)

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

    fun refreshConfiguredSlots() {
        val set = mutableSetOf<Int>()
        for (i in 0 until ProfileManager.MAX_SLOTS) {
            if (!getSlotPrefs(i).getString("host", "").isNullOrBlank()) set.add(i)
        }
        configuredSlots = set
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
        realityShortId = realityShortId.trim()
    )

    var saveJob by remember { mutableStateOf<Job?>(null) }
    fun saveConfigForSlot(slot: Int, immediate: Boolean = false) {
        getSlotPrefs(slot).edit()
            .putString("proxy_type", proxyType.name)
            .putString("transport_mode", transportMode.name)
            .putString("ip_mode", ipMode.name)
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
        val uids = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
        val pkgs = selectedPackages.toSet()
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            if (!immediate) delay(600)
            if (startOnBoot && slot == bootSlot) BootManager.syncBootState(context, true, snap, uids)
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
        isFetchingIp = true
        ipFetchJob = scope.launch(Dispatchers.IO) {
            for (attempt in 1..4) {
                delay(if (attempt == 1) 300L else 1100L)
                val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId) ?: activeSlot
                val socksPort = 10800 + (ProfileManager.androidUserId * 100) + (rSlot * 10) + 1
                val res = IpFetcher.getPublicIpInfo(if (isProxyActive) socksPort else null)
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
        if (!isProxyActive) {
            publicIpInfo = null
        }
        triggerExitNodeCheck()
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("Tap Refresh to inspect kernel logs...") }

    LaunchedEffect(selectedTab, runningSlot, activeSlot) {
        if (selectedTab == 4) {
            withContext(Dispatchers.IO) {
                val l = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
                withContext(Dispatchers.Main) { currentLogs = l.ifEmpty { "No logs recorded." } }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.skyTop, palette.skyMid, palette.skyBottom)
                )
            )
    ) {
        // 1. CLEAN DEEP-SPACE STARFIELD BACKDROP
        CleanStarfieldBackdrop(
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. TOP HEADER + ANIMATED PLANET SLOTS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SolarHeaderBar(
                    isProxyActive = isProxyActive,
                    runningSlot = runningSlot,
                    activeSlot = activeSlot,
                    rootState = rootState,
                    onRecheckRoot = onRecheckRoot
                )
                Spacer(modifier = Modifier.height(10.dp))
                PlanetSlotBar(
                    activeSlot = activeSlot,
                    runningSlot = runningSlot,
                    isProxyActive = isProxyActive,
                    startOnBoot = startOnBoot,
                    bootSlot = bootSlot,
                    configuredSlots = configuredSlots,
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

            // 3. SMOOTH SPRING-ANIMATED TAB VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { fullW -> dir * (fullW / 4) }
                        ) + fadeIn(tween(220))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(170),
                                targetOffsetX = { fullW -> -dir * (fullW / 5) }
                            ) + fadeOut(tween(150))
                        )
                    },
                    label = "tabTransition"
                ) { tabIdx ->
                    when (tabIdx) {
                        0 -> SolarCockpitTab(
                            continuousTimeSec = continuousTimeSec,
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
                            currentRxRate = currentRxRate,
                            currentTxRate = currentTxRate,
                            totalRxBytes = totalRxBytes,
                            totalTxBytes = totalTxBytes,
                            onRefreshIp = { triggerExitNodeCheck() },
                            onOpenIpFighterTab = { selectedTab = 1 },
                            onToggleProxy = {
                                if (isProxyActive && runningSlot == activeSlot) {
                                    onStopProxy { }
                                } else {
                                    if (host.trim().isEmpty()) {
                                        selectedTab = 2
                                        return@SolarCockpitTab
                                    }
                                    saveConfigForSlot(activeSlot, true)
                                    val targets = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
                                    onStartProxy(getCurrentSettings(), targets) { ok, err ->
                                        testStatus = if (!ok) "Error: ${err?.lines()?.lastOrNull()}" else null
                                    }
                                }
                            },
                            isTesting = isTesting,
                            testStatus = testStatus,
                            onPingNode = {
                                if (host.trim().isEmpty()) {
                                    selectedTab = 2
                                    return@SolarCockpitTab
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testStatus = "Pinging ${host.trim()}:${port.trim()}..."
                                scope.launch {
                                    testStatus = when (val r = ProxyTester.testProxy(getCurrentSettings())) {
                                        is TestResult.Success -> "ONLINE • ${r.latencyMs} ms"
                                        is TestResult.Failure -> "FAILED • ${r.error}"
                                    }
                                    isTesting = false
                                }
                            }
                        )

                        1 -> EmbeddedIpFighterWebViewScreen(
                            isProxyActive = isProxyActive,
                            runningSlot = runningSlot,
                            publicIpInfo = publicIpInfo
                        )

                        2 -> SolarNodeConfigTab(
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
                                Toast.makeText(context, "Node P$activeSlot reset", Toast.LENGTH_SHORT).show()
                            },
                            getCurrentSettings = { getCurrentSettings() }
                        )

                        3 -> SolarRoutingTab(
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

                        4 -> SolarLogsTab(
                            activeSlot = activeSlot,
                            runningSlot = runningSlot,
                            logs = currentLogs,
                            onRefresh = {
                                scope.launch(Dispatchers.IO) {
                                    val l = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
                                    withContext(Dispatchers.Main) { currentLogs = l.ifEmpty { "No logs." } }
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

            // 4. ANIMATED BOTTOM NAVIGATION DOCK
            SolarBottomDock(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }
    }
}

// -------------------------------------------------------------
// 1. CLEAN DEEP-SPACE STARFIELD BACKDROP
// -------------------------------------------------------------

@Composable
fun CleanStarfieldBackdrop(
    continuousTimeSec: Float,
    isProxyActive: Boolean
) {
    val palette = LocalCosmicPalette.current

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    (if (isProxyActive) palette.emeraldLive else palette.cosmicViolet).copy(
                        alpha = if (palette.isDark) 0.14f else 0.10f
                    ),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.24f),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.50f, h * 0.24f)
        )

        for (i in 0 until 55) {
            val sx = (w * ((i * 37 + 13) % 100)) / 100f
            val sy = (h * ((i * 61 + 29) % 100)) / 100f
            val twinkle = ((sin(continuousTimeSec * 2.0f + i * 1.7f) + 1f) * 0.5f)
            val sr = (0.9f + (i % 3) * 0.65f).dp.toPx()
            val starCol = when {
                i % 5 == 0 -> palette.sunGold
                i % 3 == 0 -> palette.earthCyan
                palette.isDark -> Color.White
                else -> Color(0xFF475569)
            }
            val starAlpha = if (palette.isDark) (0.18f + twinkle * 0.55f) else (0.14f + twinkle * 0.30f)

            drawCircle(
                color = starCol,
                radius = sr,
                center = Offset(sx, sy),
                alpha = starAlpha
            )
        }
    }
}

// -------------------------------------------------------------
// 2. LIVE 3D 8-PLANET SOLAR SYSTEM STAGE
// -------------------------------------------------------------

private data class OrbitPlanetSpec(
    val name: String,
    val semiMajorFrac: Float,
    val degPerSec: Float,
    val baseRadiusDp: Float,
    val litColor: Color,
    val coreColor: Color,
    val shadowColor: Color,
    val phaseDeg: Float
)

@Composable
fun Interactive3DSolarSystemStage(
    continuousTimeSec: Float,
    isProxyActive: Boolean,
    activeSlot: Int,
    runningSlot: Int?
) {
    val palette = LocalCosmicPalette.current
    val textMeasurer = rememberTextMeasurer()

    val planets = remember {
        listOf(
            OrbitPlanetSpec("Mercury", 0.16f, 34f, 4.4f, Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF334155), 15f),
            OrbitPlanetSpec("Venus", 0.25f, 26f, 5.8f, Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFF78350F), 85f),
            OrbitPlanetSpec("Earth", 0.36f, 20f, 6.8f, Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF0C4A6E), 165f),
            OrbitPlanetSpec("Mars", 0.46f, 16f, 5.5f, Color(0xFFFDA4AF), Color(0xFFE11D48), Color(0xFF881337), 245f),
            OrbitPlanetSpec("Jupiter", 0.60f, 12f, 10.2f, Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFF78350F), 35f),
            OrbitPlanetSpec("Saturn", 0.73f, 9f, 8.6f, Color(0xFFFEF9C3), Color(0xFFEAB308), Color(0xFF713F12), 130f),
            OrbitPlanetSpec("Uranus", 0.85f, 7f, 6.6f, Color(0xFFA5F3FC), Color(0xFF06B6D4), Color(0xFF164E63), 275f),
            OrbitPlanetSpec("Neptune", 0.96f, 5.5f, 6.4f, Color(0xFF93C5FD), Color(0xFF2563EB), Color(0xFF1E3A8A), 330f)
        )
    }

    val labelStyle = TextStyle(
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary
    )
    val measuredLabels = remember(textMeasurer, palette.isDark) {
        planets.map { textMeasurer.measure(it.name, labelStyle) }
    }

    CrystalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp),
        cornerRadius = 24,
        accentColor = if (isProxyActive) palette.emeraldLive else palette.cosmicViolet,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isProxyActive) palette.emeraldLive else palette.sunGold, CircleShape)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (isProxyActive) {
                        "SOLAR ORBIT LIVE • ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()}"
                    } else {
                        "SOLAR SYSTEM • ${SlotPlanetNames[activeSlot].uppercase()} STANDBY"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp,
                    color = if (isProxyActive) palette.emeraldLive else palette.sunGold
                )
            }
            Text(
                text = "3D ORBIT",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = palette.textMuted
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val w = size.width
                val h = size.height
                val center = Offset(w * 0.50f, h * 0.52f)
                val maxRx = w * 0.46f
                val tiltRatio = 0.54f
                val speedFactor = if (isProxyActive) 1.45f else 1.0f

                planets.forEach { p ->
                    val rx = maxRx * p.semiMajorFrac
                    val ry = rx * tiltRatio
                    drawOval(
                        color = palette.orbitTrack,
                        topLeft = Offset(center.x - rx, center.y - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                data class RenderNode(
                    val index: Int,
                    val spec: OrbitPlanetSpec,
                    val x: Float,
                    val y: Float,
                    val depthSin: Float,
                    val scale: Float
                )

                val nodes = planets.mapIndexed { idx, p ->
                    val rx = maxRx * p.semiMajorFrac
                    val ry = rx * tiltRatio
                    val totalDeg = p.phaseDeg + (continuousTimeSec * p.degPerSec * speedFactor)
                    val rad = Math.toRadians(totalDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val px = center.x + rx * cosA
                    val py = center.y + ry * sinA
                    val depthScale = 0.84f + 0.30f * ((sinA + 1f) * 0.5f)
                    RenderNode(idx, p, px, py, sinA, depthScale)
                }

                fun drawSunCore() {
                    val pulse = 0.94f + 0.08f * sin(continuousTimeSec * 2.5f)
                    val sunR = 16.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.sunGold.copy(alpha = 0.65f),
                                palette.sunOrange.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = sunR * 3.0f * pulse
                        ),
                        radius = sunR * 3.0f * pulse,
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFBEB), palette.sunGold, palette.sunOrange),
                            center = Offset(center.x - sunR * 0.2f, center.y - sunR * 0.2f),
                            radius = sunR * 1.2f
                        ),
                        radius = sunR,
                        center = center
                    )
                }

                fun drawPlanetNode(node: RenderNode) {
                    val idx = node.index
                    val p = node.spec
                    val px = node.x
                    val py = node.y
                    val pPos = Offset(px, py)
                    val pr = p.baseRadiusDp.dp.toPx() * node.scale

                    drawCircle(
                        color = p.coreColor.copy(alpha = 0.24f),
                        radius = pr * 1.6f,
                        center = pPos
                    )

                    if (idx == 5) {
                        rotate(degrees = -22f, pivot = pPos) {
                            drawOval(
                                color = Color(0xFFFDE047).copy(alpha = 0.88f),
                                topLeft = Offset(px - pr * 2.2f, py - pr * 0.65f),
                                size = Size(pr * 4.4f, pr * 1.3f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    } else if (idx == 6) {
                        rotate(degrees = 65f, pivot = pPos) {
                            drawOval(
                                color = Color(0xFFA5F3FC).copy(alpha = 0.72f),
                                topLeft = Offset(px - pr * 1.7f, py - pr * 0.45f),
                                size = Size(pr * 3.4f, pr * 0.9f),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                    }

                    val dx = center.x - px
                    val dy = center.y - py
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val lightOff = Offset(px + (dx / dist) * pr * 0.35f, py + (dy / dist) * pr * 0.35f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(p.litColor, p.coreColor, p.shadowColor),
                            center = lightOff,
                            radius = pr * 1.35f
                        ),
                        radius = pr,
                        center = pPos
                    )

                    if (idx == 2) {
                        val mRad = Math.toRadians((continuousTimeSec * 110f).toDouble())
                        drawCircle(
                            color = Color(0xFFE2E8F0),
                            radius = 1.8.dp.toPx() * node.scale,
                            center = Offset(
                                px + (pr * 1.9f * cos(mRad)).toFloat(),
                                py + (pr * 1.2f * sin(mRad)).toFloat()
                            )
                        )
                    }

                    val layout = measuredLabels[idx]
                    val lx = (px - layout.size.width / 2f).coerceIn(2f, w - layout.size.width - 2f)
                    val ly = (py + pr + 2.dp.toPx()).coerceIn(2f, h - layout.size.height - 2f)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(lx, ly),
                        alpha = (0.60f + 0.40f * ((node.depthSin + 1f) * 0.5f)).coerceIn(0.45f, 1f)
                    )
                }

                nodes.filter { it.depthSin < 0f }.sortedBy { it.depthSin }.forEach { drawPlanetNode(it) }
                drawSunCore()
                nodes.filter { it.depthSin >= 0f }.sortedBy { it.depthSin }.forEach { drawPlanetNode(it) }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. HEADER & ANIMATED PLANET SLOT BAR
// -------------------------------------------------------------

@Composable
fun SolarHeaderBar(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    onRecheckRoot: () -> Unit
) {
    val palette = LocalCosmicPalette.current
    val userId = ProfileManager.androidUserId
    val userTag = if (userId == 0) "OWNER • U0" else "USER #$userId"

    CrystalGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                Brush.linearGradient(listOf(palette.earthCyan, palette.cosmicViolet)),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NAMELESS SOLAR",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = palette.textPrimary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isProxyActive) {
                        "$userTag  •  ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()} ORBIT ACTIVE"
                    } else {
                        "$userTag  •  KERNEL TRANSPARENT PROXY"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = if (isProxyActive) palette.emeraldLive else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val rootCol = when (rootState) {
                RootState.GRANTED -> palette.emeraldLive
                RootState.DENIED -> palette.marsCoral
                RootState.CHECKING -> palette.sunGold
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.glassPodFill)
                    .border(1.dp, rootCol, RoundedCornerShape(12.dp))
                    .clickable { onRecheckRoot() }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).background(rootCol, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (rootState == RootState.GRANTED) "ROOT" else "NO ROOT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = palette.textPrimary
                )
            }
        }
    }
}

@Composable
fun PlanetSlotBar(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    configuredSlots: Set<Int>,
    onSelectSlot: (Int) -> Unit
) {
    val palette = LocalCosmicPalette.current
    val pColors = listOf(
        Color(0xFF94A3B8),
        Color(0xFFF59E0B),
        Color(0xFF00E5FF),
        Color(0xFFFF2E63),
        Color(0xFFA855F7)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0 until ProfileManager.MAX_SLOTS).forEach { idx ->
            val sel = idx == activeSlot
            val run = isProxyActive && runningSlot == idx
            val isBoot = startOnBoot && bootSlot == idx

            val scale by animateFloatAsState(
                targetValue = if (sel) 1.05f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "slotScale"
            )

            val borderCol by animateColorAsState(
                targetValue = when {
                    run -> palette.emeraldLive
                    sel -> palette.earthCyan
                    else -> palette.glassRimSubtle
                },
                animationSpec = tween(240),
                label = "slotBorder"
            )

            val fillCol by animateColorAsState(
                targetValue = when {
                    run -> palette.emeraldLive.copy(alpha = 0.16f)
                    sel -> palette.cosmicViolet.copy(alpha = 0.24f)
                    else -> palette.glassFillTop
                },
                animationSpec = tween(240),
                label = "slotFill"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(fillCol)
                    .border(if (sel || run) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(14.dp))
                    .clickable { onSelectSlot(idx) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(pColors[idx], CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "P$idx",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = palette.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        run -> "LIVE"
                        isBoot -> "BOOT"
                        configuredSlots.contains(idx) -> SlotPlanetNames[idx]
                        else -> "Empty"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    color = when {
                        run -> palette.emeraldLive
                        isBoot -> palette.sunGold
                        sel -> palette.earthCyan
                        else -> palette.textMuted
                    },
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. TAB 0: SOLAR COCKPIT + FLUID TURBINE SPEEDOMETER (NO GRAPH LINE)
// -------------------------------------------------------------

@Composable
fun SolarCockpitTab(
    continuousTimeSec: Float,
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
    currentRxRate: Long,
    currentTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    onRefreshIp: () -> Unit,
    onOpenIpFighterTab: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    onPingNode: () -> Unit
) {
    val palette = LocalCosmicPalette.current
    val context = LocalContext.current
    val isCurrentSlotRunning = isProxyActive && runningSlot == activeSlot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. LIVE 3D SOLAR SYSTEM STAGE
        Interactive3DSolarSystemStage(
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            activeSlot = activeSlot,
            runningSlot = runningSlot
        )

        // 2. EXIT IP & COUNTRY FLAG CARD
        CrystalGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenIpFighterTab() },
            cornerRadius = 22,
            accentColor = if (isProxyActive) palette.emeraldLive else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(palette.glassPodFill)
                            .border(1.dp, palette.glassRimBright, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (publicIpInfo != null && publicIpInfo.flagEmoji != "🌐") {
                                publicIpInfo.flagEmoji
                            } else if (isProxyActive) {
                                "🇺🇸"
                            } else {
                                "IP"
                            },
                            fontSize = if (publicIpInfo != null || isProxyActive) 20.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.earthCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                isProxyActive && publicIpInfo != null -> publicIpInfo.ip
                                isProxyActive && isFetchingIp -> "Resolving Exit IP..."
                                isProxyActive && host.isNotBlank() -> host.trim()
                                host.isNotBlank() -> "${host.trim()}:${port.trim()}"
                                else -> "No Server Configured"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = palette.textPrimary,
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isProxyActive && publicIpInfo != null -> "${publicIpInfo.country}  •  PID ${activePid ?: "OK"}"
                                isProxyActive -> "Verifying location...  •  Tap to refresh"
                                else -> "${SlotPlanetNames[activeSlot]} Node (P$activeSlot)  •  ${proxyType.displayTitle}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isProxyActive) palette.emeraldLive else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onRefreshIp() }
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (isProxyActive && sessionStartMs > 0L) {
                        UptimeBadge(sessionStartMs)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.earthCyan.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, palette.earthCyan),
                        modifier = Modifier.clickable { onOpenIpFighterTab() }
                    ) {
                        Text(
                            text = "IPFighter ➔",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = palette.earthCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 3. SMOOTH CONTINUOUS TWIN-TURBINE SPEEDOMETER (NO GRAPH LINE, NO ON/OFF FLICKERING)
        FluidTwinTurbineSpeedometer(
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            rxRate = currentRxRate,
            txRate = currentTxRate,
            totalRx = totalRxBytes,
            totalTx = totalTxBytes
        )

        // 4. PING & ANIMATED MASTER LAUNCH BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPingNode,
                enabled = !isTesting && !isTransitioning,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, palette.earthCyan),
                modifier = Modifier
                    .weight(0.36f)
                    .height(52.dp)
            ) {
                Text(
                    text = if (isTesting) "Wait..." else "Ping P$activeSlot",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = palette.earthCyan,
                    maxLines = 1
                )
            }

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val btnScale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "launchBtnScale"
            )

            val btnBrush = when {
                isTransitioning -> Brush.horizontalGradient(listOf(palette.sunGold, palette.sunOrange))
                isCurrentSlotRunning -> Brush.horizontalGradient(listOf(palette.marsCoral, Color(0xFFBE123C)))
                else -> Brush.horizontalGradient(listOf(palette.cosmicViolet, palette.earthCyan))
            }

            Box(
                modifier = Modifier
                    .weight(0.64f)
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = btnScale
                        scaleY = btnScale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(btnBrush)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !isTransitioning
                    ) { onToggleProxy() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isTransitioning -> "SYNCHRONIZING..."
                        !isProxyActive -> "START ${SlotPlanetNames[activeSlot].uppercase()} (P$activeSlot)"
                        isCurrentSlotRunning -> "STOP TUNNEL (P$activeSlot)"
                        else -> "SWITCH TO P$activeSlot"
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        if (testStatus != null) {
            Text(
                text = testStatus,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (testStatus.startsWith("ONLINE")) palette.emeraldLive else palette.marsCoral,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 5. FLUID TWIN-TURBINE SPEEDOMETER (NO GRAPH LINE, NO ON/OFF FLICKER)
// -------------------------------------------------------------

private fun realisticSpeedFraction(bytesPerSec: Long): Float {
    if (bytesPerSec <= 0L) return 0f
    val kb = bytesPerSec / 1024.0
    return when {
        kb <= 64.0 -> (0.08 + (kb / 64.0) * 0.22).toFloat()
        kb <= 512.0 -> (0.30 + ((kb - 64.0) / (512.0 - 64.0)) * 0.28).toFloat()
        kb <= 4096.0 -> (0.58 + ((kb - 512.0) / (4096.0 - 512.0)) * 0.26).toFloat()
        else -> (0.84 + (ln(kb / 4096.0 + 1.0) / ln(16.0)) * 0.16).toFloat().coerceAtMost(1.0f)
    }
}

@Composable
fun FluidTwinTurbineSpeedometer(
    continuousTimeSec: Float,
    isProxyActive: Boolean,
    rxRate: Long,
    txRate: Long,
    totalRx: Long,
    totalTx: Long
) {
    // 900ms continuous easing so the numbers and turbine rings glide smoothly without snapping to 0
    val animatedRxBytes by animateFloatAsState(
        targetValue = if (isProxyActive) rxRate.toFloat() else 0f,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "fluidRxBytes"
    )
    val animatedTxBytes by animateFloatAsState(
        targetValue = if (isProxyActive) txRate.toFloat() else 0f,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "fluidTxBytes"
    )

    // Keep a gentle baseline glow when the tunnel is active so the gauge never looks "turned off"
    val rxTargetFrac = if (isProxyActive) {
        realisticSpeedFraction(animatedRxBytes.toLong()).coerceAtLeast(0.10f)
    } else 0f
    val txTargetFrac = if (isProxyActive) {
        realisticSpeedFraction(animatedTxBytes.toLong()).coerceAtLeast(0.10f)
    } else 0f

    val smoothRxFrac by animateFloatAsState(
        targetValue = rxTargetFrac,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "smoothRxFrac"
    )
    val smoothTxFrac by animateFloatAsState(
        targetValue = txTargetFrac,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "smoothTxFrac"
    )

    val palette = LocalCosmicPalette.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TurbineGaugeCard(
            modifier = Modifier.weight(1f),
            title = "DOWNLOAD",
            displayRateBytes = animatedRxBytes.toLong(),
            totalBytes = totalRx,
            arcFraction = smoothRxFrac,
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            primaryColor = palette.earthCyan,
            secondaryColor = palette.emeraldLive
        )

        TurbineGaugeCard(
            modifier = Modifier.weight(1f),
            title = "UPLOAD",
            displayRateBytes = animatedTxBytes.toLong(),
            totalBytes = totalTx,
            arcFraction = smoothTxFrac,
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            primaryColor = palette.cosmicViolet,
            secondaryColor = palette.sunGold
        )
    }
}

@Composable
private fun TurbineGaugeCard(
    modifier: Modifier = Modifier,
    title: String,
    displayRateBytes: Long,
    totalBytes: Long,
    arcFraction: Float,
    continuousTimeSec: Float,
    isProxyActive: Boolean,
    primaryColor: Color,
    secondaryColor: Color
) {
    val palette = LocalCosmicPalette.current

    CrystalGlassCard(
        modifier = modifier,
        cornerRadius = 22,
        accentColor = if (isProxyActive) primaryColor else null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Continuous Rotating Turbine Ring + Smooth Progress Arc
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val strokeW = 5.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Base Track
                    drawArc(
                        color = palette.glassRimSubtle.copy(alpha = 0.35f),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Inner Rotating Turbine Halo when Tunnel is Active
                    if (isProxyActive) {
                        val spinSpeed = 60f + (arcFraction * 220f)
                        rotate(degrees = (continuousTimeSec * spinSpeed) % 360f, pivot = center) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        primaryColor.copy(alpha = 0.35f),
                                        secondaryColor.copy(alpha = 0.55f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension * 0.34f,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Smooth Active Speed Arc (Never disappears while tunnel is ON)
                    if (arcFraction > 0.01f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(primaryColor, secondaryColor, primaryColor)
                            ),
                            startAngle = 140f,
                            sweepAngle = 260f * arcFraction.coerceIn(0.08f, 1f),
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }

                Text(
                    text = splitSpeedUnit(displayRateBytes),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = primaryColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = splitSpeedValue(displayRateBytes),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatBytes(totalBytes),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textMuted,
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 6. TAB 1: LIVE EMBEDDED IPFIGHTER.COM
// (Mounted ONLY when Tab 1 is open; killed immediately on tab exit;
// flushes all stale HTTP/2 sockets before loading so US IP shows live!)
// -------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedIpFighterWebViewScreen(
    isProxyActive: Boolean,
    runningSlot: Int?,
    publicIpInfo: GeoIpResult?
) {
    val palette = LocalCosmicPalette.current
    val scope = rememberCoroutineScope()
    var reloadNonce by remember { mutableIntStateOf(0) }
    var isFlushingSockets by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }

    val targetTz = remember(isProxyActive, publicIpInfo?.country) {
        if (!isProxyActive) ""
        else {
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

    val liveCacheBustAndTzScript = remember(targetTz, isProxyActive) {
        """
        (function() {
            try {
                window.localStorage.clear();
                window.sessionStorage.clear();
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

    fun triggerFreshLiveLoad() {
        isFlushingSockets = true
        scope.launch(Dispatchers.IO) {
            try {
                // Kill all cached HTTP/HTTPS/DNS sockets in the kernel so WebView MUST open a new socket through the current tunnel state
                ProcessBuilder(
                    "su", "-c",
                    "ss -K dport = :443 2>/dev/null; ss -K dport = :80 2>/dev/null; ss -K dport = :853 2>/dev/null; ss -K dport = :53 2>/dev/null; conntrack -F 2>/dev/null"
                ).start().waitFor()
            } catch (_: Exception) {}
            delay(250)
            withContext(Dispatchers.Main) {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                reloadNonce++
                isFlushingSockets = false
            }
        }
    }

    LaunchedEffect(isProxyActive, runningSlot) {
        triggerFreshLiveLoad()
    }

    // Ensure WebView cookies/storage are wiped when leaving the IPFighter tab
    DisposableEffect(Unit) {
        onDispose {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    CrystalGlassCard(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 22,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isProxyActive) palette.emeraldLive else palette.sunGold, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isProxyActive) {
                        "LIVE IPFIGHTER • ${publicIpInfo?.ip ?: "TUNNEL ON"}"
                    } else {
                        "LIVE IPFIGHTER • DIRECT"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = palette.textPrimary
                )
            }

            Button(
                onClick = { triggerFreshLiveLoad() },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.earthCyan,
                    contentColor = Color.White
                )
            ) {
                Text("↻ Reload Live", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 4.dp),
                color = palette.earthCyan,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, palette.glassRimBright, RoundedCornerShape(18.dp))
        ) {
            if (!isFlushingSockets) {
                key(reloadNonce) {
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
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = false
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString =
                                        "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        view?.evaluateJavascript(liveCacheBustAndTzScript, null)
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        view?.evaluateJavascript(liveCacheBustAndTzScript, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        loadProgress = 100
                                        view?.evaluateJavascript(liveCacheBustAndTzScript, null)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean = false
                                }
                                val noCacheHeaders = mapOf(
                                    "Cache-Control" to "no-cache, no-store, must-revalidate",
                                    "Pragma" to "no-cache",
                                    "Expires" to "0",
                                    "Connection" to "close"
                                )
                                loadUrl("https://ipfighter.com/?live=${System.currentTimeMillis()}", noCacheHeaders)
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
}

// -------------------------------------------------------------
// 7. TAB 2: NODE CONFIG | TAB 3: ROUTING | TAB 4: LOGS | DOCK
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarNodeConfigTab(
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
    onImportUri: (ProxySettings) -> Unit,
    onResetSlot: () -> Unit,
    getCurrentSettings: () -> ProxySettings
) {
    val palette = LocalCosmicPalette.current
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
        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "QUICK URI IMPORT & SOCKET PROBE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = palette.sunGold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Paste vless://, trojan://, ss://, hy2://, socks5:// or test reachability",
                fontSize = 10.5.sp,
                color = palette.textSecondary
            )

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
                    border = BorderStroke(1.dp, palette.earthCyan)
                ) {
                    Text("Paste URI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.earthCyan)
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
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cosmicViolet, contentColor = Color.White)
                ) {
                    Text(
                        text = if (isCheckingAlive) "Probing..." else "Probe Socket",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (aliveResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                when (val res = aliveResult) {
                    is TestResult.Success -> {
                        Text(
                            text = "ONLINE • HANDSHAKE VERIFIED (${res.latencyMs} ms)",
                            color = palette.emeraldLive,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is TestResult.Failure -> {
                        Text(
                            text = "UNREACHABLE • ${res.error}",
                            color = palette.marsCoral,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    null -> {}
                }
            }
        }

        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${SlotPlanetNames[activeSlot].uppercase()} NODE (P$activeSlot) PARAMETERS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = palette.sunGold
                )
                Text(
                    text = "Reset P$activeSlot",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.marsCoral,
                    modifier = Modifier.clickable { onResetSlot() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = palette.textSecondary)
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
                        label = { Text(t.displayTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("TRANSPORT & WEBRTC MODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = transportMode == TransportMode.TCP_AND_UDP,
                    onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                    label = { Text("TCP + UDP (WebRTC Safe)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = transportMode == TransportMode.TCP_ONLY,
                    onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                    label = { Text("TCP Only", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("IP STACK STRATEGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Pair(IpMode.IPV4_ONLY, "IPv4 Only"),
                    Pair(IpMode.DUAL_STACK, "Dual-Stack"),
                    Pair(IpMode.IPV6_ONLY, "IPv6 Only")
                ).forEach { (mode, lbl) ->
                    FilterChip(
                        selected = ipMode == mode,
                        onClick = { onIpModeChange(mode); onConfigChanged() },
                        label = { Text(lbl, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { onHostChange(it); onConfigChanged() },
                    label = { Text("Server Host / IP") },
                    placeholder = { Text("48.45.153.215") },
                    modifier = Modifier.weight(0.68f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { onPortChange(it); onConfigChanged() },
                    label = { Text("Port") },
                    placeholder = { Text("1080") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.32f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = proxyType,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "protocolFields"
            ) { targetProtocol ->
                Column {
                    when (targetProtocol) {
                        ProxyType.SOCKS5, ProxyType.HTTP -> {
                            var showPass by remember { mutableStateOf(false) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { onUsernameChange(it); onConfigChanged() },
                                    label = { Text("Username") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { onPasswordChange(it); onConfigChanged() },
                                    label = { Text("Password") },
                                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Text(
                                            text = if (showPass) "Hide" else "Show",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.earthCyan,
                                            modifier = Modifier.clickable { showPass = !showPass }.padding(end = 10.dp)
                                        )
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
                                label = { Text("Cipher Method") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { onPasswordChange(it); onConfigChanged() },
                                label = { Text("Password / PSK") },
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Text(
                                        text = if (showPass) "Hide" else "Show",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.earthCyan,
                                        modifier = Modifier.clickable { showPass = !showPass }.padding(end = 10.dp)
                                    )
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
                                label = { Text("VLESS UUID") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = sni,
                                onValueChange = { onSniChange(it); onConfigChanged() },
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
                                label = { Text("${targetProtocol.displayTitle} Password") },
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Text(
                                        text = if (showPass) "Hide" else "Show",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.earthCyan,
                                        modifier = Modifier.clickable { showPass = !showPass }.padding(end = 10.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = sni,
                                onValueChange = { onSniChange(it); onConfigChanged() },
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarRoutingTab(
    startOnBoot: Boolean,
    bootSlot: Int,
    routeHotspot: Boolean,
    routeWholeProfile: Boolean,
    selectedPackages: Set<String>,
    installedApps: List<AppItem>,
    onStartOnBootChange: (Boolean) -> Unit,
    onBootSlotChange: (Int) -> Unit,
    onRouteHotspotChange: (Boolean) -> Unit,
    onRouteWholeProfileChange: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    val palette = LocalCosmicPalette.current
    val uid = ProfileManager.androidUserId
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Start on Boot", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (startOnBoot) "Armed for P$bootSlot (/data/adb/service.d)" else "Manual launch only",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                AnimatedCosmicSwitch(checked = startOnBoot, onCheckedChange = onStartOnBootChange)
            }

            AnimatedVisibility(
                visible = startOnBoot,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0 until ProfileManager.MAX_SLOTS).forEach { s ->
                        FilterChip(
                            selected = bootSlot == s,
                            onClick = { onBootSlotChange(s) },
                            label = { Text("P$s", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.glassRimSubtle, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hotspot & Tethering Bridge", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Share tunnel with Wi-Fi / USB clients", fontSize = 11.sp, color = palette.textSecondary)
                }
                AnimatedCosmicSwitch(
                    checked = routeHotspot && uid == 0,
                    enabled = uid == 0,
                    onCheckedChange = onRouteHotspotChange
                )
            }

            HorizontalDivider(color = palette.glassRimSubtle, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tunnel All Apps (User #$uid)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (routeWholeProfile) "Global profile active" else "${selectedPackages.size} apps selected",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                AnimatedCosmicSwitch(checked = routeWholeProfile, onCheckedChange = onRouteWholeProfileChange)
            }
        }

        AnimatedVisibility(
            visible = !routeWholeProfile,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.weight(1f)
        ) {
            CrystalGlassCard(modifier = Modifier.fillMaxSize()) {
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
                    Text("${selectedPackages.size} selected", fontSize = 11.sp, color = palette.textSecondary)
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("Select All", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.earthCyan)
                        }
                        TextButton(onClick = onClearAll) {
                            Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.marsCoral)
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleApp(app.packageName) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1)
                                Text("${app.packageName} • UID ${app.uid}", fontSize = 10.5.sp, color = palette.textSecondary, maxLines = 1)
                            }
                            AnimatedCosmicSwitch(checked = checked, onCheckedChange = { onToggleApp(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SolarLogsTab(
    activeSlot: Int,
    runningSlot: Int?,
    logs: String,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val palette = LocalCosmicPalette.current
    val context = LocalContext.current
    CrystalGlassCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KERNEL LOGS (P${runningSlot ?: activeSlot})",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = palette.sunGold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", logs))
                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = palette.sunGold, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onClear) { Text("Clear", color = palette.marsCoral, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onRefresh) { Text("Refresh", color = palette.earthCyan, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.glassPodFill)
                .padding(12.dp)
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.textPrimary,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun SolarBottomDock(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val palette = LocalCosmicPalette.current
    val tabs = listOf("Cockpit", "IPFighter", "Nodes", "Routing", "Logs")

    CrystalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 20,
        contentPadding = PaddingValues(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                val sel = selectedTab == idx
                val scale by animateFloatAsState(
                    targetValue = if (sel) 1.06f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "dockScale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (sel) Brush.horizontalGradient(listOf(palette.cosmicViolet, palette.earthCyan))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onSelectTab(idx) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = if (sel) Color.White else palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun UptimeBadge(sessionStartMs: Long) {
    val palette = LocalCosmicPalette.current
    var sec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sessionStartMs) {
        while (isActive) {
            sec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            delay(1000)
        }
    }
    Text(
        text = String.format("%02d:%02d", (sec % 3600) / 60, sec % 60),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        color = palette.emeraldLive
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
                } else null
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
