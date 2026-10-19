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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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

data class AppItem(
    val name: String,
    val packageName: String,
    val uid: Int
)

data class LiveNetworkState(
    val transportLabel: String = "PROBING",
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
        ProxyType.SOCKS5 -> "S5"
        ProxyType.SOCKS4 -> "S4"
        ProxyType.HTTP -> "HTTP"
        ProxyType.SHADOWSOCKS -> "SS"
        ProxyType.VLESS -> "VLESS"
        ProxyType.TROJAN -> "TRJ"
        ProxyType.HYSTERIA2 -> "HY2"
    }

// Obsidian Cyber-Pulse Palette
private val CyberAbyss = Color(0xFF060911)
private val CyberSurface = Color(0xFF0D1322)
private val CyberElevated = Color(0xFF151E32)
private val CyberBorder = Color(0xFF22304D)
private val CyberCyan = Color(0xFF00E5FF)
private val CyberSky = Color(0xFF38BDF8)
private val CyberIndigo = Color(0xFF6366F1)
private val CyberViolet = Color(0xFF818CF8)
private val CyberEmerald = Color(0xFF10B981)
private val CyberEmeraldBright = Color(0xFF34D399)
private val CyberEmeraldSoft = Color(0xFF092823)
private val CyberCyanSoft = Color(0xFF0A2436)
private val CyberTextPrimary = Color(0xFFF1F5F9)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberTextMuted = Color(0xFF64748B)
private val CyberAmber = Color(0xFFF59E0B)
private val CyberDanger = Color(0xFFF43F5E)

@Composable
fun CyberGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CyberBorder,
    backgroundColor: Color = CyberSurface,
    accentBrush: Brush? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (accentBrush != null) Modifier.background(accentBrush)
                else Modifier.background(backgroundColor)
            )
            .border(1.dp, borderColor, shape)
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
                primary = CyberCyan,
                onPrimary = CyberAbyss,
                secondary = CyberIndigo,
                background = CyberAbyss,
                surface = CyberSurface,
                onBackground = CyberTextPrimary,
                onSurface = CyberTextPrimary,
                error = CyberDanger
            )

            MaterialTheme(colorScheme = scheme) {
                MainScreen(
                    rootState = rootState,
                    rootLabel = rootLabel,
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
                override fun onAvailable(network: Network) {
                    handleNetworkChange()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    handleNetworkChange()
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    handleNetworkChange()
                }

                override fun onLost(network: Network) {
                    handleNetworkChange()
                }
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
                liveNetState = LiveNetworkState(
                    transportLabel = "OFFLINE",
                    interfaceName = iface,
                    isCellular = false,
                    isWifi = false,
                    isConnected = false
                )
                return
            }

            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isEth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

            val label = when {
                isWifi -> "WI-FI"
                isCell -> "CELLULAR"
                isEth -> "ETHERNET"
                else -> "ONLINE"
            }

            liveNetState = LiveNetworkState(
                transportLabel = label,
                interfaceName = iface.ifEmpty { if (isWifi) "wlan0" else if (isCell) "rmnet+" else "net" },
                isCellular = isCell,
                isWifi = isWifi,
                isConnected = true
            )
        } catch (_: Exception) {
            liveNetState = LiveNetworkState("ACTIVE", detectFallbackInterface(), false, false, true)
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
    var rxHistory by remember { mutableStateOf(List(28) { 0L }) }
    var txHistory by remember { mutableStateOf(List(28) { 0L }) }

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
            rxHistory = List(28) { 0L }
            txHistory = List(28) { 0L }
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
        val protoMap = mutableMapOf<Int, ProxyType>()
        for (i in 0 until ProfileManager.MAX_SLOTS) {
            val sp = getSlotPrefs(i)
            if (!sp.getString("host", "").isNullOrBlank()) set.add(i)
            val pType = runCatching { ProxyType.valueOf(sp.getString("proxy_type", "SOCKS5")!!) }.getOrDefault(ProxyType.SOCKS5)
            protoMap[i] = pType
        }
        configuredSlots = set
        slotProtocolMap = protoMap
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
                delay(if (attempt == 1) 350L else 1000L)
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

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("Initializing live kernel telemetry...") }
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
                currentLogs = output.ifEmpty { "No kernel logs recorded yet." }
                if (!liveLogStreaming) break
                delay(1600)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberAbyss)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CyberCommandHeader(
                isProxyActive = isProxyActive,
                runningSlot = runningSlot,
                activeSlot = activeSlot,
                rootState = rootState,
                liveNet = liveNet,
                onRecheckRoot = onRecheckRoot
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberSlotMatrix(
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
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> CyberDashboardTab(
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
                    transportMode = transportMode,
                    ipMode = ipMode,
                    strictWebRtcShield = strictWebRtcShield,
                    routeHotspot = routeHotspot,
                    routeWholeProfile = routeWholeProfile,
                    selectedAppsCount = selectedPackages.size,
                    currentRxRate = currentRxRate,
                    currentTxRate = currentTxRate,
                    totalRxBytes = totalRxBytes,
                    totalTxBytes = totalTxBytes,
                    rxHistory = rxHistory,
                    txHistory = txHistory,
                    onRefreshIp = { triggerExitNodeCheck() },
                    onOpenIpFighterTab = { selectedTab = 1 },
                    onToggleProxy = {
                        if (isProxyActive && runningSlot == activeSlot) {
                            onStopProxy { }
                        } else {
                            if (host.trim().isEmpty()) {
                                selectedTab = 2
                                return@CyberDashboardTab
                            }
                            saveConfigForSlot(activeSlot, true)
                            val targets = buildTargetUids()
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
                            return@CyberDashboardTab
                        }
                        saveConfigForSlot(activeSlot)
                        isTesting = true
                        testStatus = "Probing ${host.trim()}:${port.trim()}..."
                        scope.launch {
                            val res = ProxyTester.testProxy(getCurrentSettings())
                            testStatus = when (res) {
                                is TestResult.Success -> "ONLINE • ${res.latencyMs} ms"
                                is TestResult.Failure -> "FAILED • ${res.error}"
                            }
                            isTesting = false
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
                                    Toast.makeText(context, "Imported all 5 slots from Downloads", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No backup found at ${PersistentStorage.PUBLIC_BACKUP_PATH}", Toast.LENGTH_SHORT).show()
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

        CyberBottomNav(
            selectedTab = selectedTab,
            isProxyActive = isProxyActive,
            onSelectTab = { selectedTab = it }
        )
    }
}

@Composable
fun CyberCommandHeader(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    liveNet: LiveNetworkState,
    onRecheckRoot: () -> Unit
) {
    val userId = ProfileManager.androidUserId
    val infiniteTransition = rememberInfiniteTransition(label = "headerPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isProxyActive) CyberEmeraldSoft else CyberCyanSoft)
                    .border(
                        width = 1.5.dp,
                        color = if (isProxyActive) CyberEmeraldBright else CyberCyan.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    drawCircle(
                        color = (if (isProxyActive) CyberEmeraldBright else CyberCyan).copy(alpha = 0.25f),
                        radius = w * 0.46f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    val boltPath = Path().apply {
                        moveTo(w * 0.55f, h * 0.08f)
                        lineTo(w * 0.22f, h * 0.52f)
                        lineTo(w * 0.47f, h * 0.52f)
                        lineTo(w * 0.38f, h * 0.92f)
                        lineTo(w * 0.78f, h * 0.44f)
                        lineTo(w * 0.53f, h * 0.44f)
                        close()
                    }
                    drawPath(
                        path = boltPath,
                        color = if (isProxyActive) CyberEmeraldBright else CyberCyan
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NAMELESS",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        color = CyberTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isProxyActive) CyberEmeraldSoft else CyberElevated)
                            .border(
                                1.dp,
                                if (isProxyActive) CyberEmeraldBright.copy(alpha = 0.6f) else CyberBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isProxyActive) "KERNEL LIVE" else "STANDBY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isProxyActive) CyberEmeraldBright else CyberTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "U#$userId • ${liveNet.transportLabel} (${liveNet.interfaceName}) • P${runningSlot ?: activeSlot}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isProxyActive) CyberSky else CyberTextSecondary
                )
            }
        }

        val rootCol = when (rootState) {
            RootState.GRANTED -> CyberEmeraldBright
            RootState.DENIED -> CyberDanger
            RootState.CHECKING -> CyberAmber
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CyberSurface)
                .border(1.dp, rootCol.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .clickable { onRecheckRoot() }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(rootCol.copy(alpha = dotAlpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (rootState == RootState.GRANTED) "ROOT UID 0" else "NO ROOT",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyberTextPrimary
            )
        }
    }
}

@Composable
fun CyberSlotMatrix(
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
            val protoBadge = slotProtocolMap[idx]?.shortBadge ?: "S5"

            val bgCol = when {
                run -> CyberEmeraldSoft
                sel -> CyberCyanSoft
                else -> CyberSurface
            }

            val borderCol = when {
                run -> CyberEmeraldBright
                sel -> CyberCyan
                isConf -> CyberBorder.copy(alpha = 0.9f)
                else -> CyberBorder.copy(alpha = 0.45f)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgCol)
                    .border(if (sel || run) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(14.dp))
                    .clickable { onSelectSlot(idx) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    run -> CyberEmeraldBright
                                    isBoot -> CyberAmber
                                    isConf -> CyberSky
                                    else -> CyberTextMuted.copy(alpha = 0.4f)
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "P$idx",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            run -> CyberEmeraldBright
                            sel -> CyberCyan
                            else -> CyberTextPrimary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        run -> "LIVE • $protoBadge"
                        isBoot -> "BOOT • $protoBadge"
                        isConf -> protoBadge
                        else -> "EMPTY"
                    },
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        run -> CyberEmeraldBright
                        isBoot -> CyberAmber
                        isConf -> CyberTextSecondary
                        else -> CyberTextMuted
                    }
                )
            }
        }
    }
}

@Composable
fun CyberDashboardTab(
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
    transportMode: TransportMode,
    ipMode: IpMode,
    strictWebRtcShield: Boolean,
    routeHotspot: Boolean,
    routeWholeProfile: Boolean,
    selectedAppsCount: Int,
    currentRxRate: Long,
    currentTxRate: Long,
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
    val context = LocalContext.current
    val isCurrentSlotRunning = isProxyActive && runningSlot == activeSlot

    val infiniteTransition = rememberInfiniteTransition(label = "reactorOrbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. HERO ORBITAL REACTOR & EXIT NODE INTELLIGENCE CARD
        CyberGlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isProxyActive) CyberEmeraldBright.copy(alpha = 0.75f) else CyberBorder,
            accentBrush = if (isProxyActive) {
                Brush.verticalGradient(listOf(CyberEmeraldSoft.copy(alpha = 0.85f), CyberSurface))
            } else {
                Brush.verticalGradient(listOf(CyberCyanSoft.copy(alpha = 0.45f), CyberSurface))
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    when {
                                        isTransitioning -> CyberAmber
                                        isProxyActive -> CyberEmeraldBright
                                        else -> CyberTextMuted
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isTransitioning -> "SYNCHRONIZING KERNEL NETFILTER..."
                                isProxyActive -> "ZERO-LEAK TUNNEL • SLOT P${runningSlot ?: activeSlot}"
                                else -> "READY • SLOT P$activeSlot"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isProxyActive) CyberEmeraldBright else CyberTextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activePid != null) {
                            Text(
                                text = "PID $activePid",
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberSky
                            )
                        }
                        if (isProxyActive && sessionStartMs > 0L) {
                            UptimeBadge(sessionStartMs)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val coreColor = when {
                    isTransitioning -> CyberAmber
                    isProxyActive -> CyberEmeraldBright
                    else -> CyberCyan
                }

                // Animated Orbital Reactor Button
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(
                            if (isProxyActive) CyberEmeraldSoft.copy(alpha = 0.9f)
                            else CyberElevated.copy(alpha = 0.85f)
                        )
                        .clickable(enabled = !isTransitioning) { onToggleProxy() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isProxyActive) Modifier.graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                else Modifier
                            )
                    ) {
                        val w = size.width
                        val h = size.height
                        val center = Offset(w / 2f, h / 2f)

                        // Outer dashed orbital telemetry ring
                        rotate(degrees = if (isProxyActive || isTransitioning) orbitAngle else 0f, pivot = center) {
                            drawCircle(
                                color = coreColor.copy(alpha = if (isProxyActive) 0.75f else 0.35f),
                                radius = w * 0.46f,
                                style = Stroke(
                                    width = 2.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                                )
                            )
                        }

                        // Counter-rotating inner shield ring
                        rotate(degrees = if (isProxyActive || isTransitioning) -orbitAngle * 1.4f else 0f, pivot = center) {
                            drawArc(
                                color = (if (isProxyActive) CyberCyan else CyberViolet).copy(alpha = pulseAlpha),
                                startAngle = 20f,
                                sweepAngle = 140f,
                                useCenter = false,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = (if (isProxyActive) CyberCyan else CyberViolet).copy(alpha = pulseAlpha),
                                startAngle = 200f,
                                sweepAngle = 140f,
                                useCenter = false,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Lightning Bolt Core
                        val bw = w * 0.46f
                        val bh = h * 0.46f
                        val ox = (w - bw) / 2f
                        val oy = (h - bh) / 2f
                        val boltPath = Path().apply {
                            moveTo(ox + bw * 0.54f, oy + bh * 0.06f)
                            lineTo(ox + bw * 0.20f, oy + bh * 0.52f)
                            lineTo(ox + bw * 0.46f, oy + bh * 0.52f)
                            lineTo(ox + bw * 0.36f, oy + bh * 0.94f)
                            lineTo(ox + bw * 0.80f, oy + bh * 0.44f)
                            lineTo(ox + bw * 0.54f, oy + bh * 0.44f)
                            close()
                        }

                        if (isProxyActive) {
                            drawPath(
                                path = boltPath,
                                color = CyberEmerald.copy(alpha = 0.45f),
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        drawPath(
                            path = boltPath,
                            color = if (isProxyActive) CyberEmeraldBright else coreColor.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when {
                        isProxyActive && publicIpInfo != null -> "${publicIpInfo.flagEmoji} ${publicIpInfo.ip}"
                        isProxyActive && isFetchingIp -> "Verifying Exit Node IP..."
                        host.isNotBlank() -> "${host.trim()}:${port.trim()}"
                        else -> "Tap to Configure Slot P$activeSlot"
                    },
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberTextPrimary,
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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        isProxyActive && publicIpInfo != null -> buildString {
                            append(publicIpInfo.country)
                            if (publicIpInfo.timezone.isNotEmpty()) append(" • ${publicIpInfo.timezone}")
                            if (publicIpInfo.isp.isNotEmpty()) append(" • ${publicIpInfo.isp}")
                        }
                        isProxyActive -> "All ${liveNet.transportLabel} traffic locked into sing-box tunnel"
                        else -> "${proxyType.displayTitle} • ${if (routeWholeProfile) "Entire Profile (U#${ProfileManager.androidUserId})" else "$selectedAppsCount Apps Selected"}"
                    },
                    fontSize = 11.5.sp,
                    color = CyberTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberElevated,
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onRefreshIp() }
                    ) {
                        Text(
                            text = if (isFetchingIp) "Probing..." else "↻ Verify Exit IP",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberCyanSoft,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenIpFighterTab() }
                    ) {
                        Text(
                            text = "⚡ IPFighter Live Check",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 2. LIVE CELLULAR & WI-FI ZERO-LEAK SHIELD TELEMETRY MATRIX
        CyberGlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE KERNEL SHIELD TELEMETRY",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyberSky
                )
                Text(
                    text = "${liveNet.transportLabel} (${liveNet.interfaceName})",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (liveNet.isConnected) CyberEmeraldBright else CyberAmber
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CyberTelemetryBadge("DNS 53/853 Hijack", isProxyActive)
                CyberTelemetryBadge(if (strictWebRtcShield) "WebRTC UDP Lock" else "STUN Port Block", isProxyActive)
                CyberTelemetryBadge("464XLAT / IPv6 Guard", isProxyActive)
                CyberTelemetryBadge(proxyType.displayTitle, true)
                CyberTelemetryBadge(ipMode.name.replace("_", " "), true)
                if (routeHotspot) CyberTelemetryBadge("Hotspot Bridge", isProxyActive)
            }
        }

        // 3. REAL-TIME WAVEFORM SPARKLINE & THROUGHPUT METERS
        CyberGlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DOWNLOAD (RX)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CyberTextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${splitSpeedValue(currentRxRate)} ${splitSpeedUnit(currentRxRate)}",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberEmeraldBright
                    )
                    Text(
                        text = "Total: ${formatBytes(totalRxBytes)}",
                        fontSize = 10.5.sp,
                        color = CyberTextMuted
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("UPLOAD (TX)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CyberTextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${splitSpeedValue(currentTxRate)} ${splitSpeedUnit(currentTxRate)}",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberCyan
                    )
                    Text(
                        text = "Total: ${formatBytes(totalTxBytes)}",
                        fontSize = 10.5.sp,
                        color = CyberTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Dual-Channel Sparkline Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberAbyss)
                    .border(1.dp, CyberBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val maxVal = (rxHistory + txHistory).maxOrNull()?.coerceAtLeast(1024L) ?: 1024L

                    fun buildWavePath(points: List<Long>): Path {
                        val p = Path()
                        if (points.isEmpty()) return p
                        val stepX = w / (points.size - 1).coerceAtLeast(1)
                        points.forEachIndexed { i, v ->
                            val x = i * stepX
                            val norm = (v.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                            val y = h - (norm * (h * 0.88f)) - (h * 0.06f)
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        return p
                    }

                    val txPath = buildWavePath(txHistory)
                    val rxPath = buildWavePath(rxHistory)

                    drawPath(
                        path = txPath,
                        color = CyberCyan.copy(alpha = 0.65f),
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = rxPath,
                        color = CyberEmeraldBright,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        // 4. ACTION COMMAND BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPingNode,
                enabled = !isTesting && !isTransitioning,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyberBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CyberSurface,
                    contentColor = CyberTextPrimary
                ),
                modifier = Modifier
                    .weight(0.36f)
                    .height(52.dp)
            ) {
                Text(
                    text = if (isTesting) "Probing..." else "⚡ Ping P$activeSlot",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onToggleProxy,
                enabled = !isTransitioning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentSlotRunning) CyberDanger else CyberEmerald,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(0.64f)
                    .height(52.dp)
            ) {
                Text(
                    text = when {
                        isTransitioning -> "SYNCHRONIZING..."
                        !isProxyActive -> "ENGAGE TUNNEL (P$activeSlot)"
                        isCurrentSlotRunning -> "DISCONNECT (P$activeSlot)"
                        else -> "HOT-SWAP TO SLOT P$activeSlot"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        if (testStatus != null) {
            Text(
                text = testStatus,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (testStatus.startsWith("ONLINE")) CyberEmeraldBright else CyberDanger,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun CyberTelemetryBadge(text: String, active: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) CyberEmeraldSoft else CyberElevated)
            .border(
                1.dp,
                if (active) CyberEmeraldBright.copy(alpha = 0.5f) else CyberBorder,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(if (active) CyberEmeraldBright else CyberTextMuted, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) CyberTextPrimary else CyberTextSecondary
        )
    }
}

// -------------------------------------------------------------
// TAB 1: EPHEMERAL NON-CACHED LIVE IPFIGHTER & LEAK INSPECTOR TAB
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

    val inspectorTargets = listOf(
        Pair("IPFighter", "https://ipfighter.com/"),
        Pair("WebRTC Test", "https://browserleaks.com/webrtc"),
        Pair("DNS Leak Test", "https://browserleaks.com/dns")
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

    CyberGlassCard(
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
                            .background(if (isProxyActive) CyberEmeraldBright else CyberAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyActive) {
                            "LIVE INSPECTOR • ${publicIpInfo?.ip ?: "TUNNEL ACTIVE"}"
                        } else {
                            "LIVE INSPECTOR • DIRECT (${liveNet.transportLabel})"
                        },
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inspectorTargets.forEachIndexed { idx, (label, _) ->
                        val sel = targetSiteIndex == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) CyberCyanSoft else CyberElevated)
                                .border(1.dp, if (sel) CyberCyan else CyberBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    targetSiteIndex = idx
                                    reloadNonce++
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) CyberCyan else CyberTextSecondary
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
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberEmerald,
                    contentColor = Color.White
                )
            ) {
                Text("↻ Fresh", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 4.dp),
                color = CyberCyan,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
        ) {
            key(reloadNonce, targetSiteIndex) {
                val baseUrl = inspectorTargets[targetSiteIndex].second
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
        CyberGlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "QUICK URI IMPORT & SOCKET PROBE",
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(10.dp))

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
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("📋 Paste URI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberIndigo,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isCheckingAlive) "Probing..." else "⚡ Test Socket",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (aliveResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                when (val res = aliveResult) {
                    is TestResult.Success -> {
                        Text(
                            text = "SOCKET ONLINE • ${res.latencyMs} ms",
                            color = CyberEmeraldBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is TestResult.Failure -> {
                        Text(
                            text = "SOCKET FAILED • ${res.error}",
                            color = CyberDanger,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    null -> {}
                }
            }
        }

        // Strict WebRTC & Cellular/Wi-Fi DNS Shield Card
        CyberGlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (strictWebRtcShield) CyberEmeraldBright.copy(alpha = 0.5f) else CyberBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Strict WebRTC & UDP Leak Shield",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextPrimary
                    )
                    Text(
                        text = "Redirects all UDP connect()/getsockname() & STUN to 127.0.0.1 discard so Cellular & Wi-Fi IPs never leak",
                        fontSize = 11.sp,
                        color = CyberTextSecondary
                    )
                }
                Switch(
                    checked = strictWebRtcShield,
                    onCheckedChange = {
                        onStrictWebRtcChange(it)
                        onConfigChanged()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CyberEmerald
                    )
                )
            }
        }

        CyberGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SLOT P$activeSlot NODE STUDIO",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
                Text(
                    text = "Reset P$activeSlot",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberDanger,
                    modifier = Modifier.clickable { onResetSlot() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Protocol Engine", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = CyberTextSecondary)
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
                        label = { Text(t.displayTitle, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberIndigo,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Transport Mode", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = CyberTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = transportMode == TransportMode.TCP_AND_UDP,
                    onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                    label = { Text("TCP + UDP", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberIndigo,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = transportMode == TransportMode.TCP_ONLY,
                    onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                    label = { Text("TCP Only", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberIndigo,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("IP Stack Policy (464XLAT Cellular Safe)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = CyberTextSecondary)
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
                        label = { Text(lbl, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberIndigo,
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

            when (proxyType) {
                ProxyType.SOCKS5, ProxyType.HTTP -> {
                    var showPass by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    modifier = Modifier.clickable { showPass = !showPass }.padding(end = 10.dp)
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
                                color = CyberCyan,
                                modifier = Modifier.clickable { showPass = !showPass }.padding(end = 10.dp)
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
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { onSniChange(it); onConfigChanged() },
                        label = { Text("TLS SNI") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        label = { Text("${proxyType.displayTitle} Password") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (showPass) "Hide" else "Show",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                modifier = Modifier.clickable { showPass = !showPass }.padding(end = 10.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
        CyberGlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "KERNEL BOOT & TETHERING MATRIX",
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Start on System Boot", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                    Text(
                        text = if (startOnBoot) "Armed for Slot P$bootSlot (/data/adb/service.d)" else "Manual launch only",
                        fontSize = 11.5.sp,
                        color = CyberTextSecondary
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CyberEmerald
                    )
                )
            }

            if (startOnBoot) {
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
                            label = { Text("P$s", fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberIndigo,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = CyberBorder, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hotspot & Tethering Bridge", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                    Text("Pass Wi-Fi Hotspot / USB RNDIS traffic through proxy with zero leaks", fontSize = 11.5.sp, color = CyberTextSecondary)
                }
                Switch(
                    checked = routeHotspot && uid == 0,
                    enabled = uid == 0,
                    onCheckedChange = onRouteHotspotChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CyberEmerald
                    )
                )
            }
        }

        CyberGlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "5-SLOT VAULT BACKUP (/sdcard/Download)",
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("Export 5 Slots", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                }
                Button(
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberIndigo,
                        contentColor = Color.White
                    )
                ) {
                    Text("Import Vault", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        CyberGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tunnel Entire Android Profile (User #$uid)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                    Text(
                        text = if (routeWholeProfile) "Routing 100% of apps & sandbox processes in User #$uid" else "${selectedPackages.size} apps selected for split tunnel",
                        fontSize = 11.5.sp,
                        color = CyberTextSecondary
                    )
                }
                Switch(
                    checked = routeWholeProfile,
                    onCheckedChange = onRouteWholeProfileChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CyberEmerald
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
                    Text("${selectedPackages.size} selected", fontSize = 11.5.sp, color = CyberTextSecondary)
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                        }
                        TextButton(onClick = onClearAll) {
                            Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberDanger)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
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
                                Text(app.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CyberTextPrimary, maxLines = 1)
                                Text("${app.packageName} • UID ${app.uid}", fontSize = 11.sp, color = CyberTextSecondary, maxLines = 1)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = CyberEmerald,
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

    CyberGlassCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (showKernelAuditMode) "NETFILTER RULE AUDIT (P${runningSlot ?: activeSlot})" else "LIVE KERNEL STREAM (P${runningSlot ?: activeSlot})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (liveLogStreaming) CyberEmeraldSoft else CyberElevated)
                            .border(1.dp, if (liveLogStreaming) CyberEmeraldBright else CyberBorder, RoundedCornerShape(8.dp))
                            .clickable { onToggleLiveStream() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (liveLogStreaming) "● LIVE STREAM ON" else "○ PAUSED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (liveLogStreaming) CyberEmeraldBright else CyberTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showKernelAuditMode) CyberCyanSoft else CyberElevated)
                            .border(1.dp, if (showKernelAuditMode) CyberCyan else CyberBorder, RoundedCornerShape(8.dp))
                            .clickable { onToggleAuditMode() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showKernelAuditMode) "View Sing-Box Logs" else "Audit Iptables",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showKernelAuditMode) CyberCyan else CyberTextSecondary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", logs))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = CyberTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onClear) { Text("Clear", color = CyberDanger, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onRefresh) { Text("Sync", color = CyberCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(CyberAbyss)
                .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = CyberTextPrimary,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun CyberBottomNav(
    selectedTab: Int,
    isProxyActive: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf("Reactor", "IPFighter", "Nodes", "Matrix", "Stream")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(18.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val sel = selectedTab == idx
            val activeAccent = if (isProxyActive && idx == 0) CyberEmerald else CyberIndigo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (sel) activeAccent else Color.Transparent)
                    .clickable { onSelectTab(idx) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (sel) Color.White else CyberTextSecondary,
                    maxLines = 1
                )
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
        color = CyberEmeraldBright
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
