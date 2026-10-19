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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

// LocalSend Dark Teal Palette (Untouched)
private val LocalSendBg = Color(0xFF0F171A)
private val LocalSendCard = Color(0xFF182429)
private val LocalSendCardElevated = Color(0xFF203036)
private val LocalSendBorder = Color(0xFF2A3E46)
private val LocalSendTeal = Color(0xFF0D9488)
private val LocalSendTealLight = Color(0xFF2DD4BF)
private val LocalSendTealSoft = Color(0xFF133836)
private val LocalSendTextPrimary = Color(0xFFE6F1F5)
private val LocalSendTextSecondary = Color(0xFF94ADB8)
private val LocalSendTextMuted = Color(0xFF637B85)
private val LocalSendAmber = Color(0xFFF59E0B)
private val LocalSendDanger = Color(0xFFEF4444)

@Composable
fun LocalSendSurfaceCard(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalSendBorder,
    backgroundColor: Color = LocalSendCard,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()

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
            }
        })

        setContent {
            val scheme = darkColorScheme(
                primary = LocalSendTealLight,
                onPrimary = LocalSendBg,
                secondary = LocalSendTeal,
                background = LocalSendBg,
                surface = LocalSendCard,
                onBackground = LocalSendTextPrimary,
                onSurface = LocalSendTextPrimary,
                error = LocalSendDanger
            )

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

                currentRxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)
                currentTxRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)
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
                withContext(Dispatchers.Main) {
                    currentLogs = l.ifEmpty { "No logs recorded." }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalSendBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            LocalSendTopHeader(
                isProxyActive = isProxyActive,
                runningSlot = runningSlot,
                activeSlot = activeSlot,
                rootState = rootState,
                onRecheckRoot = onRecheckRoot
            )
            Spacer(modifier = Modifier.height(10.dp))
            LocalSendSlotBar(
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> LocalSendStatusTab(
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
                    transportMode = transportMode,
                    ipMode = ipMode,
                    routeHotspot = routeHotspot,
                    routeWholeProfile = routeWholeProfile,
                    selectedAppsCount = selectedPackages.size,
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
                                return@LocalSendStatusTab
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
                            return@LocalSendStatusTab
                        }
                        saveConfigForSlot(activeSlot)
                        isTesting = true
                        testStatus = "Pinging ${host.trim()}:${port.trim()}..."
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
                    onRefresh = {
                        scope.launch(Dispatchers.IO) {
                            val l = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, runningSlot ?: activeSlot)
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

        LocalSendBottomNav(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it }
        )
    }
}

@Composable
fun LocalSendTopHeader(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    onRecheckRoot: () -> Unit
) {
    val userId = ProfileManager.androidUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalSendTealSoft)
                    .border(1.5.dp, if (isProxyActive) LocalSendTealLight else LocalSendBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width
                    val h = size.height
                    val boltPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.54f, h * 0.08f)
                        lineTo(w * 0.22f, h * 0.52f)
                        lineTo(w * 0.46f, h * 0.52f)
                        lineTo(w * 0.38f, h * 0.92f)
                        lineTo(w * 0.78f, h * 0.44f)
                        lineTo(w * 0.54f, h * 0.44f)
                        close()
                    }
                    drawPath(
                        path = boltPath,
                        color = if (isProxyActive) LocalSendTealLight else LocalSendTeal
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Nameless Proxy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSendTextPrimary
                )
                Text(
                    text = if (isProxyActive) {
                        "User #$userId • Slot P${runningSlot ?: activeSlot} Connected"
                    } else {
                        "User #$userId • Port ${ProfileManager.localInboundPort} • Standby"
                    },
                    fontSize = 11.5.sp,
                    color = if (isProxyActive) LocalSendTealLight else LocalSendTextSecondary
                )
            }
        }

        val rootCol = when (rootState) {
            RootState.GRANTED -> LocalSendTealLight
            RootState.DENIED -> LocalSendDanger
            RootState.CHECKING -> LocalSendAmber
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(LocalSendCard)
                .border(1.dp, rootCol.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .clickable { onRecheckRoot() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).background(rootCol, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (rootState == RootState.GRANTED) "ROOT" else "NO ROOT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LocalSendTextPrimary
            )
        }
    }
}

@Composable
fun LocalSendSlotBar(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    configuredSlots: Set<Int>,
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

            val bgCol = when {
                run -> LocalSendTealSoft
                sel -> LocalSendCardElevated
                else -> LocalSendCard
            }

            val borderCol = when {
                run -> LocalSendTealLight
                sel -> LocalSendTeal
                else -> LocalSendBorder
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgCol)
                    .border(if (sel || run) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(14.dp))
                    .clickable { onSelectSlot(idx) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "P$idx",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (run) LocalSendTealLight else LocalSendTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        run -> "Active"
                        isBoot -> "Boot"
                        isConf -> "Ready"
                        else -> "Empty"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        run -> LocalSendTealLight
                        isBoot -> LocalSendAmber
                        isConf -> LocalSendTextSecondary
                        else -> LocalSendTextMuted
                    }
                )
            }
        }
    }
}

@Composable
fun LocalSendStatusTab(
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
    transportMode: TransportMode,
    ipMode: IpMode,
    routeHotspot: Boolean,
    routeWholeProfile: Boolean,
    selectedAppsCount: Int,
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
    val context = LocalContext.current
    val isCurrentSlotRunning = isProxyActive && runningSlot == activeSlot

    val infiniteTransition = rememberInfiniteTransition(label = "flashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LocalSendSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isProxyActive) LocalSendTeal else LocalSendBorder
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
                    Text(
                        text = when {
                            isTransitioning -> "Synchronizing Tunnel..."
                            isProxyActive -> "Tunnel Active • Slot P${runningSlot ?: activeSlot}"
                            else -> "Standby • Slot P$activeSlot"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isProxyActive) LocalSendTealLight else LocalSendTextSecondary
                    )

                    if (isProxyActive && sessionStartMs > 0L) {
                        UptimeBadge(sessionStartMs)
                    } else if (activePid != null) {
                        Text(
                            text = "PID $activePid",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LocalSendTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val flashColor = when {
                    isTransitioning -> LocalSendAmber
                    isProxyActive -> LocalSendTealLight
                    else -> LocalSendTextSecondary.copy(alpha = 0.40f)
                }

                val flashGlowBg = when {
                    isTransitioning -> LocalSendAmber.copy(alpha = 0.15f)
                    isProxyActive -> LocalSendTealSoft
                    else -> LocalSendCardElevated
                }

                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(flashGlowBg)
                        .border(
                            width = if (isProxyActive) 2.dp else 1.5.dp,
                            color = if (isProxyActive) flashColor.copy(alpha = pulseAlpha) else LocalSendBorder,
                            shape = RoundedCornerShape(32.dp)
                        )
                        .clickable(enabled = !isTransitioning) { onToggleProxy() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(72.dp)
                            .then(if (isProxyActive) Modifier.graphicsLayer(scaleX = pulseScale, scaleY = pulseScale) else Modifier)
                    ) {
                        val w = size.width
                        val h = size.height
                        val boltPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.54f, h * 0.08f)
                            lineTo(w * 0.22f, h * 0.52f)
                            lineTo(w * 0.46f, h * 0.52f)
                            lineTo(w * 0.38f, h * 0.92f)
                            lineTo(w * 0.78f, h * 0.44f)
                            lineTo(w * 0.54f, h * 0.44f)
                            close()
                        }

                        // Electric outer aura when active
                        if (isProxyActive) {
                            drawPath(
                                path = boltPath,
                                color = LocalSendTeal.copy(alpha = 0.40f),
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        // Core Flash Bolt
                        drawPath(
                            path = boltPath,
                            color = flashColor
                        )

                        // High-tech inner accent highlight
                        if (isProxyActive) {
                            drawPath(
                                path = boltPath,
                                color = Color.White.copy(alpha = 0.45f),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        isProxyActive && publicIpInfo != null -> "${publicIpInfo.flagEmoji} ${publicIpInfo.ip}"
                        isProxyActive && isFetchingIp -> "Verifying Tunnel IP..."
                        host.isNotBlank() -> "${host.trim()}:${port.trim()}"
                        else -> "No Proxy Configured"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSendTextPrimary,
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
                        }
                        isProxyActive -> "Routing traffic through proxy tunnel"
                        else -> "${proxyType.displayTitle} • ${if (routeWholeProfile) "All Apps (User #${ProfileManager.androidUserId})" else "$selectedAppsCount Selected Apps"}"
                    },
                    fontSize = 12.sp,
                    color = LocalSendTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LocalSendCardElevated,
                        border = BorderStroke(1.dp, LocalSendBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onRefreshIp() }
                    ) {
                        Text(
                            text = if (isFetchingIp) "Checking..." else "↻ Verify IP",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalSendTextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LocalSendTealSoft,
                        border = BorderStroke(1.dp, LocalSendTeal),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenIpFighterTab() }
                    ) {
                        Text(
                            text = "IPFighter Check ↗",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalSendTealLight,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LocalSendSurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Download", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = LocalSendTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${splitSpeedValue(currentRxRate)} ${splitSpeedUnit(currentRxRate)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSendTealLight
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Session: ${formatBytes(totalRxBytes)}",
                    fontSize = 11.sp,
                    color = LocalSendTextMuted
                )
            }

            LocalSendSurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Upload", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = LocalSendTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${splitSpeedValue(currentTxRate)} ${splitSpeedUnit(currentTxRate)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSendTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Session: ${formatBytes(totalTxBytes)}",
                    fontSize = 11.sp,
                    color = LocalSendTextMuted
                )
            }
        }

        LocalSendSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalSendChip(proxyType.displayTitle)
                LocalSendChip(if (transportMode == TransportMode.TCP_AND_UDP) "TCP + UDP" else "TCP Only")
                LocalSendChip(ipMode.name.replace("_", " "))
                if (routeHotspot) LocalSendChip("Hotspot Bridge")
                LocalSendChip("FakeIP + Zero-Leak Shield")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPingNode,
                enabled = !isTesting && !isTransitioning,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LocalSendBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = LocalSendCard,
                    contentColor = LocalSendTextPrimary
                ),
                modifier = Modifier
                    .weight(0.35f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (isTesting) "Wait..." else "Ping P$activeSlot",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onToggleProxy,
                enabled = !isTransitioning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentSlotRunning) LocalSendDanger else LocalSendTeal,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(0.65f)
                    .height(50.dp)
            ) {
                Text(
                    text = when {
                        isTransitioning -> "Synchronizing..."
                        !isProxyActive -> "Start Proxy (P$activeSlot)"
                        isCurrentSlotRunning -> "Stop Proxy (P$activeSlot)"
                        else -> "Switch to Slot P$activeSlot"
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (testStatus != null) {
            Text(
                text = testStatus,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (testStatus.startsWith("ONLINE")) LocalSendTealLight else LocalSendDanger,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun LocalSendChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LocalSendCardElevated)
            .border(1.dp, LocalSendBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = LocalSendTextPrimary
        )
    }
}

// -------------------------------------------------------------
// TAB 1: EPHEMERAL NON-CACHED LIVE IPFIGHTER TAB
// Matches Chrome's real WebRTC behavior (shows `-` when blocked by kernel, never fake `N/A`)
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

    // Syncs Timezone to Proxy Exit IP while keeping real WebRTC active (so it matches Chrome!)
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

    LocalSendSurfaceCard(
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isProxyActive) LocalSendTealLight else LocalSendAmber, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isProxyActive) {
                        "Live IPFighter • ${publicIpInfo?.ip ?: "Tunnel Active"}"
                    } else {
                        "Live IPFighter • Direct"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSendTextPrimary
                )
            }

            Button(
                onClick = {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    reloadNonce++
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalSendTeal,
                    contentColor = Color.White
                )
            ) {
                Text("↻ Fresh Check", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (loadProgress in 1..95) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 4.dp),
                color = LocalSendTealLight,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, LocalSendBorder, RoundedCornerShape(14.dp))
        ) {
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
                            loadUrl("https://ipfighter.com/?_t=${System.currentTimeMillis()}", noCacheHeaders)
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
        LocalSendSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "URI Import & Socket Test",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LocalSendTextPrimary
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
                    border = BorderStroke(1.dp, LocalSendBorder)
                ) {
                    Text("Paste URI", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalSendTextPrimary)
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
                        containerColor = LocalSendTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isCheckingAlive) "Testing..." else "Test Socket",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (aliveResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                when (val res = aliveResult) {
                    is TestResult.Success -> {
                        Text(
                            text = "ONLINE • ${res.latencyMs} ms",
                            color = LocalSendTealLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is TestResult.Failure -> {
                        Text(
                            text = "FAILED • ${res.error}",
                            color = LocalSendDanger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    null -> {}
                }
            }
        }

        LocalSendSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Slot P$activeSlot Configuration",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSendTextPrimary
                )
                Text(
                    text = "Reset P$activeSlot",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalSendDanger,
                    modifier = Modifier.clickable { onResetSlot() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Protocol", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = LocalSendTextSecondary)
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
                            selectedContainerColor = LocalSendTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Transport Mode", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = LocalSendTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = transportMode == TransportMode.TCP_AND_UDP,
                    onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                    label = { Text("TCP + UDP (WebRTC Safe)", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LocalSendTeal,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = transportMode == TransportMode.TCP_ONLY,
                    onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                    label = { Text("TCP Only", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LocalSendTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("IP Mode", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = LocalSendTextSecondary)
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
                            selectedContainerColor = LocalSendTeal,
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
                                    color = LocalSendTealLight,
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
                                color = LocalSendTealLight,
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
                                color = LocalSendTealLight,
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
        LocalSendSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Kernel Routing & Automation",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LocalSendTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Start on System Boot", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = LocalSendTextPrimary)
                    Text(
                        text = if (startOnBoot) "Armed for Slot P$bootSlot (/data/adb/service.d)" else "Manual launch only",
                        fontSize = 11.5.sp,
                        color = LocalSendTextSecondary
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LocalSendTeal
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
                                selectedContainerColor = LocalSendTeal,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = LocalSendBorder, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hotspot & Tethering Bridge", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = LocalSendTextPrimary)
                    Text("Share proxy tunnel with Wi-Fi / USB clients", fontSize = 11.5.sp, color = LocalSendTextSecondary)
                }
                Switch(
                    checked = routeHotspot && uid == 0,
                    enabled = uid == 0,
                    onCheckedChange = onRouteHotspotChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LocalSendTeal
                    )
                )
            }
        }

        LocalSendSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "5-Slot Config Backup (/sdcard/Download)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LocalSendTextPrimary
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
                    border = BorderStroke(1.dp, LocalSendBorder)
                ) {
                    Text("Export All Slots", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalSendTextPrimary)
                }
                Button(
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalSendTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text("Import Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LocalSendSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tunnel All Apps (User #$uid)", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = LocalSendTextPrimary)
                    Text(
                        text = if (routeWholeProfile) "Routing entire user profile #$uid" else "${selectedPackages.size} apps selected",
                        fontSize = 11.5.sp,
                        color = LocalSendTextSecondary
                    )
                }
                Switch(
                    checked = routeWholeProfile,
                    onCheckedChange = onRouteWholeProfileChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LocalSendTeal
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
                    Text("${selectedPackages.size} selected", fontSize = 11.5.sp, color = LocalSendTextSecondary)
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalSendTealLight)
                        }
                        TextButton(onClick = onClearAll) {
                            Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalSendDanger)
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
                                Text(app.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalSendTextPrimary, maxLines = 1)
                                Text("${app.packageName} • UID ${app.uid}", fontSize = 11.sp, color = LocalSendTextSecondary, maxLines = 1)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = LocalSendTeal,
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
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    LocalSendSurfaceCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kernel Logs (Slot P${runningSlot ?: activeSlot})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LocalSendTextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", logs))
                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = LocalSendTextPrimary, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onClear) { Text("Clear", color = LocalSendDanger, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onRefresh) { Text("Refresh", color = LocalSendTealLight, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(LocalSendBg)
                .border(1.dp, LocalSendBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = LocalSendTextPrimary,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun LocalSendBottomNav(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val tabs = listOf("Status", "IPFighter", "Nodes", "Routing", "Logs")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalSendCard)
            .border(1.dp, LocalSendBorder, RoundedCornerShape(16.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val sel = selectedTab == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) LocalSendTeal else Color.Transparent)
                    .clickable { onSelectTab(idx) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) Color.White else LocalSendTextSecondary,
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
    Text(
        text = String.format("%02d:%02d", (sec % 3600) / 60, sec % 60),
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = LocalSendTealLight
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
