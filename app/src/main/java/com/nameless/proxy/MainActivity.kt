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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.ln
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
// SOLAR SYSTEM PALETTE (AUTOMATICALLY FOLLOWS SYSTEM DARK/LIGHT)
// -------------------------------------------------------------

data class SolarPalette(
    val isDark: Boolean,
    val spaceBgTop: Color,
    val spaceBgBottom: Color,
    val cardSurface: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val orbitLineColor: Color,
    val sunCore: Color,
    val sunCorona: Color,
    val earthBlue: Color,
    val marsCoral: Color,
    val jupiterAmber: Color,
    val neptuneCyan: Color,
    val activeEmerald: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

// Dark Mode: Deep Cosmos Observatory
private val DarkSolarPalette = SolarPalette(
    isDark = true,
    spaceBgTop = Color(0xFF050814),
    spaceBgBottom = Color(0xFF0C142C),
    cardSurface = Color(0xD9101935),
    cardElevated = Color(0xEE172347),
    cardBorder = Color(0xFF28396B),
    orbitLineColor = Color(0x3894A3B8),
    sunCore = Color(0xFFFFB703),
    sunCorona = Color(0xFFFB8500),
    earthBlue = Color(0xFF38BDF8),
    marsCoral = Color(0xFFFF4D6D),
    jupiterAmber = Color(0xFFF59E0B),
    neptuneCyan = Color(0xFF00F5D4),
    activeEmerald = Color(0xFF10B981),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF64748B)
)

// Light Mode: Celestial Daylight Observatory (High-contrast planets & orbits on soft sky ivory)
private val LightSolarPalette = SolarPalette(
    isDark = false,
    spaceBgTop = Color(0xFFF0F5FF),
    spaceBgBottom = Color(0xFFE2ECFF),
    cardSurface = Color(0xF2FFFFFF),
    cardElevated = Color(0xFFF5F8FF),
    cardBorder = Color(0xFFCBD8F0),
    orbitLineColor = Color(0x55475569),
    sunCore = Color(0xFFF59E0B),
    sunCorona = Color(0xFFEA580C),
    earthBlue = Color(0xFF0284C7),
    marsCoral = Color(0xFFE11D48),
    jupiterAmber = Color(0xFFD97706),
    neptuneCyan = Color(0xFF0D9488),
    activeEmerald = Color(0xFF059669),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B)
)

val LocalSolarPalette = compositionLocalOf { DarkSolarPalette }

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var rootState by mutableStateOf(RootState.CHECKING)
    private var rootLabel by mutableStateOf("Checking")
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
            // Automatically follows Android System Dark / Light Mode
            val isDarkSystem = isSystemInDarkTheme()
            val palette = if (isDarkSystem) DarkSolarPalette else LightSolarPalette

            val colorScheme = if (isDarkSystem) {
                darkColorScheme(
                    primary = palette.sunCore,
                    secondary = palette.earthBlue,
                    background = palette.spaceBgTop,
                    surface = palette.cardSurface,
                    onPrimary = Color(0xFF0F172A),
                    onSurface = palette.textPrimary,
                    error = palette.marsCoral
                )
            } else {
                lightColorScheme(
                    primary = palette.sunCorona,
                    secondary = palette.earthBlue,
                    background = palette.spaceBgTop,
                    surface = palette.cardSurface,
                    onPrimary = Color.White,
                    onSurface = palette.textPrimary,
                    error = palette.marsCoral
                )
            }

            CompositionLocalProvider(LocalSolarPalette provides palette) {
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
            rootLabel = "Wait"
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
    val palette = LocalSolarPalette.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    var selectedScreen by remember { mutableIntStateOf(0) }

    // Interactive Tap-Reactive Solar System State (Every tap triggers a gravitational wave & planet orbit boost!)
    var lastTapPosition by remember { mutableStateOf(Offset.Unspecified) }
    var tapCount by remember { mutableIntStateOf(0) }
    val tapRippleAnim = remember { Animatable(1f) }
    val orbitalBoostAnim = remember { Animatable(0f) }

    fun triggerSolarTapReaction(tapOffset: Offset = Offset.Unspecified) {
        lastTapPosition = tapOffset
        tapCount++
        coroutineScope.launch {
            tapRippleAnim.snapTo(0f)
            tapRippleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(950, easing = FastOutSlowInEasing)
            )
        }
        coroutineScope.launch {
            orbitalBoostAnim.animateTo(
                targetValue = orbitalBoostAnim.value + 38f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Hoisted Live Telemetry
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var rxWaveHistory by remember { mutableStateOf(List(30) { 0L }) }
    var txWaveHistory by remember { mutableStateOf(List(30) { 0L }) }
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
                delay(650)
                val currRx = TrafficStats.getTotalRxBytes().coerceAtLeast(prevRx)
                val currTx = TrafficStats.getTotalTxBytes().coerceAtLeast(prevTx)
                val currTime = System.currentTimeMillis()
                val dt = (currTime - prevTime).coerceAtLeast(1L) / 1000.0

                val rxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0L)
                val txRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0L)

                currentRxRate = rxRate
                currentTxRate = txRate
                if (rxRate > peakRxRate) peakRxRate = rxRate
                if (txRate > peakTxRate) peakTxRate = txRate
                totalRxBytes = (currRx - startRx).coerceAtLeast(0L)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0L)

                rxWaveHistory = rxWaveHistory.drop(1) + rxRate
                txWaveHistory = txWaveHistory.drop(1) + txRate

                prevRx = currRx
                prevTx = currTx
                prevTime = currTime
            }
        } else {
            sessionStartMs = 0L
            currentRxRate = 0L
            currentTxRate = 0L
            peakRxRate = 0L
            peakTxRate = 0L
            totalRxBytes = 0L
            totalTxBytes = 0L
            rxWaveHistory = List(30) { 0L }
            txWaveHistory = List(30) { 0L }
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

    var slotProtocols by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    fun refreshSlotMetadata() {
        val map = mutableMapOf<Int, String>()
        for (i in 0 until ProfileManager.MAX_SLOTS) {
            val sp = getSlotPrefs(i)
            val h = sp.getString("host", "") ?: ""
            if (h.isNotBlank()) {
                val pName = sp.getString("proxy_type", "SOCKS5") ?: "SOCKS5"
                map[i] = pName
            }
        }
        slotProtocols = map
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
            refreshSlotMetadata()
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
                        refreshSlotMetadata()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        applySettingsToState("", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "", ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, emptySet())
                        refreshSlotMetadata()
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

        refreshSlotMetadata()

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
                BootManager.syncBootState(context, true, settingsSnapshot, uidsSnapshot)
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
        triggerSolarTapReaction()
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
    var currentLogs by remember { mutableStateOf("Tap Refresh to load kernel daemon telemetry...") }

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }
    var ipFetchFailed by remember { mutableStateOf(false) }

    fun triggerPublicIpCheck() {
        isFetchingIp = true
        ipFetchFailed = false
        coroutineScope.launch(Dispatchers.IO) {
            delay(500)
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

    LaunchedEffect(selectedScreen, runningSlot, activeSlot) {
        if (selectedScreen == 3) {
            withContext(Dispatchers.IO) {
                val targetSlot = runningSlot ?: activeSlot
                val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                withContext(Dispatchers.Main) {
                    currentLogs = logs.ifEmpty { "No kernel logs recorded yet." }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.spaceBgTop, palette.spaceBgBottom)
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        triggerSolarTapReaction(offset)
                    }
                )
            }
    ) {
        // 1. LIVE 8-PLANET INTERACTIVE SOLAR SYSTEM BACKGROUND (MOVES & RIPPLES ON EVERY TAP)
        InteractiveSolarSystemBackground(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning || isTesting,
            orbitalBoostDeg = orbitalBoostAnim.value,
            tapPosition = lastTapPosition,
            tapRippleProgress = tapRippleAnim.value
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. SOLAR SYSTEM HEADER + MULTI-USER PROFILE BADGE + PLANETARY NODE RIBBON
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SolarSystemTopBar(
                    isProxyActive = isProxyActive,
                    runningSlot = runningSlot,
                    activeSlot = activeSlot,
                    rootState = rootState,
                    onRecheckRoot = {
                        triggerSolarTapReaction()
                        onRecheckRoot()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PlanetarySlotSelector(
                    activeSlot = activeSlot,
                    runningSlot = runningSlot,
                    isProxyActive = isProxyActive,
                    startOnBoot = startOnBoot,
                    bootSlot = bootSlot,
                    slotProtocols = slotProtocols,
                    onSelectSlot = { switchSlot(it) }
                )
            }

            // 3. ANIMATED VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = selectedScreen,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { fullW -> dir * (fullW / 3) }
                        ) + fadeIn(tween(220))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(170),
                                targetOffsetX = { fullW -> -dir * (fullW / 4) }
                            ) + fadeOut(tween(150))
                        )
                    },
                    label = "solarScreenTransition"
                ) { screenIdx ->
                    when (screenIdx) {
                        0 -> {
                            SolarObservatoryCockpit(
                                isProxyActive = isProxyActive,
                                isTransitioning = isTransitioning,
                                runningSlot = runningSlot,
                                activeSlot = activeSlot,
                                orbitalBoostDeg = orbitalBoostAnim.value,
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
                                peakTxRate = peakTxRate,
                                totalRxBytes = totalRxBytes,
                                totalTxBytes = totalTxBytes,
                                rxWaveHistory = rxWaveHistory,
                                txWaveHistory = txWaveHistory,
                                onRefreshIp = {
                                    triggerSolarTapReaction()
                                    triggerPublicIpCheck()
                                },
                                onToggleProxy = {
                                    triggerSolarTapReaction()
                                    if (isProxyActive && runningSlot == activeSlot) {
                                        onStopProxy { }
                                    } else {
                                        if (host.trim().isEmpty()) {
                                            testStatus = "Configure Server Host/IP in Nodes tab first"
                                            testLatencyMs = null
                                            selectedScreen = 1
                                            return@SolarObservatoryCockpit
                                        }
                                        saveConfigForSlot(activeSlot, immediateRootSync = true)
                                        val settings = getCurrentSettings()
                                        val targets = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
                                        onStartProxy(settings, targets) { success, error ->
                                            if (!success) {
                                                testStatus = "Launch Failed: ${error?.lines()?.lastOrNull() ?: "See Logs tab"}"
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
                                    triggerSolarTapReaction()
                                    if (host.trim().isEmpty()) {
                                        testStatus = "Enter Server Host/IP first"
                                        testLatencyMs = null
                                        selectedScreen = 1
                                        return@SolarObservatoryCockpit
                                    }
                                    saveConfigForSlot(activeSlot)
                                    isTesting = true
                                    testStatus = "Pinging ${host.trim()}:${port.trim()}..."
                                    testLatencyMs = null
                                    val settings = getCurrentSettings()
                                    coroutineScope.launch {
                                        when (val res = ProxyTester.testProxy(settings)) {
                                            is TestResult.Success -> {
                                                testLatencyMs = res.latencyMs
                                                testStatus = "Orbit Link Verified • ${res.latencyMs} ms"
                                            }
                                            is TestResult.Failure -> {
                                                testLatencyMs = null
                                                testStatus = "Failed: ${res.error}"
                                            }
                                        }
                                        isTesting = false
                                    }
                                }
                            )
                        }

                        1 -> {
                            SolarNodeConfigScreen(
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
                                onProxyTypeChange = { triggerSolarTapReaction(); proxyType = it },
                                onTransportModeChange = { triggerSolarTapReaction(); transportMode = it },
                                onIpModeChange = { triggerSolarTapReaction(); ipMode = it },
                                onImportUri = { imported ->
                                    triggerSolarTapReaction()
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
                                    triggerSolarTapReaction()
                                    val targetSlot = activeSlot
                                    getSlotPrefs(targetSlot).edit().clear().apply()
                                    applySettingsToState("", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "", ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, emptySet())
                                    refreshSlotMetadata()
                                    coroutineScope.launch(Dispatchers.IO) {
                                        PersistentStorage.clearBackup(targetSlot, ProfileManager.androidUserId)
                                    }
                                    Toast.makeText(context, "Orbit Node P$targetSlot reset", Toast.LENGTH_SHORT).show()
                                },
                                getCurrentSettings = { getCurrentSettings() }
                            )
                        }

                        2 -> {
                            SolarRoutingScreen(
                                startOnBoot = startOnBoot,
                                bootSlot = bootSlot,
                                activeSlot = activeSlot,
                                routeHotspot = routeHotspot,
                                routeWholeProfile = routeWholeProfile,
                                selectedPackages = selectedPackages,
                                installedApps = installedApps,
                                onStartOnBootChange = { enabled ->
                                    triggerSolarTapReaction()
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
                                    triggerSolarTapReaction()
                                    bootSlot = s
                                    globalPrefs.edit().putInt("boot_slot", s).apply()
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                    Toast.makeText(context, "Node P$s designated for Boot", Toast.LENGTH_SHORT).show()
                                },
                                onRouteHotspotChange = {
                                    triggerSolarTapReaction()
                                    routeHotspot = it
                                    globalPrefs.edit().putBoolean("route_hotspot", it).apply()
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                },
                                onRouteWholeProfileChange = {
                                    triggerSolarTapReaction()
                                    routeWholeProfile = it
                                    saveConfigForSlot(activeSlot, immediateRootSync = true)
                                },
                                onToggleAppSelection = { pkg ->
                                    triggerSolarTapReaction()
                                    selectedPackages = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg
                                    saveConfigForSlot(activeSlot)
                                },
                                onSelectAll = {
                                    triggerSolarTapReaction()
                                    selectedPackages = installedApps.map { it.packageName }.toSet()
                                    saveConfigForSlot(activeSlot)
                                },
                                onClearAll = {
                                    triggerSolarTapReaction()
                                    selectedPackages = emptySet()
                                    saveConfigForSlot(activeSlot)
                                }
                            )
                        }

                        3 -> {
                            SolarLogsScreen(
                                activeSlot = activeSlot,
                                runningSlot = runningSlot,
                                currentLogs = currentLogs,
                                onRefreshLogs = {
                                    triggerSolarTapReaction()
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val targetSlot = runningSlot ?: activeSlot
                                        val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                                        withContext(Dispatchers.Main) {
                                            currentLogs = logs.ifEmpty { "No logs recorded." }
                                        }
                                    }
                                },
                                onClearLogs = {
                                    triggerSolarTapReaction()
                                    val targetSlot = runningSlot ?: activeSlot
                                    currentLogs = "Logs cleared."
                                    coroutineScope.launch(Dispatchers.IO) {
                                        ProxyController.clearLogs(ProfileManager.androidUserId, targetSlot)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 4. SOLAR OBSERVATORY BOTTOM NAVIGATION DOCK
            SolarBottomNavigationDock(
                selectedScreen = selectedScreen,
                onSelectScreen = {
                    triggerSolarTapReaction()
                    selectedScreen = it
                },
                splitAppsCount = if (routeWholeProfile) null else selectedPackages.size
            )
        }
    }
}

// -------------------------------------------------------------
// 1. LIVE 8-PLANET SOLAR SYSTEM BACKGROUND (MOVES & RIPPLES ON TAP)
// -------------------------------------------------------------

private data class PlanetSpec(
    val name: String,
    val orbitRadiusFraction: Float,
    val speedMultiplier: Float,
    val sizeDp: Float,
    val primaryColor: Color,
    val secondaryColor: Color,
    val initialPhaseDeg: Float,
    val hasRings: Boolean = false,
    val hasMoon: Boolean = false
)

@Composable
fun InteractiveSolarSystemBackground(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    orbitalBoostDeg: Float,
    tapPosition: Offset,
    tapRippleProgress: Float
) {
    val palette = LocalSolarPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "solarSystemOrbits")

    val baseOrbitTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = if (isProxyActive) 18000 else 42000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "baseOrbitTime"
    )

    val sunPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "sunPulse"
    )

    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 2600 else 5500, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shootingStar"
    )

    val planets = remember {
        listOf(
            PlanetSpec("Mercury", 0.14f, 4.1f, 4.5f, Color(0xFF94A3B8), Color(0xFFCBD5E1), 15f),
            PlanetSpec("Venus", 0.21f, 3.0f, 6.2f, Color(0xFFFBBF24), Color(0xFFF59E0B), 85f),
            PlanetSpec("Earth", 0.29f, 2.3f, 7.2f, Color(0xFF0EA5E9), Color(0xFF10B981), 160f, hasMoon = true),
            PlanetSpec("Mars", 0.37f, 1.8f, 5.6f, Color(0xFFEF4444), Color(0xFFF97316), 235f),
            PlanetSpec("Jupiter", 0.48f, 1.3f, 11.5f, Color(0xFFD97706), Color(0xFFFDE68A), 45f),
            PlanetSpec("Saturn", 0.60f, 0.95f, 9.5f, Color(0xFFEAB308), Color(0xFFFEF08A), 130f, hasRings = true),
            PlanetSpec("Uranus", 0.72f, 0.70f, 7.5f, Color(0xFF06B6D4), Color(0xFF67E8F9), 275f),
            PlanetSpec("Neptune", 0.84f, 0.52f, 7.2f, Color(0xFF3B82F6), Color(0xFF6366F1), 320f)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height

        // Center the Solar System slightly above the vertical midpoint so planets orbit visibly around the UI
        val sunCenter = Offset(w * 0.82f, h * 0.18f)
        val maxOrbitExtent = w * 1.05f

        // 1. Twinkling Starfield (Dark Mode: luminous white/gold stars | Light Mode: subtle celestial dust)
        val starCount = 42
        for (i in 0 until starCount) {
            val sx = (w * ((i * 37 + 13) % 100)) / 100f
            val sy = (h * ((i * 61 + 29) % 100)) / 100f
            val twinkle = ((sin(Math.toRadians((baseOrbitTime * 4f + i * 25f).toDouble())) + 1.0) * 0.5).toFloat()
            val starRadius = (1.0f + (i % 3) * 0.8f).dp.toPx()

            drawCircle(
                color = if (palette.isDark) {
                    if (i % 5 == 0) palette.sunCore else Color.White
                } else {
                    if (i % 3 == 0) palette.sunCorona else palette.earthBlue
                },
                radius = starRadius,
                center = Offset(sx, sy),
                alpha = if (palette.isDark) (0.20f + twinkle * 0.55f) else (0.12f + twinkle * 0.22f)
            )
        }

        // 2. Glowing Sun Corona & Core in the Upper Sky
        val sunRadius = 26.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.sunCore.copy(alpha = if (palette.isDark) 0.55f else 0.42f),
                    palette.sunCorona.copy(alpha = if (palette.isDark) 0.22f else 0.16f),
                    Color.Transparent
                ),
                center = sunCenter,
                radius = sunRadius * 4.2f * sunPulse
            ),
            radius = sunRadius * 4.2f * sunPulse,
            center = sunCenter
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFBEB), palette.sunCore, palette.sunCorona),
                center = sunCenter,
                radius = sunRadius
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // 3. All 8 Planetary Orbits + Live Moving Planets
        planets.forEach { planet ->
            val orbitR = maxOrbitExtent * planet.orbitRadiusFraction

            // Draw elliptical/circular orbital track
            drawCircle(
                color = palette.orbitLineColor,
                radius = orbitR,
                center = sunCenter,
                style = Stroke(width = 1.1.dp.toPx())
            )

            // Planet live angle = continuous time + tap-reactive boost
            val totalAngleDeg = planet.initialPhaseDeg +
                (baseOrbitTime * planet.speedMultiplier) +
                (orbitalBoostDeg * planet.speedMultiplier * 0.65f)
            val rad = Math.toRadians(totalAngleDeg.toDouble())

            val px = sunCenter.x + (orbitR * cos(rad)).toFloat()
            val py = sunCenter.y + (orbitR * sin(rad)).toFloat()
            val pCenter = Offset(px, py)
            val pRadius = planet.sizeDp.dp.toPx()

            // Subtle planetary aura
            drawCircle(
                color = planet.primaryColor.copy(alpha = if (palette.isDark) 0.28f else 0.22f),
                radius = pRadius * 1.9f,
                center = pCenter
            )

            // Saturn's iconic rings
            if (planet.hasRings) {
                rotate(degrees = -28f, pivot = pCenter) {
                    drawOval(
                        color = planet.secondaryColor.copy(alpha = 0.85f),
                        topLeft = Offset(px - pRadius * 2.2f, py - pRadius * 0.65f),
                        size = Size(pRadius * 4.4f, pRadius * 1.3f),
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                }
            }

            // Planet sphere
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(planet.secondaryColor, planet.primaryColor),
                    start = Offset(px - pRadius, py - pRadius),
                    end = Offset(px + pRadius, py + pRadius)
                ),
                radius = pRadius,
                center = pCenter
            )

            // Earth's orbiting Moon
            if (planet.hasMoon) {
                val moonAngle = Math.toRadians((totalAngleDeg * 5f).toDouble())
                val moonR = pRadius * 2.1f
                val mx = px + (moonR * cos(moonAngle)).toFloat()
                val my = py + (moonR * sin(moonAngle)).toFloat()
                drawCircle(
                    color = if (palette.isDark) Color(0xFFE2E8F0) else Color(0xFF475569),
                    radius = 2.2.dp.toPx(),
                    center = Offset(mx, my)
                )
            }
        }

        // 4. Shooting Comet Across Cosmos
        val cometStartX = w * 0.10f
        val cometStartY = h * 0.12f
        val cometX = cometStartX + (w * 0.85f * shootingStarProgress)
        val cometY = cometStartY + (h * 0.45f * shootingStarProgress)
        if (shootingStarProgress in 0.1f..0.85f) {
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, palette.sunCore.copy(alpha = 0.7f)),
                    start = Offset(cometX - 45.dp.toPx(), cometY - 24.dp.toPx()),
                    end = Offset(cometX, cometY)
                ),
                start = Offset(cometX - 45.dp.toPx(), cometY - 24.dp.toPx()),
                end = Offset(cometX, cometY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = palette.sunCore,
                radius = 2.8.dp.toPx(),
                center = Offset(cometX, cometY)
            )
        }

        // 5. Interactive Tap Gravitational Wave & Stardust Burst
        if (tapPosition != Offset.Unspecified && tapRippleProgress < 0.99f) {
            val maxRippleR = w * 0.65f
            val currentR = maxRippleR * tapRippleProgress
            val fadeAlpha = (1f - tapRippleProgress).coerceIn(0f, 1f)

            drawCircle(
                color = palette.sunCore.copy(alpha = fadeAlpha * 0.55f),
                radius = currentR,
                center = tapPosition,
                style = Stroke(width = 2.2.dp.toPx())
            )

            drawCircle(
                color = palette.earthBlue.copy(alpha = fadeAlpha * 0.35f),
                radius = currentR * 0.68f,
                center = tapPosition,
                style = Stroke(width = 1.4.dp.toPx())
            )

            // 8 Orbiting Stardust Sparks bursting from the tap point
            for (s in 0 until 8) {
                val sparkAngle = Math.toRadians((s * 45.0) + (tapRippleProgress * 90.0))
                val dist = (currentR * 0.55f)
                val sx = tapPosition.x + (dist * cos(sparkAngle)).toFloat()
                val sy = tapPosition.y + (dist * sin(sparkAngle)).toFloat()
                drawCircle(
                    color = if (s % 2 == 0) palette.sunCore else palette.earthBlue,
                    radius = (3.5f * fadeAlpha).dp.toPx(),
                    center = Offset(sx, sy),
                    alpha = fadeAlpha
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SOLAR SYSTEM TOP BAR WITH MULTI-USER PROFILE INDICATOR
// -------------------------------------------------------------

@Composable
fun SolarSystemTopBar(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    onRecheckRoot: () -> Unit
) {
    val palette = LocalSolarPalette.current
    val userId = ProfileManager.androidUserId
    val userProfileLabel = if (userId == 0) "OWNER (U0)" else "PROFILE #$userId"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "☀️",
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NAMELESS SOLAR",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isProxyActive) {
                    "$userProfileLabel • ${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()} ORBIT LIVE"
                } else {
                    "$userProfileLabel • PORT ${ProfileManager.localInboundPort} • ${if (palette.isDark) "DARK COSMOS" else "DAYLIGHT SKY"}"
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isProxyActive) palette.activeEmerald else palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Multi-User Profile Isolation Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.cardSurface,
                border = BorderStroke(1.dp, palette.cardBorder)
            ) {
                Text(
                    text = "👤 $userProfileLabel",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.earthBlue,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }

            // Root Status Pill
            val rootColor = when (rootState) {
                RootState.GRANTED -> palette.activeEmerald
                RootState.DENIED -> palette.marsCoral
                RootState.CHECKING -> palette.sunCore
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.cardSurface,
                border = BorderStroke(1.2.dp, rootColor),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onRecheckRoot() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(rootColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when (rootState) {
                            RootState.GRANTED -> "ROOT"
                            RootState.DENIED -> "NO ROOT"
                            RootState.CHECKING -> "..."
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PLANETARY NODE SELECTOR (MERCURY P0 .. SATURN P4)
// -------------------------------------------------------------

@Composable
fun PlanetarySlotSelector(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    slotProtocols: Map<Int, String>,
    onSelectSlot: (Int) -> Unit
) {
    val palette = LocalSolarPalette.current
    val planetColors = listOf(
        Color(0xFF94A3B8), // P0 Mercury
        Color(0xFFF59E0B), // P1 Venus
        Color(0xFF0EA5E9), // P2 Earth
        Color(0xFFEF4444), // P3 Mars
        Color(0xFFEAB308)  // P4 Saturn
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        (0 until ProfileManager.MAX_SLOTS).forEach { slotIdx ->
            val isSelected = slotIdx == activeSlot
            val isRunning = isProxyActive && (runningSlot == slotIdx)
            val isBoot = startOnBoot && (bootSlot == slotIdx)
            val isConfigured = slotProtocols.containsKey(slotIdx)
            val pColor = planetColors[slotIdx]

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "planetSlotScale"
            )

            val bg by animateColorAsState(
                targetValue = when {
                    isRunning -> palette.activeEmerald.copy(alpha = if (palette.isDark) 0.24f else 0.16f)
                    isSelected -> palette.sunCore.copy(alpha = if (palette.isDark) 0.24f else 0.18f)
                    else -> palette.cardSurface
                },
                animationSpec = tween(220),
                label = "planetSlotBg"
            )

            val borderCol by animateColorAsState(
                targetValue = when {
                    isRunning -> palette.activeEmerald
                    isSelected -> palette.sunCore
                    else -> palette.cardBorder
                },
                animationSpec = tween(220),
                label = "planetSlotBorder"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(if (isSelected && !palette.isDark) 4.dp else 0.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(if (isSelected || isRunning) 1.6.dp else 1.dp, borderCol, RoundedCornerShape(14.dp))
                    .clickable { onSelectSlot(slotIdx) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(pColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "P$slotIdx",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isRunning -> "ORBITING"
                        isBoot -> "BOOT"
                        isConfigured -> SlotPlanetNames[slotIdx]
                        else -> "Empty"
                    },
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isRunning -> palette.activeEmerald
                        isBoot -> palette.sunCorona
                        isSelected -> if (palette.isDark) palette.sunCore else palette.sunCorona
                        else -> palette.textMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 0: SOLAR OBSERVATORY COCKPIT (MINI ORRERY + PANORAMIC SPEEDOMETER)
// -------------------------------------------------------------

@Composable
fun SolarObservatoryCockpit(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    orbitalBoostDeg: Float,
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
    peakTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxWaveHistory: List<Long>,
    txWaveHistory: List<Long>,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    testLatencyMs: Long?,
    onTestUpstream: () -> Unit
) {
    val palette = LocalSolarPalette.current
    val isCurrentSlotRunning = isProxyActive && (runningSlot == activeSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. INTERACTIVE SOLAR SYSTEM ORRERY CORE + EXIT NODE CARD
        SolarOrreryHeroCard(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning,
            runningSlot = runningSlot,
            activeSlot = activeSlot,
            orbitalBoostDeg = orbitalBoostDeg,
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

        // 2. COMPLETELY REDESIGNED PANORAMIC ORBITAL SPEEDOMETER & LIQUID TELEMETRY
        PanoramicOrbitalSpeedometer(
            isProxyActive = isProxyActive,
            currentRxRate = currentRxRate,
            currentTxRate = currentTxRate,
            peakRxRate = peakRxRate,
            peakTxRate = peakTxRate,
            totalRxBytes = totalRxBytes,
            totalTxBytes = totalTxBytes,
            rxWaveHistory = rxWaveHistory,
            txWaveHistory = txWaveHistory
        )

        // 3. TELEMETRY BADGES & LATENCY TEST
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (palette.isDark) 0.dp else 3.dp, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SolarBadge(label = proxyType.displayTitle, color = palette.sunCorona)
                    SolarBadge(
                        label = if (transportMode == TransportMode.TCP_AND_UDP) "TCP + UDP TPROXY" else "TCP ONLY",
                        color = palette.earthBlue
                    )
                    SolarBadge(label = "WEBRTC & DoT 853 SHIELD", color = palette.activeEmerald)
                    SolarBadge(
                        label = if (routeWholeProfile) "ALL APPS" else "$selectedAppsCount APPS",
                        color = palette.jupiterAmber
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onTestUpstream,
                    enabled = !isTesting && !isTransitioning,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.earthBlue.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = if (isTesting) "Scanning Orbital Link..." else "🛰️ Ping ${SlotPlanetNames[activeSlot]} Node (P$activeSlot)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.earthBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(
                    visible = testStatus != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val sCol = when {
                        isTesting -> palette.sunCore
                        testLatencyMs != null -> palette.activeEmerald
                        else -> palette.marsCoral
                    }
                    Text(
                        text = testStatus ?: "",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = sCol,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 4. SOLAR IGNITION LAUNCH BUTTON
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val btnScale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "solarLaunchScale"
        )

        val btnBrush = when {
            isTransitioning -> Brush.horizontalGradient(listOf(palette.sunCore, palette.sunCorona))
            isCurrentSlotRunning -> Brush.horizontalGradient(listOf(palette.marsCoral, Color(0xFFBE123C)))
            else -> Brush.horizontalGradient(listOf(palette.sunCorona, palette.sunCore, palette.earthBlue))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .graphicsLayer {
                    scaleX = btnScale
                    scaleY = btnScale
                }
                .shadow(if (palette.isDark) 0.dp else 4.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(btnBrush)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isTransitioning
                ) { onToggleProxy() },
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
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// INTERACTIVE SOLAR ORRERY HERO CARD
// -------------------------------------------------------------

@Composable
fun SolarOrreryHeroCard(
    isProxyActive: Boolean,
    isTransitioning: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    orbitalBoostDeg: Float,
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
    val palette = LocalSolarPalette.current
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "miniOrrery")

    val orreryAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 4500 else 14000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "orreryAngle"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = palette.cardSurface,
        border = BorderStroke(
            width = 1.4.dp,
            color = if (isProxyActive) palette.activeEmerald else palette.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (palette.isDark) 0.dp else 4.dp, RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left: Interactive Mini Solar System Orrery (Sun + 3 Orbiting Planets — Tap to Toggle Tunnel)
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isTransitioning) { onToggleProxy() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f

                    // 3 Concentric Planetary Rings
                    val ringRadii = listOf(maxR * 0.45f, maxR * 0.68f, maxR * 0.90f)
                    val ringSpeeds = listOf(2.2f, 1.4f, 0.8f)
                    val planetColors = listOf(palette.marsCoral, palette.earthBlue, palette.sunCore)

                    ringRadii.forEachIndexed { idx, r ->
                        drawCircle(
                            color = palette.orbitLineColor,
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.2.dp.toPx())
                        )

                        val deg = (orreryAngle + orbitalBoostDeg) * ringSpeeds[idx] + (idx * 110f)
                        val rad = Math.toRadians(deg.toDouble())
                        val px = cx + (r * cos(rad)).toFloat()
                        val py = cy + (r * sin(rad)).toFloat()

                        drawCircle(
                            color = planetColors[idx],
                            radius = (3.6f + idx * 0.8f).dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    // Glowing Sun Core in Center
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = if (isProxyActive) {
                                listOf(palette.activeEmerald.copy(alpha = 0.45f), Color.Transparent)
                            } else {
                                listOf(palette.sunCore.copy(alpha = 0.45f), Color.Transparent)
                            }
                        ),
                        radius = maxR * 0.42f,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = if (isProxyActive) {
                                listOf(palette.activeEmerald, palette.neptuneCyan)
                            } else {
                                listOf(palette.sunCore, palette.sunCorona)
                            }
                        ),
                        radius = maxR * 0.24f,
                        center = Offset(cx, cy)
                    )
                }

                Text(
                    text = if (isProxyActive) "ON" else "GO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // Right: Exit Node & Private DNS Status Stack
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isProxyActive) {
                            "${SlotPlanetNames[runningSlot ?: activeSlot].uppercase()} ORBIT ACTIVE"
                        } else {
                            "${SlotPlanetNames[activeSlot].uppercase()} NODE (P$activeSlot)"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isProxyActive) palette.activeEmerald else palette.sunCorona,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isProxyActive && sessionStartMs > 0L) {
                        SolarUptimePill(sessionStartMs = sessionStartMs)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.cardElevated,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (isProxyActive && publicIpInfo != null) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ExitIP", publicIpInfo.ip))
                                Toast.makeText(context, "Copied: ${publicIpInfo.ip}", Toast.LENGTH_SHORT).show()
                            } else if (isProxyActive) {
                                onRefreshIp()
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
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
                                    text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌍") else "🪐",
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isProxyActive) {
                                        when {
                                            isFetchingIp -> "Verifying..."
                                            publicIpInfo != null -> publicIpInfo.ip
                                            ipFetchFailed -> "Tap Retry"
                                            else -> "Resolving..."
                                        }
                                    } else {
                                        if (host.isNotBlank()) host.trim() else "No Host Configured"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isProxyActive) {
                                Text(
                                    text = if (isFetchingIp) "..." else "↻",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.earthBlue,
                                    modifier = Modifier
                                        .clickable { onRefreshIp() }
                                        .padding(start = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isProxyActive) {
                                when {
                                    isFetchingIp -> "Checking tunnel exit node..."
                                    publicIpInfo != null -> "${publicIpInfo.country} • Tap to copy"
                                    ipFetchFailed -> "Tap card to re-verify exit IP"
                                    else -> "Synchronizing..."
                                }
                            } else {
                                if (host.isNotBlank()) "Port ${port.trim()} • ${proxyType.displayTitle}" else "Open Nodes tab to set server"
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (ipFetchFailed && isProxyActive) palette.marsCoral else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (isProxyActive) {
                        "PID ${activePid ?: "Active"} • Cloudflare & WebRTC Protected"
                    } else {
                        "Tap screen to boost planet orbits • Tap Sun to start"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------
// COMPLETELY REDESIGNED PANORAMIC ORBITAL SPEEDOMETER & LIQUID TELEMETRY
// -------------------------------------------------------------

@Composable
fun PanoramicOrbitalSpeedometer(
    isProxyActive: Boolean,
    currentRxRate: Long,
    currentTxRate: Long,
    peakRxRate: Long,
    peakTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxWaveHistory: List<Long>,
    txWaveHistory: List<Long>
) {
    val palette = LocalSolarPalette.current

    val combinedRate = currentRxRate + currentTxRate
    val combinedFrac = if (isProxyActive) speedToLogFraction(combinedRate, 50L * 1024L * 1024L) else 0f
    val animCombinedFrac by animateFloatAsState(
        targetValue = combinedFrac,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "combinedOrbitalSpeed"
    )

    val rxFrac by animateFloatAsState(
        targetValue = if (isProxyActive) speedToLogFraction(currentRxRate, 50L * 1024L * 1024L) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "rxBarFrac"
    )

    val txFrac by animateFloatAsState(
        targetValue = if (isProxyActive) speedToLogFraction(currentTxRate, 50L * 1024L * 1024L) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "txBarFrac"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "horizonWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 1600 else 4500, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = palette.cardSurface,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (palette.isDark) 0.dp else 4.dp, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Header: Orbital Velocity Title + Total Session Traffic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ORBITAL VELOCITY TELEMETRY",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textSecondary,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = "Session ${formatBytes(totalRxBytes + totalTxBytes)}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.sunCorona
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Clean Telemetry Readout Cards (Download & Upload with Liquid Solar Progress Bars)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // DOWNLOAD CARD
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.cardElevated)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↓ DOWNLOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.earthBlue
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.earthBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = splitSpeedUnit(currentRxRate),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.earthBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = splitSpeedValue(currentRxRate),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Liquid Solar Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(palette.cardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(rxFrac.coerceIn(0.04f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(listOf(palette.earthBlue, palette.neptuneCyan))
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Peak ${formatCompactRate(peakRxRate)} • ${formatBytes(totalRxBytes)}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // UPLOAD CARD
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.cardElevated)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↑ UPLOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.sunCorona
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.sunCorona.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = splitSpeedUnit(currentTxRate),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.sunCorona,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = splitSpeedValue(currentTxRate),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Liquid Solar Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(palette.cardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(txFrac.coerceIn(0.04f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(listOf(palette.sunCore, palette.sunCorona))
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Peak ${formatCompactRate(peakTxRate)} • ${formatBytes(totalTxBytes)}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Panoramic Celestial Horizon Wave & Satellite Speedometer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cardElevated)
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val w = size.width
                    val h = size.height
                    val count = rxWaveHistory.size.coerceAtLeast(2)
                    val maxSample = (rxWaveHistory.maxOrNull() ?: 0L)
                        .coerceAtLeast(txWaveHistory.maxOrNull() ?: 0L)
                        .coerceAtLeast(32 * 1024L)

                    val stepX = w / (count - 1).toFloat()
                    val rxPath = Path()
                    val fillPath = Path()
                    fillPath.moveTo(0f, h)

                    for (i in 0 until count) {
                        val x = i * stepX
                        val norm = speedToLogFraction(rxWaveHistory[i], maxSample)
                        val wave = (sin(wavePhase + i * 0.35f) * if (isProxyActive) 4f else 2f).toFloat()
                        val y = (h * 0.80f) - (norm * h * 0.60f) + wave

                        if (i == 0) {
                            rxPath.moveTo(x, y)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevX = (i - 1) * stepX
                            val midX = (prevX + x) / 2f
                            rxPath.quadraticTo(midX, y, x, y)
                            fillPath.quadraticTo(midX, y, x, y)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                palette.earthBlue.copy(alpha = if (isProxyActive) 0.28f else 0.08f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = rxPath,
                        brush = Brush.horizontalGradient(
                            listOf(palette.earthBlue, palette.sunCore, palette.sunCorona)
                        ),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Gliding Satellite Marker showing instantaneous combined velocity along the horizon
                    val satX = (w * 0.06f) + (w * 0.88f * animCombinedFrac)
                    val satY = (h * 0.75f) - (animCombinedFrac * h * 0.52f)
                    drawCircle(
                        color = palette.sunCore.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = Offset(satX, satY)
                    )
                    drawCircle(
                        color = palette.sunCore,
                        radius = 4.dp.toPx(),
                        center = Offset(satX, satY)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 1: SOLAR NODE CONFIGURATION & URI IMPORT
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarNodeConfigScreen(
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
    val palette = LocalSolarPalette.current
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
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "QUICK URI IMPORT & SOCKET PROBE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.sunCorona
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Import vless://, trojan://, ss://, hy2://, socks5:// or test reachability",
                    fontSize = 10.5.sp,
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
                        border = BorderStroke(1.dp, palette.earthBlue)
                    ) {
                        Text("📋 Paste URI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.earthBlue)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.sunCorona,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (isCheckingAlive) "Probing..." else "🛰️ Probe Socket",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = aliveResult != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        when (val res = aliveResult) {
                            is TestResult.Success -> {
                                Text(
                                    text = "✓ HANDSHAKE VERIFIED (${res.latencyMs} ms)",
                                    color = palette.activeEmerald,
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
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${SlotPlanetNames[activeSlot].uppercase()} NODE (P$activeSlot) PARAMETERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.sunCorona
                    )
                    Text(
                        text = "Reset P$activeSlot",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.marsCoral,
                        modifier = Modifier.clickable { onResetSlot() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
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
                            label = { Text(t.displayTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.sunCorona,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("TRANSPORT & WEBRTC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_AND_UDP,
                        onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                        label = { Text("TCP + UDP (TProxy)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.earthBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_ONLY,
                        onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                        label = { Text("TCP Only", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.earthBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("IP STACK STRATEGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
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
                            label = { Text(lbl, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.sunCorona,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                    label = "solarProtocolFields"
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
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { onPasswordChange(it); onConfigChanged() },
                                        label = { Text("Password") },
                                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            Text(
                                                text = if (passVisible) "Hide" else "Show",
                                                fontSize = 11.sp,
                                                color = palette.earthBlue,
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
                                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Text(
                                            text = if (passVisible) "Hide" else "Show",
                                            fontSize = 11.sp,
                                            color = palette.earthBlue,
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
                                var passVisible by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { onPasswordChange(it); onConfigChanged() },
                                    label = { Text("${targetProtocol.displayTitle} Password") },
                                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        Text(
                                            text = if (passVisible) "Hide" else "Show",
                                            fontSize = 11.sp,
                                            color = palette.earthBlue,
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
// SCREEN 2: MULTI-USER PROFILE ROUTING & BOOT AUTOMATION
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarRoutingScreen(
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
    val palette = LocalSolarPalette.current
    val userId = ProfileManager.androidUserId

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start on System Boot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(
                            text = if (startOnBoot) "Armed for ${SlotPlanetNames[bootSlot]} (P$bootSlot)" else "Manual launch only",
                            fontSize = 10.5.sp,
                            color = if (startOnBoot) palette.activeEmerald else palette.textSecondary
                        )
                    }
                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBootChange
                    )
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                HorizontalDivider(color = palette.cardBorder, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hotspot & USB Tethering Bridge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(
                            text = if (userId == 0) "Route Wi-Fi & RNDIS clients through proxy" else "Available in Owner (User 0) profile",
                            fontSize = 10.5.sp,
                            color = if (routeHotspot && userId == 0) palette.earthBlue else palette.textSecondary
                        )
                    }
                    Switch(
                        checked = routeHotspot && userId == 0,
                        enabled = userId == 0,
                        onCheckedChange = onRouteHotspotChange
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profile #$userId Isolated Tunneling",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (routeWholeProfile) {
                                "All apps in User Profile #$userId (${ProfileManager.uidStart}..${ProfileManager.uidEnd}) routed"
                            } else {
                                "Split Tunneling (${selectedPackages.size} apps in Profile #$userId)"
                            },
                            fontSize = 10.5.sp,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = onRouteWholeProfileChange
                    )
                }

                AnimatedVisibility(
                    visible = !routeWholeProfile,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(8.dp))

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
                            placeholder = { Text("Search apps in User #$userId...") },
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
                                TextButton(onClick = onSelectAll) { Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = onClearAll) { Text("Clear", fontSize = 11.sp, color = palette.marsCoral, fontWeight = FontWeight.Bold) }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.packageName }) { app ->
                                val isChecked = selectedPackages.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isChecked) palette.cardElevated else Color.Transparent)
                                        .clickable { onToggleAppSelection(app.packageName) }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${app.packageName} • UID ${app.uid}", color = palette.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleAppSelection(app.packageName) }
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
// SCREEN 3: SOLAR TELEMETRY LOGS CONSOLE
// -------------------------------------------------------------

@Composable
fun SolarLogsScreen(
    activeSlot: Int,
    runningSlot: Int?,
    currentLogs: String,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val palette = LocalSolarPalette.current
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.cardSurface,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOLAR KERNEL LOGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.sunCorona
                    )
                    Text(
                        text = "User #${ProfileManager.androidUserId} • Node P${runningSlot ?: activeSlot}",
                        fontSize = 10.5.sp,
                        color = palette.textSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", currentLogs))
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.earthBlue)
                    }
                    TextButton(onClick = onClearLogs) {
                        Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.marsCoral)
                    }
                    Button(
                        onClick = onRefreshLogs,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Refresh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cardElevated)
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = currentLogs,
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
}

// -------------------------------------------------------------
// SOLAR OBSERVATORY BOTTOM NAVIGATION DOCK
// -------------------------------------------------------------

@Composable
fun SolarBottomNavigationDock(
    selectedScreen: Int,
    onSelectScreen: (Int) -> Unit,
    splitAppsCount: Int?
) {
    val palette = LocalSolarPalette.current
    val items = listOf(
        Pair("☀️", "Observatory"),
        Pair("🪐", "Nodes"),
        Pair("🛰️", if (splitAppsCount == null) "Routing" else "Split ($splitAppsCount)"),
        Pair("📜", "Logs")
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.cardSurface,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(if (palette.isDark) 0.dp else 4.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { index, (icon, title) ->
                val isSelected = selectedScreen == index

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(listOf(palette.sunCorona, palette.sunCore))
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .clickable { onSelectScreen(index) }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = icon, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// REUSABLE SOLAR BADGES, UPTIME PILL & URI PARSER
// -------------------------------------------------------------

@Composable
fun SolarBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SolarUptimePill(sessionStartMs: Long) {
    val palette = LocalSolarPalette.current
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
        shape = RoundedCornerShape(8.dp),
        color = palette.activeEmerald.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, palette.activeEmerald)
    ) {
        Text(
            text = "⏱ $timeStr",
            fontSize = 10.sp,
            color = palette.activeEmerald,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun speedToLogFraction(bytesPerSec: Long, maxBytesPerSec: Long): Float {
    if (bytesPerSec <= 0L) return 0f
    val num = ln(bytesPerSec.toDouble() + 1.0)
    val den = ln(maxBytesPerSec.toDouble() + 1.0)
    return (num / den).toFloat().coerceIn(0.05f, 1.0f)
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
