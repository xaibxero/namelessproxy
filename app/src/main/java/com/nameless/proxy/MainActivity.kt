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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class AppItem(
    val name: String,
    val packageName: String,
    val uid: Int
)

// Cyber-Obsidian Theme Palette
private val BgObsidian = Color(0xFF04070F)
private val CardSurface = Color(0xCC0B111E)
private val CardSurfaceElevated = Color(0xE6111A2E)
private val CyberEmerald = Color(0xFF00F5A0)
private val CyberCyan = Color(0xFF00D9F5)
private val CyberIndigo = Color(0xFF6366F1)
private val CyberAmber = Color(0xFFF59E0B)
private val CyberCrimson = Color(0xFFFF3366)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var rootState by mutableStateOf(RootState.CHECKING)
    private var rootLabel by mutableStateOf("CHECKING")
    private var isProxyRunningState by mutableStateOf(false)
    private var isTransitioningState by mutableStateOf(false)
    private var runningSlotState by mutableStateOf<Int?>(null)
    private var activePidState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()

        val globalPrefs = getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE)
        val startOnBoot = globalPrefs.getBoolean("start_on_boot", false)
        if (!startOnBoot) {
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CyberCyan,
                    secondary = CyberEmerald,
                    background = BgObsidian,
                    surface = CardSurface,
                    error = CyberCrimson
                )
            ) {
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
                    onStartProxy = { settings: ProxySettings, selectedUids: List<Int>?, onResult: (Boolean, String?) -> Unit ->
                        if (isTransitioningState) return@MainScreen
                        isTransitioningState = true
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = ProxyController.startProxy(this@MainActivity, settings, selectedUids)
                            val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)
                            val pid = if (rSlot != null) ProxyController.getActivePid(ProfileManager.androidUserId, rSlot) else null
                            withContext(Dispatchers.Main) {
                                isProxyRunningState = rSlot != null
                                runningSlotState = rSlot
                                activePidState = pid
                                isTransitioningState = false
                                onResult(result.success, result.errorMessage)
                            }
                        }
                    },
                    onStopProxy = { onResult: (Boolean) -> Unit ->
                        if (isTransitioningState) return@MainScreen
                        isTransitioningState = true
                        lifecycleScope.launch(Dispatchers.IO) {
                            val success = ProxyController.stopProxy(this@MainActivity)
                            withContext(Dispatchers.Main) {
                                isProxyRunningState = false
                                runningSlotState = null
                                activePidState = null
                                isTransitioningState = false
                                onResult(success)
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
            val isRunning = rSlot != null
            val pid = if (rSlot != null) ProxyController.getActivePid(ProfileManager.androidUserId, rSlot) else null
            withContext(Dispatchers.Main) {
                isProxyRunningState = isRunning
                runningSlotState = rSlot
                activePidState = pid
            }
        }
    }

    private fun refreshRootStatus() {
        lifecycleScope.launch {
            rootState = RootState.CHECKING
            rootLabel = "CHECKING"
            val (state, label) = RootChecker.verifyRoot()
            rootState = state
            rootLabel = label
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
    val coroutineScope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    var selectedNavTab by remember { mutableIntStateOf(0) }

    // Hoisted session timer & telemetry history so switching tabs never resets live data
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var rxHistory by remember { mutableStateOf(List(40) { 0L }) }
    var txHistory by remember { mutableStateOf(List(40) { 0L }) }
    var currentRxRate by remember { mutableLongStateOf(0L) }
    var currentTxRate by remember { mutableLongStateOf(0L) }
    var peakRxRate by remember { mutableLongStateOf(0L) }
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
                delay(1000)
                val currRx = TrafficStats.getTotalRxBytes().coerceAtLeast(prevRx)
                val currTx = TrafficStats.getTotalTxBytes().coerceAtLeast(prevTx)
                val currTime = System.currentTimeMillis()
                val dt = (currTime - prevTime).coerceAtLeast(1L) / 1000.0

                val rxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)
                val txRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)

                currentRxRate = rxRate
                currentTxRate = txRate
                if (rxRate > peakRxRate) peakRxRate = rxRate
                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)

                rxHistory = (rxHistory.drop(1) + rxRate)
                txHistory = (txHistory.drop(1) + txRate)

                prevRx = currRx
                prevTx = currTx
                prevTime = currTime
            }
        } else {
            sessionStartMs = 0L
            currentRxRate = 0L
            currentTxRate = 0L
            peakRxRate = 0L
            totalRxBytes = 0L
            totalTxBytes = 0L
            rxHistory = List(40) { 0L }
            txHistory = List(40) { 0L }
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

    // Track configured state per slot for visual badges on P0..P4
    var configuredSlots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    fun refreshConfiguredSlots() {
        val set = mutableSetOf<Int>()
        for (i in 0 until ProfileManager.MAX_SLOTS) {
            val h = getSlotPrefs(i).getString("host", "") ?: ""
            if (h.isNotBlank()) set.add(i)
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
        val prefs = getSlotPrefs(slot)
        val prefHost = prefs.getString("host", "") ?: ""

        if (prefHost.isNotEmpty()) {
            applySettingsToState(
                pHost = prefHost,
                pPort = prefs.getString("port", "1080") ?: "1080",
                pUser = prefs.getString("username", "") ?: "",
                pPass = prefs.getString("password", "") ?: "",
                pSni = prefs.getString("sni", "") ?: "",
                pSsMethod = prefs.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm",
                pRpk = prefs.getString("reality_pk", "") ?: "",
                pRsid = prefs.getString("reality_sid", "") ?: "",
                pType = try { ProxyType.valueOf(prefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name) } catch (_: Exception) { ProxyType.SOCKS5 },
                pTransport = try { TransportMode.valueOf(prefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name) } catch (_: Exception) { TransportMode.TCP_AND_UDP },
                pIpMode = try { IpMode.valueOf(prefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name) } catch (_: Exception) { IpMode.IPV4_ONLY },
                pRouteWhole = prefs.getBoolean("route_whole_profile", true),
                pPkgs = (prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()).toSet()
            )
            refreshConfiguredSlots()
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                val backup = PersistentStorage.loadBackup(slot, ProfileManager.androidUserId)
                if (backup != null) {
                    val bHost = backup.optString("host", "")
                    val bPort = backup.optString("port", "1080")
                    val bUser = backup.optString("username", "")
                    val bPass = backup.optString("password", "")
                    val bSni = backup.optString("sni", "")
                    val bSs = backup.optString("ss_method", "2022-blake3-aes-128-gcm")
                    val bRpk = backup.optString("reality_pk", "")
                    val bRsid = backup.optString("reality_sid", "")
                    val bType = try { ProxyType.valueOf(backup.optString("proxy_type", ProxyType.SOCKS5.name)) } catch (_: Exception) { ProxyType.SOCKS5 }
                    val bTrans = try { TransportMode.valueOf(backup.optString("transport_mode", TransportMode.TCP_AND_UDP.name)) } catch (_: Exception) { TransportMode.TCP_AND_UDP }
                    val bIp = try { IpMode.valueOf(backup.optString("ip_mode", IpMode.IPV4_ONLY.name)) } catch (_: Exception) { IpMode.IPV4_ONLY }
                    val bRoute = backup.optBoolean("route_whole_profile", true)

                    val bPkgs = mutableSetOf<String>()
                    val pkgsArray = backup.optJSONArray("selected_packages")
                    if (pkgsArray != null) {
                        for (i in 0 until pkgsArray.length()) {
                            bPkgs.add(pkgsArray.getString(i))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        applySettingsToState(bHost, bPort, bUser, bPass, bSni, bSs, bRpk, bRsid, bType, bTrans, bIp, bRoute, bPkgs)
                        prefs.edit()
                            .putString("host", bHost).putString("port", bPort).putString("username", bUser)
                            .putString("password", bPass).putString("sni", bSni).putString("ss_method", bSs)
                            .putString("reality_pk", bRpk).putString("reality_sid", bRsid)
                            .putString("proxy_type", bType.name).putString("transport_mode", bTrans.name)
                            .putString("ip_mode", bIp.name).putBoolean("route_whole_profile", bRoute)
                            .putStringSet("selected_packages", bPkgs.toSet())
                            .apply()
                        refreshConfiguredSlots()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        applySettingsToState("", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "", ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, emptySet())
                        refreshConfiguredSlots()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSlotData(activeSlot)
    }

    fun getCurrentSettings(): ProxySettings {
        return ProxySettings(
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
    }

    var ioSyncJob by remember { mutableStateOf<Job?>(null) }

    fun saveConfigForSlot(slot: Int, immediateRootSync: Boolean = false) {
        val prefs = getSlotPrefs(slot)
        prefs.edit()
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

        val settingsSnapshot = getCurrentSettings()
        val uidsSnapshot = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
        val bootEnabledSnapshot = startOnBoot
        val bootSlotSnapshot = bootSlot
        val routeWholeSnapshot = routeWholeProfile
        val pkgsSnapshot = selectedPackages.toSet()

        ioSyncJob?.cancel()
        ioSyncJob = coroutineScope.launch(Dispatchers.IO) {
            if (!immediateRootSync) {
                delay(600)
            }
            if (bootEnabledSnapshot && slot == bootSlotSnapshot) {
                BootManager.syncBootState(context, true, settingsSnapshot, uidsSnapshot, slot)
            }
            if (settingsSnapshot.host.isNotEmpty()) {
                PersistentStorage.saveBackup(
                    context = context,
                    slot = slot,
                    settings = settingsSnapshot,
                    startOnBoot = bootEnabledSnapshot && (slot == bootSlotSnapshot),
                    routeWholeProfile = routeWholeSnapshot,
                    selectedPackages = pkgsSnapshot,
                    userId = ProfileManager.androidUserId
                )
            }
        }
    }

    fun switchSlot(newSlot: Int) {
        if (newSlot == activeSlot) return
        saveConfigForSlot(activeSlot, immediateRootSync = true)
        ProfileManager.activeSlot = newSlot
        activeSlot = newSlot
        loadSlotData(newSlot)
        onSyncStatus()
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testLatencyMs by remember { mutableStateOf<Long?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("") }

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }
    var ipFetchFailed by remember { mutableStateOf(false) }

    fun triggerPublicIpCheck() {
        isFetchingIp = true
        ipFetchFailed = false
        coroutineScope.launch(Dispatchers.IO) {
            delay(550)
            val currentRunning = ProxyController.getRunningSlot(ProfileManager.androidUserId) ?: activeSlot
            val socksPort = 10800 + (ProfileManager.androidUserId * 100) + (currentRunning * 10) + 1
            var result = IpFetcher.getPublicIpInfo(socksPort)
            if (result == null) {
                delay(1000)
                result = IpFetcher.getPublicIpInfo(socksPort)
            }
            withContext(Dispatchers.Main) {
                if (result != null) {
                    publicIpInfo = result
                    ipFetchFailed = false
                } else {
                    ipFetchFailed = true
                }
                isFetchingIp = false
            }
        }
    }

    LaunchedEffect(isProxyActive, runningSlot) {
        if (isProxyActive) {
            triggerPublicIpCheck()
        } else {
            publicIpInfo = null
            ipFetchFailed = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgObsidian)
    ) {
        // 1. LIVE REACTIVE CYBER-GRID & QUANTUM AURORA BACKGROUND
        CyberQuantumBackground(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning || isTesting
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 2. TOP COMMAND HEADER
            CyberHeader(
                activeSlot = activeSlot,
                runningSlot = runningSlot,
                isProxyActive = isProxyActive,
                rootState = rootState,
                rootLabel = rootLabel,
                onRecheckRoot = onRecheckRoot
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. INTERACTIVE NODE SLOT MATRIX (P0 - P4)
            CyberSlotMatrix(
                activeSlot = activeSlot,
                runningSlot = runningSlot,
                isProxyActive = isProxyActive,
                startOnBoot = startOnBoot,
                bootSlot = bootSlot,
                configuredSlots = configuredSlots,
                onSelectSlot = { switchSlot(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. SMOOTH SPRING NAVIGATION DECK
            CyberSegmentedTabs(
                selectedTab = selectedNavTab,
                onTabSelected = { selectedNavTab = it },
                selectedAppsCount = if (routeWholeProfile) null else selectedPackages.size
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. ANIMATED VIEWPORT WITH DIRECTIONAL TRANSITIONS
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedNavTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { fullWidth -> direction * (fullWidth / 4) }
                        ) + fadeIn(animationSpec = tween(220)) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(220)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(180),
                                targetOffsetX = { fullWidth -> -direction * (fullWidth / 5) }
                            ) + fadeOut(animationSpec = tween(160))
                        )
                    },
                    label = "mainTabTransition"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> {
                            ConsoleHudView(
                                isProxyActive = isProxyActive,
                                isTransitioning = isTransitioning,
                                runningSlot = runningSlot,
                                activeSlot = activeSlot,
                                sessionStartMs = sessionStartMs,
                                publicIpInfo = publicIpInfo,
                                isFetchingIp = isFetchingIp,
                                ipFetchFailed = ipFetchFailed,
                                activePid = activePid,
                                proxyType = proxyType,
                                transportMode = transportMode,
                                ipMode = ipMode,
                                routeHotspot = routeHotspot,
                                routeWholeProfile = routeWholeProfile,
                                selectedAppsCount = selectedPackages.size,
                                host = host,
                                port = port,
                                currentRxRate = currentRxRate,
                                currentTxRate = currentTxRate,
                                peakRxRate = peakRxRate,
                                totalRxBytes = totalRxBytes,
                                totalTxBytes = totalTxBytes,
                                rxHistory = rxHistory,
                                txHistory = txHistory,
                                onRefreshIp = { triggerPublicIpCheck() },
                                onToggleProxy = {
                                    if (isProxyActive && runningSlot == activeSlot) {
                                        onStopProxy { }
                                    } else {
                                        if (host.trim().isEmpty()) {
                                            testStatus = "Configure Server Host/IP in Config Deck first"
                                            testLatencyMs = null
                                            selectedNavTab = 1
                                            return@ConsoleHudView
                                        }
                                        saveConfigForSlot(activeSlot, immediateRootSync = true)
                                        val settings = getCurrentSettings()
                                        val targets = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
                                        onStartProxy(settings, targets) { success, error ->
                                            if (!success) {
                                                testStatus = "Launch Failed: ${error?.lines()?.lastOrNull() ?: "Check Core Logs"}"
                                                testLatencyMs = null
                                            } else {
                                                testStatus = null
                                            }
                                        }
                                    }
                                },
                                isTesting = isTesting,
                                testStatus = testStatus,
                                testLatencyMs = testLatencyMs,
                                onTestUpstream = {
                                    if (host.trim().isEmpty()) {
                                        testStatus = "Enter Server Host/IP first"
                                        testLatencyMs = null
                                        selectedNavTab = 1
                                        return@ConsoleHudView
                                    }
                                    saveConfigForSlot(activeSlot)
                                    isTesting = true
                                    testStatus = "Probing ${host.trim()}:${port.trim()}..."
                                    testLatencyMs = null
                                    val settings = getCurrentSettings()
                                    coroutineScope.launch {
                                        when (val res = ProxyTester.testProxy(settings)) {
                                            is TestResult.Success -> {
                                                testLatencyMs = res.latencyMs
                                                testStatus = "Node Reachable • ${res.latencyMs} ms"
                                            }
                                            is TestResult.Failure -> {
                                                testLatencyMs = null
                                                testStatus = "Unreachable: ${res.error}"
                                            }
                                        }
                                        isTesting = false
                                    }
                                },
                                onViewLogs = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val targetSlot = runningSlot ?: activeSlot
                                        val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                                        withContext(Dispatchers.Main) {
                                            currentLogs = logs.ifEmpty { "No logs recorded." }
                                            showLogsDialog = true
                                        }
                                    }
                                }
                            )
                        }

                        1 -> {
                            ServerConfigView(
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
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                },
                                onResetSlot = {
                                    val targetSlot = activeSlot
                                    getSlotPrefs(targetSlot).edit().clear().apply()
                                    applySettingsToState("", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "", ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, emptySet())
                                    refreshConfiguredSlots()
                                    coroutineScope.launch(Dispatchers.IO) {
                                        PersistentStorage.clearBackup(targetSlot, ProfileManager.androidUserId)
                                    }
                                    Toast.makeText(context, "Node P$targetSlot cleared", Toast.LENGTH_SHORT).show()
                                },
                                getCurrentSettings = { getCurrentSettings() }
                            )
                        }

                        2 -> {
                            AppsMasterView(
                                startOnBoot = startOnBoot,
                                bootSlot = bootSlot,
                                activeSlot = activeSlot,
                                routeHotspot = routeHotspot,
                                routeWholeProfile = routeWholeProfile,
                                selectedPackages = selectedPackages,
                                installedApps = installedApps,
                                onStartOnBootChange = { enabled ->
                                    startOnBoot = enabled
                                    if (enabled) {
                                        bootSlot = activeSlot
                                        globalPrefs.edit().putBoolean("start_on_boot", true).putInt("boot_slot", activeSlot).apply()
                                        saveConfigForSlot(activeSlot, immediateRootSync = true)
                                        Toast.makeText(context, "Node P$activeSlot armed for System Boot", Toast.LENGTH_SHORT).show()
                                    } else {
                                        globalPrefs.edit().putBoolean("start_on_boot", false).apply()
                                        coroutineScope.launch(Dispatchers.IO) {
                                            BootManager.removeBootScript()
                                        }
                                    }
                                },
                                onBootSlotChange = { s ->
                                    bootSlot = s
                                    globalPrefs.edit().putInt("boot_slot", s).apply()
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                    Toast.makeText(context, "Node P$s designated as Boot Target", Toast.LENGTH_SHORT).show()
                                },
                                onRouteHotspotChange = {
                                    routeHotspot = it
                                    globalPrefs.edit().putBoolean("route_hotspot", it).apply()
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                },
                                onRouteWholeProfileChange = {
                                    routeWholeProfile = it
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                },
                                onToggleAppSelection = { pkg ->
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
                        }
                    }
                }
            }
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", currentLogs))
                            Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copy", color = CyberCyan, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            val targetSlot = runningSlot ?: activeSlot
                            currentLogs = "Logs cleared."
                            coroutineScope.launch(Dispatchers.IO) {
                                ProxyController.clearLogs(ProfileManager.androidUserId, targetSlot)
                            }
                        }) {
                            Text("Clear", color = CyberCrimson, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val targetSlot = runningSlot ?: activeSlot
                                val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                                withContext(Dispatchers.Main) {
                                    currentLogs = logs.ifEmpty { "No logs recorded." }
                                }
                            }
                        }) {
                            Text("Refresh", color = CyberEmerald, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showLogsDialog = false }) {
                            Text("Close", color = TextSecondary)
                        }
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(CyberEmerald, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("KERNEL CORE TELEMETRY & LOGS", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = TextPrimary)
                }
            },
            containerColor = Color(0xFF0A0F1D),
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color(0xFF050811), RoundedCornerShape(14.dp))
                        .border(1.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = currentLogs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 1. LIVE REACTIVE CYBER-GRID & QUANTUM AURORA BACKGROUND
// -------------------------------------------------------------

@Composable
fun CyberQuantumBackground(
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quantumBg")

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitAngle"
    )

    val streamOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 6500 else 16000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "streamOffset"
    )

    val primaryOrbColor by animateColorAsState(
        targetValue = when {
            isTransitioning -> CyberAmber.copy(alpha = 0.22f)
            isProxyActive -> CyberEmerald.copy(alpha = 0.24f)
            else -> CyberCyan.copy(alpha = 0.12f)
        },
        animationSpec = tween(700),
        label = "primaryOrbColor"
    )

    val secondaryOrbColor by animateColorAsState(
        targetValue = when {
            isTransitioning -> CyberCrimson.copy(alpha = 0.16f)
            isProxyActive -> CyberCyan.copy(alpha = 0.22f)
            else -> CyberIndigo.copy(alpha = 0.10f)
        },
        animationSpec = tween(700),
        label = "secondaryOrbColor"
    )

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height
        val rad = Math.toRadians(orbitAngle.toDouble())

        // Subtle Tactical Cyber Grid
        val gridSpacing = 44.dp.toPx()
        val gridAlpha = if (isProxyActive) 0.045f else 0.022f
        val gridColor = if (isProxyActive) CyberEmerald else CyberCyan

        var gx = 0f
        while (gx < w) {
            drawLine(
                color = gridColor.copy(alpha = gridAlpha),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f
            )
            gx += gridSpacing
        }

        var gy = (streamOffset % gridSpacing)
        while (gy < h) {
            drawLine(
                color = gridColor.copy(alpha = gridAlpha),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
            gy += gridSpacing
        }

        // Dynamic Aurora Plasma Orbs
        val orb1X = (w * 0.72f) - (sin(rad) * w * 0.18f).toFloat()
        val orb1Y = (h * 0.28f) + (cos(rad) * h * 0.10f).toFloat()

        val orb2X = (w * 0.25f) + (cos(rad * 1.2) * w * 0.16f).toFloat()
        val orb2Y = (h * 0.68f) + (sin(rad * 1.2) * h * 0.12f).toFloat()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryOrbColor, Color.Transparent),
                center = Offset(orb1X, orb1Y),
                radius = w * 0.85f
            ),
            center = Offset(orb1X, orb1Y),
            radius = w * 0.85f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryOrbColor, Color.Transparent),
                center = Offset(orb2X, orb2Y),
                radius = w * 0.80f
            ),
            center = Offset(orb2X, orb2Y),
            radius = w * 0.80f
        )

        // Rising Quantum Data Particles
        val particleCount = 26
        for (i in 0 until particleCount) {
            val px = (w * ((i * 41 + 7) % 100) / 100f)
            val baseY = (h * ((i * 59 + 13) % 100) / 100f)
            val speedFactor = 0.45f + (i % 4) * 0.22f
            val py = (baseY - (streamOffset * speedFactor)) % h
            val actualY = if (py < 0) py + h else py
            val dotRadius = (1.3f + (i % 3) * 1.0f).dp.toPx()

            val pColor = when {
                isTransitioning -> CyberAmber
                isProxyActive -> if (i % 2 == 0) CyberEmerald else CyberCyan
                else -> CyberCyan
            }

            drawCircle(
                color = pColor,
                radius = dotRadius,
                center = Offset(px, actualY),
                alpha = if (isProxyActive) 0.42f else 0.14f
            )
        }
    }
}

// -------------------------------------------------------------
// 2. TOP COMMAND HEADER
// -------------------------------------------------------------

@Composable
fun CyberHeader(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    rootState: RootState,
    rootLabel: String,
    onRecheckRoot: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerPulse")
    val statusPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "statusPulse"
    )

    val accentColor by animateColorAsState(
        targetValue = if (isProxyActive) CyberEmerald else CyberCyan,
        animationSpec = tween(400),
        label = "headerAccent"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NAMELESS",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 2.8.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .graphicsLayer { alpha = if (isProxyActive) statusPulse else 1f }
                                .background(accentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isProxyActive) "P${runningSlot ?: activeSlot} LIVE" else "IDLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "KERNEL TPROXY • USER ${ProfileManager.androidUserId} • PORT ${ProfileManager.localInboundPort}",
                fontSize = 10.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
        }

        val rootColor = when (rootState) {
            RootState.GRANTED -> CyberEmerald
            RootState.DENIED -> CyberCrimson
            RootState.CHECKING -> CyberAmber
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = rootColor.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, rootColor.copy(alpha = 0.55f)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onRecheckRoot() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(rootColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = rootLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 3. INTERACTIVE NODE SLOT MATRIX (P0 - P4)
// -------------------------------------------------------------

@Composable
fun CyberSlotMatrix(
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
        (0 until ProfileManager.MAX_SLOTS).forEach { slotIdx ->
            val isSelected = slotIdx == activeSlot
            val isRunning = isProxyActive && (runningSlot == slotIdx)
            val isBootTarget = startOnBoot && (slotIdx == bootSlot)
            val isConfigured = configuredSlots.contains(slotIdx)

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.03f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "slotScale"
            )

            val borderColor by animateColorAsState(
                targetValue = when {
                    isRunning -> CyberEmerald
                    isSelected -> CyberCyan
                    isConfigured -> Color(0x33FFFFFF)
                    else -> Color(0x16FFFFFF)
                },
                animationSpec = tween(250),
                label = "slotBorder"
            )

            val containerColor by animateColorAsState(
                targetValue = when {
                    isRunning -> CyberEmerald.copy(alpha = 0.18f)
                    isSelected -> CyberCyan.copy(alpha = 0.16f)
                    else -> CardSurface
                },
                animationSpec = tween(250),
                label = "slotContainer"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor)
                    .border(if (isSelected || isRunning) 1.4.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                    .clickable { onSelectSlot(slotIdx) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "P$slotIdx",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = when {
                                isRunning -> CyberEmerald
                                isSelected -> CyberCyan
                                isConfigured -> TextPrimary
                                else -> TextMuted
                            },
                            fontFamily = FontFamily.Monospace
                        )
                        if (isConfigured && !isRunning) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .background(if (isSelected) CyberCyan else TextSecondary, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isRunning -> "ACTIVE"
                            isBootTarget -> "BOOT"
                            isSelected -> "SELECTED"
                            isConfigured -> "READY"
                            else -> "EMPTY"
                        },
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            isRunning -> CyberEmerald
                            isBootTarget -> CyberAmber
                            isSelected -> CyberCyan
                            isConfigured -> TextSecondary
                            else -> TextMuted
                        },
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. SEGMENTED NAVIGATION TABS
// -------------------------------------------------------------

@Composable
fun CyberSegmentedTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    selectedAppsCount: Int?
) {
    val tabs = listOf(
        "Command HUD",
        "Node Config",
        if (selectedAppsCount == null) "Routing & Boot" else "Routing ($selectedAppsCount)"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index

                val bg by animateColorAsState(
                    targetValue = if (isSelected) CyberCyan.copy(alpha = 0.18f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "tabBg"
                )
                val borderCol by animateColorAsState(
                    targetValue = if (isSelected) CyberCyan.copy(alpha = 0.65f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "tabBorder"
                )
                val textCol by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextSecondary,
                    animationSpec = tween(220),
                    label = "tabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        color = textCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VIEW 1: COMMAND HUD VIEW
// -------------------------------------------------------------

@Composable
fun ConsoleHudView(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    sessionStartMs: Long,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    ipFetchFailed: Boolean,
    activePid: String?,
    proxyType: ProxyType,
    transportMode: TransportMode,
    ipMode: IpMode,
    routeHotspot: Boolean,
    routeWholeProfile: Boolean,
    selectedAppsCount: Int,
    host: String,
    port: String,
    currentRxRate: Long,
    currentTxRate: Long,
    peakRxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxHistory: List<Long>,
    txHistory: List<Long>,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    testLatencyMs: Long?,
    onTestUpstream: () -> Unit,
    onViewLogs: () -> Unit
) {
    val isCurrentSlotRunning = isProxyActive && (runningSlot == activeSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. INTERACTIVE QUANTUM REACTOR CORE CARD
        QuantumReactorCard(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning,
            runningSlot = runningSlot,
            activeSlot = activeSlot,
            sessionStartMs = sessionStartMs,
            activePid = activePid,
            host = host,
            port = port,
            proxyType = proxyType,
            publicIpInfo = publicIpInfo,
            isFetchingIp = isFetchingIp,
            ipFetchFailed = ipFetchFailed,
            onRefreshIp = onRefreshIp,
            onToggleProxy = onToggleProxy
        )

        // 2. REAL-TIME HISTORICAL BEZIER TELEMETRY OSCILLOSCOPE
        LiveTelemetryOscilloscope(
            isProxyActive = isProxyActive,
            currentRxRate = currentRxRate,
            currentTxRate = currentTxRate,
            peakRxRate = peakRxRate,
            totalRxBytes = totalRxBytes,
            totalTxBytes = totalTxBytes,
            rxHistory = rxHistory,
            txHistory = txHistory
        )

        // 3. LIVE PIPELINE TAGS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberBadge(label = proxyType.label, color = CyberCyan)
            CyberBadge(
                label = if (transportMode == TransportMode.TCP_AND_UDP) "TCP + UDP (TPROXY)" else "TCP ONLY (QUIC BLOCK)",
                color = if (transportMode == TransportMode.TCP_AND_UDP) CyberEmerald else CyberAmber
            )
            CyberBadge(
                label = when (ipMode) {
                    IpMode.IPV4_ONLY -> "IPv4 ONLY"
                    IpMode.DUAL_STACK -> "DUAL-STACK"
                    IpMode.IPV6_ONLY -> "IPv6 ONLY"
                },
                color = CyberCyan
            )
            CyberBadge(
                label = if (routeWholeProfile) "GLOBAL PROFILE" else "$selectedAppsCount APPS TUNNELED",
                color = CyberIndigo
            )
            if (routeHotspot) {
                CyberBadge(label = "HOTSPOT BRIDGE", color = CyberEmerald)
            }
        }

        // 4. DIAGNOSTICS & LOGS BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTestUpstream,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                enabled = !isTesting && !isTransitioning
            ) {
                Text(
                    text = if (isTesting) "Probing Node..." else "⚡ Ping Node P$activeSlot",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            OutlinedButton(
                onClick = onViewLogs,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Text("Kernel Telemetry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = testStatus != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val isOk = testLatencyMs != null
            val statusColor = when {
                isTesting -> CyberAmber
                isOk -> CyberEmerald
                else -> CyberCrimson
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = testStatus ?: "",
                    color = statusColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }

        // 5. MASTER IGNITION BUTTON WITH SPRING PRESS FEEDBACK
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val btnScale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "masterBtnScale"
        )

        val buttonText = when {
            isTransitioning -> "SYNCHRONIZING KERNEL NETFILTER..."
            !isProxyActive -> "ENGAGE KERNEL TUNNEL • NODE P$activeSlot"
            isCurrentSlotRunning -> "TERMINATE TUNNEL • DISCONNECT P$activeSlot"
            else -> "HOT-SWAP TO NODE P$activeSlot & ENGAGE"
        }

        val buttonBrush = when {
            isTransitioning -> Brush.horizontalGradient(listOf(CyberAmber.copy(alpha = 0.8f), CyberCyan.copy(alpha = 0.8f)))
            isCurrentSlotRunning -> Brush.horizontalGradient(listOf(CyberCrimson, Color(0xFFBE123C)))
            else -> Brush.horizontalGradient(listOf(CyberEmerald, CyberCyan))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .graphicsLayer {
                    scaleX = btnScale
                    scaleY = btnScale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(buttonBrush)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isTransitioning
                ) { onToggleProxy() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = if (isCurrentSlotRunning) Color.White else BgObsidian,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

// -------------------------------------------------------------
// INTERACTIVE QUANTUM REACTOR CORE CARD
// -------------------------------------------------------------

@Composable
fun QuantumReactorCard(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    sessionStartMs: Long,
    activePid: String?,
    host: String,
    port: String,
    proxyType: ProxyType,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    ipFetchFailed: Boolean,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "reactorCore")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    isTransitioning -> 1800
                    isProxyActive -> 4200
                    else -> 14000
                },
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "outerRot"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    isTransitioning -> 1200
                    isProxyActive -> 2800
                    else -> 10000
                },
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "innerRot"
    )

    val coreBreath by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "coreBreath"
    )

    val stateColor by animateColorAsState(
        targetValue = when {
            isTransitioning -> CyberAmber
            isProxyActive -> CyberEmerald
            else -> CyberCyan
        },
        animationSpec = tween(450),
        label = "reactorStateColor"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        border = BorderStroke(1.2.dp, stateColor.copy(alpha = if (isProxyActive) 0.65f else 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(stateColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isTransitioning -> "SYNCHRONIZING TUNNEL..."
                            isProxyActive -> "TUNNEL ENGAGED • NODE P$runningSlot"
                            else -> "STANDBY • NODE P$activeSlot"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = stateColor,
                        letterSpacing = 0.8.sp
                    )
                    if (isProxyActive && activePid != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PID $activePid",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isProxyActive && sessionStartMs > 0L) {
                    LiveUptimeCounter(sessionStartMs = sessionStartMs)
                } else {
                    Text(
                        text = if (host.isNotBlank()) "${proxyType.name} • :$port" else "UNCONFIGURED",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Central Reactor Core (Tap to Toggle Tunnel)
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isTransitioning) { onToggleProxy() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f

                    // Ambient Halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                stateColor.copy(alpha = if (isProxyActive) 0.30f else 0.10f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * coreBreath
                        ),
                        radius = radius,
                        center = center
                    )

                    // Outer Segmented Telemetry Ring
                    rotate(degrees = outerRotation, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(stateColor, CyberCyan, Color.Transparent, stateColor)
                            ),
                            startAngle = 0f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Inner Counter-Rotating Dashed Ring
                    rotate(degrees = innerRotation, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(CyberCyan, CyberIndigo, Color.Transparent, CyberCyan)
                            ),
                            startAngle = 30f,
                            sweepAngle = 220f,
                            useCenter = false,
                            topLeft = Offset(radius * 0.22f, radius * 0.22f),
                            size = androidx.compose.ui.geometry.Size(radius * 1.56f, radius * 1.56f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                            )
                        )
                    }

                    // Inner Core Sphere
                    drawCircle(
                        color = stateColor.copy(alpha = if (isProxyActive) 0.24f else 0.10f),
                        radius = (radius * 0.48f) * if (isProxyActive) coreBreath else 1f,
                        center = center
                    )
                    drawCircle(
                        color = stateColor,
                        radius = radius * 0.30f,
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            isTransitioning -> "SYNC"
                            isProxyActive -> "LIVE"
                            else -> "START"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "P${if (isProxyActive) (runningSlot ?: activeSlot) else activeSlot}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Exit Node GeoIP Telemetry Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardSurfaceElevated,
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (isProxyActive && publicIpInfo != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ExitNodeIP", publicIpInfo.ip))
                            Toast.makeText(context, "Exit IP Copied: ${publicIpInfo.ip}", Toast.LENGTH_SHORT).show()
                        } else if (isProxyActive) {
                            onRefreshIp()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "🛡️",
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isProxyActive) {
                                    when {
                                        isFetchingIp -> "Verifying exit tunnel..."
                                        publicIpInfo != null -> publicIpInfo.ip
                                        ipFetchFailed -> "Exit check timed out"
                                        else -> "Resolving exit IP..."
                                    }
                                } else {
                                    if (host.isNotBlank()) "${host.trim()}:${port.trim()}" else "No Upstream Configured"
                                },
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isProxyActive) {
                                    when {
                                        isFetchingIp -> "Routing through local SOCKS5 telemetry port..."
                                        publicIpInfo != null -> buildString {
                                            append(publicIpInfo.country)
                                            if (publicIpInfo.isp.isNotBlank()) append(" • ${publicIpInfo.isp}")
                                        }
                                        ipFetchFailed -> "Tap Refresh to re-verify exit node"
                                        else -> "Synchronizing..."
                                    }
                                } else "Direct Kernel Interface • Tap Core to Engage",
                                fontSize = 10.5.sp,
                                color = if (ipFetchFailed && isProxyActive) CyberCrimson else stateColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isProxyActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberCyan.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, CyberCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRefreshIp() }
                        ) {
                            Text(
                                text = if (isFetchingIp) "..." else "Verify",
                                fontSize = 10.5.sp,
                                color = CyberCyan,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// REAL-TIME HISTORICAL BEZIER TELEMETRY OSCILLOSCOPE
// -------------------------------------------------------------

@Composable
fun LiveTelemetryOscilloscope(
    isProxyActive: Boolean,
    currentRxRate: Long,
    currentTxRate: Long,
    peakRxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxHistory: List<Long>,
    txHistory: List<Long>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "carrierWave")
    val carrierPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 1800 else 5000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "carrierPhase"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // DOWNLOAD METRIC CARD
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberEmerald.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.30f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("DOWNLINK (RX)", fontSize = 9.sp, fontWeight = FontWeight.Black, color = CyberEmerald, letterSpacing = 0.8.sp)
                            Text("Peak ${formatCompactRate(peakRxRate)}", fontSize = 8.5.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = splitSpeedValue(currentRxRate),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = splitSpeedUnit(currentRxRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Session: ${formatBytes(totalRxBytes)}",
                            fontSize = 9.5.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // UPLOAD METRIC CARD
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberCyan.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.30f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("UPLINK (TX)", fontSize = 9.sp, fontWeight = FontWeight.Black, color = CyberCyan, letterSpacing = 0.8.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = splitSpeedValue(currentTxRate),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = splitSpeedUnit(currentTxRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Session: ${formatBytes(totalTxBytes)}",
                            fontSize = 9.5.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 40-Point Real-Time Bezier Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF060A14))
                    .border(1.dp, Color(0x16FFFFFF), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val w = size.width
                    val h = size.height
                    val maxSample = (rxHistory.maxOrNull() ?: 0L)
                        .coerceAtLeast(txHistory.maxOrNull() ?: 0L)
                        .coerceAtLeast(64 * 1024L) // minimum 64 KB/s scale floor

                    val count = rxHistory.size.coerceAtLeast(2)
                    val stepX = w / (count - 1).toFloat()

                    val rxPath = Path()
                    val rxFill = Path()
                    val txPath = Path()

                    rxFill.moveTo(0f, h)

                    for (i in 0 until count) {
                        val x = i * stepX
                        val normalizedRx = (rxHistory[i].toFloat() / maxSample.toFloat()).coerceIn(0f, 1f)
                        val normalizedTx = (txHistory[i].toFloat() / maxSample.toFloat()).coerceIn(0f, 1f)

                        // Blend subtle carrier wave so the oscilloscope stays alive at low/idle bitrates
                        val carrier = (sin((i * 0.35f) + carrierPhase) * if (isProxyActive) 3.5f else 1.8f)
                        val ry = (h * 0.84f) - (normalizedRx * (h * 0.68f)) + carrier
                        val ty = (h * 0.86f) - (normalizedTx * (h * 0.65f)) - (carrier * 0.7f)

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
                                listOf(CyberEmerald.copy(alpha = 0.26f), Color.Transparent)
                            )
                        )
                    }

                    drawPath(
                        path = txPath,
                        color = if (isProxyActive) CyberCyan.copy(alpha = 0.75f) else Color(0x33475569),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawPath(
                        path = rxPath,
                        brush = Brush.horizontalGradient(
                            if (isProxyActive) listOf(CyberEmerald, CyberCyan)
                            else listOf(Color(0x44475569), Color(0x44475569))
                        ),
                        style = Stroke(width = if (isProxyActive) 2.2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VIEW 2: NODE CONFIG DECK (WITH 1-TAP URI IMPORTER)
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigView(
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
    val coroutineScope = rememberCoroutineScope()
    var isCheckingAlive by remember { mutableStateOf(false) }
    var aliveResult by remember { mutableStateOf<TestResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. DIRECT SOCKET HEALTH PROBE & CLIPBOARD URI IMPORT CARD
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Node Diagnostics & Quick Import", fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("Probe TCP/QUIC socket or paste URI from clipboard", fontSize = 10.5.sp, color = TextSecondary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val rawClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                val parsed = parseProxyUri(rawClip)
                                if (parsed != null) {
                                    onImportUri(parsed)
                                    Toast.makeText(context, "Imported ${parsed.type.label} (${parsed.host}:${parsed.port})", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No valid vless/trojan/ss/hy2/socks5/http URI in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
                        ) {
                            Text("Import URI", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CyberCyan)
                        }

                        Button(
                            onClick = {
                                if (host.trim().isEmpty()) {
                                    aliveResult = TestResult.Failure("Host is empty")
                                    return@Button
                                }
                                isCheckingAlive = true
                                aliveResult = null
                                coroutineScope.launch {
                                    aliveResult = ProxyTester.testProxy(getCurrentSettings())
                                    isCheckingAlive = false
                                }
                            },
                            enabled = !isCheckingAlive,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = BgObsidian)
                        ) {
                            Text(
                                text = if (isCheckingAlive) "..." else "Probe",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = aliveResult != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        when (val res = aliveResult) {
                            is TestResult.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CyberEmerald.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, CyberEmerald),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "ONLINE • HANDSHAKE VERIFIED (${res.latencyMs} ms)",
                                        color = CyberEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            is TestResult.Failure -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CyberCrimson.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, CyberCrimson),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "UNREACHABLE • ${res.error}",
                                        color = CyberCrimson,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            null -> {}
                        }
                    }
                }
            }
        }

        // 2. SLOT PARAMETERS CARD
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NODE P$activeSlot CONFIGURATION",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyberCrimson.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CyberCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onResetSlot() }
                    ) {
                        Text(
                            text = "Clear P$activeSlot",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberCrimson,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("TUNNEL PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProxyType.entries.forEach { t ->
                        FilterChip(
                            selected = proxyType == t,
                            onClick = { onProxyTypeChange(t); onConfigChanged() },
                            label = { Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = BgObsidian
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("TRANSPORT & WEBRTC MODE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_AND_UDP,
                        onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                        label = { Text("TCP + UDP (Full TProxy)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberEmerald,
                            selectedLabelColor = BgObsidian
                        )
                    )
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_ONLY,
                        onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                        label = { Text("TCP Only (Block QUIC)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan,
                            selectedLabelColor = BgObsidian
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("IP STACK STRATEGY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ipModes = listOf(
                        Pair(IpMode.IPV4_ONLY, "IPv4 Only"),
                        Pair(IpMode.DUAL_STACK, "Dual-Stack"),
                        Pair(IpMode.IPV6_ONLY, "IPv6 Only")
                    )
                    ipModes.forEach { (mode, lbl) ->
                        FilterChip(
                            selected = ipMode == mode,
                            onClick = { onIpModeChange(mode); onConfigChanged() },
                            label = { Text(lbl, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = BgObsidian
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { onHostChange(it); onConfigChanged() },
                        label = { Text("Server Host / IP") },
                        placeholder = { Text("e.g. 45.76.12.90") },
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

                // Smooth Animated Protocol-Specific Fields
                AnimatedContent(
                    targetState = proxyType,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "protocolFieldsTransition"
                ) { targetProtocol ->
                    Column {
                        when (targetProtocol) {
                            ProxyType.SOCKS5, ProxyType.HTTP -> {
                                var passVisible by remember { mutableStateOf(false) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { onUsernameChange(it); onConfigChanged() },
                                        label = { Text("Username") },
                                        placeholder = { Text("Optional") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { onPasswordChange(it); onConfigChanged() },
                                        label = { Text("Password") },
                                        placeholder = { Text("Optional") },
                                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            Text(
                                                text = if (passVisible) "Hide" else "Show",
                                                fontSize = 11.sp,
                                                color = CyberCyan,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { passVisible = !passVisible }
                                                    .padding(end = 10.dp)
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }
                            }

                            ProxyType.SHADOWSOCKS -> {
                                var passVisible by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = ssMethod,
                                    onValueChange = { onSsMethodChange(it); onConfigChanged() },
                                    label = { Text("AEAD / 2022 Cipher Method") },
                                    placeholder = { Text("2022-blake3-aes-128-gcm or aes-256-gcm") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { onPasswordChange(it); onConfigChanged() },
                                    label = { Text("Password / Pre-Shared Key") },
                                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Text(
                                            text = if (passVisible) "Hide" else "Show",
                                            fontSize = 11.sp,
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { passVisible = !passVisible }
                                                .padding(end = 10.dp)
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
                                    placeholder = { Text("e.g. 550e8400-e29b-41d4-a716-446655440000") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = sni,
                                    onValueChange = { onSniChange(it); onConfigChanged() },
                                    label = { Text("TLS SNI / Handshake Server Name") },
                                    placeholder = { Text("e.g. www.microsoft.com") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = realityPublicKey,
                                        onValueChange = { onRealityPublicKeyChange(it); onConfigChanged() },
                                        label = { Text("REALITY Public Key") },
                                        placeholder = { Text("Optional (uTLS Chrome)") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = realityShortId,
                                        onValueChange = { onRealityShortIdChange(it); onConfigChanged() },
                                        label = { Text("Short ID (SID)") },
                                        placeholder = { Text("Optional") },
                                        modifier = Modifier.weight(0.6f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }
                            }

                            ProxyType.TROJAN, ProxyType.HYSTERIA2 -> {
                                var passVisible by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { onPasswordChange(it); onConfigChanged() },
                                    label = { Text("${targetProtocol.label} Auth Password") },
                                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Text(
                                            text = if (passVisible) "Hide" else "Show",
                                            fontSize = 11.sp,
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { passVisible = !passVisible }
                                                .padding(end = 10.dp)
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
                                    label = { Text("TLS SNI / Server Name") },
                                    placeholder = { Text("e.g. cdn.yourdomain.com") },
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

        Spacer(modifier = Modifier.height(14.dp))
    }
}

// -------------------------------------------------------------
// VIEW 3: SPLIT TUNNELING & KERNEL BOOT AUTOMATION
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsMasterView(
    startOnBoot: Boolean,
    bootSlot: Int,
    activeSlot: Int,
    routeHotspot: Boolean,
    routeWholeProfile: Boolean,
    selectedPackages: Set<String>,
    installedApps: List<AppItem>,
    onStartOnBootChange: (Boolean) -> Unit,
    onBootSlotChange: (Int) -> Unit,
    onRouteHotspotChange: (Boolean) -> Unit,
    onRouteWholeProfileChange: (Boolean) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("KERNEL AUTOMATION & TETHERING", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = CyberCyan, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Engage on System Boot", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = if (startOnBoot) "Service script active (/data/adb/service.d) • Node P$bootSlot" else "Disabled — manual launch only",
                            fontSize = 11.sp,
                            color = if (startOnBoot) CyberEmerald else TextSecondary
                        )
                    }
                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBootChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgObsidian,
                            checkedTrackColor = CyberEmerald
                        )
                    )
                }

                AnimatedVisibility(
                    visible = startOnBoot,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("DESIGNATED BOOT NODE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 0.8.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (0 until ProfileManager.MAX_SLOTS).forEach { s ->
                                FilterChip(
                                    selected = bootSlot == s,
                                    onClick = { onBootSlotChange(s) },
                                    label = { Text("P$s", fontSize = 11.sp, fontWeight = FontWeight.Black) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberAmber,
                                        selectedLabelColor = BgObsidian
                                    )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x16FFFFFF), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hotspot & USB Tethering Bridge", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = "Tunnel Wi-Fi Hotspot (wlan+/ap+) & RNDIS/USB clients",
                            fontSize = 11.sp,
                            color = if (routeHotspot) CyberCyan else TextSecondary
                        )
                    }
                    Switch(
                        checked = routeHotspot,
                        onCheckedChange = onRouteHotspotChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgObsidian,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Route Entire Android User Profile", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = if (routeWholeProfile) "All apps in User ${ProfileManager.androidUserId} tunneled" else "Per-UID Split Tunnel (${selectedPackages.size} apps selected)",
                            fontSize = 11.sp,
                            color = if (routeWholeProfile) CyberCyan else CyberEmerald
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = onRouteWholeProfileChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgObsidian,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                AnimatedVisibility(
                    visible = !routeWholeProfile,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(10.dp))

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
                            placeholder = { Text("Search apps or package names...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedPackages.size} / ${installedApps.size} selected",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Row {
                                TextButton(onClick = onSelectAll) {
                                    Text("Select All", color = CyberCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = onClearAll) {
                                    Text("Clear", color = CyberCrimson, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.packageName }) { app ->
                                val isChecked = selectedPackages.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isChecked) CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                                        .clickable { onToggleAppSelection(app.packageName) }
                                        .padding(vertical = 7.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${app.packageName} • UID ${app.uid}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleAppSelection(app.packageName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = CyberCyan,
                                            checkmarkColor = BgObsidian
                                        )
                                    )
                                }
                                HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// -------------------------------------------------------------
// REUSABLE BADGES, UPTIME COUNTER & URI PARSER
// -------------------------------------------------------------

@Composable
fun CyberBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = color,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LiveUptimeCounter(sessionStartMs: Long) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sessionStartMs) {
        while (isActive) {
            elapsedSeconds = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            delay(1000)
        }
    }

    val hrs = elapsedSeconds / 3600
    val mins = (elapsedSeconds % 3600) / 60
    val secs = elapsedSeconds % 60
    val timeStr = if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs) else String.format("%02d:%02d", mins, secs)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = CyberEmerald.copy(alpha = 0.14f),
        border = BorderStroke(0.8.dp, CyberEmerald.copy(alpha = 0.5f))
    ) {
        Text(
            text = "⏱ $timeStr",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            color = CyberEmerald,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
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

private fun formatCompactRate(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun splitSpeedValue(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.1f", bytesPerSec / 1024.0)
        else -> "$bytesPerSec"
    }
}

private fun splitSpeedUnit(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> "MB/s"
        bytesPerSec >= 1024 -> "KB/s"
        else -> "B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
