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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

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
// AUTOMATIC SYSTEM DARK & LIGHT COSMIC GLASS PALETTE
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
    val marsCoral: Color,
    val emeraldLive: Color,
    val lightningColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

private val DarkCosmicPalette = CosmicPalette(
    isDark = true,
    skyTop = Color(0xFF03050C),
    skyMid = Color(0xFF070D1F),
    skyBottom = Color(0xFF0C1633),
    glassFillTop = Color(0x33162447),
    glassFillBottom = Color(0x1F0D162E),
    glassPodFill = Color(0x2E1E2F5C),
    glassRimBright = Color(0x7594A3B8),
    glassRimSubtle = Color(0x2464748B),
    orbitTrack = Color(0x4094A3B8),
    sunGold = Color(0xFFFFBE0B),
    sunOrange = Color(0xFFFB5607),
    earthCyan = Color(0xFF00D2FF),
    marsCoral = Color(0xFFFF3366),
    emeraldLive = Color(0xFF00F5A0),
    lightningColor = Color(0xFF7DD3FC),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8)
)

private val LightCosmicPalette = CosmicPalette(
    isDark = false,
    skyTop = Color(0xFFDBEAFE),
    skyMid = Color(0xFFE0E7FF),
    skyBottom = Color(0xFFEDE9FE),
    glassFillTop = Color(0x52FFFFFF),
    glassFillBottom = Color(0x30F8FAFC),
    glassPodFill = Color(0x47FFFFFF),
    glassRimBright = Color(0x99FFFFFF),
    glassRimSubtle = Color(0x4564748B),
    orbitTrack = Color(0x45334155),
    sunGold = Color(0xFFD97706),
    sunOrange = Color(0xFFEA580C),
    earthCyan = Color(0xFF0284C7),
    marsCoral = Color(0xFFE11D48),
    emeraldLive = Color(0xFF059669),
    lightningColor = Color(0xFF6366F1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF1E293B),
    textMuted = Color(0xFF475569)
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
                    primary = palette.sunGold,
                    secondary = palette.earthCyan,
                    background = palette.skyTop,
                    surface = palette.glassFillTop,
                    onSurface = palette.textPrimary
                )
            } else {
                lightColorScheme(
                    primary = palette.sunOrange,
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

// Accurate Physical Interface Reader (Avoids counting local loopback twice)
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
    // 5 Tabs: 0 = Cockpit, 1 = Live IPFighter WebView, 2 = Nodes, 3 = Routing, 4 = Logs
    var selectedTab by remember { mutableIntStateOf(0) }

    // Continuous 120Hz Monotonic Time Clock (Never resets to 0 so orbits never jump/restart!)
    var continuousTimeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = System.nanoTime()
        while (isActive) {
            withInfiniteAnimationFrameMillis {
                continuousTimeSec = (System.nanoTime() - startNanos) / 1_000_000_000f
            }
        }
    }

    // Tap-Reactive Solar System & Lightning State
    var tapPos by remember { mutableStateOf(Offset.Unspecified) }
    var lightningSeed by remember { mutableIntStateOf(1) }
    val rippleAnim = remember { Animatable(1f) }
    val orbitBoost = remember { Animatable(0f) }

    fun pulseSolarTap(pos: Offset = Offset.Unspecified) {
        tapPos = pos
        lightningSeed = Random.nextInt(1, 10000)
        scope.launch {
            rippleAnim.snapTo(0f)
            rippleAnim.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
        scope.launch {
            orbitBoost.animateTo(
                targetValue = orbitBoost.value + 36f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Accurate Physical Network Telemetry
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var rxSpectrum by remember { mutableStateOf(List(30) { 0L }) }
    var txSpectrum by remember { mutableStateOf(List(30) { 0L }) }
    var currentRxRate by remember { mutableLongStateOf(0L) }
    var currentTxRate by remember { mutableLongStateOf(0L) }
    var peakRxRate by remember { mutableLongStateOf(0L) }
    var peakTxRate by remember { mutableLongStateOf(0L) }
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

            while (isActive) {
                delay(650)
                val (currRxRaw, currTxRaw) = withContext(Dispatchers.IO) { readPhysicalNetworkBytes() }
                val currRx = currRxRaw.coerceAtLeast(prevRx)
                val currTx = currTxRaw.coerceAtLeast(prevTx)
                val now = System.currentTimeMillis()
                val dt = (now - prevTime).coerceAtLeast(1L) / 1000.0

                val rxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)
                val txRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)

                currentRxRate = rxRate
                currentTxRate = txRate
                if (rxRate > peakRxRate) peakRxRate = rxRate
                if (txRate > peakTxRate) peakTxRate = txRate
                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)

                rxSpectrum = rxSpectrum.drop(1) + rxRate
                txSpectrum = txSpectrum.drop(1) + txRate

                prevRx = currRx
                prevTx = currTx
                prevTime = now
            }
        } else {
            sessionStartMs = 0L
            currentRxRate = 0L
            currentTxRate = 0L
            peakRxRate = 0L
            peakTxRate = 0L
            totalRxBytes = 0L
            totalTxBytes = 0L
            rxSpectrum = List(30) { 0L }
            txSpectrum = List(30) { 0L }
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

    fun triggerExitNodeCheck() {
        isFetchingIp = true
        scope.launch(Dispatchers.IO) {
            delay(450)
            val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId) ?: activeSlot
            val socksPort = 10800 + (ProfileManager.androidUserId * 100) + (rSlot * 10) + 1
            var res = IpFetcher.getPublicIpInfo(if (isProxyActive) socksPort else null)
            if (res == null) {
                delay(900)
                res = IpFetcher.getPublicIpInfo(if (isProxyActive) socksPort else null)
            }
            withContext(Dispatchers.Main) {
                publicIpInfo = res
                isFetchingIp = false
            }
        }
    }

    LaunchedEffect(isProxyActive, runningSlot) {
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
            .pointerInput(Unit) {
                detectTapGestures(onPress = { offset -> pulseSolarTap(offset) })
            }
    ) {
        // 1. LIVE STARS, COSMIC LIGHTNING BOLTS & FLOATING EMOJIS BACKGROUND (DARK & LIGHT MODE)
        LiveStarsLightningEmojiBackdrop(
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            tapPos = tapPos,
            rippleProgress = rippleAnim.value,
            lightningSeed = lightningSeed
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. SPACIOUS TOP HEADER + PLANET SLOTS
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
                    onRecheckRoot = { pulseSolarTap(); onRecheckRoot() }
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
                        pulseSolarTap()
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

            // 3. SMOOTH ANIMATED VIEWPORT (WITH PORTAL TRANSITION FOR IPFIGHTER)
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
                        ) + fadeIn(tween(260)) + scaleIn(
                            initialScale = 0.95f,
                            animationSpec = tween(260)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(200),
                                targetOffsetX = { fullW -> -dir * (fullW / 5) }
                            ) + fadeOut(tween(180)) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(180)
                            )
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
                            orbitBoostDeg = orbitBoost.value,
                            tapPos = tapPos,
                            rippleProgress = rippleAnim.value,
                            sessionStartMs = sessionStartMs,
                            activePid = activePid,
                            publicIpInfo = publicIpInfo,
                            isFetchingIp = isFetchingIp,
                            host = host,
                            port = port,
                            proxyType = proxyType,
                            transportMode = transportMode,
                            routeWholeProfile = routeWholeProfile,
                            selectedAppsCount = selectedPackages.size,
                            currentRxRate = currentRxRate,
                            currentTxRate = currentTxRate,
                            peakRxRate = peakRxRate,
                            peakTxRate = peakTxRate,
                            totalRxBytes = totalRxBytes,
                            totalTxBytes = totalTxBytes,
                            rxSpectrum = rxSpectrum,
                            txSpectrum = txSpectrum,
                            onRefreshIp = { pulseSolarTap(); triggerExitNodeCheck() },
                            onOpenIpFighterTab = { pulseSolarTap(); selectedTab = 1 },
                            onToggleProxy = {
                                pulseSolarTap()
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
                                pulseSolarTap()
                                if (host.trim().isEmpty()) {
                                    selectedTab = 2
                                    return@SolarCockpitTab
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testStatus = "Pinging ${host.trim()}:${port.trim()}..."
                                scope.launch {
                                    testStatus = when (val r = ProxyTester.testProxy(getCurrentSettings())) {
                                        is TestResult.Success -> "✓ Orbit Link Online (${r.latencyMs} ms)"
                                        is TestResult.Failure -> "✕ Failed: ${r.error}"
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
                            onProxyTypeChange = { pulseSolarTap(); proxyType = it },
                            onTransportModeChange = { pulseSolarTap(); transportMode = it },
                            onIpModeChange = { pulseSolarTap(); ipMode = it },
                            onImportUri = { imported ->
                                pulseSolarTap()
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
                                pulseSolarTap()
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
                                pulseSolarTap()
                                startOnBoot = it
                                if (it) {
                                    bootSlot = activeSlot
                                    saveConfigForSlot(activeSlot, true)
                                } else {
                                    scope.launch(Dispatchers.IO) { BootManager.removeBootScript() }
                                }
                            },
                            onBootSlotChange = {
                                pulseSolarTap()
                                bootSlot = it
                                saveConfigForSlot(activeSlot, true)
                            },
                            onRouteHotspotChange = {
                                pulseSolarTap()
                                routeHotspot = it
                                saveConfigForSlot(activeSlot, true)
                            },
                            onRouteWholeProfileChange = {
                                pulseSolarTap()
                                routeWholeProfile = it
                                saveConfigForSlot(activeSlot, true)
                            },
                            onToggleApp = { pkg ->
                                pulseSolarTap()
                                selectedPackages = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg
                                saveConfigForSlot(activeSlot)
                            },
                            onSelectAll = {
                                pulseSolarTap()
                                selectedPackages = installedApps.map { it.packageName }.toSet()
                                saveConfigForSlot(activeSlot)
                            },
                            onClearAll = {
                                pulseSolarTap()
                                selectedPackages = emptySet()
                                saveConfigForSlot(activeSlot)
                            }
                        )

                        4 -> SolarLogsTab(
                            activeSlot = activeSlot,
                            runningSlot = runningSlot,
                            logs = currentLogs,
                            onRefresh = {
                                pulseSolarTap()
                                scope.launch(Dispatchers.IO) {
                                    val l = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
                                    withContext(Dispatchers.Main) { currentLogs = l.ifEmpty { "No logs." } }
                                }
                            },
                            onClear = {
                                pulseSolarTap()
                                currentLogs = "Cleared."
                                scope.launch(Dispatchers.IO) {
                                    ProxyController.clearLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
                                }
                            }
                        )
                    }
                }
            }

            // 4. SPACIOUS 5-TAB SOLAR NAVIGATION DOCK
            SolarBottomDock(
                selectedTab = selectedTab,
                onSelectTab = {
                    pulseSolarTap()
                    selectedTab = it
                }
            )
        }
    }
}

// -------------------------------------------------------------
// 1. LIVE STARS, COSMIC LIGHTNING & FLOATING EMOJIS BACKGROUND
// -------------------------------------------------------------

private data class FloatingEmojiSpec(
    val emoji: String,
    val xFrac: Float,
    val baseYFrac: Float,
    val speed: Float,
    val swayAmp: Float,
    val phase: Float
)

@Composable
fun LiveStarsLightningEmojiBackdrop(
    continuousTimeSec: Float,
    isProxyActive: Boolean,
    tapPos: Offset,
    rippleProgress: Float,
    lightningSeed: Int
) {
    val palette = LocalCosmicPalette.current
    val textMeasurer = rememberTextMeasurer()

    val floatingEmojis = remember {
        listOf(
            FloatingEmojiSpec("✨", 0.10f, 0.18f, 0.022f, 18f, 0.5f),
            FloatingEmojiSpec("⚡", 0.86f, 0.26f, 0.028f, 22f, 1.4f),
            FloatingEmojiSpec("🪐", 0.16f, 0.52f, 0.016f, 24f, 2.2f),
            FloatingEmojiSpec("🌟", 0.82f, 0.64f, 0.024f, 16f, 3.1f),
            FloatingEmojiSpec("☄️", 0.48f, 0.14f, 0.032f, 28f, 4.0f),
            FloatingEmojiSpec("🚀", 0.74f, 0.84f, 0.026f, 20f, 5.2f),
            FloatingEmojiSpec("🌌", 0.24f, 0.82f, 0.018f, 15f, 1.9f),
            FloatingEmojiSpec("🛸", 0.62f, 0.42f, 0.021f, 25f, 2.8f),
            FloatingEmojiSpec("⭐", 0.34f, 0.32f, 0.025f, 17f, 0.9f),
            FloatingEmojiSpec("💫", 0.90f, 0.48f, 0.029f, 21f, 4.7f)
        )
    }

    val emojiLayouts = remember(textMeasurer) {
        floatingEmojis.map { spec ->
            textMeasurer.measure(
                text = spec.emoji,
                style = TextStyle(fontSize = 15.sp)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height
        val speedMult = if (isProxyActive) 1.45f else 1.0f
        val t = continuousTimeSec * speedMult

        // 1. Soft Ambient Nebula Glows
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    (if (isProxyActive) palette.emeraldLive else palette.sunGold).copy(
                        alpha = if (palette.isDark) 0.14f else 0.12f
                    ),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.25f),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.50f, h * 0.25f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.earthCyan.copy(alpha = if (palette.isDark) 0.12f else 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.20f, h * 0.75f),
                radius = w * 0.65f
            ),
            radius = w * 0.65f,
            center = Offset(w * 0.20f, h * 0.75f)
        )

        // 2. 60 Live Twinkling Stars Across Sky
        for (i in 0 until 60) {
            val sx = (w * ((i * 37 + 13) % 100)) / 100f
            val sy = (h * ((i * 61 + 29) % 100)) / 100f
            val twinkle = ((sin(t * 2.8f + i * 1.7f) + 1f) * 0.5f)
            val sr = (1.0f + (i % 3) * 0.75f).dp.toPx()
            val starCol = when {
                i % 5 == 0 -> palette.sunGold
                i % 3 == 0 -> palette.earthCyan
                palette.isDark -> Color.White
                else -> Color(0xFF334155)
            }
            val starAlpha = if (palette.isDark) (0.22f + twinkle * 0.65f) else (0.18f + twinkle * 0.38f)

            drawCircle(
                color = starCol,
                radius = sr,
                center = Offset(sx, sy),
                alpha = starAlpha
            )
        }

        // 3. Live Floating Space Emojis (Drifting Upward & Swaying Gently in Dark & Light Mode)
        floatingEmojis.forEachIndexed { idx, spec ->
            val rawY = (spec.baseYFrac - (t * spec.speed)) % 1.0f
            val normY = if (rawY < 0f) rawY + 1.0f else rawY
            val ex = (w * spec.xFrac) + (sin(t * 1.5f + spec.phase) * spec.swayAmp.dp.toPx())
            val ey = h * normY

            drawText(
                textLayoutResult = emojiLayouts[idx],
                topLeft = Offset(ex, ey),
                alpha = if (palette.isDark) 0.42f else 0.36f
            )
        }

        // 4. Periodic Ambient Cosmic Lightning Flash + Tap-Triggered Lightning Bolt
        val ambientCycle = (t * 0.7f) % 4.0f
        val showAmbientBolt = ambientCycle < 0.28f
        val showTapBolt = rippleProgress < 0.55f && tapPos != Offset.Unspecified

        if (showAmbientBolt || showTapBolt) {
            val boltAlpha = if (showTapBolt) {
                ((0.55f - rippleProgress) / 0.55f).coerceIn(0f, 1f)
            } else {
                ((0.28f - ambientCycle) / 0.28f).coerceIn(0f, 0.75f)
            }

            val seed = if (showTapBolt) lightningSeed else ((t * 0.7f).toInt() * 31 + 7)
            val startX = if (showTapBolt) {
                tapPos.x.coerceIn(w * 0.1f, w * 0.9f)
            } else {
                w * (0.15f + ((seed * 43) % 70) / 100f)
            }
            val startY = if (showTapBolt) (tapPos.y - h * 0.25f).coerceAtLeast(0f) else h * 0.04f
            val endY = if (showTapBolt) (tapPos.y + h * 0.18f).coerceAtMost(h) else h * 0.52f

            val boltPath = Path()
            val branchPath = Path()
            var currX = startX
            var currY = startY
            boltPath.moveTo(currX, currY)

            val segments = 8
            val stepY = (endY - startY) / segments
            for (s in 1..segments) {
                val offset = (((seed * s * 29) % 41) - 20) * 1.6f.dp.toPx()
                currX = (currX + offset).coerceIn(16f, w - 16f)
                currY += stepY
                boltPath.lineTo(currX, currY)

                if (s == 3 || s == 5) {
                    branchPath.moveTo(currX, currY)
                    branchPath.lineTo(currX + offset * 1.4f, currY + stepY * 1.3f)
                    branchPath.lineTo(currX + offset * 2.1f, currY + stepY * 2.2f)
                }
            }

            // Outer Electric Glow
            drawPath(
                path = boltPath,
                color = palette.lightningColor.copy(alpha = boltAlpha * 0.45f),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Core Lightning Bolt
            drawPath(
                path = boltPath,
                color = if (palette.isDark) Color.White.copy(alpha = boltAlpha) else palette.lightningColor.copy(alpha = boltAlpha),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Secondary Branches
            drawPath(
                path = branchPath,
                color = palette.sunGold.copy(alpha = boltAlpha * 0.75f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

// -------------------------------------------------------------
// 2. SEAMLESS 360° NEVER-RESTARTING 8-PLANET SOLAR SYSTEM STAGE
// (Zero trailing lines, continuous monotonic orbits, full 360° loops)
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
    orbitBoostDeg: Float,
    tapPos: Offset,
    rippleProgress: Float,
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
        accentColor = if (isProxyActive) palette.emeraldLive else palette.sunGold,
        contentPadding = PaddingValues(12.dp)
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
                        "SOLAR SYSTEM • TAP TO BOOST ORBITS & LIGHTNING"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp,
                    color = if (isProxyActive) palette.emeraldLive else palette.sunGold
                )
            }
            Text(
                text = "360° LIVE",
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
                val speedFactor = if (isProxyActive) 1.55f else 1.0f

                // 1. Clean Elliptical Orbit Rings (No partial trail lines!)
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

                // 2. Compute Continuous 360° Positions for All 8 Planets (Never resets!)
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
                    val totalDeg = p.phaseDeg +
                        (continuousTimeSec * p.degPerSec * speedFactor) +
                        (orbitBoostDeg * (p.degPerSec / 18f))
                    val rad = Math.toRadians(totalDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val px = center.x + rx * cosA
                    val py = center.y + ry * sinA
                    val depthScale = 0.84f + 0.30f * ((sinA + 1f) * 0.5f)
                    RenderNode(idx, p, px, py, sinA, depthScale)
                }

                fun drawSunCore() {
                    val pulse = 0.94f + 0.10f * sin(continuousTimeSec * 2.5f)
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

                    // Soft Halo
                    drawCircle(
                        color = p.coreColor.copy(alpha = 0.24f),
                        radius = pr * 1.6f,
                        center = pPos
                    )

                    // Saturn & Uranus Rings
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

                    // 3D Sun-Lit Planet Sphere
                    val dx = center.x - px
                    val dy = center.y - py
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
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

                    // Earth's Moon
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

                    // Floating Planet Label
                    val layout = measuredLabels[idx]
                    val lx = (px - layout.size.width / 2f).coerceIn(2f, w - layout.size.width - 2f)
                    val ly = (py + pr + 2.dp.toPx()).coerceIn(2f, h - layout.size.height - 2f)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(lx, ly),
                        alpha = (0.60f + 0.40f * ((node.depthSin + 1f) * 0.5f)).coerceIn(0.45f, 1f)
                    )
                }

                // 3. True 3D Depth Sorting (Back Planets -> Sun -> Front Planets)
                nodes.filter { it.depthSin < 0f }.sortedBy { it.depthSin }.forEach { drawPlanetNode(it) }
                drawSunCore()
                nodes.filter { it.depthSin >= 0f }.sortedBy { it.depthSin }.forEach { drawPlanetNode(it) }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. SPACIOUS HEADER & PLANET SLOT BAR (UN-GLUED TYPOGRAPHY)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☀️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NAMELESS SOLAR",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = palette.textPrimary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isProxyActive) {
                        "$userTag  •  ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()} ORBIT ACTIVE"
                    } else {
                        "$userTag  •  TAP ANYWHERE FOR LIGHTNING & ORBIT BOOST"
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
        Color(0xFF38BDF8),
        Color(0xFFFB7185),
        Color(0xFFFACC15)
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
                targetValue = if (sel) 1.04f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "slotScale"
            )

            val borderCol by animateColorAsState(
                targetValue = when {
                    run -> palette.emeraldLive
                    sel -> palette.sunGold
                    else -> palette.glassRimSubtle
                },
                animationSpec = tween(250),
                label = "slotBorder"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel || run) palette.glassPodFill else palette.glassFillTop)
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
                        isBoot -> palette.sunOrange
                        sel -> palette.sunGold
                        else -> palette.textMuted
                    },
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. TAB 0: SOLAR COCKPIT + SYNCHRONIZED TWIN-PULSE SPEEDOMETER
// -------------------------------------------------------------

@Composable
fun SolarCockpitTab(
    continuousTimeSec: Float,
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    orbitBoostDeg: Float,
    tapPos: Offset,
    rippleProgress: Float,
    sessionStartMs: Long,
    activePid: String?,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    host: String,
    port: String,
    proxyType: ProxyType,
    transportMode: TransportMode,
    routeWholeProfile: Boolean,
    selectedAppsCount: Int,
    currentRxRate: Long,
    currentTxRate: Long,
    peakRxRate: Long,
    peakTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxSpectrum: List<Long>,
    txSpectrum: List<Long>,
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
        // 1. UNOBSTRUCTED 360° SOLAR SYSTEM STAGE
        Interactive3DSolarSystemStage(
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            orbitBoostDeg = orbitBoostDeg,
            tapPos = tapPos,
            rippleProgress = rippleProgress,
            activeSlot = activeSlot,
            runningSlot = runningSlot
        )

        // 2. SPACIOUS EXIT TUNNEL & IPFIGHTER PORTAL CARD
        CrystalGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenIpFighterTab() },
            cornerRadius = 22,
            accentColor = if (isProxyActive) palette.emeraldLive else null,
            contentPadding = PaddingValues(16.dp)
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
                    Text(
                        text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "🪐",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isProxyActive) {
                                when {
                                    isFetchingIp -> "Verifying exit tunnel..."
                                    publicIpInfo != null -> publicIpInfo.ip
                                    else -> "Resolving exit IP..."
                                }
                            } else {
                                if (host.isNotBlank()) "${host.trim()}:${port.trim()}" else "No Host Configured"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                if (isProxyActive && publicIpInfo != null) {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("ExitIP", publicIpInfo.ip))
                                    Toast.makeText(context, "Copied: ${publicIpInfo.ip}", Toast.LENGTH_SHORT).show()
                                } else {
                                    onRefreshIp()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isProxyActive) {
                                "${publicIpInfo?.country ?: "Active Tunnel"}  •  PID ${activePid ?: "OK"}"
                            } else {
                                "${SlotPlanetNames[activeSlot]} Node (P$activeSlot)  •  ${proxyType.displayTitle}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp,
                            color = if (isProxyActive) palette.emeraldLive else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                            text = "🌐 IPFighter ➔",
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

        // 3. COMPLETELY REDESIGNED SYNCHRONIZED TWIN-PULSE SPEEDOMETER
        SynchronizedCyberPulseSpeedometer(
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive,
            rxRate = currentRxRate,
            txRate = currentTxRate,
            peakRx = peakRxRate,
            peakTx = peakTxRate,
            totalRx = totalRxBytes,
            totalTx = totalTxBytes,
            rxSpectrum = rxSpectrum,
            txSpectrum = txSpectrum
        )

        // 4. PING & MASTER IGNITION BAR
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
                    .weight(0.38f)
                    .height(52.dp)
            ) {
                Text(
                    text = if (isTesting) "Wait..." else "🛰️ Ping P$activeSlot",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = palette.earthCyan,
                    maxLines = 1
                )
            }

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "btnScale")

            val btnBrush = when {
                isTransitioning -> Brush.horizontalGradient(listOf(palette.sunGold, palette.sunOrange))
                isCurrentSlotRunning -> Brush.horizontalGradient(listOf(palette.marsCoral, Color(0xFFBE123C)))
                else -> Brush.horizontalGradient(listOf(palette.sunOrange, palette.sunGold, palette.earthCyan))
            }

            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .height(52.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(16.dp))
                    .background(btnBrush)
                    .clickable(interactionSource = interaction, indication = null, enabled = !isTransitioning) {
                        onToggleProxy()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isTransitioning -> "SYNCHRONIZING..."
                        !isProxyActive -> "IGNITE ${SlotPlanetNames[activeSlot].uppercase()} (P$activeSlot)"
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
                letterSpacing = 0.4.sp,
                color = if (testStatus.startsWith("✓")) palette.emeraldLive else palette.marsCoral,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 5. SYNCHRONIZED TWIN-PULSE LED & SPLINE SPEEDOMETER
// (Numbers, 22-Segment LED Bars, Particle Stream & Spline Wave 100% Synced)
// -------------------------------------------------------------

private fun realisticSpeedFraction(bytesPerSec: Long): Float {
    if (bytesPerSec < 512L) return 0f
    val kb = bytesPerSec / 1024.0
    return when {
        kb <= 64.0 -> (kb / 64.0 * 0.22).toFloat()
        kb <= 512.0 -> (0.22 + ((kb - 64.0) / (512.0 - 64.0)) * 0.28).toFloat()
        kb <= 4096.0 -> (0.50 + ((kb - 512.0) / (4096.0 - 512.0)) * 0.30).toFloat()
        else -> (0.80 + (ln(kb / 4096.0 + 1.0) / ln(16.0)) * 0.20).toFloat().coerceAtMost(1.0f)
    }
}

@Composable
fun SynchronizedCyberPulseSpeedometer(
    continuousTimeSec: Float,
    isProxyActive: Boolean,
    rxRate: Long,
    txRate: Long,
    peakRx: Long,
    peakTx: Long,
    totalRx: Long,
    totalTx: Long,
    rxSpectrum: List<Long>,
    txSpectrum: List<Long>
) {
    val palette = LocalCosmicPalette.current

    // Smoothly interpolated live rates so numbers, LED segments, and waves move in 100% sync!
    val smoothRxRate by animateFloatAsState(
        targetValue = if (isProxyActive) rxRate.toFloat() else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "smoothRxRate"
    )
    val smoothTxRate by animateFloatAsState(
        targetValue = if (isProxyActive) txRate.toFloat() else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "smoothTxRate"
    )

    val rxFraction = realisticSpeedFraction(smoothRxRate.toLong())
    val txFraction = realisticSpeedFraction(smoothTxRate.toLong())

    CrystalGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24,
        contentPadding = PaddingValues(16.dp)
    ) {
        // Header Row: Title + Total Session Traffic
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYNCHRONIZED VELOCITY TELEMETRY",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.0.sp,
                color = palette.textSecondary
            )
            Text(
                text = "TOTAL  ${formatBytes(totalRx + totalTx)}",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = palette.sunGold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // DOWNLOAD TELEMETRY CHANNEL
        SpeedChannelRow(
            label = "DOWNLOAD",
            icon = "↓",
            rateBytesPerSec = smoothRxRate.toLong(),
            peakBytesPerSec = peakRx,
            totalBytes = totalRx,
            fraction = rxFraction,
            primaryColor = palette.earthCyan,
            secondaryColor = palette.emeraldLive,
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive
        )

        Spacer(modifier = Modifier.height(14.dp))

        // UPLOAD TELEMETRY CHANNEL
        SpeedChannelRow(
            label = "UPLOAD",
            icon = "↑",
            rateBytesPerSec = smoothTxRate.toLong(),
            peakBytesPerSec = peakTx,
            totalBytes = totalTx,
            fraction = txFraction,
            primaryColor = palette.sunGold,
            secondaryColor = palette.sunOrange,
            continuousTimeSec = continuousTimeSec,
            isProxyActive = isProxyActive
        )

        Spacer(modifier = Modifier.height(14.dp))

        // LIVE 60FPS DUAL-STREAM SPLINE WAVEFORM
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.glassPodFill)
                .border(1.dp, palette.glassRimSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val w = size.width
                val h = size.height
                val count = rxSpectrum.size.coerceAtLeast(2)
                val stepX = w / (count - 1).toFloat()

                val rxPath = Path()
                val rxFill = Path()
                val txPath = Path()
                rxFill.moveTo(0f, h)

                for (i in 0 until count) {
                    val x = i * stepX
                    val rFrac = if (i == count - 1) rxFraction else realisticSpeedFraction(rxSpectrum[i])
                    val tFrac = if (i == count - 1) txFraction else realisticSpeedFraction(txSpectrum[i])

                    // Flow speed scales directly with live throughput!
                    val livePhase = continuousTimeSec * (2.2f + (rxFraction + txFraction) * 6f)
                    val shimmer = if (isProxyActive) (sin(livePhase + i * 0.45f) * 2.2f) else 0f

                    val ry = (h * 0.88f) - (rFrac * h * 0.76f) + shimmer
                    val ty = (h * 0.90f) - (tFrac * h * 0.72f) - shimmer * 0.6f

                    if (i == 0) {
                        rxPath.moveTo(x, ry)
                        rxFill.lineTo(x, ry)
                        txPath.moveTo(x, ty)
                    } else {
                        val prevX = (i - 1) * stepX
                        val midX = (prevX + x) / 2f
                        rxPath.quadraticTo(midX, ry, x, ry)
                        rxFill.quadraticTo(midX, ry, x, ry)
                        txPath.quadraticTo(midX, ty, x, ty)
                    }
                }

                rxFill.lineTo(w, h)
                rxFill.close()

                if (isProxyActive) {
                    drawPath(
                        path = rxFill,
                        brush = Brush.verticalGradient(
                            listOf(palette.earthCyan.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
                }

                drawPath(
                    path = txPath,
                    color = palette.sunGold.copy(alpha = if (isProxyActive) 0.80f else 0.30f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = rxPath,
                    brush = Brush.horizontalGradient(listOf(palette.earthCyan, palette.emeraldLive)),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun SpeedChannelRow(
    label: String,
    icon: String,
    rateBytesPerSec: Long,
    peakBytesPerSec: Long,
    totalBytes: Long,
    fraction: Float,
    primaryColor: Color,
    secondaryColor: Color,
    continuousTimeSec: Float,
    isProxyActive: Boolean
) {
    val palette = LocalCosmicPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.glassPodFill)
            .border(1.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$icon  $label",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp,
                    color = primaryColor
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Peak ${formatCompactRate(peakBytesPerSec)}  •  ${formatBytes(totalBytes)}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    color = palette.textMuted
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = splitSpeedValue(rateBytesPerSec),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = splitSpeedUnit(rateBytesPerSec),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 22-Segment Synchronized LED Bar + Flowing Speed Particles
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .graphicsLayer()
        ) {
            val segCount = 22
            val gap = 4.dp.toPx()
            val segW = (size.width - gap * (segCount - 1)) / segCount
            val activeSegs = (fraction * segCount).toInt()
            val pulseWave = ((continuousTimeSec * (2f + fraction * 8f)) % 1f) * segCount

            for (i in 0 until segCount) {
                val isLit = isProxyActive && (i < activeSegs || (rateBytesPerSec > 0 && i == 0))
                val distFromPulse = kotlin.math.abs(i - pulseWave)
                val pulseBoost = if (isLit && distFromPulse < 2.5f) (1f - distFromPulse / 2.5f) * 0.35f else 0f

                val segColor = when {
                    !isLit -> palette.glassRimSubtle.copy(alpha = 0.30f)
                    i < segCount * 0.6f -> primaryColor.copy(alpha = (0.75f + pulseBoost).coerceAtMost(1f))
                    else -> secondaryColor.copy(alpha = (0.85f + pulseBoost).coerceAtMost(1f))
                }

                drawRoundRect(
                    color = segColor,
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 6. TAB 1: EMBEDDED LIVE IPFIGHTER.COM WITH SMOOTH PORTAL TRANSITION
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }

    val targetTz = remember(isProxyActive, publicIpInfo?.country) {
        if (!isProxyActive) "America/New_York"
        else {
            val c = publicIpInfo?.country?.lowercase() ?: ""
            when {
                c.contains("united kingdom") || c.contains("london") -> "Europe/London"
                c.contains("germany") || c.contains("frankfurt") -> "Europe/Berlin"
                c.contains("SINGAPORE".lowercase()) -> "Asia/Singapore"
                c.contains("japan") || c.contains("tokyo") -> "Asia/Tokyo"
                else -> "America/New_York"
            }
        }
    }

    val restyleAndTzScript = remember(targetTz, isProxyActive) {
        """
        (function() {
            try {
                if (${isProxyActive}) {
                    const targetTz = '$targetTz';
                    const origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
                    Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                        const res = origResolved.call(this);
                        res.timeZone = targetTz;
                        return res;
                    };
                }
                const styleId = 'nameless-solar-theme';
                if (!document.getElementById(styleId)) {
                    const style = document.createElement('style');
                    style.id = styleId;
                    style.innerHTML = `
                        body, html {
                            background: #060B19 !important;
                            color: #F8FAFC !important;
                        }
                        header, nav, footer, .ads, .banner, .promo, [class*="ad-"], [id*="ad-"] {
                            display: none !important;
                        }
                        .card, .box, .panel, [class*="card"], [class*="container"] {
                            background: rgba(16, 26, 56, 0.85) !important;
                            border-radius: 18px !important;
                            border: 1px solid rgba(56, 189, 248, 0.30) !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
            } catch (e) {}
        })();
        """.trimIndent()
    }

    fun triggerCleanReload() {
        isPageReady = false
        scope.launch(Dispatchers.IO) {
            try {
                val p = ProcessBuilder(
                    "su", "-c",
                    "ss -K dport = :853 2>/dev/null; ss -K dport = :53 2>/dev/null; conntrack -F 2>/dev/null"
                ).start()
                p.waitFor()
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                webViewRef?.clearCache(true)
                webViewRef?.loadUrl("https://ipfighter.com/?t=${System.currentTimeMillis()}")
            }
        }
    }

    LaunchedEffect(isProxyActive, runningSlot) {
        delay(550)
        triggerCleanReload()
    }

    val webAlpha by animateFloatAsState(
        targetValue = if (isPageReady) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "webAlpha"
    )
    val webScale by animateFloatAsState(
        targetValue = if (isPageReady) 1f else 0.96f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "webScale"
    )

    CrystalGlassCard(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 22,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
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
                        "TUNNEL EXIT • ${publicIpInfo?.ip ?: "ACTIVE"}"
                    } else {
                        "LIVE IPFIGHTER INSPECTOR"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp,
                    color = palette.textPrimary
                )
            }

            Button(
                onClick = { triggerCleanReload() },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.earthCyan,
                    contentColor = Color.White
                )
            ) {
                Text("↻ Reload", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF060B19))
                .border(1.dp, palette.glassRimBright, RoundedCornerShape(18.dp))
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = webAlpha
                        scaleX = webScale
                        scaleY = webScale
                    },
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#060B19"))
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                view?.evaluateJavascript(restyleAndTzScript, null)
                                if (newProgress >= 75) {
                                    isPageReady = true
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                view?.evaluateJavascript(restyleAndTzScript, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(restyleAndTzScript, null)
                                isPageReady = true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        loadUrl("https://ipfighter.com/")
                        webViewRef = this
                    }
                }
            )

            // Smooth Animated Orbital Scanner Overlay while IPFighter loads
            androidx.compose.animation.AnimatedVisibility(
                visible = !isPageReady,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(380))
            ) {
                val inf = rememberInfiniteTransition(label = "portalLoader")
                val spin by inf.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                    label = "spin"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF060B19)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            rotate(spin) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(Color.Transparent, palette.earthCyan, palette.sunGold)
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = 260f,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "SCANNING TUNNEL TELEMETRY...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            color = palette.earthCyan
                        )
                    }
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
                    Text("📋 Paste URI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.earthCyan)
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
                    colors = ButtonDefaults.buttonColors(containerColor = palette.sunOrange, contentColor = Color.White)
                ) {
                    Text(
                        text = if (isCheckingAlive) "Probing..." else "🛰️ Probe Socket",
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
                            text = "✓ HANDSHAKE VERIFIED (${res.latencyMs} ms)",
                            color = palette.emeraldLive,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is TestResult.Failure -> {
                        Text(
                            text = "✕ UNREACHABLE: ${res.error}",
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
                Switch(checked = startOnBoot, onCheckedChange = onStartOnBootChange)
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
                Switch(checked = routeHotspot && uid == 0, enabled = uid == 0, onCheckedChange = onRouteHotspotChange)
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
                Switch(checked = routeWholeProfile, onCheckedChange = onRouteWholeProfileChange)
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
                            Checkbox(checked = checked, onCheckedChange = { onToggleApp(app.packageName) })
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
    val tabs = listOf(
        Pair("☀️", "Cockpit"),
        Pair("🌐", "IPFighter"),
        Pair("🪐", "Nodes"),
        Pair("🛰️", "Routing"),
        Pair("📜", "Logs")
    )

    CrystalGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 22,
        contentPadding = PaddingValues(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { idx, (icon, label) ->
                val sel = selectedTab == idx
                val scale by animateFloatAsState(
                    targetValue = if (sel) 1.05f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dockTabScale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (sel) Brush.horizontalGradient(listOf(palette.sunOrange, palette.sunGold))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onSelectTab(idx) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 10.sp,
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
        text = String.format("⏱ %02d:%02d", (sec % 3600) / 60, sec % 60),
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

private fun formatCompactRate(b: Long) = when {
    b >= 1024 * 1024 -> String.format("%.1f MB/s", b / (1024.0 * 1024.0))
    b >= 1024 -> String.format("%.0f KB/s", b / 1024.0)
    else -> "$b B/s"
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
