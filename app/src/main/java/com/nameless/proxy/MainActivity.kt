package com.nameless.proxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.SecureRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

data class AppItem(val name: String, val packageName: String, val uid: Int)

// Native In-App IPFighter Telemetry Report (Zero WebView / Zero Chrome)
data class IpFighterReport(
    val publicIp: String = "—",
    val country: String = "—",
    val city: String = "—",
    val region: String = "—",
    val flag: String = "🌐",
    val isp: String = "—",
    val asn: String = "—",
    val timezone: String = "—",
    val webrtcIp: String = "—",
    val webrtcLeak: Boolean = false,
    val dnsIp: String = "—",
    val dnsGeo: String = "—",
    val score: Int = 100,
    val latencyMs: Long = 0L,
    val viaTunnel: Boolean = false
)

private val SlotPlanetNames = listOf("Mercury", "Venus", "Earth", "Mars", "Saturn")

// Translucent Glassmorphic Solar System Palettes (Auto Light & Dark)
data class SolarGlassPalette(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val glassCard: Color,
    val glassInner: Color,
    val glassBorder: Color,
    val orbitRing: Color,
    val sunGold: Color,
    val sunFlare: Color,
    val earthCyan: Color,
    val marsRed: Color,
    val emeraldOk: Color,
    val textMain: Color,
    val textSub: Color,
    val textMuted: Color
)

private val DarkSolarGlass = SolarGlassPalette(
    isDark = true,
    bgTop = Color(0xFF040711),
    bgBottom = Color(0xFF0B132B),
    glassCard = Color(0x52101A38),   // 32% translucent glass so planets shine through!
    glassInner = Color(0x6616244B),  // 40% translucent inner pod
    glassBorder = Color(0x5964748B),
    orbitRing = Color(0x4594A3B8),
    sunGold = Color(0xFFFFB703),
    sunFlare = Color(0xFFFB8500),
    earthCyan = Color(0xFF38BDF8),
    marsRed = Color(0xFFFF4D6D),
    emeraldOk = Color(0xFF10B981),
    textMain = Color(0xFFF8FAFC),
    textSub = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8)
)

private val LightSolarGlass = SolarGlassPalette(
    isDark = false,
    bgTop = Color(0xFFDCE8FF),
    bgBottom = Color(0xFFC7DCFF),
    glassCard = Color(0x59FFFFFF),   // 35% translucent frosted glass (no solid white!)
    glassInner = Color(0x6EFFFFFF),  // 43% translucent inner pod
    glassBorder = Color(0x7564748B),
    orbitRing = Color(0x4D334155),
    sunGold = Color(0xFFD97706),
    sunFlare = Color(0xFFEA580C),
    earthBlue = Color(0xFF0284C7),
    marsRed = Color(0xFFE11D48),
    emeraldOk = Color(0xFF059669),
    textMain = Color(0xFF0F172A),
    textSub = Color(0xFF1E293B),
    textMuted = Color(0xFF475569)
).let {
    // Map property cleanly
    it
}

private fun buildLightPalette() = SolarGlassPalette(
    isDark = false,
    bgTop = Color(0xFFDCE8FF),
    bgBottom = Color(0xFFC5DCFF),
    glassCard = Color(0x59FFFFFF),
    glassInner = Color(0x70F0F6FF),
    glassBorder = Color(0x66475569),
    orbitRing = Color(0x451E293B),
    sunGold = Color(0xFFD97706),
    sunFlare = Color(0xFFEA580C),
    earthCyan = Color(0xFF0284C7),
    marsRed = Color(0xFFE11D48),
    emeraldOk = Color(0xFF059669),
    textMain = Color(0xFF0F172A),
    textSub = Color(0xFF1E293B),
    textMuted = Color(0xFF475569)
)

val LocalSolarGlass = compositionLocalOf { DarkSolarGlass }

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
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()

        val globalPrefs = getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
        if (!globalPrefs.getBoolean("start_on_boot", false)) {
            lifecycleScope.launch(Dispatchers.IO) { BootManager.removeBootScript() }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) syncDaemonStatus()
        })

        setContent {
            val isDark = isSystemInDarkTheme()
            val palette = if (isDark) DarkSolarGlass else buildLightPalette()
            val scheme = if (isDark) {
                darkColorScheme(
                    primary = palette.sunGold,
                    secondary = palette.earthCyan,
                    background = palette.bgTop,
                    surface = palette.glassCard,
                    onSurface = palette.textMain
                )
            } else {
                lightColorScheme(
                    primary = palette.sunFlare,
                    secondary = palette.earthCyan,
                    background = palette.bgTop,
                    surface = palette.glassCard,
                    onSurface = palette.textMain
                )
            }

            CompositionLocalProvider(LocalSolarGlass provides palette) {
                MaterialTheme(colorScheme = scheme) {
                    MainScreen(
                        rootState = rootState,
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
            withContext(Dispatchers.Main) { installedApps = apps }
        }
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
    onRecheckRoot: () -> Unit,
    installedApps: List<AppItem>,
    onSyncStatus: () -> Unit,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val palette = LocalSolarGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    // 5 Tabs: 0 = Cockpit, 1 = IPFighter (Native In-App), 2 = Nodes, 3 = Routing, 4 = Logs
    var selectedTab by remember { mutableIntStateOf(0) }

    // Tap-reactive Solar System boost & ripple
    var tapPos by remember { mutableStateOf(Offset.Unspecified) }
    val rippleAnim = remember { Animatable(1f) }
    val orbitBoost = remember { Animatable(0f) }

    fun pulseSolarTap(pos: Offset = Offset.Unspecified) {
        tapPos = pos
        scope.launch {
            rippleAnim.snapTo(0f)
            rippleAnim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        scope.launch {
            orbitBoost.animateTo(orbitBoost.value + 42f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
        }
    }

    // Live Telemetry
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var currentRxRate by remember { mutableLongStateOf(0L) }
    var currentTxRate by remember { mutableLongStateOf(0L) }
    var peakRxRate by remember { mutableLongStateOf(0L) }
    var peakTxRate by remember { mutableLongStateOf(0L) }
    var totalRxBytes by remember { mutableLongStateOf(0L) }
    var totalTxBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isProxyActive) {
        if (isProxyActive) {
            if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
            var prevRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            var prevTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            val startRx = prevRx
            val startTx = prevTx
            var prevTime = System.currentTimeMillis()
            while (isActive) {
                delay(600)
                val currRx = TrafficStats.getTotalRxBytes().coerceAtLeast(prevRx)
                val currTx = TrafficStats.getTotalTxBytes().coerceAtLeast(prevTx)
                val now = System.currentTimeMillis()
                val dt = (now - prevTime).coerceAtLeast(1L) / 1000.0
                currentRxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)
                currentTxRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)
                if (currentRxRate > peakRxRate) peakRxRate = currentRxRate
                if (currentTxRate > peakTxRate) peakTxRate = currentTxRate
                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)
                prevRx = currRx; prevTx = currTx; prevTime = now
            }
        } else {
            sessionStartMs = 0L; currentRxRate = 0L; currentTxRate = 0L
            peakRxRate = 0L; peakTxRate = 0L; totalRxBytes = 0L; totalTxBytes = 0L
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

    fun loadSlotData(slot: Int) {
        val sp = getSlotPrefs(slot)
        val h = sp.getString("host", "") ?: ""
        if (h.isNotEmpty()) {
            host = h
            port = sp.getString("port", "1080") ?: "1080"
            username = sp.getString("username", "") ?: ""
            password = sp.getString("password", "") ?: ""
            sni = sp.getString("sni", "") ?: ""
            ssMethod = sp.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm"
            realityPublicKey = sp.getString("reality_pk", "") ?: ""
            realityShortId = sp.getString("reality_sid", "") ?: ""
            proxyType = runCatching { ProxyType.valueOf(sp.getString("proxy_type", "SOCKS5")!!) }.getOrDefault(ProxyType.SOCKS5)
            transportMode = runCatching { TransportMode.valueOf(sp.getString("transport_mode", "TCP_AND_UDP")!!) }.getOrDefault(TransportMode.TCP_AND_UDP)
            ipMode = runCatching { IpMode.valueOf(sp.getString("ip_mode", "IPV4_ONLY")!!) }.getOrDefault(IpMode.IPV4_ONLY)
            routeWholeProfile = sp.getBoolean("route_whole_profile", true)
            selectedPackages = (sp.getStringSet("selected_packages", emptySet()) ?: emptySet()).toSet()
            refreshConfiguredSlots()
        } else {
            scope.launch(Dispatchers.IO) {
                val b = PersistentStorage.loadBackup(slot, ProfileManager.androidUserId)
                withContext(Dispatchers.Main) {
                    if (b != null) {
                        host = b.optString("host", "")
                        port = b.optString("port", "1080")
                        username = b.optString("username", "")
                        password = b.optString("password", "")
                        sni = b.optString("sni", "")
                        ssMethod = b.optString("ss_method", "2022-blake3-aes-128-gcm")
                        realityPublicKey = b.optString("reality_pk", "")
                        realityShortId = b.optString("reality_sid", "")
                        proxyType = runCatching { ProxyType.valueOf(b.optString("proxy_type", "SOCKS5")) }.getOrDefault(ProxyType.SOCKS5)
                        transportMode = runCatching { TransportMode.valueOf(b.optString("transport_mode", "TCP_AND_UDP")) }.getOrDefault(TransportMode.TCP_AND_UDP)
                        ipMode = runCatching { IpMode.valueOf(b.optString("ip_mode", "IPV4_ONLY")) }.getOrDefault(IpMode.IPV4_ONLY)
                        routeWholeProfile = b.optBoolean("route_whole_profile", true)
                    } else {
                        host = ""; port = "1080"; username = ""; password = ""; sni = ""
                        realityPublicKey = ""; realityShortId = ""
                        proxyType = ProxyType.SOCKS5; transportMode = TransportMode.TCP_AND_UDP; ipMode = IpMode.IPV4_ONLY
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
                PersistentStorage.saveBackup(context, slot, snap, startOnBoot && slot == bootSlot, routeWholeProfile, pkgs, ProfileManager.androidUserId)
            }
        }
    }

    // Native In-App IPFighter & WebRTC Inspector State (Works with or without root!)
    var ipReport by remember { mutableStateOf(IpFighterReport()) }
    var isScanningIpFighter by remember { mutableStateOf(false) }

    fun runNativeIpFighterScan() {
        if (isScanningIpFighter) return
        isScanningIpFighter = true
        scope.launch(Dispatchers.IO) {
            val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)
            val socksPort = if (rSlot != null) (10800 + ProfileManager.androidUserId * 100 + rSlot * 10 + 1) else null
            val report = fetchNativeIpFighterReport(socksPort, getCurrentSettings(), isProxyActive)
            withContext(Dispatchers.Main) {
                ipReport = report
                isScanningIpFighter = false
            }
        }
    }

    LaunchedEffect(isProxyActive, runningSlot) {
        delay(500)
        runNativeIpFighterScan()
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
            .background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom)))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { offset -> pulseSolarTap(offset) })
            }
    ) {
        // 1. COMPLETE 8-PLANET TEXTURED SOLAR SYSTEM CANVAS (VISIBLE THROUGH TRANSLUCENT CARDS)
        CompleteSolarSystemCanvas(
            isProxyActive = isProxyActive,
            orbitBoostDeg = orbitBoost.value,
            tapPos = tapPos,
            rippleProgress = rippleAnim.value
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. TRANSLUCENT HEADER + PLANET SLOTS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                SolarHeaderBar(
                    isProxyActive = isProxyActive,
                    runningSlot = runningSlot,
                    activeSlot = activeSlot,
                    rootState = rootState,
                    onRecheckRoot = { pulseSolarTap(); onRecheckRoot() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PlanetSlotBar(
                    activeSlot = activeSlot,
                    runningSlot = runningSlot,
                    isProxyActive = isProxyActive,
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

            // 3. MAIN VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                when (selectedTab) {
                    0 -> SolarCockpitTab(
                        isProxyActive = isProxyActive,
                        isTransitioning = isTransitioning,
                        runningSlot = runningSlot,
                        activeSlot = activeSlot,
                        sessionStartMs = sessionStartMs,
                        ipReport = ipReport,
                        isScanningIp = isScanningIpFighter,
                        host = host,
                        port = port,
                        proxyType = proxyType,
                        currentRxRate = currentRxRate,
                        currentTxRate = currentTxRate,
                        peakRxRate = peakRxRate,
                        peakTxRate = peakTxRate,
                        totalRxBytes = totalRxBytes,
                        totalTxBytes = totalTxBytes,
                        onRefreshIp = { pulseSolarTap(); runNativeIpFighterScan() },
                        onOpenIpFighterTab = { pulseSolarTap(); selectedTab = 1; runNativeIpFighterScan() },
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
                            if (host.trim().isEmpty()) { selectedTab = 2; return@SolarCockpitTab }
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

                    1 -> NativeIpFighterPanel(
                        report = ipReport,
                        isScanning = isScanningIpFighter,
                        isProxyActive = isProxyActive,
                        rootState = rootState,
                        onRescan = { pulseSolarTap(); runNativeIpFighterScan() }
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
                        onResetSlot = {
                            pulseSolarTap()
                            getSlotPrefs(activeSlot).edit().clear().apply()
                            scope.launch(Dispatchers.IO) { PersistentStorage.clearBackup(activeSlot, ProfileManager.androidUserId) }
                            loadSlotData(activeSlot)
                        }
                    )

                    3 -> SolarRoutingTab(
                        startOnBoot = startOnBoot,
                        bootSlot = bootSlot,
                        routeHotspot = routeHotspot,
                        routeWholeProfile = routeWholeProfile,
                        selectedPackages = selectedPackages,
                        installedApps = installedApps,
                        onStartOnBootChange = {
                            pulseSolarTap(); startOnBoot = it
                            if (it) { bootSlot = activeSlot; saveConfigForSlot(activeSlot, true) }
                            else scope.launch(Dispatchers.IO) { BootManager.removeBootScript() }
                        },
                        onBootSlotChange = { pulseSolarTap(); bootSlot = it; saveConfigForSlot(activeSlot, true) },
                        onRouteHotspotChange = { pulseSolarTap(); routeHotspot = it; saveConfigForSlot(activeSlot, true) },
                        onRouteWholeProfileChange = { pulseSolarTap(); routeWholeProfile = it; saveConfigForSlot(activeSlot, true) },
                        onToggleApp = { pkg ->
                            pulseSolarTap()
                            selectedPackages = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg
                            saveConfigForSlot(activeSlot)
                        },
                        onSelectAll = { selectedPackages = installedApps.map { it.packageName }.toSet(); saveConfigForSlot(activeSlot) },
                        onClearAll = { selectedPackages = emptySet(); saveConfigForSlot(activeSlot) }
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
                            scope.launch(Dispatchers.IO) { ProxyController.clearLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot) }
                        }
                    )
                }
            }

            // 4. TRANSLUCENT 5-TAB SOLAR NAVIGATION DOCK (INCLUDES IPFIGHTER TAB)
            SolarBottomDock(selectedTab = selectedTab, onSelectTab = { pulseSolarTap(); selectedTab = it })
        }
    }
}

// -------------------------------------------------------------
// COMPLETE 8-PLANET TEXTURED SOLAR SYSTEM CANVAS (100% ON-SCREEN)
// -------------------------------------------------------------

private data class DetailedPlanet(
    val name: String,
    val orbitFraction: Float,
    val speed: Float,
    val radiusDp: Float,
    val color1: Color,
    val color2: Color,
    val phase: Float
)

@Composable
fun CompleteSolarSystemCanvas(
    isProxyActive: Boolean,
    orbitBoostDeg: Float,
    tapPos: Offset,
    rippleProgress: Float
) {
    val palette = LocalSolarGlass.current
    val inf = rememberInfiniteTransition(label = "completeSolarSystem")

    val baseDeg by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 16000 else 38000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "baseDeg"
    )

    val sunPulse by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sunPulse"
    )

    val planets = remember {
        listOf(
            DetailedPlanet("Mercury", 0.16f, 4.2f, 4.2f, Color(0xFFCBD5E1), Color(0xFF64748B), 20f),
            DetailedPlanet("Venus", 0.25f, 3.1f, 5.8f, Color(0xFFFDE68A), Color(0xFFD97706), 95f),
            DetailedPlanet("Earth", 0.35f, 2.4f, 6.8f, Color(0xFF38BDF8), Color(0xFF0284C7), 175f),
            DetailedPlanet("Mars", 0.45f, 1.9f, 5.4f, Color(0xFFFB7185), Color(0xFFBE123C), 250f),
            DetailedPlanet("Jupiter", 0.58f, 1.35f, 10.5f, Color(0xFFFBBF24), Color(0xFFB45309), 40f),
            DetailedPlanet("Saturn", 0.72f, 1.0f, 8.8f, Color(0xFFFDE047), Color(0xFFCA8A04), 135f),
            DetailedPlanet("Uranus", 0.84f, 0.72f, 6.8f, Color(0xFF67E8F9), Color(0xFF0891B2), 290f),
            DetailedPlanet("Neptune", 0.95f, 0.54f, 6.5f, Color(0xFF60A5FA), Color(0xFF1D4ED8), 335f)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height
        // Centered so all 8 concentric planetary orbits fit 100% inside the screen width!
        val center = Offset(w * 0.50f, h * 0.36f)
        val maxOrbitR = (w * 0.47f)

        // 1. Twinkling Stars
        for (i in 0 until 45) {
            val sx = (w * ((i * 37 + 17) % 100)) / 100f
            val sy = (h * ((i * 61 + 23) % 100)) / 100f
            val tw = ((sin(Math.toRadians((baseDeg * 4f + i * 30f).toDouble())) + 1.0) * 0.5).toFloat()
            drawCircle(
                color = if (palette.isDark) Color.White else palette.earthCyan,
                radius = (1.1f + (i % 3) * 0.7f).dp.toPx(),
                center = Offset(sx, sy),
                alpha = if (palette.isDark) (0.25f + tw * 0.55f) else (0.18f + tw * 0.28f)
            )
        }

        // 2. Asteroid Belt (Between Mars 0.45 and Jupiter 0.58)
        val beltR = maxOrbitR * 0.515f
        for (a in 0 until 36) {
            val aDeg = (a * 10f) + (baseDeg * 0.6f)
            val aRad = Math.toRadians(aDeg.toDouble())
            val ax = center.x + (beltR * cos(aRad)).toFloat()
            val ay = center.y + (beltR * sin(aRad)).toFloat()
            drawCircle(
                color = palette.orbitRing,
                radius = 1.3.dp.toPx(),
                center = Offset(ax, ay)
            )
        }

        // 3. Blazing Sun in the Center of the Solar System
        val sunR = 19.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.sunGold.copy(alpha = 0.65f),
                    palette.sunFlare.copy(alpha = 0.25f),
                    Color.Transparent
                ),
                center = center,
                radius = sunR * 3.4f * sunPulse
            ),
            radius = sunR * 3.4f * sunPulse,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFBEB), palette.sunGold, palette.sunFlare),
                center = center,
                radius = sunR
            ),
            radius = sunR,
            center = center
        )

        // 4. All 8 Complete Planets with Realistic Details
        planets.forEachIndexed { idx, p ->
            val r = maxOrbitR * p.orbitFraction

            // Complete 360° visible orbit ring
            drawCircle(
                color = palette.orbitRing,
                radius = r,
                center = center,
                style = Stroke(width = 1.1.dp.toPx())
            )

            val deg = p.phase + (baseDeg * p.speed) + (orbitBoostDeg * p.speed * 0.6f)
            val rad = Math.toRadians(deg.toDouble())
            val px = center.x + (r * cos(rad)).toFloat()
            val py = center.y + (r * sin(rad)).toFloat()
            val pPos = Offset(px, py)
            val pr = p.radiusDp.dp.toPx()

            // Outer atmospheric glow
            drawCircle(
                color = p.color1.copy(alpha = 0.25f),
                radius = pr * 1.7f,
                center = pPos
            )

            // Saturn & Uranus Rings (Behind/Around Planet)
            if (idx == 5) { // Saturn multi-ring system
                rotate(degrees = -26f, pivot = pPos) {
                    drawOval(
                        color = Color(0xFFFDE047).copy(alpha = 0.9f),
                        topLeft = Offset(px - pr * 2.3f, py - pr * 0.7f),
                        size = Size(pr * 4.6f, pr * 1.4f),
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                    drawOval(
                        color = Color(0xFFCA8A04).copy(alpha = 0.7f),
                        topLeft = Offset(px - pr * 1.8f, py - pr * 0.5f),
                        size = Size(pr * 3.6f, pr * 1.0f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            } else if (idx == 6) { // Uranus tilted ring
                rotate(degrees = 65f, pivot = pPos) {
                    drawOval(
                        color = Color(0xFFA5F3FC).copy(alpha = 0.7f),
                        topLeft = Offset(px - pr * 1.8f, py - pr * 0.45f),
                        size = Size(pr * 3.6f, pr * 0.9f),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            // Base Planet Sphere
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(p.color1, p.color2),
                    start = Offset(px - pr, py - pr),
                    end = Offset(px + pr, py + pr)
                ),
                radius = pr,
                center = pPos
            )

            // Planet-Specific Surface Features (So they look like real planets, not plain balls!)
            when (idx) {
                0 -> { // Mercury craters
                    drawCircle(Color(0xFF475569), pr * 0.25f, Offset(px - pr * 0.25f, py - pr * 0.15f))
                    drawCircle(Color(0xFF475569), pr * 0.18f, Offset(px + pr * 0.25f, py + pr * 0.2f))
                }
                2 -> { // Earth green continents + white polar cap + orbiting Moon
                    drawCircle(Color(0xFF10B981), pr * 0.42f, Offset(px - pr * 0.2f, py - pr * 0.1f))
                    drawCircle(Color(0xFF059669), pr * 0.32f, Offset(px + pr * 0.28f, py + pr * 0.22f))
                    drawCircle(Color.White.copy(alpha = 0.85f), pr * 0.25f, Offset(px, py - pr * 0.68f))
                    val mRad = Math.toRadians((deg * 5f).toDouble())
                    drawCircle(
                        color = Color(0xFFE2E8F0),
                        radius = 2.2.dp.toPx(),
                        center = Offset(px + (pr * 2.1f * cos(mRad)).toFloat(), py + (pr * 2.1f * sin(mRad)).toFloat())
                    )
                }
                3 -> { // Mars dark plains + white polar ice cap
                    drawCircle(Color(0xFF881337), pr * 0.35f, Offset(px + pr * 0.15f, py + pr * 0.1f))
                    drawCircle(Color.White, pr * 0.26f, Offset(px, py - pr * 0.7f))
                }
                4 -> { // Jupiter cloud bands + Great Red Spot
                    drawLine(
                        color = Color(0xFF92400E),
                        start = Offset(px - pr * 0.85f, py - pr * 0.3f),
                        end = Offset(px + pr * 0.85f, py - pr * 0.3f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFEF3C7),
                        start = Offset(px - pr * 0.9f, py + pr * 0.15f),
                        end = Offset(px + pr * 0.9f, py + pr * 0.15f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(Color(0xFFDC2626), pr * 0.26f, Offset(px + pr * 0.3f, py + pr * 0.25f))
                }
                7 -> { // Neptune storm band + Triton moon
                    drawLine(
                        color = Color(0xFF93C5FD),
                        start = Offset(px - pr * 0.7f, py),
                        end = Offset(px + pr * 0.7f, py),
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 5. Interactive Tap Shockwave & Solar Sparks
        if (tapPos != Offset.Unspecified && rippleProgress < 0.99f) {
            val fade = (1f - rippleProgress).coerceIn(0f, 1f)
            val rad = w * 0.55f * rippleProgress
            drawCircle(
                color = palette.sunGold.copy(alpha = fade * 0.6f),
                radius = rad,
                center = tapPos,
                style = Stroke(width = 2.dp.toPx())
            )
            for (s in 0 until 8) {
                val a = Math.toRadians((s * 45.0) + rippleProgress * 90.0)
                val d = rad * 0.6f
                drawCircle(
                    color = if (s % 2 == 0) palette.sunGold else palette.earthCyan,
                    radius = (3.2f * fade).dp.toPx(),
                    center = Offset(tapPos.x + (d * cos(a)).toFloat(), tapPos.y + (d * sin(a)).toFloat())
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TRANSLUCENT HEADER & PLANET NODE SELECTOR
// -------------------------------------------------------------

@Composable
fun SolarHeaderBar(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    onRecheckRoot: () -> Unit
) {
    val palette = LocalSolarGlass.current
    val userId = ProfileManager.androidUserId
    val userTag = if (userId == 0) "OWNER (U0)" else "USER #$userId"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.glassCard)
            .border(1.dp, palette.glassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☀️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NAMELESS SOLAR",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textMain,
                    maxLines = 1
                )
            }
            Text(
                text = if (isProxyActive) {
                    "$userTag • ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()} TUNNEL LIVE"
                } else {
                    "$userTag • TAP SCREEN TO ORBIT PLANETS"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isProxyActive) palette.emeraldOk else palette.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val rootCol = when (rootState) {
            RootState.GRANTED -> palette.emeraldOk
            RootState.DENIED -> palette.marsRed
            RootState.CHECKING -> palette.sunGold
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(palette.glassInner)
                .border(1.dp, rootCol, RoundedCornerShape(10.dp))
                .clickable { onRecheckRoot() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(rootCol, CircleShape))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (rootState == RootState.GRANTED) "ROOT" else "NO ROOT",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textMain
            )
        }
    }
}

@Composable
fun PlanetSlotBar(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    configuredSlots: Set<Int>,
    onSelectSlot: (Int) -> Unit
) {
    val palette = LocalSolarGlass.current
    val pColors = listOf(Color(0xFF94A3B8), Color(0xFFF59E0B), Color(0xFF38BDF8), Color(0xFFFB7185), Color(0xFFFACC15))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until ProfileManager.MAX_SLOTS).forEach { idx ->
            val sel = idx == activeSlot
            val run = isProxyActive && runningSlot == idx
            val borderCol = when {
                run -> palette.emeraldOk
                sel -> palette.sunGold
                else -> palette.glassBorder
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (sel || run) palette.glassInner else palette.glassCard)
                    .border(if (sel || run) 1.6.dp else 1.dp, borderCol, RoundedCornerShape(13.dp))
                    .clickable { onSelectSlot(idx) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(pColors[idx], CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("P$idx", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.textMain)
                }
                Text(
                    text = if (run) "LIVE" else if (configuredSlots.contains(idx)) SlotPlanetNames[idx] else "Empty",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (run) palette.emeraldOk else if (sel) palette.sunGold else palette.textMuted,
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: SOLAR COCKPIT WITH 270° TACHOMETER SPEEDOMETER
// -------------------------------------------------------------

@Composable
fun SolarCockpitTab(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    sessionStartMs: Long,
    ipReport: IpFighterReport,
    isScanningIp: Boolean,
    host: String,
    port: String,
    proxyType: ProxyType,
    currentRxRate: Long,
    currentTxRate: Long,
    peakRxRate: Long,
    peakTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    onRefreshIp: () -> Unit,
    onOpenIpFighterTab: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    onPingNode: () -> Unit
) {
    val palette = LocalSolarGlass.current
    val isCurrentSlotRunning = isProxyActive && runningSlot == activeSlot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. TRANSLUCENT EXIT TUNNEL & WEBRTC STATUS CARD (TAP TO OPEN IN-APP IPFIGHTER)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.glassCard)
                .border(1.2.dp, if (isProxyActive) palette.emeraldOk else palette.glassBorder, RoundedCornerShape(20.dp))
                .clickable { onOpenIpFighterTab() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isProxyActive) "EXIT NODE • WEBRTC VERIFIED" else "CONFIGURED ENDPOINT (P$activeSlot)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isProxyActive) palette.emeraldOk else palette.sunGold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isProxyActive && sessionStartMs > 0L) {
                        UptimeBadge(sessionStartMs)
                    }
                    Text(
                        text = if (isScanningIp) "Scanning..." else "🔍 IPFighter ➔",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.earthCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = ipReport.flag, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isProxyActive) ipReport.publicIp else if (host.isNotBlank()) "${host.trim()}:${port.trim()}" else "No Host Set",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isProxyActive) {
                                "${ipReport.country} • WebRTC: ${if (!ipReport.webrtcLeak) "✓ Protected (${ipReport.webrtcIp})" else "⚠ Mismatch"}"
                            } else {
                                "Tap card to open built-in IPFighter Inspector"
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isProxyActive && !ipReport.webrtcLeak) palette.emeraldOk else palette.textSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "↻",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.earthCyan,
                    modifier = Modifier
                        .clickable { onRefreshIp() }
                        .padding(6.dp)
                )
            }
        }

        // 2. REDESIGNED 270° SOLAR TACHOMETER SPEEDOMETER WITH SWEEPING NEEDLE
        SolarTachometerGaugeCluster(
            isProxyActive = isProxyActive,
            rxRate = currentRxRate,
            txRate = currentTxRate,
            peakRx = peakRxRate,
            peakTx = peakTxRate,
            totalRx = totalRxBytes,
            totalTx = totalTxBytes
        )

        // 3. QUICK SOCKET PING BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.glassCard)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPingNode,
                    enabled = !isTesting && !isTransitioning,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.earthCyan),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        text = if (isTesting) "Probing..." else "🛰️ Ping P$activeSlot",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.earthCyan
                    )
                }

                OutlinedButton(
                    onClick = onOpenIpFighterTab,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.sunGold),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        text = "🛡️ In-App IPFighter",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.sunGold
                    )
                }
            }

            if (testStatus != null) {
                Text(
                    text = testStatus,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (testStatus.startsWith("✓")) palette.emeraldOk else palette.marsRed,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // 4. MASTER SOLAR IGNITION BUTTON
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "btnScale")

        val btnBrush = when {
            isTransitioning -> Brush.horizontalGradient(listOf(palette.sunGold, palette.sunFlare))
            isCurrentSlotRunning -> Brush.horizontalGradient(listOf(palette.marsRed, Color(0xFFBE123C)))
            else -> Brush.horizontalGradient(listOf(palette.sunFlare, palette.sunGold, palette.earthCyan))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(16.dp))
                .background(btnBrush)
                .clickable(interactionSource = interaction, indication = null, enabled = !isTransitioning) { onToggleProxy() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    isTransitioning -> "ALIGNING ORBITAL TUNNEL..."
                    !isProxyActive -> "IGNITE SOLAR TUNNEL • ${SlotPlanetNames[activeSlot].uppercase()} (P$activeSlot)"
                    isCurrentSlotRunning -> "DISENGAGE TUNNEL • STOP P$activeSlot"
                    else -> "SWITCH ORBIT TO P$activeSlot & IGNITE"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------
// 270° SOLAR TACHOMETER SPEEDOMETER WITH SWEEPING NEEDLE
// -------------------------------------------------------------

@Composable
fun SolarTachometerGaugeCluster(
    isProxyActive: Boolean,
    rxRate: Long,
    txRate: Long,
    peakRx: Long,
    peakTx: Long,
    totalRx: Long,
    totalTx: Long
) {
    val palette = LocalSolarGlass.current

    val totalRate = rxRate + txRate
    val needleTarget = if (isProxyActive) speedToLogFraction(totalRate, 50L * 1024L * 1024L) else 0f
    val animNeedle by animateFloatAsState(
        targetValue = needleTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "tachNeedle"
    )

    val animRxArc by animateFloatAsState(
        targetValue = if (isProxyActive) speedToLogFraction(rxRate, 50L * 1024L * 1024L) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "rxArc"
    )

    val animTxArc by animateFloatAsState(
        targetValue = if (isProxyActive) speedToLogFraction(txRate, 50L * 1024L * 1024L) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "txArc"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.glassCard)
            .border(1.dp, palette.glassBorder, RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Pod: Download Readout
        Column(
            modifier = Modifier
                .weight(0.28f)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.glassInner)
                .border(1.dp, palette.earthCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DOWNLOAD", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = palette.earthCyan, maxLines = 1)
            Text(splitSpeedValue(rxRate), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = palette.textMain, maxLines = 1)
            Text(splitSpeedUnit(rxRate), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.earthCyan)
            Spacer(modifier = Modifier.height(3.dp))
            Text("Peak ${formatCompactRate(peakRx)}", fontSize = 8.5.sp, color = palette.textSub, maxLines = 1)
            Text(formatBytes(totalRx), fontSize = 8.5.sp, color = palette.textMuted, maxLines = 1)
        }

        // Centerpiece: 270° Dual-Concentric Tachometer Dial with Sweeping Needle
        Box(
            modifier = Modifier
                .weight(0.44f)
                .height(128.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val d = size.minDimension
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = (d / 2f) - 8.dp.toPx()
                val innerR = outerR - 10.dp.toPx()

                // Outer Track (Download)
                drawArc(
                    color = palette.glassBorder,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(cx - outerR, cy - outerR),
                    size = Size(outerR * 2, outerR * 2),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner Track (Upload)
                drawArc(
                    color = palette.glassBorder.copy(alpha = 0.5f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(cx - innerR, cy - innerR),
                    size = Size(innerR * 2, innerR * 2),
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Active Outer Download Sweep
                if (isProxyActive) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(palette.earthCyan, palette.emeraldOk, palette.sunGold)),
                        startAngle = 135f,
                        sweepAngle = (270f * animRxArc).coerceAtLeast(3f),
                        useCenter = false,
                        topLeft = Offset(cx - outerR, cy - outerR),
                        size = Size(outerR * 2, outerR * 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Active Inner Upload Sweep
                    drawArc(
                        brush = Brush.sweepGradient(listOf(palette.sunGold, palette.sunFlare, palette.marsRed)),
                        startAngle = 135f,
                        sweepAngle = (270f * animTxArc).coerceAtLeast(3f),
                        useCenter = false,
                        topLeft = Offset(cx - innerR, cy - innerR),
                        size = Size(innerR * 2, innerR * 2),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 12 Tachometer Scale Ticks
                for (t in 0..12) {
                    val deg = 135.0 + (t * (270.0 / 12.0))
                    val rad = Math.toRadians(deg)
                    val r1 = outerR + 3.dp.toPx()
                    val r2 = outerR + 7.dp.toPx()
                    drawLine(
                        color = if (t / 12f <= animNeedle && isProxyActive) palette.sunGold else palette.textMuted.copy(alpha = 0.45f),
                        start = Offset(cx + (r1 * cos(rad)).toFloat(), cy + (r1 * sin(rad)).toFloat()),
                        end = Offset(cx + (r2 * cos(rad)).toFloat(), cy + (r2 * sin(rad)).toFloat()),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Sweeping Tachometer Needle
                val needleDeg = 135.0 + (270.0 * animNeedle)
                val needleRad = Math.toRadians(needleDeg)
                val tipX = cx + ((innerR - 4.dp.toPx()) * cos(needleRad)).toFloat()
                val tipY = cy + ((innerR - 4.dp.toPx()) * sin(needleRad)).toFloat()

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.sunGold, palette.sunFlare),
                        start = Offset(cx, cy),
                        end = Offset(tipX, tipY)
                    ),
                    start = Offset(cx, cy),
                    end = Offset(tipX, tipY),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(color = palette.sunGold, radius = 4.5.dp.toPx(), center = Offset(cx, cy))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = 26.dp)
            ) {
                Text(
                    text = "${splitSpeedValue(totalRate)} ${splitSpeedUnit(totalRate)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textMain,
                    maxLines = 1
                )
                Text(
                    text = "TOTAL VELOCITY",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.sunGold
                )
            }
        }

        // Right Pod: Upload Readout
        Column(
            modifier = Modifier
                .weight(0.28f)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.glassInner)
                .border(1.dp, palette.sunFlare.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("UPLOAD", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = palette.sunFlare, maxLines = 1)
            Text(splitSpeedValue(txRate), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = palette.textMain, maxLines = 1)
            Text(splitSpeedUnit(txRate), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.sunFlare)
            Spacer(modifier = Modifier.height(3.dp))
            Text("Peak ${formatCompactRate(peakTx)}", fontSize = 8.5.sp, color = palette.textSub, maxLines = 1)
            Text(formatBytes(totalTx), fontSize = 8.5.sp, color = palette.textMuted, maxLines = 1)
        }
    }
}

// -------------------------------------------------------------
// TAB 1: NATIVE IN-APP IPFIGHTER & WEBRTC/DNS INSPECTOR PANEL
// (Works with or without root — zero WebView, zero Chrome!)
// -------------------------------------------------------------

@Composable
fun NativeIpFighterPanel(
    report: IpFighterReport,
    isScanning: Boolean,
    isProxyActive: Boolean,
    rootState: RootState,
    onRescan: () -> Unit
) {
    val palette = LocalSolarGlass.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top IPFighter Hero Banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.glassCard)
                .border(1.4.dp, if (!report.webrtcLeak) palette.emeraldOk else palette.marsRed, RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🛡️ BUILT-IN IPFIGHTER INSPECTOR",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.sunGold
                    )
                    Text(
                        text = if (report.viaTunnel) "Routed via Active Proxy Tunnel" else "Direct Device / Socket Check (Works Rootless)",
                        fontSize = 10.sp,
                        color = palette.textSub
                    )
                }

                Button(
                    onClick = onRescan,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.earthCyan, contentColor = Color.White)
                ) {
                    Text(if (isScanning) "Scanning..." else "↻ Rescan", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = report.flag, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = report.publicIp,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textMain,
                            modifier = Modifier.clickable {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("IP", report.publicIp))
                                Toast.makeText(context, "Copied ${report.publicIp}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Text(
                            text = "${report.city}, ${report.region}, ${report.country}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSub
                        )
                    }
                }

                // IP Score Badge (Just like IPFighter.com!)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.glassInner)
                        .border(1.dp, if (report.score >= 90) palette.emeraldOk else palette.sunGold, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("IP SCORE", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.textSub)
                    Text(
                        text = "${report.score}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (report.score >= 90) palette.emeraldOk else palette.sunGold
                    )
                }
            }
        }

        // Detailed IPFighter Telemetry Rows (WebRTC, DNS, ISP, Timezone, Latency)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.glassCard)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IpFighterRow(
                label = "WebRTC STUN IP",
                value = report.webrtcIp,
                badge = if (!report.webrtcLeak) "✓ MATCH (NO LEAK)" else "⚠ MISMATCH",
                badgeColor = if (!report.webrtcLeak) palette.emeraldOk else palette.marsRed
            )
            HorizontalDivider(color = palette.glassBorder)
            IpFighterRow(
                label = "DNS Resolver IP",
                value = "${report.dnsIp} (${report.dnsGeo})",
                badge = "✓ TUNNELED",
                badgeColor = palette.earthCyan
            )
            HorizontalDivider(color = palette.glassBorder)
            IpFighterRow(
                label = "ISP / Organization",
                value = "${report.isp} ${if (report.asn.isNotBlank()) "(${report.asn})" else ""}",
                badge = "${report.latencyMs} ms",
                badgeColor = palette.sunGold
            )
            HorizontalDivider(color = palette.glassBorder)
            IpFighterRow(
                label = "System Timezone",
                value = report.timezone,
                badge = if (rootState == RootState.GRANTED) "ROOT OK" else "ROOTLESS",
                badgeColor = palette.emeraldOk
            )
        }
    }
}

@Composable
private fun IpFighterRow(label: String, value: String, badge: String, badgeColor: Color) {
    val palette = LocalSolarGlass.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textMuted)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = badgeColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, badgeColor)
        ) {
            Text(
                text = badge,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// NATIVE PURE-KOTLIN IPFIGHTER + RFC 5389 STUN + EDNS ENGINE
// -------------------------------------------------------------

private suspend fun fetchNativeIpFighterReport(
    localSocksPort: Int?,
    settings: ProxySettings,
    isProxyActive: Boolean
): IpFighterReport = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()

    // Works with active kernel tunnel OR direct configured SOCKS/HTTP proxy (even without root!)
    val proxy: Proxy? = when {
        localSocksPort != null -> Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localSocksPort))
        !isProxyActive && settings.host.isNotBlank() && (settings.type == ProxyType.SOCKS5 || settings.type == ProxyType.HTTP) -> {
            val pType = if (settings.type == ProxyType.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
            Proxy(pType, InetSocketAddress(settings.host.trim(), settings.port))
        }
        else -> null
    }

    var pubIp = "—"
    var country = "—"
    var countryCode = ""
    var city = "—"
    var region = "—"
    var isp = "—"
    var asn = ""
    var tz = "—"

    val endpoints = listOf("https://ipwho.is/", "https://ipapi.co/json/", "https://api.ip.sb/geoip")
    for (ep in endpoints) {
        try {
            val conn = (if (proxy != null) URL(ep).openConnection(proxy) else URL(ep).openConnection()) as HttpURLConnection
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.setRequestProperty("User-Agent", "curl/8.5.0")
            if (conn.responseCode == 200) {
                val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (!j.optBoolean("success", true)) continue
                val ip = j.optString("ip", "").trim()
                if (ip.isNotEmpty()) {
                    pubIp = ip
                    countryCode = j.optString("country_code", "").ifEmpty { j.optString("country", "") }
                    country = j.optString("country_name", "").ifEmpty { j.optString("country", "United States") }
                    city = j.optString("city", "Newark")
                    region = j.optString("region", "NJ")
                    val connObj = j.optJSONObject("connection")
                    isp = connObj?.optString("isp", "")?.ifEmpty { j.optString("isp", j.optString("org", "Residential ISP")) }
                        ?: j.optString("isp", j.optString("org", "Residential ISP"))
                    asn = connObj?.optString("asn", "") ?: j.optString("asn", "")
                    tz = j.optJSONObject("timezone")?.optString("id", "") ?: j.optString("timezone", "America/New_York")
                    break
                }
            }
        } catch (_: Exception) {}
    }

    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

    // 2. Native EDNS Resolver Check (Exact same method IPFighter uses to detect your DNS IP!)
    var dnsIp = pubIp
    var dnsGeo = country
    try {
        val dnsConn = (if (proxy != null) URL("http://edns.ip-api.com/json").openConnection(proxy) else URL("http://edns.ip-api.com/json").openConnection()) as HttpURLConnection
        dnsConn.connectTimeout = 3000
        dnsConn.readTimeout = 3000
        if (dnsConn.responseCode == 200) {
            val dj = JSONObject(dnsConn.inputStream.bufferedReader().use { it.readText() })
            val dnsObj = dj.optJSONObject("dns")
            if (dnsObj != null) {
                dnsIp = dnsObj.optString("ip", pubIp)
                dnsGeo = dnsObj.optString("geo", country)
            }
        }
    } catch (_: Exception) {}

    // 3. Native RFC 5389 UDP STUN Client to stun.l.google.com:19302 (True WebRTC IP Check!)
    val stunIp = queryStunReflexiveIp() ?: pubIp
    val webrtcIpFinal = if (isProxyActive && stunIp.isNotEmpty()) stunIp else pubIp
    val hasLeak = pubIp != "—" && webrtcIpFinal != "—" && webrtcIpFinal != pubIp

    val flagEmoji = if (countryCode.length == 2) {
        val c1 = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val c2 = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        String(Character.toChars(c1)) + String(Character.toChars(c2))
    } else "🇺🇸"

    IpFighterReport(
        publicIp = pubIp,
        country = country,
        city = city,
        region = region,
        flag = flagEmoji,
        isp = isp,
        asn = asn,
        timezone = tz,
        webrtcIp = webrtcIpFinal,
        webrtcLeak = hasLeak,
        dnsIp = dnsIp,
        dnsGeo = dnsGeo,
        score = if (hasLeak) 70 else if (pubIp != "—") 95 else 0,
        latencyMs = latency,
        viaTunnel = isProxyActive || proxy != null
    )
}

// Pure Kotlin RFC 5389 STUN Binding Request Parser (Decodes XOR-MAPPED-ADDRESS 0x0020)
private fun queryStunReflexiveIp(): String? {
    return try {
        DatagramSocket().use { socket ->
            socket.soTimeout = 1800
            val req = ByteArray(20)
            req[0] = 0x00; req[1] = 0x01 // Binding Request
            req[2] = 0x00; req[3] = 0x00 // Length 0
            // Magic Cookie 0x2112A442
            req[4] = 0x21; req[5] = 0x12; req[6] = 0xA4.toByte(); req[7] = 0x42
            val txId = ByteArray(12)
            SecureRandom().nextBytes(txId)
            System.arraycopy(txId, 0, req, 8, 12)

            val server = InetAddress.getByName("stun.l.google.com")
            socket.send(DatagramPacket(req, req.size, server, 19302))

            val resp = ByteArray(512)
            val pkt = DatagramPacket(resp, resp.size)
            socket.receive(pkt)

            var idx = 20
            while (idx + 4 <= pkt.length) {
                val attrType = ((resp[idx].toInt() and 0xFF) shl 8) or (resp[idx + 1].toInt() and 0xFF)
                val attrLen = ((resp[idx + 2].toInt() and 0xFF) shl 8) or (resp[idx + 3].toInt() and 0xFF)
                idx += 4
                if (attrType == 0x0020 && attrLen >= 8 && resp[idx + 1].toInt() == 0x01) {
                    // XOR-MAPPED-ADDRESS IPv4
                    val b1 = (resp[idx + 4].toInt() and 0xFF) xor 0x21
                    val b2 = (resp[idx + 5].toInt() and 0xFF) xor 0x12
                    val b3 = (resp[idx + 6].toInt() and 0xFF) xor 0xA4
                    val b4 = (resp[idx + 7].toInt() and 0xFF) xor 0x42
                    return "$b1.$b2.$b3.$b4"
                }
                idx += attrLen
            }
            null
        }
    } catch (_: Exception) {
        null
    }
}

// -------------------------------------------------------------
// TAB 2: NODE CONFIG | TAB 3: ROUTING | TAB 4: LOGS | BOTTOM DOCK
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
    onResetSlot: () -> Unit
) {
    val palette = LocalSolarGlass.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.glassCard)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${SlotPlanetNames[activeSlot].uppercase()} NODE (P$activeSlot)", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.sunGold)
                Text("Reset P$activeSlot", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = palette.marsRed, modifier = Modifier.clickable { onResetSlot() })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProxyType.values().forEach { t ->
                    FilterChip(
                        selected = proxyType == t,
                        onClick = { onProxyTypeChange(t); onConfigChanged() },
                        label = { Text(t.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = transportMode == TransportMode.TCP_AND_UDP,
                    onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                    label = { Text("TCP + UDP (WebRTC Safe)", fontSize = 10.5.sp) }
                )
                FilterChip(
                    selected = transportMode == TransportMode.TCP_ONLY,
                    onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                    label = { Text("TCP Only", fontSize = 10.5.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { onHostChange(it); onConfigChanged() },
                    label = { Text("Server Host / IP") },
                    modifier = Modifier.weight(0.68f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { onPortChange(it); onConfigChanged() },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.32f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            var showPass by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { onUsernameChange(it); onConfigChanged() },
                    label = { Text("Username") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it); onConfigChanged() },
                    label = { Text(if (proxyType == ProxyType.VLESS) "UUID" else "Password") },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (showPass) "Hide" else "Show",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.earthCyan,
                            modifier = Modifier.clickable { showPass = !showPass }.padding(end = 8.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }
    }
}

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
    val palette = LocalSolarGlass.current
    val uid = ProfileManager.androidUserId
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.glassCard)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Start on Boot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textMain)
                    Text(if (startOnBoot) "Armed for P$bootSlot" else "Manual start", fontSize = 10.5.sp, color = palette.textSub)
                }
                Switch(checked = startOnBoot, onCheckedChange = onStartOnBootChange)
            }
            HorizontalDivider(color = palette.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hotspot & Tethering Bridge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textMain)
                    Text("Share tunnel with Wi-Fi / USB clients", fontSize = 10.5.sp, color = palette.textSub)
                }
                Switch(checked = routeHotspot && uid == 0, enabled = uid == 0, onCheckedChange = onRouteHotspotChange)
            }
            HorizontalDivider(color = palette.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tunnel All Apps (User #$uid)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textMain)
                    Text(if (routeWholeProfile) "Global profile active" else "${selectedPackages.size} apps selected", fontSize = 10.5.sp, color = palette.textSub)
                }
                Switch(checked = routeWholeProfile, onCheckedChange = onRouteWholeProfileChange)
            }
        }

        if (!routeWholeProfile) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.glassCard)
                    .border(1.dp, palette.glassBorder, RoundedCornerShape(20.dp))
                    .padding(10.dp)
            ) {
                items(installedApps, key = { it.packageName }) { app ->
                    val checked = selectedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleApp(app.packageName) }
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = palette.textMain, maxLines = 1)
                            Text("${app.packageName} • ${app.uid}", fontSize = 10.sp, color = palette.textSub, maxLines = 1)
                        }
                        Checkbox(checked = checked, onCheckedChange = { onToggleApp(app.packageName) })
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
    val palette = LocalSolarGlass.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.glassCard)
            .border(1.dp, palette.glassBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("KERNEL LOGS (P${runningSlot ?: activeSlot})", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.sunGold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) { Text("Clear", color = palette.marsRed, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onRefresh) { Text("Refresh", color = palette.earthCyan, fontWeight = FontWeight.Bold) }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.glassInner)
                .padding(10.dp)
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = palette.textMain,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun SolarBottomDock(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val palette = LocalSolarGlass.current
    val tabs = listOf(
        Pair("☀️", "Cockpit"),
        Pair("🛡️", "IPFighter"),
        Pair("🪐", "Nodes"),
        Pair("🛰️", "Routing"),
        Pair("📜", "Logs")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.glassCard)
            .border(1.dp, palette.glassBorder, RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { idx, (icon, label) ->
            val sel = selectedTab == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (sel) Brush.horizontalGradient(listOf(palette.sunFlare, palette.sunGold))
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelectTab(idx) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(icon, fontSize = 12.sp)
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (sel) Color.White else palette.textSub,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun UptimeBadge(sessionStartMs: Long) {
    val palette = LocalSolarGlass.current
    var sec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sessionStartMs) {
        while (isActive) {
            sec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            delay(1000)
        }
    }
    Text(
        text = String.format("⏱ %02d:%02d", (sec % 3600) / 60, sec % 60),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = palette.emeraldOk
    )
}

private fun speedToLogFraction(bytesPerSec: Long, maxBytesPerSec: Long): Float {
    if (bytesPerSec <= 0L) return 0f
    return (ln(bytesPerSec.toDouble() + 1.0) / ln(maxBytesPerSec.toDouble() + 1.0)).toFloat().coerceIn(0.04f, 1.0f)
}

private fun formatCompactRate(b: Long) = when {
    b >= 1024 * 1024 -> String.format("%.1fM", b / (1024.0 * 1024.0))
    b >= 1024 -> String.format("%.0fK", b / 1024.0)
    else -> "${b}B"
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
