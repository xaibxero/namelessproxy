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

import android.webkit.PermissionRequest

import android.webkit.WebChromeClient

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

import java.io.DataOutputStream

import java.io.File

import kotlin.math.PI

import kotlin.math.atan2

import kotlin.math.cos

import kotlin.math.sin



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

// TRUE CRYSTAL-GLASS SOLAR SYSTEM PALETTE (AUTO SYSTEM DARK / LIGHT)

// -------------------------------------------------------------



data class CosmicPalette(

    val isDark: Boolean,

    val skyTop: Color,

    val skyMiddle: Color,

    val skyBottom: Color,

    val glassSurface: Color,

    val glassPod: Color,

    val glassRimTop: Color,

    val glassRimBottom: Color,

    val orbitTrack: Color,

    val sunCore: Color,

    val sunCorona: Color,

    val cyanAccent: Color,

    val emeraldLive: Color,

    val crimsonAlert: Color,

    val violetAccent: Color,

    val textPrimary: Color,

    val textSecondary: Color,

    val textMuted: Color

)



// Dark Mode: Deep Space Observatory with Ultra-Clear 15% Crystal Glass

private val DarkCosmicPalette = CosmicPalette(

    isDark = true,

    skyTop = Color(0xFF02040A),

    skyMiddle = Color(0xFF060C1E),

    skyBottom = Color(0xFF0A1432),

    glassSurface = Color(0x26101B38), // 15% alpha true crystal glass

    glassPod = Color(0x36162447),     // 21% alpha inner pod

    glassRimTop = Color(0x667DD3FC),

    glassRimBottom = Color(0x24FFFFFF),

    orbitTrack = Color(0x4594A3B8),

    sunCore = Color(0xFFFFD166),

    sunCorona = Color(0xFFF77F00),

    cyanAccent = Color(0xFF00E5FF),

    emeraldLive = Color(0xFF00F5A0),

    crimsonAlert = Color(0xFFFF2A55),

    violetAccent = Color(0xFF8B5CF6),

    textPrimary = Color(0xFFF8FAFC),

    textSecondary = Color(0xFFCBD5E1),

    textMuted = Color(0xFF64748B)

)



// Light Mode: Celestial Star-Chart Daylight with True 25% Frosted Glass (No Solid White)

private val LightCosmicPalette = CosmicPalette(

    isDark = false,

    skyTop = Color(0xFFDCE8FF),

    skyMiddle = Color(0xFFC8DCFD),

    skyBottom = Color(0xFFB4CFFA),

    glassSurface = Color(0x40FFFFFF), // 25% alpha true translucent glass

    glassPod = Color(0x52FFFFFF),     // 32% alpha inner pod

    glassRimTop = Color(0x99FFFFFF),

    glassRimBottom = Color(0x480284C7),

    orbitTrack = Color(0x4D1E3A8A),

    sunCore = Color(0xFFF59E0B),

    sunCorona = Color(0xFFEA580C),

    cyanAccent = Color(0xFF0284C7),

    emeraldLive = Color(0xFF059669),

    crimsonAlert = Color(0xFFE11D48),

    violetAccent = Color(0xFF6D28D9),

    textPrimary = Color(0xFF0B132B),

    textSecondary = Color(0xFF1E293B),

    textMuted = Color(0xFF475569)

)



val LocalCosmicPalette = compositionLocalOf { DarkCosmicPalette }



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



            val colorScheme = if (isDark) {

                darkColorScheme(

                    primary = palette.cyanAccent,

                    secondary = palette.sunCore,

                    background = palette.skyTop,

                    surface = palette.glassSurface,

                    onSurface = palette.textPrimary

                )

            } else {

                lightColorScheme(

                    primary = palette.cyanAccent,

                    secondary = palette.sunCorona,

                    background = palette.skyTop,

                    surface = palette.glassSurface,

                    onSurface = palette.textPrimary

                )

            }



            CompositionLocalProvider(LocalCosmicPalette provides palette) {

                MaterialTheme(colorScheme = colorScheme) {

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



// -------------------------------------------------------------

// ACCURATE PHYSICAL NETWORK INTERFACE BYTE COUNTER (IGNORES LOOPBACK)

// -------------------------------------------------------------



private fun readPhysicalNetworkBytes(): Pair<Long, Long> {

    try {

        val file = File("/proc/net/dev")

        if (file.canRead()) {

            var rxTotal = 0L

            var txTotal = 0L

            file.forEachLine { line ->

                val trimmed = line.trim()

                if (trimmed.contains(":")) {

                    val iface = trimmed.substringBefore(":").trim()

                    val isPhysical = iface.startsWith("wlan") ||

                        iface.startsWith("rmnet") ||

                        iface.startsWith("ccmni") ||

                        iface.startsWith("eth") ||

                        iface.startsWith("rndis") ||

                        iface.startsWith("ncm")

                    if (isPhysical) {

                        val statsPart = trimmed.substringAfter(":").trim()

                        val tokens = statsPart.split(Regex("\\s+"))

                        if (tokens.size >= 9) {

                            rxTotal += tokens[0].toLongOrNull() ?: 0L

                            txTotal += tokens[8].toLongOrNull() ?: 0L

                        }

                    }

                }

            }

            if (rxTotal > 0L || txTotal > 0L) {

                return Pair(rxTotal, txTotal)

            }

        }

    } catch (_: Exception) {}



    val fallbackRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)

    val fallbackTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

    return Pair(fallbackRx, fallbackTx)

}



// Piecewise-Linear Realistic Speed Scale (0.0 .. 1.0)

private fun realisticSpeedFraction(bytesPerSec: Long): Float {

    if (bytesPerSec <= 128L) return 0f

    val kb = bytesPerSec / 1024.0

    val mb = kb / 1024.0

    return when {

        kb < 50.0 -> ((kb / 50.0) * 0.12).toFloat()

        kb < 250.0 -> (0.12 + ((kb - 50.0) / 200.0) * 0.18).toFloat()

        mb < 1.0 -> (0.30 + ((kb - 250.0) / 774.0) * 0.20).toFloat()

        mb < 5.0 -> (0.50 + ((mb - 1.0) / 4.0) * 0.22).toFloat()

        mb < 20.0 -> (0.72 + ((mb - 5.0) / 15.0) * 0.18).toFloat()

        else -> (0.90 + ((mb - 20.0) / 30.0).coerceIn(0.0, 1.0) * 0.10).toFloat()

    }.coerceIn(0f, 1f)

}



// Helper to flush stale DNS & DoT sockets in kernel before reloading IPFighter

private fun flushKernelDnsSockets() {

    try {

        val process = ProcessBuilder("su").redirectErrorStream(true).start()

        DataOutputStream(process.outputStream).use { os ->

            os.writeBytes("ss -K dport = :853 2>/dev/null || true\n")

            os.writeBytes("ss -K dport = :53 2>/dev/null || true\n")

            os.writeBytes("conntrack -F 2>/dev/null || true\n")

            os.writeBytes("ndc resolver clearnetdns wlan0 2>/dev/null || true\n")

            os.writeBytes("ndc resolver flushdefaultif 2>/dev/null || true\n")

            os.writeBytes("exit\n")

            os.flush()

        }

        process.waitFor()

    } catch (_: Exception) {}

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

    // 5 Tabs: 0 = Cockpit, 1 = IPFighter (Restyled In-App WebView + Telemetry), 2 = Nodes, 3 = Routing, 4 = Logs

    var selectedTab by remember { mutableIntStateOf(0) }



    // Interactive Tap-to-Warp Solar System State

    var tapPos by remember { mutableStateOf(Offset.Unspecified) }

    val rippleAnim = remember { Animatable(1f) }

    val orbitBoost = remember { Animatable(0f) }



    fun triggerSolarPulse(pos: Offset = Offset.Unspecified) {

        tapPos = pos

        scope.launch {

            rippleAnim.snapTo(0f)

            rippleAnim.animateTo(1f, tween(850, easing = FastOutSlowInEasing))

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



    // Accurate Physical Network Speed & History

    var sessionStartMs by remember { mutableLongStateOf(0L) }

    var rxHistory by remember { mutableStateOf(List(36) { 0L }) }

    var txHistory by remember { mutableStateOf(List(36) { 0L }) }

    var currentRxRate by remember { mutableLongStateOf(0L) }

    var currentTxRate by remember { mutableLongStateOf(0L) }

    var peakRxRate by remember { mutableLongStateOf(0L) }

    var peakTxRate by remember { mutableLongStateOf(0L) }

    var totalRxBytes by remember { mutableLongStateOf(0L) }

    var totalTxBytes by remember { mutableLongStateOf(0L) }



    LaunchedEffect(isProxyActive) {

        if (isProxyActive) {

            if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()

            var (prevRx, prevTx) = readPhysicalNetworkBytes()

            val startRx = prevRx

            val startTx = prevTx

            var prevTime = System.currentTimeMillis()



            while (isActive) {

                delay(500)

                val (currRxRaw, currTxRaw) = readPhysicalNetworkBytes()

                val currRx = currRxRaw.coerceAtLeast(prevRx)

                val currTx = currTxRaw.coerceAtLeast(prevTx)

                val now = System.currentTimeMillis()

                val dt = (now - prevTime).coerceAtLeast(1L) / 1000.0



                val instantRx = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)

                val instantTx = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)



                val smoothedRx = if (currentRxRate == 0L) instantRx else ((instantRx * 0.65) + (currentRxRate * 0.35)).toLong()

                val smoothedTx = if (currentTxRate == 0L) instantTx else ((instantTx * 0.65) + (currentTxRate * 0.35)).toLong()



                currentRxRate = smoothedRx

                currentTxRate = smoothedTx

                if (smoothedRx > peakRxRate) peakRxRate = smoothedRx

                if (smoothedTx > peakTxRate) peakTxRate = smoothedTx

                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)

                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)



                rxHistory = rxHistory.drop(1) + smoothedRx

                txHistory = txHistory.drop(1) + smoothedTx



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

            rxHistory = List(36) { 0L }

            txHistory = List(36) { 0L }

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



    // Exit Node GeoIP Check

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }

    var isFetchingIp by remember { mutableStateOf(false) }



    fun refreshExitGeoIp() {

        isFetchingIp = true

        scope.launch(Dispatchers.IO) {

            delay(450)

            val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)

            val socksPort = if (rSlot != null) (10800 + ProfileManager.androidUserId * 100 + rSlot * 10 + 1) else null

            val res = IpFetcher.getPublicIpInfo(socksPort)

            withContext(Dispatchers.Main) {

                publicIpInfo = res

                isFetchingIp = false

            }

        }

    }



    LaunchedEffect(isProxyActive, runningSlot) {

        refreshExitGeoIp()

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

                    listOf(palette.skyTop, palette.skyMiddle, palette.skyBottom)

                )

            )

            .pointerInput(Unit) {

                detectTapGestures(onPress = { offset -> triggerSolarPulse(offset) })

            }

    ) {

        // 1. VOLUMETRIC SOLAR GOD-RAYS, AURORA RIBBONS & STARFIELD BACKDROP

        CosmicDeepBackdrop(

            isProxyActive = isProxyActive,

            isTransitioning = isTransitioning || isTesting,

            tapPos = tapPos,

            rippleProgress = rippleAnim.value

        )



        Column(

            modifier = Modifier

                .fillMaxSize()

                .statusBarsPadding()

                .navigationBarsPadding()

        ) {

            // 2. TRUE CRYSTAL-GLASS HEADER + PLANET SLOTS

            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 14.dp, vertical = 6.dp)

            ) {

                CosmicHeaderBar(

                    isProxyActive = isProxyActive,

                    runningSlot = runningSlot,

                    activeSlot = activeSlot,

                    rootState = rootState,

                    onRecheckRoot = { triggerSolarPulse(); onRecheckRoot() }

                )

                Spacer(modifier = Modifier.height(8.dp))

                CosmicPlanetSlotRibbon(

                    activeSlot = activeSlot,

                    runningSlot = runningSlot,

                    isProxyActive = isProxyActive,

                    startOnBoot = startOnBoot,

                    bootSlot = bootSlot,

                    configuredSlots = configuredSlots,

                    onSelectSlot = { newSlot ->

                        triggerSolarPulse()

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

                AnimatedContent(

                    targetState = selectedTab,

                    transitionSpec = {

                        val dir = if (targetState > initialState) 1 else -1

                        (slideInHorizontally(

                            animationSpec = spring(

                                dampingRatio = Spring.DampingRatioLowBouncy,

                                stiffness = Spring.StiffnessMediumLow

                            ),

                            initialOffsetX = { fullW -> dir * (fullW / 3) }

                        ) + fadeIn(tween(200))).togetherWith(

                            slideOutHorizontally(

                                animationSpec = tween(160),

                                targetOffsetX = { fullW -> -dir * (fullW / 4) }

                            ) + fadeOut(tween(140))

                        )

                    },

                    label = "cosmicTabTransition"

                ) { tabIdx ->

                    when (tabIdx) {

                        0 -> CosmicCockpitScreen(

                            isProxyActive = isProxyActive,

                            isTransitioning = isTransitioning,

                            runningSlot = runningSlot,

                            activeSlot = activeSlot,

                            orbitBoostDeg = orbitBoost.value,

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

                            rxHistory = rxHistory,

                            txHistory = txHistory,

                            onRefreshIp = { triggerSolarPulse(); refreshExitGeoIp() },

                            onOpenIpFighterTab = { triggerSolarPulse(); selectedTab = 1 },

                            onToggleProxy = {

                                triggerSolarPulse()

                                if (isProxyActive && runningSlot == activeSlot) {

                                    onStopProxy { }

                                } else {

                                    if (host.trim().isEmpty()) {

                                        selectedTab = 2

                                        return@CosmicCockpitScreen

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

                                triggerSolarPulse()

                                if (host.trim().isEmpty()) {

                                    selectedTab = 2

                                    return@CosmicCockpitScreen

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

                            activeSlot = runningSlot ?: activeSlot,

                            publicIpInfo = publicIpInfo,

                            onRefreshNativeGeoIp = { refreshExitGeoIp() }

                        )



                        2 -> CosmicNodeConfigScreen(

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

                            onProxyTypeChange = { triggerSolarPulse(); proxyType = it },

                            onTransportModeChange = { triggerSolarPulse(); transportMode = it },

                            onIpModeChange = { triggerSolarPulse(); ipMode = it },

                            onImportUri = { imported ->

                                triggerSolarPulse()

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

                                triggerSolarPulse()

                                getSlotPrefs(activeSlot).edit().clear().apply()

                                scope.launch(Dispatchers.IO) {

                                    PersistentStorage.clearBackup(activeSlot, ProfileManager.androidUserId)

                                }

                                loadSlotData(activeSlot)

                                Toast.makeText(context, "Node P$activeSlot reset", Toast.LENGTH_SHORT).show()

                            },

                            getCurrentSettings = { getCurrentSettings() }

                        )



                        3 -> CosmicRoutingScreen(

                            startOnBoot = startOnBoot,

                            bootSlot = bootSlot,

                            routeHotspot = routeHotspot,

                            routeWholeProfile = routeWholeProfile,

                            selectedPackages = selectedPackages,

                            installedApps = installedApps,

                            onStartOnBootChange = {

                                triggerSolarPulse()

                                startOnBoot = it

                                if (it) {

                                    bootSlot = activeSlot

                                    saveConfigForSlot(activeSlot, true)

                                } else {

                                    scope.launch(Dispatchers.IO) { BootManager.removeBootScript() }

                                }

                            },

                            onBootSlotChange = {

                                triggerSolarPulse()

                                bootSlot = it

                                saveConfigForSlot(activeSlot, true)

                            },

                            onRouteHotspotChange = {

                                triggerSolarPulse()

                                routeHotspot = it

                                saveConfigForSlot(activeSlot, true)

                            },

                            onRouteWholeProfileChange = {

                                triggerSolarPulse()

                                routeWholeProfile = it

                                saveConfigForSlot(activeSlot, true)

                            },

                            onToggleApp = { pkg ->

                                triggerSolarPulse()

                                selectedPackages = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg

                                saveConfigForSlot(activeSlot)

                            },

                            onSelectAll = {

                                triggerSolarPulse()

                                selectedPackages = installedApps.map { it.packageName }.toSet()

                                saveConfigForSlot(activeSlot)

                            },

                            onClearAll = {

                                triggerSolarPulse()

                                selectedPackages = emptySet()

                                saveConfigForSlot(activeSlot)

                            }

                        )



                        4 -> CosmicLogsScreen(

                            activeSlot = activeSlot,

                            runningSlot = runningSlot,

                            logs = currentLogs,

                            onRefresh = {

                                triggerSolarPulse()

                                scope.launch(Dispatchers.IO) {

                                    val l = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)

                                    withContext(Dispatchers.Main) { currentLogs = l.ifEmpty { "No logs." } }

                                }

                            },

                            onClear = {

                                triggerSolarPulse()

                                currentLogs = "Cleared."

                                scope.launch(Dispatchers.IO) {

                                    ProxyController.clearLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)

                                }

                            }

                        )

                    }

                }

            }



            // 4. CRYSTAL-GLASS 5-TAB NAVIGATION DOCK

            CosmicBottomDock(

                selectedTab = selectedTab,

                onSelectTab = {

                    triggerSolarPulse()

                    selectedTab = it

                }

            )

        }

    }

}



// -------------------------------------------------------------

// REUSABLE TRUE CRYSTAL-GLASS CARD CONTAINER

// -------------------------------------------------------------



@Composable

fun CrystalGlassCard(

    modifier: Modifier = Modifier,

    highlightColor: Color? = null,

    content: @Composable ColumnScope.() -> Unit

) {

    val palette = LocalCosmicPalette.current

    val rimBrush = if (highlightColor != null) {

        Brush.linearGradient(listOf(highlightColor.copy(alpha = 0.85f), palette.glassRimBottom))

    } else {

        Brush.linearGradient(listOf(palette.glassRimTop, palette.glassRimBottom))

    }



    Column(

        modifier = modifier

            .clip(RoundedCornerShape(20.dp))

            .background(palette.glassSurface)

            .border(1.2.dp, rimBrush, RoundedCornerShape(20.dp)),

        content = content

    )

}



// -------------------------------------------------------------

// VOLUMETRIC SOLAR GOD-RAYS, AURORA RIBBONS & STARFIELD BACKDROP

// -------------------------------------------------------------



@Composable

fun CosmicDeepBackdrop(

    isProxyActive: Boolean,

    isTransitioning: Boolean,

    tapPos: Offset,

    rippleProgress: Float

) {

    val palette = LocalCosmicPalette.current

    val inf = rememberInfiniteTransition(label = "cosmicBackdrop")



    val rayRotation by inf.animateFloat(

        initialValue = 0f,

        targetValue = 360f,

        animationSpec = infiniteRepeatable(

            tween(if (isProxyActive) 22000 else 45000, easing = LinearEasing),

            RepeatMode.Restart

        ),

        label = "rayRotation"

    )



    val auroraWave by inf.animateFloat(

        initialValue = 0f,

        targetValue = (2 * PI).toFloat(),

        animationSpec = infiniteRepeatable(

            tween(if (isProxyActive) 4500 else 9500, easing = LinearEasing),

            RepeatMode.Restart

        ),

        label = "auroraWave"

    )



    val nebulaBreath by inf.animateFloat(

        initialValue = 0.80f,

        targetValue = 1.22f,

        animationSpec = infiniteRepeatable(

            tween(2800, easing = FastOutSlowInEasing),

            RepeatMode.Reverse

        ),

        label = "nebulaBreath"

    )



    val primaryGlow by animateColorAsState(

        targetValue = when {

            isTransitioning -> palette.sunCorona

            isProxyActive -> palette.emeraldLive

            else -> palette.sunCore

        },

        animationSpec = tween(700),

        label = "primaryGlow"

    )



    val secondaryGlow by animateColorAsState(

        targetValue = if (isProxyActive) palette.cyanAccent else palette.violetAccent,

        animationSpec = tween(700),

        label = "secondaryGlow"

    )



    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {

        val w = size.width

        val h = size.height

        val lightOrigin = Offset(w * 0.50f, h * 0.23f)

        val baseAlpha = if (palette.isDark) 0.20f else 0.14f



        // 1. Volumetric Rotating Solar God-Rays across the entire background

        val rayCount = 8

        for (r in 0 until rayCount) {

            val angleDeg = rayRotation + (r * (360f / rayCount))

            rotate(degrees = angleDeg, pivot = lightOrigin) {

                val beamPath = Path().apply {

                    moveTo(lightOrigin.x, lightOrigin.y)

                    lineTo(lightOrigin.x - w * 0.16f, lightOrigin.y + h * 1.1f)

                    lineTo(lightOrigin.x + w * 0.16f, lightOrigin.y + h * 1.1f)

                    close()

                }

                drawPath(

                    path = beamPath,

                    brush = Brush.verticalGradient(

                        colors = listOf(

                            primaryGlow.copy(alpha = baseAlpha * 0.42f),

                            secondaryGlow.copy(alpha = baseAlpha * 0.16f),

                            Color.Transparent

                        ),

                        startY = lightOrigin.y,

                        endY = lightOrigin.y + h * 0.95f

                    )

                )

            }

        }



        // 2. Dynamic Drifting Nebula Light Clouds (Top, Mid, Bottom)

        val orb1 = Offset(

            w * 0.24f + (sin(auroraWave) * w * 0.14f).toFloat(),

            h * 0.20f + (cos(auroraWave) * h * 0.06f).toFloat()

        )

        val orb2 = Offset(

            w * 0.78f - (cos(auroraWave * 0.8f) * w * 0.15f).toFloat(),

            h * 0.58f + (sin(auroraWave * 0.8f) * h * 0.08f).toFloat()

        )

        val orb3 = Offset(

            w * 0.45f + (sin(auroraWave * 1.2f) * w * 0.16f).toFloat(),

            h * 0.86f - (cos(auroraWave * 1.2f) * h * 0.05f).toFloat()

        )



        drawCircle(

            brush = Brush.radialGradient(

                colors = listOf(primaryGlow.copy(alpha = baseAlpha * 1.1f), Color.Transparent),

                center = orb1,

                radius = w * 0.75f * nebulaBreath

            ),

            radius = w * 0.75f * nebulaBreath,

            center = orb1

        )

        drawCircle(

            brush = Brush.radialGradient(

                colors = listOf(palette.cyanAccent.copy(alpha = baseAlpha), Color.Transparent),

                center = orb2,

                radius = w * 0.72f

            ),

            radius = w * 0.72f,

            center = orb2

        )

        drawCircle(

            brush = Brush.radialGradient(

                colors = listOf(palette.violetAccent.copy(alpha = baseAlpha * 0.8f), Color.Transparent),

                center = orb3,

                radius = w * 0.68f

            ),

            radius = w * 0.68f,

            center = orb3

        )



        // 3. Flowing Cosmic Aurora Light Ribbons

        for (ribbon in 0..1) {

            val auroraPath = Path()

            val steps = 28

            val baseY = if (ribbon == 0) h * 0.52f else h * 0.76f

            for (i in 0..steps) {

                val x = (i.toFloat() / steps) * w

                val y = baseY + (sin(auroraWave + i * 0.32f + ribbon * 1.5f) * 24.dp.toPx()).toFloat()

                if (i == 0) auroraPath.moveTo(x, y) else auroraPath.lineTo(x, y)

            }

            drawPath(

                path = auroraPath,

                brush = Brush.horizontalGradient(

                    listOf(

                        Color.Transparent,

                        primaryGlow.copy(alpha = baseAlpha * 0.65f),

                        palette.cyanAccent.copy(alpha = baseAlpha * 0.75f),

                        Color.Transparent

                    )

                ),

                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)

            )

        }



        // 4. Twinkling Starfield + 4-Point Star Cross-Flares

        for (i in 0 until 48) {

            val sx = (w * ((i * 37 + 13) % 100)) / 100f

            val sy = (h * ((i * 61 + 29) % 100)) / 100f

            val tw = ((sin(auroraWave * 2f + i * 19f) + 1.0) * 0.5).toFloat()

            val sr = (0.9f + (i % 3) * 0.7f).dp.toPx()

            val starAlpha = if (palette.isDark) (0.22f + tw * 0.58f) else (0.14f + tw * 0.25f)

            val starCol = if (palette.isDark) {

                if (i % 4 == 0) palette.sunCore else Color.White

            } else {

                if (i % 3 == 0) palette.sunCorona else palette.cyanAccent

            }



            drawCircle(

                color = starCol,

                radius = sr,

                center = Offset(sx, sy),

                alpha = starAlpha

            )



            if (i % 7 == 0 && tw > 0.6f) {

                val flareLen = sr * 3.6f

                drawLine(

                    color = starCol.copy(alpha = starAlpha * 0.7f),

                    start = Offset(sx - flareLen, sy),

                    end = Offset(sx + flareLen, sy),

                    strokeWidth = 1.dp.toPx()

                )

                drawLine(

                    color = starCol.copy(alpha = starAlpha * 0.7f),

                    start = Offset(sx, sy - flareLen),

                    end = Offset(sx, sy + flareLen),

                    strokeWidth = 1.dp.toPx()

                )

            }

        }



        // 5. Tap-Reactive Gravitational Shockwave

        if (tapPos != Offset.Unspecified && rippleProgress < 0.99f) {

            val fade = (1f - rippleProgress).coerceIn(0f, 1f)

            val rad = w * 0.55f * rippleProgress

            drawCircle(

                color = palette.sunCore.copy(alpha = fade * 0.55f),

                radius = rad,

                center = tapPos,

                style = Stroke(width = 2.dp.toPx())

            )

            drawCircle(

                color = palette.cyanAccent.copy(alpha = fade * 0.35f),

                radius = rad * 0.65f,

                center = tapPos,

                style = Stroke(width = 1.4.dp.toPx())

            )

        }

    }

}



// -------------------------------------------------------------

// DEDICATED UNOBSTRUCTED 3D-PERSPECTIVE 8-PLANET SOLAR SYSTEM STAGE

// (Includes Glowing Orbital Comet Trails, 3D Depth Occlusion & Sun Lens Flare)

// -------------------------------------------------------------



private data class Planet3DSpec(

    val name: String,

    val semiMajorFraction: Float,

    val speedMultiplier: Float,

    val radiusDp: Float,

    val dayColor: Color,

    val nightColor: Color,

    val initialPhaseDeg: Float

)



@Composable

fun Interactive3DSolarSystemStage(

    isProxyActive: Boolean,

    isTransitioning: Boolean,

    activeSlot: Int,

    runningSlot: Int?,

    orbitBoostDeg: Float,

    onToggleProxy: () -> Unit

) {

    val palette = LocalCosmicPalette.current

    val textMeasurer = rememberTextMeasurer()

    val inf = rememberInfiniteTransition(label = "orrery3D")



    val baseOrbitDeg by inf.animateFloat(

        initialValue = 0f,

        targetValue = 360f,

        animationSpec = infiniteRepeatable(

            tween(durationMillis = if (isProxyActive) 14000 else 32000, easing = LinearEasing),

            RepeatMode.Restart

        ),

        label = "baseOrbitDeg"

    )



    val sunPulse by inf.animateFloat(

        initialValue = 0.92f,

        targetValue = 1.15f,

        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),

        label = "sunPulse"

    )



    val planets = remember {

        listOf(

            Planet3DSpec("Mercury", 0.17f, 4.1f, 4.2f, Color(0xFFE2E8F0), Color(0xFF475569), 15f),

            Planet3DSpec("Venus", 0.26f, 3.1f, 5.6f, Color(0xFFFDE68A), Color(0xFFB45309), 85f),

            Planet3DSpec("Earth", 0.37f, 2.3f, 6.6f, Color(0xFF38BDF8), Color(0xFF0369A1), 165f),

            Planet3DSpec("Mars", 0.47f, 1.8f, 5.2f, Color(0xFFFB7185), Color(0xFF881337), 240f),

            Planet3DSpec("Jupiter", 0.61f, 1.25f, 10.2f, Color(0xFFFBBF24), Color(0xFF78350F), 35f),

            Planet3DSpec("Saturn", 0.75f, 0.92f, 8.5f, Color(0xFFFDE047), Color(0xFF854D0E), 135f),

            Planet3DSpec("Uranus", 0.86f, 0.68f, 6.5f, Color(0xFF67E8F9), Color(0xFF155E75), 285f),

            Planet3DSpec("Neptune", 0.96f, 0.50f, 6.2f, Color(0xFF60A5FA), Color(0xFF1E3A8A), 330f)

        )

    }



    CrystalGlassCard(

        modifier = Modifier.fillMaxWidth(),

        highlightColor = if (isProxyActive) palette.emeraldLive else palette.sunCore

    ) {

        Box(

            modifier = Modifier

                .fillMaxWidth()

                .height(215.dp)

                .clickable(enabled = !isTransitioning) { onToggleProxy() }

        ) {

            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {

                val w = size.width

                val h = size.height

                val cx = w * 0.50f

                val cy = h * 0.53f

                val maxRx = w * 0.46f

                val tiltRatio = 0.46f



                // 1. Draw All 8 Elliptical Orbital Paths + Glowing Comet Trails

                planets.forEach { p ->

                    val rx = maxRx * p.semiMajorFraction

                    val ry = rx * tiltRatio

                    drawOval(

                        color = palette.orbitTrack,

                        topLeft = Offset(cx - rx, cy - ry),

                        size = Size(rx * 2f, ry * 2f),

                        style = Stroke(width = 1.1.dp.toPx())

                    )



                    // Glowing 38-degree orbital trail behind each planet

                    val deg = p.initialPhaseDeg + (baseOrbitDeg * p.speedMultiplier) + (orbitBoostDeg * p.speedMultiplier * 0.6f)

                    drawArc(

                        color = p.dayColor.copy(alpha = 0.55f),

                        startAngle = deg - 38f,

                        sweepAngle = 38f,

                        useCenter = false,

                        topLeft = Offset(cx - rx, cy - ry),

                        size = Size(rx * 2f, ry * 2f),

                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)

                    )

                }



                // Asteroid Belt between Mars (0.47) and Jupiter (0.61)

                val beltRx = maxRx * 0.54f

                val beltRy = beltRx * tiltRatio

                for (a in 0 until 44) {

                    val aRad = Math.toRadians((a * (360.0 / 44.0)) + (baseOrbitDeg * 0.5))

                    val ax = cx + (beltRx * cos(aRad)).toFloat()

                    val ay = cy + (beltRy * sin(aRad)).toFloat()

                    drawCircle(

                        color = palette.orbitTrack,

                        radius = 1.2.dp.toPx(),

                        center = Offset(ax, ay)

                    )

                }



                data class ComputedPlanet(

                    val index: Int,

                    val spec: Planet3DSpec,

                    val x: Float,

                    val y: Float,

                    val depthSin: Float,

                    val deg: Float

                )



                val computed = planets.mapIndexed { idx, p ->

                    val rx = maxRx * p.semiMajorFraction

                    val ry = rx * tiltRatio

                    val deg = p.initialPhaseDeg + (baseOrbitDeg * p.speedMultiplier) + (orbitBoostDeg * p.speedMultiplier * 0.6f)

                    val rad = Math.toRadians(deg.toDouble())

                    val s = sin(rad).toFloat()

                    val c = cos(rad).toFloat()

                    ComputedPlanet(

                        index = idx,

                        spec = p,

                        x = cx + rx * c,

                        y = cy + ry * s,

                        depthSin = s,

                        deg = deg

                    )

                }.sortedBy { it.depthSin }



                fun drawDetailedPlanet3D(cp: ComputedPlanet) {

                    val perspectiveScale = 1f + (cp.depthSin * 0.18f)

                    val pr = cp.spec.radiusDp.dp.toPx() * perspectiveScale

                    val pCenter = Offset(cp.x, cp.y)



                    val toSunAngle = atan2(cy - cp.y, cx - cp.x)

                    val lightOffset = Offset(

                        cp.x + cos(toSunAngle) * (pr * 0.45f),

                        cp.y + sin(toSunAngle) * (pr * 0.45f)

                    )



                    // Atmospheric Halo

                    drawCircle(

                        color = cp.spec.dayColor.copy(alpha = 0.22f),

                        radius = pr * 1.55f,

                        center = pCenter

                    )



                    // Saturn & Uranus Rings

                    if (cp.index == 5) {

                        rotate(degrees = -22f, pivot = pCenter) {

                            drawOval(

                                color = Color(0xFFFDE047).copy(alpha = 0.88f),

                                topLeft = Offset(cp.x - pr * 2.4f, cp.y - pr * 0.72f),

                                size = Size(pr * 4.8f, pr * 1.44f),

                                style = Stroke(width = 2.4.dp.toPx())

                            )

                            drawOval(

                                color = Color(0xFFCA8A04).copy(alpha = 0.65f),

                                topLeft = Offset(cp.x - pr * 1.85f, cp.y - pr * 0.52f),

                                size = Size(pr * 3.7f, pr * 1.04f),

                                style = Stroke(width = 1.4.dp.toPx())

                            )

                        }

                    } else if (cp.index == 6) {

                        rotate(degrees = 68f, pivot = pCenter) {

                            drawOval(

                                color = Color(0xFFA5F3FC).copy(alpha = 0.72f),

                                topLeft = Offset(cp.x - pr * 1.9f, cp.y - pr * 0.48f),

                                size = Size(pr * 3.8f, pr * 0.96f),

                                style = Stroke(width = 1.3.dp.toPx())

                            )

                        }

                    }



                    // 3D Sun-Illuminated Sphere

                    drawCircle(

                        brush = Brush.radialGradient(

                            colors = listOf(Color.White.copy(alpha = 0.9f), cp.spec.dayColor, cp.spec.nightColor),

                            center = lightOffset,

                            radius = pr * 1.35f

                        ),

                        radius = pr,

                        center = pCenter

                    )



                    when (cp.index) {

                        2 -> { // Earth continents + orbiting Moon

                            drawCircle(Color(0xFF10B981), pr * 0.40f, Offset(cp.x - pr * 0.18f, cp.y - pr * 0.08f))

                            drawCircle(Color(0xFF059669), pr * 0.30f, Offset(cp.x + pr * 0.25f, cp.y + pr * 0.20f))

                            val mRad = Math.toRadians((cp.deg * 5f).toDouble())

                            drawCircle(

                                color = Color(0xFFE2E8F0),

                                radius = 2.0.dp.toPx() * perspectiveScale,

                                center = Offset(

                                    cp.x + (pr * 2.1f * cos(mRad)).toFloat(),

                                    cp.y + (pr * 1.3f * sin(mRad)).toFloat()

                                )

                            )

                        }

                        4 -> { // Jupiter equatorial bands + Great Red Spot

                            drawLine(

                                color = Color(0xFF92400E),

                                start = Offset(cp.x - pr * 0.82f, cp.y - pr * 0.22f),

                                end = Offset(cp.x + pr * 0.82f, cp.y - pr * 0.22f),

                                strokeWidth = 1.8.dp.toPx()

                            )

                            drawCircle(Color(0xFFDC2626), pr * 0.26f, Offset(cp.x + pr * 0.28f, cp.y + pr * 0.22f))

                        }

                    }



                    val labelLayout = textMeasurer.measure(

                        text = cp.spec.name,

                        style = TextStyle(

                            fontSize = 8.5.sp,

                            fontWeight = FontWeight.Bold,

                            color = palette.textPrimary.copy(alpha = 0.88f)

                        )

                    )

                    drawText(

                        textLayoutResult = labelLayout,

                        topLeft = Offset(cp.x - labelLayout.size.width / 2f, cp.y + pr + 2.dp.toPx())

                    )

                }



                // 2. Draw planets behind the Sun (depthSin < 0)

                computed.filter { it.depthSin < 0f }.forEach { drawDetailedPlanet3D(it) }



                // 3. Draw Blazing 3D Sun + Lens Flare Cross in Center

                val sunR = 18.dp.toPx()

                val sunGlowColor = if (isProxyActive) palette.emeraldLive else palette.sunCore

                drawCircle(

                    brush = Brush.radialGradient(

                        colors = listOf(

                            sunGlowColor.copy(alpha = 0.68f),

                            palette.sunCorona.copy(alpha = 0.28f),

                            Color.Transparent

                        ),

                        center = Offset(cx, cy),

                        radius = sunR * 3.6f * sunPulse

                    ),

                    radius = sunR * 3.6f * sunPulse,

                    center = Offset(cx, cy)

                )

                // Horizontal & vertical lens flare beams through the Sun

                drawLine(

                    brush = Brush.horizontalGradient(

                        listOf(Color.Transparent, sunGlowColor.copy(alpha = 0.75f), Color.Transparent)

                    ),

                    start = Offset(cx - sunR * 4.2f, cy),

                    end = Offset(cx + sunR * 4.2f, cy),

                    strokeWidth = 2.dp.toPx(),

                    cap = StrokeCap.Round

                )

                drawCircle(

                    brush = Brush.radialGradient(

                        colors = listOf(Color(0xFFFFFBEB), palette.sunCore, palette.sunCorona),

                        center = Offset(cx - sunR * 0.25f, cy - sunR * 0.25f),

                        radius = sunR * 1.2f

                    ),

                    radius = sunR,

                    center = Offset(cx, cy)

                )



                // 4. Draw planets in front of the Sun (depthSin >= 0)

                computed.filter { it.depthSin >= 0f }.forEach { drawDetailedPlanet3D(it) }

            }



            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 12.dp, vertical = 8.dp),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Text(

                    text = if (isProxyActive) {

                        "☀️ SOLAR CORE ONLINE • ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()}"

                    } else {

                        "☀️ SOLAR SYSTEM ORRERY • TAP TO IGNITE"

                    },

                    fontSize = 10.sp,

                    fontWeight = FontWeight.ExtraBold,

                    color = if (isProxyActive) palette.emeraldLive else palette.sunCore

                )



                Text(

                    text = "3D LIVE VIEW",

                    fontSize = 9.sp,

                    fontWeight = FontWeight.Bold,

                    color = palette.cyanAccent

                )

            }

        }

    }

}



// -------------------------------------------------------------

// HEADER & PLANET SLOT RIBBON

// -------------------------------------------------------------



@Composable

fun CosmicHeaderBar(

    isProxyActive: Boolean,

    runningSlot: Int?,

    activeSlot: Int,

    rootState: RootState,

    onRecheckRoot: () -> Unit

) {

    val palette = LocalCosmicPalette.current

    val userId = ProfileManager.androidUserId

    val userTag = if (userId == 0) "OWNER (U0)" else "USER #$userId"



    CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(horizontal = 12.dp, vertical = 8.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(modifier = Modifier.weight(1f)) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text("🪐", fontSize = 16.sp)

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(

                        text = "NAMELESS SOLAR",

                        fontSize = 17.sp,

                        fontWeight = FontWeight.ExtraBold,

                        color = palette.textPrimary,

                        maxLines = 1

                    )

                }

                Text(

                    text = if (isProxyActive) {

                        "$userTag • ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()} ORBIT ACTIVE"

                    } else {

                        "$userTag • PORT ${ProfileManager.localInboundPort} • ${if (palette.isDark) "DARK COSMOS" else "DAYLIGHT SKY"}"

                    },

                    fontSize = 10.sp,

                    fontWeight = FontWeight.SemiBold,

                    color = if (isProxyActive) palette.emeraldLive else palette.textSecondary,

                    maxLines = 1,

                    overflow = TextOverflow.Ellipsis

                )

            }



            val rootCol = when (rootState) {

                RootState.GRANTED -> palette.emeraldLive

                RootState.DENIED -> palette.crimsonAlert

                RootState.CHECKING -> palette.sunCore

            }



            Row(

                modifier = Modifier

                    .clip(RoundedCornerShape(10.dp))

                    .background(palette.glassPod)

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

                    color = palette.textPrimary

                )

            }

        }

    }

}



@Composable

fun CosmicPlanetSlotRibbon(

    activeSlot: Int,

    runningSlot: Int?,

    isProxyActive: Boolean,

    startOnBoot: Boolean,

    bootSlot: Int,

    configuredSlots: Set<Int>,

    onSelectSlot: (Int) -> Unit

) {

    val palette = LocalCosmicPalette.current

    val pColors = listOf(Color(0xFF94A3B8), Color(0xFFF59E0B), Color(0xFF38BDF8), Color(0xFFFB7185), Color(0xFFFACC15))



    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {

        (0 until ProfileManager.MAX_SLOTS).forEach { idx ->

            val sel = idx == activeSlot

            val run = isProxyActive && runningSlot == idx

            val isBoot = startOnBoot && bootSlot == idx

            val borderCol = when {

                run -> palette.emeraldLive

                sel -> palette.cyanAccent

                else -> palette.glassRimBottom

            }

            Column(

                modifier = Modifier

                    .weight(1f)

                    .clip(RoundedCornerShape(13.dp))

                    .background(if (sel || run) palette.glassPod else palette.glassSurface)

                    .border(if (sel || run) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(13.dp))

                    .clickable { onSelectSlot(idx) }

                    .padding(vertical = 6.dp, horizontal = 4.dp),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(modifier = Modifier.size(6.dp).background(pColors[idx], CircleShape))

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("P$idx", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)

                }

                Text(

                    text = when {

                        run -> "LIVE"

                        isBoot -> "BOOT"

                        configuredSlots.contains(idx) -> SlotPlanetNames[idx]

                        else -> "Empty"

                    },

                    fontSize = 8.5.sp,

                    fontWeight = FontWeight.SemiBold,

                    color = when {

                        run -> palette.emeraldLive

                        isBoot -> palette.sunCorona

                        sel -> palette.cyanAccent

                        else -> palette.textMuted

                    },

                    maxLines = 1

                )

            }

        }

    }

}



// -------------------------------------------------------------

// TAB 0: COSMIC COCKPIT (3D SOLAR SYSTEM + ACCURATE SPEED TELEMETRY)

// -------------------------------------------------------------



@Composable

fun CosmicCockpitScreen(

    isProxyActive: Boolean,

    isTransitioning: Boolean,

    runningSlot: Int?,

    activeSlot: Int,

    orbitBoostDeg: Float,

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

    rxHistory: List<Long>,

    txHistory: List<Long>,

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

        verticalArrangement = Arrangement.spacedBy(10.dp)

    ) {

        // 1. UNOBSTRUCTED 3D SOLAR SYSTEM PLANETARIUM STAGE

        Interactive3DSolarSystemStage(

            isProxyActive = isProxyActive,

            isTransitioning = isTransitioning,

            activeSlot = activeSlot,

            runningSlot = runningSlot,

            orbitBoostDeg = orbitBoostDeg,

            onToggleProxy = onToggleProxy

        )



        // 2. EXIT NODE & 1-TAP LIVE IPFIGHTER CARD

        CrystalGlassCard(

            modifier = Modifier.fillMaxWidth(),

            highlightColor = if (isProxyActive) palette.emeraldLive else null

        ) {

            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .clickable {

                        if (isProxyActive && publicIpInfo != null) {

                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                            cb.setPrimaryClip(ClipData.newPlainText("ExitIP", publicIpInfo.ip))

                            Toast.makeText(context, "Copied: ${publicIpInfo.ip}", Toast.LENGTH_SHORT).show()

                        } else {

                            onOpenIpFighterTab()

                        }

                    }

                    .padding(12.dp),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                    Text(

                        text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌍") else "🛰️",

                        fontSize = 22.sp

                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {

                        Row(verticalAlignment = Alignment.CenterVertically) {

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

                                fontSize = 15.sp,

                                fontWeight = FontWeight.ExtraBold,

                                color = palette.textPrimary,

                                maxLines = 1,

                                overflow = TextOverflow.Ellipsis

                            )

                            if (isProxyActive && sessionStartMs > 0L) {

                                Spacer(modifier = Modifier.width(8.dp))

                                UptimeBadge(sessionStartMs)

                            }

                        }

                        Text(

                            text = if (isProxyActive) {

                                "${publicIpInfo?.country ?: "Active Tunnel"} • PID ${activePid ?: "OK"}"

                            } else {

                                "${proxyType.displayTitle} • ${if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP TProxy" else "TCP Only"}"

                            },

                            fontSize = 10.5.sp,

                            fontWeight = FontWeight.SemiBold,

                            color = if (isProxyActive) palette.emeraldLive else palette.textSecondary,

                            maxLines = 1,

                            overflow = TextOverflow.Ellipsis

                        )

                    }

                }



                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {

                    Surface(

                        shape = RoundedCornerShape(10.dp),

                        color = palette.glassPod,

                        border = BorderStroke(1.dp, palette.cyanAccent),

                        modifier = Modifier.clickable { onOpenIpFighterTab() }

                    ) {

                        Text(

                            text = "🌐 IPFighter",

                            fontSize = 10.5.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = palette.cyanAccent,

                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)

                        )

                    }

                }

            }

        }



        // 3. ACCURATE DUAL-STREAM TELEMETRY & HORIZON SPEEDOMETER

        AccurateCosmicSpeedometer(

            isProxyActive = isProxyActive,

            rxRate = currentRxRate,

            txRate = currentTxRate,

            peakRx = peakRxRate,

            peakTx = peakTxRate,

            totalRx = totalRxBytes,

            totalTx = totalTxBytes,

            rxHistory = rxHistory,

            txHistory = txHistory

        )



        // 4. PING & LAUNCH CONTROLS

        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement = Arrangement.spacedBy(8.dp)

        ) {

            OutlinedButton(

                onClick = onPingNode,

                enabled = !isTesting && !isTransitioning,

                shape = RoundedCornerShape(14.dp),

                border = BorderStroke(1.dp, palette.cyanAccent),

                modifier = Modifier.weight(0.38f).height(50.dp)

            ) {

                Text(

                    text = if (isTesting) "..." else "⚡ Ping P$activeSlot",

                    fontSize = 11.5.sp,

                    fontWeight = FontWeight.ExtraBold,

                    color = palette.cyanAccent,

                    maxLines = 1

                )

            }



            val interaction = remember { MutableInteractionSource() }

            val pressed by interaction.collectIsPressedAsState()

            val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "launchScale")



            val btnBrush = when {

                isTransitioning -> Brush.horizontalGradient(listOf(palette.sunCore, palette.sunCorona))

                isCurrentSlotRunning -> Brush.horizontalGradient(listOf(palette.crimsonAlert, Color(0xFF991B1B)))

                else -> Brush.horizontalGradient(listOf(palette.sunCorona, palette.sunCore, palette.cyanAccent))

            }



            Box(

                modifier = Modifier

                    .weight(0.62f)

                    .height(50.dp)

                    .graphicsLayer { scaleX = scale; scaleY = scale }

                    .clip(RoundedCornerShape(14.dp))

                    .background(btnBrush)

                    .clickable(interactionSource = interaction, indication = null, enabled = !isTransitioning) { onToggleProxy() },

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

                    color = Color.White,

                    maxLines = 1

                )

            }

        }



        if (testStatus != null) {

            Text(

                text = testStatus,

                fontSize = 11.sp,

                fontWeight = FontWeight.Bold,

                color = if (testStatus.startsWith("✓")) palette.emeraldLive else palette.crimsonAlert,

                modifier = Modifier.padding(horizontal = 4.dp)

            )

        }

    }

}



// -------------------------------------------------------------

// ACCURATE DUAL-STREAM TELEMETRY & HORIZON SPEEDOMETER

// -------------------------------------------------------------



@Composable

fun AccurateCosmicSpeedometer(

    isProxyActive: Boolean,

    rxRate: Long,

    txRate: Long,

    peakRx: Long,

    peakTx: Long,

    totalRx: Long,

    totalTx: Long,

    rxHistory: List<Long>,

    txHistory: List<Long>

) {

    val palette = LocalCosmicPalette.current



    val rxFrac by animateFloatAsState(

        targetValue = if (isProxyActive) realisticSpeedFraction(rxRate) else 0f,

        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),

        label = "rxFrac"

    )



    val txFrac by animateFloatAsState(

        targetValue = if (isProxyActive) realisticSpeedFraction(txRate) else 0f,

        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),

        label = "txFrac"

    )



    CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {

        Column(modifier = Modifier.padding(12.dp)) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(10.dp)

            ) {

                // DOWNLOAD POD

                Column(

                    modifier = Modifier

                        .weight(1f)

                        .clip(RoundedCornerShape(14.dp))

                        .background(palette.glassPod)

                        .border(1.dp, palette.cyanAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))

                        .padding(10.dp)

                ) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text("↓ DOWNLOAD", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.cyanAccent)

                        Text("Peak ${formatCompactRate(peakRx)}", fontSize = 8.5.sp, color = palette.textSecondary)

                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.Bottom) {

                        Text(

                            text = splitSpeedValue(rxRate),

                            fontSize = 21.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = palette.textPrimary

                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(

                            text = splitSpeedUnit(rxRate),

                            fontSize = 11.sp,

                            fontWeight = FontWeight.Bold,

                            color = palette.cyanAccent,

                            modifier = Modifier.padding(bottom = 2.dp)

                        )

                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Canvas(modifier = Modifier.fillMaxWidth().height(7.dp)) {

                        val segments = 16

                        val gap = 3.dp.toPx()

                        val segW = (size.width - gap * (segments - 1)) / segments

                        for (s in 0 until segments) {

                            val active = (s + 1) / segments.toFloat() <= rxFrac && rxRate > 128L

                            drawRoundRect(

                                color = if (active) palette.cyanAccent else palette.orbitTrack.copy(alpha = 0.25f),

                                topLeft = Offset(s * (segW + gap), 0f),

                                size = Size(segW, size.height),

                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

                            )

                        }

                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text("Total: ${formatBytes(totalRx)}", fontSize = 9.sp, color = palette.textMuted)

                }



                // UPLOAD POD

                Column(

                    modifier = Modifier

                        .weight(1f)

                        .clip(RoundedCornerShape(14.dp))

                        .background(palette.glassPod)

                        .border(1.dp, palette.sunCore.copy(alpha = 0.4f), RoundedCornerShape(14.dp))

                        .padding(10.dp)

                ) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text("↑ UPLOAD", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.sunCore)

                        Text("Peak ${formatCompactRate(peakTx)}", fontSize = 8.5.sp, color = palette.textSecondary)

                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.Bottom) {

                        Text(

                            text = splitSpeedValue(txRate),

                            fontSize = 21.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = palette.textPrimary

                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(

                            text = splitSpeedUnit(txRate),

                            fontSize = 11.sp,

                            fontWeight = FontWeight.Bold,

                            color = palette.sunCore,

                            modifier = Modifier.padding(bottom = 2.dp)

                        )

                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Canvas(modifier = Modifier.fillMaxWidth().height(7.dp)) {

                        val segments = 16

                        val gap = 3.dp.toPx()

                        val segW = (size.width - gap * (segments - 1)) / segments

                        for (s in 0 until segments) {

                            val active = (s + 1) / segments.toFloat() <= txFrac && txRate > 128L

                            drawRoundRect(

                                color = if (active) palette.sunCore else palette.orbitTrack.copy(alpha = 0.25f),

                                topLeft = Offset(s * (segW + gap), 0f),

                                size = Size(segW, size.height),

                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

                            )

                        }

                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text("Total: ${formatBytes(totalTx)}", fontSize = 9.sp, color = palette.textMuted)

                }

            }



            Spacer(modifier = Modifier.height(10.dp))



            Box(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(56.dp)

                    .clip(RoundedCornerShape(12.dp))

                    .background(palette.glassPod)

                    .border(1.dp, palette.glassRimBottom, RoundedCornerShape(12.dp))

                    .padding(horizontal = 6.dp, vertical = 4.dp)

            ) {

                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {

                    val w = size.width

                    val h = size.height

                    val count = rxHistory.size.coerceAtLeast(2)

                    val stepX = w / (count - 1).toFloat()



                    val rxPath = Path()

                    val rxFill = Path()

                    val txPath = Path()



                    rxFill.moveTo(0f, h)



                    for (i in 0 until count) {

                        val x = i * stepX

                        val rFrac = realisticSpeedFraction(rxHistory[i])

                        val tFrac = realisticSpeedFraction(txHistory[i])



                        val ry = (h * 0.90f) - (rFrac * (h * 0.80f))

                        val ty = (h * 0.92f) - (tFrac * (h * 0.78f))



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



                    drawPath(

                        path = rxFill,

                        brush = Brush.verticalGradient(

                            listOf(palette.cyanAccent.copy(alpha = 0.28f), Color.Transparent)

                        )

                    )



                    drawPath(

                        path = txPath,

                        color = palette.sunCore.copy(alpha = 0.85f),

                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)

                    )



                    drawPath(

                        path = rxPath,

                        brush = Brush.horizontalGradient(listOf(palette.cyanAccent, palette.emeraldLive)),

                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)

                    )

                }

            }

        }

    }

}



// -------------------------------------------------------------

// TAB 1: RESTYLED IN-APP IPFIGHTER.COM SUITE (DARK COSMIC CSS + DNS PURGE)

// -------------------------------------------------------------



@SuppressLint("SetJavaScriptEnabled")

@Composable

fun EmbeddedIpFighterWebViewScreen(

    isProxyActive: Boolean,

    activeSlot: Int,

    publicIpInfo: GeoIpResult?,

    onRefreshNativeGeoIp: () -> Unit

) {

    val palette = LocalCosmicPalette.current

    val scope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var isLoadingPage by remember { mutableStateOf(true) }

    var loadProgress by remember { mutableIntStateOf(0) }



    // Custom CSS + JS injected into ipfighter.com so its interface matches our Cosmic Glass UI

    // and spoofs America/New_York timezone when tunnel is active so IP Score doesn't lose -15%

    val cosmicCssAndTzScript = remember(isProxyActive) {

        """

        (function() {

            try {

                if ($isProxyActive) {

                    const origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;

                    Intl.DateTimeFormat.prototype.resolvedOptions = function() {

                        const res = origResolved.call(this);

                        res.timeZone = 'America/New_York';

                        return res;

                    };

                    Date.prototype.getTimezoneOffset = function() { return 240; };

                }

                var style = document.getElementById('nameless-cosmic-style');

                if (!style) {

                    style = document.createElement('style');

                    style.id = 'nameless-cosmic-style';

                    style.innerHTML = `

                        body, html {

                            background: #060D20 !important;

                            color: #F8FAFC !important;

                        }

                        header, footer, .navbar, .ads, .banner, [class*="ad-"], [id*="ad-"] {

                            display: none !important;

                        }

                        .card, .box, .container, [class*="card"] {

                            background: rgba(16, 27, 56, 0.88) !important;

                            border-radius: 16px !important;

                            border: 1px solid rgba(0, 229, 255, 0.35) !important;

                            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45) !important;

                        }

                    `;

                    document.head.appendChild(style);

                }

            } catch (e) {}

        })();

        """.trimIndent()

    }



    fun deepFlushAndReload() {

        isLoadingPage = true

        onRefreshNativeGeoIp()

        scope.launch(Dispatchers.IO) {

            flushKernelDnsSockets()

            delay(350)

            withContext(Dispatchers.Main) {

                webViewRef?.apply {

                    clearCache(true)

                    clearHistory()

                    WebStorage.getInstance().deleteAllData()

                    CookieManager.getInstance().removeAllCookies(null)

                    loadUrl("https://ipfighter.com/")

                }

            }

        }

    }



    LaunchedEffect(isProxyActive, activeSlot) {

        deepFlushAndReload()

    }



    CrystalGlassCard(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            // Sleek Cosmic Telemetry Header Above Live IPFighter View

            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .background(palette.glassPod)

                    .padding(horizontal = 12.dp, vertical = 9.dp),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                    Text(

                        text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🇺🇸") else "🌐",

                        fontSize = 20.sp

                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Box(

                                modifier = Modifier

                                    .size(7.dp)

                                    .background(if (isProxyActive) palette.emeraldLive else palette.sunCore, CircleShape)

                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(

                                text = if (isProxyActive) {

                                    "TUNNEL EXIT: ${publicIpInfo?.ip ?: "Verifying..."}"

                                } else {

                                    "DIRECT CONNECTION (TUNNEL OFF)"

                                },

                                fontSize = 11.5.sp,

                                fontWeight = FontWeight.ExtraBold,

                                color = palette.textPrimary,

                                maxLines = 1

                            )

                        }

                        Text(

                            text = if (isLoadingPage) {

                                "Syncing DNS & loading ipfighter.com ($loadProgress%)..."

                            } else {

                                "${publicIpInfo?.country ?: "Live Inspector"} • DNS & WebRTC Verified"

                            },

                            fontSize = 9.5.sp,

                            color = if (isProxyActive) palette.emeraldLive else palette.textSecondary,

                            maxLines = 1,

                            overflow = TextOverflow.Ellipsis

                        )

                    }

                }



                Button(

                    onClick = { deepFlushAndReload() },

                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),

                    shape = RoundedCornerShape(10.dp),

                    colors = ButtonDefaults.buttonColors(containerColor = palette.cyanAccent, contentColor = Color.White),

                    modifier = Modifier.height(34.dp)

                ) {

                    Text("↻ Deep Reload", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)

                }

            }



            if (isLoadingPage) {

                LinearProgressIndicator(

                    progress = { (loadProgress / 100f).coerceIn(0.05f, 1f) },

                    modifier = Modifier.fillMaxWidth().height(2.5.dp),

                    color = palette.emeraldLive,

                    trackColor = Color.Transparent

                )

            }



            AndroidView(

                modifier = Modifier

                    .fillMaxSize()

                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),

                factory = { ctx ->

                    WebView(ctx).apply {

                        layoutParams = ViewGroup.LayoutParams(

                            ViewGroup.LayoutParams.MATCH_PARENT,

                            ViewGroup.LayoutParams.MATCH_PARENT

                        )

                        setBackgroundColor(android.graphics.Color.parseColor("#060D20"))

                        settings.apply {

                            javaScriptEnabled = true

                            domStorageEnabled = true

                            databaseEnabled = true

                            loadsImagesAutomatically = true

                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            cacheMode = WebSettings.LOAD_NO_CACHE

                            useWideViewPort = true

                            loadWithOverviewMode = true

                            mediaPlaybackRequiresUserGesture = false

                            userAgentString = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                        }

                        webViewClient = object : WebViewClient() {

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {

                                super.onPageStarted(view, url, favicon)

                                isLoadingPage = true

                                view?.evaluateJavascript(cosmicCssAndTzScript, null)

                            }



                            override fun onPageFinished(view: WebView?, url: String?) {

                                super.onPageFinished(view, url)

                                view?.evaluateJavascript(cosmicCssAndTzScript, null)

                                isLoadingPage = false

                            }

                        }

                        webChromeClient = object : WebChromeClient() {

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {

                                loadProgress = newProgress

                                if (newProgress >= 40) {

                                    view?.evaluateJavascript(cosmicCssAndTzScript, null)

                                }

                                if (newProgress >= 100) isLoadingPage = false

                            }



                            override fun onPermissionRequest(request: PermissionRequest?) {

                                request?.grant(request.resources)

                            }

                        }

                        webViewRef = this

                        loadUrl("https://ipfighter.com/")

                    }

                },

                update = { view ->

                    webViewRef = view

                }

            )

        }

    }

}



// -------------------------------------------------------------

// TAB 2: FULL NODE CONFIG DECK

// -------------------------------------------------------------



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun CosmicNodeConfigScreen(

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

        verticalArrangement = Arrangement.spacedBy(10.dp)

    ) {

        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.padding(14.dp)) {

                Text(

                    text = "QUICK URI IMPORT & SOCKET PROBE",

                    fontSize = 11.sp,

                    fontWeight = FontWeight.ExtraBold,

                    color = palette.sunCore

                )

                Text(

                    text = "Paste vless://, trojan://, ss://, hy2://, socks5:// or test reachability",

                    fontSize = 10.sp,

                    color = palette.textSecondary

                )



                Spacer(modifier = Modifier.height(10.dp))



                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(8.dp)

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

                        shape = RoundedCornerShape(12.dp),

                        border = BorderStroke(1.dp, palette.cyanAccent)

                    ) {

                        Text("📋 Paste URI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.cyanAccent)

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

                        shape = RoundedCornerShape(12.dp),

                        colors = ButtonDefaults.buttonColors(containerColor = palette.sunCorona, contentColor = Color.White)

                    ) {

                        Text(

                            text = if (isCheckingAlive) "Probing..." else "🛰️ Probe Socket",

                            fontSize = 11.5.sp,

                            fontWeight = FontWeight.ExtraBold

                        )

                    }

                }



                if (aliveResult != null) {

                    Spacer(modifier = Modifier.height(8.dp))

                    when (val res = aliveResult) {

                        is TestResult.Success -> {

                            Text(

                                text = "✓ HANDSHAKE VERIFIED (${res.latencyMs} ms)",

                                color = palette.emeraldLive,

                                fontSize = 11.sp,

                                fontWeight = FontWeight.Bold

                            )

                        }

                        is TestResult.Failure -> {

                            Text(

                                text = "✕ UNREACHABLE: ${res.error}",

                                color = palette.crimsonAlert,

                                fontSize = 11.sp,

                                fontWeight = FontWeight.Bold

                            )

                        }

                        null -> {}

                    }

                }

            }

        }



        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.padding(14.dp)) {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Text(

                        text = "${SlotPlanetNames[activeSlot].uppercase()} NODE (P$activeSlot) PARAMETERS",

                        fontSize = 11.5.sp,

                        fontWeight = FontWeight.ExtraBold,

                        color = palette.sunCore

                    )

                    Text(

                        text = "Reset P$activeSlot",

                        fontSize = 10.5.sp,

                        fontWeight = FontWeight.Bold,

                        color = palette.crimsonAlert,

                        modifier = Modifier.clickable { onResetSlot() }

                    )

                }



                Spacer(modifier = Modifier.height(10.dp))

                Text("PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)

                Spacer(modifier = Modifier.height(4.dp))

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .horizontalScroll(rememberScrollState()),

                    horizontalArrangement = Arrangement.spacedBy(6.dp)

                ) {

                    ProxyType.values().forEach { t ->

                        FilterChip(

                            selected = proxyType == t,

                            onClick = { onProxyTypeChange(t); onConfigChanged() },

                            label = { Text(t.displayTitle, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }

                        )

                    }

                }



                Spacer(modifier = Modifier.height(8.dp))

                Text("TRANSPORT & WEBRTC MODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)

                Spacer(modifier = Modifier.height(4.dp))

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

                Text("IP STACK STRATEGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    listOf(

                        Pair(IpMode.IPV4_ONLY, "IPv4 Only"),

                        Pair(IpMode.DUAL_STACK, "Dual-Stack"),

                        Pair(IpMode.IPV6_ONLY, "IPv6 Only")

                    ).forEach { (mode, lbl) ->

                        FilterChip(

                            selected = ipMode == mode,

                            onClick = { onIpModeChange(mode); onConfigChanged() },

                            label = { Text(lbl, fontSize = 10.5.sp) }

                        )

                    }

                }



                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    OutlinedTextField(

                        value = host,

                        onValueChange = { onHostChange(it); onConfigChanged() },

                        label = { Text("Server Host / IP") },

                        placeholder = { Text("48.45.153.215") },

                        modifier = Modifier.weight(0.68f),

                        shape = RoundedCornerShape(12.dp),

                        singleLine = true

                    )

                    OutlinedTextField(

                        value = port,

                        onValueChange = { onPortChange(it); onConfigChanged() },

                        label = { Text("Port") },

                        placeholder = { Text("1080") },

                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                        modifier = Modifier.weight(0.32f),

                        shape = RoundedCornerShape(12.dp),

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

                                        label = { Text("Password") },

                                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),

                                        trailingIcon = {

                                            Text(

                                                text = if (showPass) "Hide" else "Show",

                                                fontSize = 10.5.sp,

                                                fontWeight = FontWeight.Bold,

                                                color = palette.cyanAccent,

                                                modifier = Modifier.clickable { showPass = !showPass }.padding(end = 8.dp)

                                            )

                                        },

                                        modifier = Modifier.weight(1f),

                                        shape = RoundedCornerShape(12.dp),

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

                                    shape = RoundedCornerShape(12.dp),

                                    singleLine = true

                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(

                                    value = password,

                                    onValueChange = { onPasswordChange(it); onConfigChanged() },

                                    label = { Text("Password / PSK") },

                                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),

                                    trailingIcon = {

                                        Text(

                                            text = if (showPass) "Hide" else "Show",

                                            fontSize = 10.5.sp,

                                            fontWeight = FontWeight.Bold,

                                            color = palette.cyanAccent,

                                            modifier = Modifier.clickable { showPass = !showPass }.padding(end = 8.dp)

                                        )

                                    },

                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(12.dp),

                                    singleLine = true

                                )

                            }



                            ProxyType.VLESS -> {

                                OutlinedTextField(

                                    value = password,

                                    onValueChange = { onPasswordChange(it); onConfigChanged() },

                                    label = { Text("VLESS UUID") },

                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(12.dp),

                                    singleLine = true

                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(

                                    value = sni,

                                    onValueChange = { onSniChange(it); onConfigChanged() },

                                    label = { Text("TLS SNI") },

                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(12.dp),

                                    singleLine = true

                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                                    OutlinedTextField(

                                        value = realityPublicKey,

                                        onValueChange = { onRealityPublicKeyChange(it); onConfigChanged() },

                                        label = { Text("REALITY Public Key") },

                                        modifier = Modifier.weight(1f),

                                        shape = RoundedCornerShape(12.dp),

                                        singleLine = true

                                    )

                                    OutlinedTextField(

                                        value = realityShortId,

                                        onValueChange = { onRealityShortIdChange(it); onConfigChanged() },

                                        label = { Text("Short ID") },

                                        modifier = Modifier.weight(0.55f),

                                        shape = RoundedCornerShape(12.dp),

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

                                            fontSize = 10.5.sp,

                                            fontWeight = FontWeight.Bold,

                                            color = palette.cyanAccent,

                                            modifier = Modifier.clickable { showPass = !showPass }.padding(end = 8.dp)

                                        )

                                    },

                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(12.dp),

                                    singleLine = true

                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(

                                    value = sni,

                                    onValueChange = { onSniChange(it); onConfigChanged() },

                                    label = { Text("TLS SNI") },

                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(12.dp),

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

}



// -------------------------------------------------------------

// TAB 3: ROUTING & SPLIT TUNNELING | TAB 4: LOGS | BOTTOM DOCK

// -------------------------------------------------------------



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun CosmicRoutingScreen(

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

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.padding(14.dp)) {

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                    Column(modifier = Modifier.weight(1f)) {

                        Text("Auto-Start on Boot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)

                        Text(if (startOnBoot) "Armed for P$bootSlot (/data/adb/service.d)" else "Manual start", fontSize = 10.5.sp, color = palette.textSecondary)

                    }

                    Switch(checked = startOnBoot, onCheckedChange = onStartOnBootChange)

                }



                if (startOnBoot) {

                    Row(

                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),

                        horizontalArrangement = Arrangement.spacedBy(6.dp)

                    ) {

                        (0 until ProfileManager.MAX_SLOTS).forEach { s ->

                            FilterChip(

                                selected = bootSlot == s,

                                onClick = { onBootSlotChange(s) },

                                label = { Text("P$s", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }

                            )

                        }

                    }

                }



                HorizontalDivider(color = palette.glassRimBottom, modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                    Column(modifier = Modifier.weight(1f)) {

                        Text("Hotspot & Tethering Bridge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)

                        Text("Share tunnel with Wi-Fi / USB clients", fontSize = 10.5.sp, color = palette.textSecondary)

                    }

                    Switch(checked = routeHotspot && uid == 0, enabled = uid == 0, onCheckedChange = onRouteHotspotChange)

                }

                HorizontalDivider(color = palette.glassRimBottom, modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                    Column(modifier = Modifier.weight(1f)) {

                        Text("Tunnel All Apps (User #$uid)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)

                        Text(if (routeWholeProfile) "Global profile active" else "${selectedPackages.size} apps selected", fontSize = 10.5.sp, color = palette.textSecondary)

                    }

                    Switch(checked = routeWholeProfile, onCheckedChange = onRouteWholeProfileChange)

                }

            }

        }



        if (!routeWholeProfile) {

            CrystalGlassCard(

                modifier = Modifier

                    .fillMaxWidth()

                    .weight(1f)

            ) {

                Column(modifier = Modifier.padding(12.dp)) {

                    var query by remember { mutableStateOf("") }

                    val filtered = remember(query, installedApps, selectedPackages) {

                        val base = if (query.isEmpty()) installedApps else installedApps.filter {

                            it.name.contains(query, true) || it.packageName.contains(query, true)

                        }

                        base.sortedWith(compareByDescending<AppItem> { selectedPackages.contains(it.packageName) }.thenBy { it.name.lowercase() })

                    }



                    OutlinedTextField(

                        value = query,

                        onValueChange = { query = it },

                        placeholder = { Text("Search installed apps...") },

                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(12.dp),

                        singleLine = true

                    )



                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text("${selectedPackages.size} selected", fontSize = 10.5.sp, color = palette.textSecondary)

                        Row {

                            TextButton(onClick = onSelectAll) { Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.cyanAccent) }

                            TextButton(onClick = onClearAll) { Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.crimsonAlert) }

                        }

                    }



                    LazyColumn(modifier = Modifier.fillMaxSize()) {

                        items(filtered, key = { it.packageName }) { app ->

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

                                    Text(app.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1)

                                    Text("${app.packageName} • UID ${app.uid}", fontSize = 10.sp, color = palette.textSecondary, maxLines = 1)

                                }

                                Checkbox(checked = checked, onCheckedChange = { onToggleApp(app.packageName) })

                            }

                        }

                    }

                }

            }

        }

    }

}



@Composable

fun CosmicLogsScreen(

    activeSlot: Int,

    runningSlot: Int?,

    logs: String,

    onRefresh: () -> Unit,

    onClear: () -> Unit

) {

    val palette = LocalCosmicPalette.current

    val context = LocalContext.current

    CrystalGlassCard(modifier = Modifier.fillMaxSize()) {

        Column(

            modifier = Modifier

                .fillMaxSize()

                .padding(14.dp)

        ) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                Text("KERNEL LOGS (P${runningSlot ?: activeSlot})", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.sunCore)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {

                    TextButton(onClick = {

                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                        cb.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", logs))

                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()

                    }) { Text("Copy", color = palette.sunCore, fontWeight = FontWeight.Bold) }

                    TextButton(onClick = onClear) { Text("Clear", color = palette.crimsonAlert, fontWeight = FontWeight.Bold) }

                    TextButton(onClick = onRefresh) { Text("Refresh", color = palette.cyanAccent, fontWeight = FontWeight.Bold) }

                }

            }

            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .clip(RoundedCornerShape(14.dp))

                    .background(palette.glassPod)

                    .padding(10.dp)

            ) {

                Text(

                    text = logs,

                    fontFamily = FontFamily.Monospace,

                    fontSize = 11.sp,

                    color = palette.textPrimary,

                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())

                )

            }

        }

    }

}



@Composable

fun CosmicBottomDock(selectedTab: Int, onSelectTab: (Int) -> Unit) {

    val palette = LocalCosmicPalette.current

    val tabs = listOf(

        Pair("☀️", "Cockpit"),

        Pair("🌐", "IPFighter"),

        Pair("🪐", "Nodes"),

        Pair("🛰️", "Routing"),

        Pair("📜", "Logs")

    )



    Row(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 14.dp, vertical = 8.dp)

            .clip(RoundedCornerShape(20.dp))

            .background(palette.glassSurface)

            .border(

                1.2.dp,

                Brush.linearGradient(listOf(palette.glassRimTop, palette.glassRimBottom)),

                RoundedCornerShape(20.dp)

            )

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

                        if (sel) Brush.horizontalGradient(listOf(palette.sunCorona, palette.sunCore))

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

        text = String.format("⏱ %02d:%02d", (sec % 3600) / 60, sec % 60),

        fontSize = 10.sp,

        fontWeight = FontWeight.ExtraBold,

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

    b >= 1024 -> String.format("%.0f KB/s", b / (1024.0 * 1024.0))

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
