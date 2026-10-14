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

// -------------------------------------------------------------
// 3 DISTINCT TACTICAL & STUDIO COLOR THEMES + DYNAMIC ENGINES
// -------------------------------------------------------------

data class AetherTheme(
    val id: Int,
    val name: String,
    val isLight: Boolean,
    val bgPrimary: Color,
    val bgElevated: Color,
    val cardBg: Color,
    val cardHighlight: Color,
    val inputBg: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val statusLive: Color,
    val statusWarn: Color,
    val statusDanger: Color,
    val textMain: Color,
    val textSub: Color,
    val textDim: Color,
    val strokeSubtle: Color
)

// Theme 0: Cyber Acid-Lime & Electric Cyan on Deep Carbon Onyx
private val AcidOnyxTheme = AetherTheme(
    id = 0,
    name = "ACID ONYX",
    isLight = false,
    bgPrimary = Color(0xFF06080E),
    bgElevated = Color(0xFF0D111C),
    cardBg = Color(0xFF101624),
    cardHighlight = Color(0xFF172034),
    inputBg = Color(0xFF0B0F1A),
    accentPrimary = Color(0xFFB8FF01),   // Neon Acid Lime
    accentSecondary = Color(0xFF00F0FF), // Electric Cyan
    accentTertiary = Color(0xFF7000FF),  // Hyper Violet
    statusLive = Color(0xFFB8FF01),
    statusWarn = Color(0xFFFFB800),
    statusDanger = Color(0xFFFF2E54),
    textMain = Color(0xFFF8FAFC),
    textSub = Color(0xFF94A3B8),
    textDim = Color(0xFF526077),
    strokeSubtle = Color(0xFF1E293B)
)

// Theme 1: Solar Titanium & Molten Orange on Obsidian Carbon
private val SolarTitaniumTheme = AetherTheme(
    id = 1,
    name = "SOLAR TITAN",
    isLight = false,
    bgPrimary = Color(0xFF0B0908),
    bgElevated = Color(0xFF15110F),
    cardBg = Color(0xFF1A1412),
    cardHighlight = Color(0xFF261D1A),
    inputBg = Color(0xFF110D0C),
    accentPrimary = Color(0xFFFF9E00),   // Molten Gold
    accentSecondary = Color(0xFFFF3D00), // Laser Orange-Red
    accentTertiary = Color(0xFF00E5FF),  // Cyan Contrast
    statusLive = Color(0xFF00E676),
    statusWarn = Color(0xFFFFAB00),
    statusDanger = Color(0xFFFF1744),
    textMain = Color(0xFFFFF8F5),
    textSub = Color(0xFFBFA89E),
    textDim = Color(0xFF7A655C),
    strokeSubtle = Color(0xFF2E221E)
)

// Theme 2: Frost Alabaster & Sapphire Cobalt (Crisp Daylight Mode)
private val FrostCobaltTheme = AetherTheme(
    id = 2,
    name = "FROST COBALT",
    isLight = true,
    bgPrimary = Color(0xFFF2F5FA),
    bgElevated = Color(0xFFE6ECF5),
    cardBg = Color(0xFFFFFFFF),
    cardHighlight = Color(0xFFEEF4FF),
    inputBg = Color(0xFFF7F9FC),
    accentPrimary = Color(0xFF0052FF),   // Deep Cobalt Blue
    accentSecondary = Color(0xFF00B87C), // Emerald Mint
    accentTertiary = Color(0xFF7C3AED),  // Royal Violet
    statusLive = Color(0xFF00A86B),
    statusWarn = Color(0xFFD97706),
    statusDanger = Color(0xFFE11D48),
    textMain = Color(0xFF0F172A),
    textSub = Color(0xFF475569),
    textDim = Color(0xFF94A3B8),
    strokeSubtle = Color(0xFFDCE4F0)
)

private val AllThemes = listOf(AcidOnyxTheme, SolarTitaniumTheme, FrostCobaltTheme)
val LocalAetherTheme = compositionLocalOf { AcidOnyxTheme }

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
            var themeIndex by remember {
                mutableIntStateOf(globalPrefs.getInt("aether_theme_idx", 0).coerceIn(0, AllThemes.lastIndex))
            }
            val theme = AllThemes[themeIndex]

            val colorScheme = if (theme.isLight) {
                lightColorScheme(
                    primary = theme.accentPrimary,
                    secondary = theme.accentSecondary,
                    background = theme.bgPrimary,
                    surface = theme.cardBg,
                    onPrimary = Color.White,
                    onSurface = theme.textMain,
                    error = theme.statusDanger
                )
            } else {
                darkColorScheme(
                    primary = theme.accentPrimary,
                    secondary = theme.accentSecondary,
                    background = theme.bgPrimary,
                    surface = theme.cardBg,
                    onPrimary = Color(0xFF06080E),
                    onSurface = theme.textMain,
                    error = theme.statusDanger
                )
            }

            CompositionLocalProvider(LocalAetherTheme provides theme) {
                MaterialTheme(colorScheme = colorScheme) {
                    MainScreen(
                        rootState = rootState,
                        rootLabel = rootLabel,
                        isProxyActive = isProxyRunningState,
                        isTransitioning = isTransitioningState,
                        runningSlot = runningSlotState,
                        activePid = activePidState,
                        onCycleTheme = {
                            themeIndex = (themeIndex + 1) % AllThemes.size
                            globalPrefs.edit().putInt("aether_theme_idx", themeIndex).apply()
                        },
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
            rootLabel = "WAIT"
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
    onCycleTheme: () -> Unit,
    onRecheckRoot: () -> Unit,
    installedApps: List<AppItem>,
    onSyncStatus: () -> Unit,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val theme = LocalAetherTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    // 4 Dedicated Screens via Floating Bottom Dock: 0 = Cockpit, 1 = Node Config, 2 = Split Tunnel, 3 = Live Logs
    var selectedScreen by remember { mutableIntStateOf(0) }

    // Hoisted Live Telemetry & Spectrum History
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var rxSpectrum by remember { mutableStateOf(List(24) { 0L }) }
    var txSpectrum by remember { mutableStateOf(List(24) { 0L }) }
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
                delay(650) // Faster 650ms sampling for snappy gauge & spectrum motion
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

                rxSpectrum = rxSpectrum.drop(1) + rxRate
                txSpectrum = txSpectrum.drop(1) + txRate

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
            rxSpectrum = List(24) { 0L }
            txSpectrum = List(24) { 0L }
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

    val animatedBg by animateColorAsState(
        targetValue = theme.bgPrimary,
        animationSpec = tween(450),
        label = "aetherBg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBg)
    ) {
        // 1. MULTI-MODE DYNAMIC GEOMETRIC & RADAR BACKGROUND
        AetherDynamicBackdrop(
            themeId = theme.id,
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning || isTesting
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. SPACIOUS COLLISION-FREE TOP BAR + HORIZONTAL NODE CAROUSEL
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AetherTopCommandBar(
                    isProxyActive = isProxyActive,
                    runningSlot = runningSlot,
                    activeSlot = activeSlot,
                    rootState = rootState,
                    onCycleTheme = onCycleTheme,
                    onRecheckRoot = onRecheckRoot
                )

                Spacer(modifier = Modifier.height(10.dp))

                AetherNodeCarousel(
                    activeSlot = activeSlot,
                    runningSlot = runningSlot,
                    isProxyActive = isProxyActive,
                    startOnBoot = startOnBoot,
                    bootSlot = bootSlot,
                    slotProtocols = slotProtocols,
                    onSelectSlot = { switchSlot(it) }
                )
            }

            // 3. MAIN VIEWPORT (4 DEDICATED SCREENS)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = selectedScreen,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { fullHeight -> if (forward) fullHeight / 6 else -fullHeight / 6 }
                        ) + fadeIn(tween(220))).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(160),
                                targetOffsetY = { fullHeight -> if (forward) -fullHeight / 8 else fullHeight / 8 }
                            ) + fadeOut(tween(150))
                        )
                    },
                    label = "screenTransition"
                ) { screenIdx ->
                    when (screenIdx) {
                        0 -> {
                            AetherCockpitScreen(
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
                                peakTxRate = peakTxRate,
                                totalRxBytes = totalRxBytes,
                                totalTxBytes = totalTxBytes,
                                rxSpectrum = rxSpectrum,
                                txSpectrum = txSpectrum,
                                onRefreshIp = { triggerPublicIpCheck() },
                                onToggleProxy = {
                                    if (isProxyActive && runningSlot == activeSlot) {
                                        onStopProxy { }
                                    } else {
                                        if (host.trim().isEmpty()) {
                                            testStatus = "Enter Server Host/IP in Nodes tab first"
                                            testLatencyMs = null
                                            selectedScreen = 1
                                            return@AetherCockpitScreen
                                        }
                                        saveConfigForSlot(activeSlot, immediateRootSync = true)
                                        val settings = getCurrentSettings()
                                        val targets = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
                                        onStartProxy(settings, targets) { success, error ->
                                            if (!success) {
                                                testStatus = "Error: ${error?.lines()?.lastOrNull() ?: "See Terminal tab"}"
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
                                        testStatus = "Configure Server Host/IP first"
                                        testLatencyMs = null
                                        selectedScreen = 1
                                        return@AetherCockpitScreen
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
                                                testStatus = "ONLINE • ${res.latencyMs} ms RTT"
                                            }
                                            is TestResult.Failure -> {
                                                testLatencyMs = null
                                                testStatus = "FAILED • ${res.error}"
                                            }
                                        }
                                        isTesting = false
                                    }
                                }
                            )
                        }

                        1 -> {
                            AetherNodeConfigScreen(
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
                                    refreshSlotMetadata()
                                    coroutineScope.launch(Dispatchers.IO) {
                                        PersistentStorage.clearBackup(targetSlot, ProfileManager.androidUserId)
                                    }
                                    Toast.makeText(context, "Node P$targetSlot cleared", Toast.LENGTH_SHORT).show()
                                },
                                getCurrentSettings = { getCurrentSettings() }
                            )
                        }

                        2 -> {
                            AetherRoutingScreen(
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
                                    Toast.makeText(context, "Node P$s set as Boot Target", Toast.LENGTH_SHORT).show()
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

                        3 -> {
                            AetherTerminalScreen(
                                activeSlot = activeSlot,
                                runningSlot = runningSlot,
                                currentLogs = currentLogs,
                                onRefreshLogs = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val targetSlot = runningSlot ?: activeSlot
                                        val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.androidUserId, targetSlot)
                                        withContext(Dispatchers.Main) {
                                            currentLogs = logs.ifEmpty { "No logs recorded." }
                                        }
                                    }
                                },
                                onClearLogs = {
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

            // 4. FLOATING BOTTOM GLASS NAVIGATION DOCK
            AetherBottomDock(
                selectedScreen = selectedScreen,
                onSelectScreen = { selectedScreen = it },
                splitAppsCount = if (routeWholeProfile) null else selectedPackages.size
            )
        }
    }
}

// -------------------------------------------------------------
// MULTI-MODE DYNAMIC BACKGROUND (RADAR SWEEP / HEXAGON PULSE)
// -------------------------------------------------------------

@Composable
fun AetherDynamicBackdrop(
    themeId: Int,
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    val theme = LocalAetherTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "aetherBackdrop")

    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 6000 else 16000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "radarSweep"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 4000 else 10000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val glowColor by animateColorAsState(
        targetValue = when {
            isTransitioning -> theme.statusWarn
            isProxyActive -> theme.statusLive
            else -> theme.accentSecondary
        },
        animationSpec = tween(600),
        label = "backdropGlow"
    )

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height

        // Top-Right & Bottom-Left Ambient Glow Fields
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = if (theme.isLight) 0.14f else 0.18f),
                    Color.Transparent
                ),
                center = Offset(w * 0.85f, h * 0.16f),
                radius = w * 0.75f
            ),
            center = Offset(w * 0.85f, h * 0.16f),
            radius = w * 0.75f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.accentTertiary.copy(alpha = if (theme.isLight) 0.10f else 0.15f),
                    Color.Transparent
                ),
                center = Offset(w * 0.15f, h * 0.78f),
                radius = w * 0.70f
            ),
            center = Offset(w * 0.15f, h * 0.78f),
            radius = w * 0.70f
        )

        when (themeId) {
            0 -> {
                // Mode 0: Tactical Radar Concentric Rings + Scanning Sweep
                val radarCenter = Offset(w * 0.5f, h * 0.32f)
                for (rIdx in 1..4) {
                    val radius = (w * 0.16f) * rIdx
                    drawCircle(
                        color = glowColor.copy(alpha = 0.05f),
                        radius = radius,
                        center = radarCenter,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                rotate(degrees = radarSweep, pivot = radarCenter) {
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(glowColor.copy(alpha = 0.22f), Color.Transparent),
                            start = radarCenter,
                            end = Offset(radarCenter.x + w * 0.55f, radarCenter.y)
                        ),
                        start = radarCenter,
                        end = Offset(radarCenter.x + w * 0.55f, radarCenter.y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            1 -> {
                // Mode 1: Solar Horizon Perspective Laser Lines
                val lines = 12
                for (i in 0 until lines) {
                    val y = (h * 0.1f) + (i * (h * 0.075f)) + (sin(wavePhase + i * 0.5f) * 8f).toFloat()
                    drawLine(
                        color = glowColor.copy(alpha = 0.055f),
                        start = Offset(0f, y),
                        end = Offset(w, y - 24f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
            }
            else -> {
                // Mode 2: Precision Isometric Dot Matrix
                val step = 36.dp.toPx()
                var x = step / 2f
                while (x < w) {
                    var y = step / 2f
                    while (y < h) {
                        drawCircle(
                            color = theme.accentPrimary.copy(alpha = 0.08f),
                            radius = 1.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        y += step
                    }
                    x += step
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SPACIOUS TOP COMMAND BAR (ZERO TEXT COLLISIONS)
// -------------------------------------------------------------

@Composable
fun AetherTopCommandBar(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    onCycleTheme: () -> Unit,
    onRecheckRoot: () -> Unit
) {
    val theme = LocalAetherTheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand & Subtitle Stack
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isProxyActive) theme.statusLive else theme.accentPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NAMELESS // CORE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isProxyActive) {
                    "TUNNEL ACTIVE • NODE P${runningSlot ?: activeSlot} • PORT ${ProfileManager.localInboundPort}"
                } else {
                    "KERNEL STANDBY • USER ${ProfileManager.androidUserId} • ${theme.name}"
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isProxyActive) theme.statusLive else theme.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Pills (Theme Switcher + Root Status)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.cardBg,
                border = BorderStroke(1.dp, theme.strokeSubtle),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCycleTheme() }
            ) {
                Text(
                    text = "🎨 ${theme.name.substringBefore(" ")}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.accentPrimary,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }

            val rootColor = when (rootState) {
                RootState.GRANTED -> theme.statusLive
                RootState.DENIED -> theme.statusDanger
                RootState.CHECKING -> theme.statusWarn
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.cardBg,
                border = BorderStroke(1.dp, rootColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onRecheckRoot() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(rootColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when (rootState) {
                            RootState.GRANTED -> "SU"
                            RootState.DENIED -> "NO SU"
                            RootState.CHECKING -> "..."
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.textMain
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NODE SLOT CAROUSEL (P0 - P4 WITH PROTOCOL TAGS)
// -------------------------------------------------------------

@Composable
fun AetherNodeCarousel(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    slotProtocols: Map<Int, String>,
    onSelectSlot: (Int) -> Unit
) {
    val theme = LocalAetherTheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        (0 until ProfileManager.MAX_SLOTS).forEach { slotIdx ->
            val isSelected = slotIdx == activeSlot
            val isRunning = isProxyActive && (runningSlot == slotIdx)
            val isBoot = startOnBoot && (bootSlot == slotIdx)
            val proto = slotProtocols[slotIdx]

            val bg by animateColorAsState(
                targetValue = when {
                    isRunning -> theme.statusLive.copy(alpha = 0.16f)
                    isSelected -> theme.accentPrimary.copy(alpha = if (theme.isLight) 0.14f else 0.20f)
                    else -> theme.cardBg
                },
                animationSpec = tween(220),
                label = "nodeBg"
            )

            val borderCol by animateColorAsState(
                targetValue = when {
                    isRunning -> theme.statusLive
                    isSelected -> theme.accentPrimary
                    proto != null -> theme.strokeSubtle
                    else -> theme.strokeSubtle.copy(alpha = 0.5f)
                },
                animationSpec = tween(220),
                label = "nodeBorder"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(if (isSelected || isRunning) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(12.dp))
                    .clickable { onSelectSlot(slotIdx) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NODE $slotIdx",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        isRunning -> theme.statusLive
                        isSelected -> theme.accentPrimary
                        proto != null -> theme.textMain
                        else -> theme.textDim
                    },
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isRunning -> "ONLINE"
                        isBoot -> "BOOT"
                        proto != null -> proto.take(6)
                        else -> "EMPTY"
                    },
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isRunning -> theme.statusLive
                        isBoot -> theme.statusWarn
                        proto != null -> theme.textSub
                        else -> theme.textDim
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------
// FLOATING BOTTOM GLASS NAVIGATION DOCK
// -------------------------------------------------------------

@Composable
fun AetherBottomDock(
    selectedScreen: Int,
    onSelectScreen: (Int) -> Unit,
    splitAppsCount: Int?
) {
    val theme = LocalAetherTheme.current
    val items = listOf(
        Pair("⚡", "Cockpit"),
        Pair("🛰️", "Nodes"),
        Pair("🛡️", if (splitAppsCount == null) "Routing" else "Split ($splitAppsCount)"),
        Pair("📟", "Terminal")
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = theme.cardBg,
        border = BorderStroke(1.dp, theme.strokeSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { index, (icon, title) ->
                val isSelected = selectedScreen == index
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) theme.accentPrimary else Color.Transparent,
                    animationSpec = tween(220),
                    label = "dockPillBg"
                )
                val contentCol = if (isSelected) {
                    if (theme.isLight) Color.White else Color(0xFF06080E)
                } else {
                    theme.textSub
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(pillBg)
                        .clickable { onSelectScreen(index) }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = icon, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        color = contentCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 0: TACTICAL COCKPIT (BENTO DECK + TWIN SPEEDOMETERS)
// -------------------------------------------------------------

@Composable
fun AetherCockpitScreen(
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
    peakTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxSpectrum: List<Long>,
    txSpectrum: List<Long>,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    testLatencyMs: Long?,
    onTestUpstream: () -> Unit
) {
    val theme = LocalAetherTheme.current
    val isCurrentSlotRunning = isProxyActive && (runningSlot == activeSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. SPLIT BENTO HERO DECK (LEFT: HEXAGON POWER CORE | RIGHT: EXIT PASSPORT & TELEMETRY)
        AetherBentoHeroDeck(
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

        // 2. TWIN-TURBINE 240° RADIAL SPEEDOMETER GAUGES + 24-BAR SPECTRUM ANALYZER
        AetherTwinSpeedometerCluster(
            isProxyActive = isProxyActive,
            currentRxRate = currentRxRate,
            currentTxRate = currentTxRate,
            peakRxRate = peakRxRate,
            peakTxRate = peakTxRate,
            totalRxBytes = totalRxBytes,
            totalTxBytes = totalTxBytes,
            rxSpectrum = rxSpectrum,
            txSpectrum = txSpectrum
        )

        // 3. PIPELINE TELEMETRY CHIPS + PING ACTION
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.cardBg,
            border = BorderStroke(1.dp, theme.strokeSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AetherChip(label = proxyType.displayTitle, color = theme.accentPrimary)
                    AetherChip(
                        label = if (transportMode == TransportMode.TCP_AND_UDP) "TPROXY TCP+UDP" else "TCP ONLY",
                        color = theme.accentSecondary
                    )
                    AetherChip(
                        label = "DoT 853 + DNS 53 TUNNELED",
                        color = theme.statusLive
                    )
                    AetherChip(
                        label = when (ipMode) {
                            IpMode.IPV4_ONLY -> "IPv4 ONLY"
                            IpMode.DUAL_STACK -> "DUAL-STACK"
                            IpMode.IPV6_ONLY -> "IPv6 ONLY"
                        },
                        color = theme.textSub
                    )
                    if (routeHotspot) {
                        AetherChip(label = "HOTSPOT READY", color = theme.accentPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onTestUpstream,
                        enabled = !isTesting && !isTransitioning,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.accentSecondary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = if (isTesting) "Probing Socket..." else "⚡ Test Node P$activeSlot Latency",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.accentSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AnimatedVisibility(
                    visible = testStatus != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val statusColor = when {
                        isTesting -> theme.statusWarn
                        testLatencyMs != null -> theme.statusLive
                        else -> theme.statusDanger
                    }
                    Text(
                        text = testStatus ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 4. MASTER IGNITION TRIGGER BAR
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "ignitionScale"
        )

        val btnBg = when {
            isTransitioning -> theme.statusWarn
            isCurrentSlotRunning -> theme.statusDanger
            else -> theme.accentPrimary
        }

        val btnTextCol = when {
            isCurrentSlotRunning -> Color.White
            theme.isLight -> Color.White
            else -> Color(0xFF06080E)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(btnBg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isTransitioning
                ) { onToggleProxy() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    isTransitioning -> "APPLYING KERNEL NETFILTER..."
                    !isProxyActive -> "IGNITE KERNEL TUNNEL • NODE P$activeSlot"
                    isCurrentSlotRunning -> "DISENGAGE TUNNEL • STOP P$activeSlot"
                    else -> "SWITCH TO NODE P$activeSlot & IGNITE"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = btnTextCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// SPLIT BENTO HERO DECK (HEXAGON POWER CORE + EXIT PASSPORT)
// -------------------------------------------------------------

@Composable
fun AetherBentoHeroDeck(
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
    val theme = LocalAetherTheme.current
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "hexCore")
    val hexAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    isTransitioning -> 1600
                    isProxyActive -> 5000
                    else -> 16000
                },
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "hexAngle"
    )

    val sonarRipple by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "sonarRipple"
    )

    val coreColor by animateColorAsState(
        targetValue = when {
            isTransitioning -> theme.statusWarn
            isProxyActive -> theme.statusLive
            else -> theme.accentSecondary
        },
        animationSpec = tween(400),
        label = "coreColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // LEFT BENTO: INTERACTIVE HEXAGON SONAR POWER CORE
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = theme.cardBg,
            border = BorderStroke(1.2.dp, if (isProxyActive) coreColor.copy(alpha = 0.65f) else theme.strokeSubtle),
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .clickable(enabled = !isTransitioning) { onToggleProxy() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isProxyActive) "CORE ONLINE" else "TAP TO START",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    color = coreColor,
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f

                        // Expanding Sonar Shockwave Ring when Active
                        if (isProxyActive || isTransitioning) {
                            drawCircle(
                                color = coreColor.copy(alpha = (1f - sonarRipple) * 0.55f),
                                radius = r * sonarRipple,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Rotating Tactical Hexagon
                        rotate(degrees = hexAngle, pivot = Offset(cx, cy)) {
                            val hexPath = Path()
                            for (i in 0 until 6) {
                                val deg = i * 60.0
                                val rad = Math.toRadians(deg)
                                val px = cx + (r * 0.78f * cos(rad)).toFloat()
                                val py = cy + (r * 0.78f * sin(rad)).toFloat()
                                if (i == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
                            }
                            hexPath.close()
                            drawPath(
                                path = hexPath,
                                color = coreColor,
                                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Counter-Rotating Inner Triangle/Hex Core
                        rotate(degrees = -hexAngle * 1.4f, pivot = Offset(cx, cy)) {
                            drawCircle(
                                color = coreColor.copy(alpha = 0.18f),
                                radius = r * 0.48f,
                                center = Offset(cx, cy)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isProxyActive) "P${runningSlot ?: activeSlot}" else "P$activeSlot",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = theme.textMain
                        )
                        Text(
                            text = if (isProxyActive) "LIVE" else "IDLE",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = coreColor
                        )
                    }
                }

                // Bottom Uptime or Protocol Label (Stacked cleanly, zero collision)
                if (isProxyActive && sessionStartMs > 0L) {
                    AetherUptimePill(sessionStartMs = sessionStartMs)
                } else {
                    Text(
                        text = proxyType.displayTitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSub,
                        maxLines = 1
                    )
                }
            }
        }

        // RIGHT BENTO STACK: EXIT NODE PASSPORT + KERNEL PID / DNS STATUS
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Right Card: Exit IP & Geo Location Passport
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = theme.cardBg,
                border = BorderStroke(1.dp, theme.strokeSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
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
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isProxyActive) "EXIT NODE IP" else "TARGET ENDPOINT",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            color = theme.textSub
                        )
                        if (isProxyActive) {
                            Text(
                                text = if (isFetchingIp) "..." else "↻ Verify",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = theme.accentSecondary,
                                modifier = Modifier.clickable { onRefreshIp() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "🛰️",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProxyActive) {
                                when {
                                    isFetchingIp -> "Checking..."
                                    publicIpInfo != null -> publicIpInfo.ip
                                    ipFetchFailed -> "Tap Verify"
                                    else -> "Resolving..."
                                }
                            } else {
                                if (host.isNotBlank()) host.trim() else "Not Configured"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = theme.textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isProxyActive) {
                            when {
                                isFetchingIp -> "Querying through tunnel..."
                                publicIpInfo != null -> publicIpInfo.country
                                ipFetchFailed -> "Timeout • Tap to retry"
                                else -> "Synchronizing..."
                            }
                        } else {
                            if (host.isNotBlank()) "Port ${port.trim()} • ${proxyType.displayTitle}" else "Open Nodes tab to configure"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ipFetchFailed && isProxyActive) theme.statusDanger else theme.accentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom Right Card: Private DNS Shield & Kernel PID Status
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.cardHighlight,
                border = BorderStroke(1.dp, theme.strokeSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = if (isProxyActive) "PRIVATE DNS (DoT 853) TUNNELED" else "DNS HIJACK ENGINE READY",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isProxyActive) theme.statusLive else theme.textSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isProxyActive) {
                            "PID ${activePid ?: "Active"} • Cloudflare / Google DoT Ready"
                        } else {
                            "Supports Cloudflare, Google & Custom DoT"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TWIN-TURBINE 240° RADIAL SPEEDOMETER GAUGES + 24-BAR SPECTRUM
// -------------------------------------------------------------

@Composable
fun AetherTwinSpeedometerCluster(
    isProxyActive: Boolean,
    currentRxRate: Long,
    currentTxRate: Long,
    peakRxRate: Long,
    peakTxRate: Long,
    totalRxBytes: Long,
    totalTxBytes: Long,
    rxSpectrum: List<Long>,
    txSpectrum: List<Long>
) {
    val theme = LocalAetherTheme.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = theme.cardBg,
        border = BorderStroke(1.dp, theme.strokeSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Twin Radial Gauges Row (Downlink Left | Uplink Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AetherRadialSpeedGauge(
                    title = "DOWNLINK",
                    bytesPerSec = currentRxRate,
                    peakBytesPerSec = peakRxRate,
                    totalBytes = totalRxBytes,
                    gaugeColor = theme.accentPrimary,
                    isProxyActive = isProxyActive,
                    modifier = Modifier.weight(1f)
                )

                AetherRadialSpeedGauge(
                    title = "UPLINK",
                    bytesPerSec = currentTxRate,
                    peakBytesPerSec = peakTxRate,
                    totalBytes = totalTxBytes,
                    gaugeColor = theme.accentSecondary,
                    isProxyActive = isProxyActive,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 24-Bar Vertical LED Packet Spectrum Equalizer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.inputBg)
                    .border(1.dp, theme.strokeSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE PACKET SPECTRUM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.textSub
                    )
                    Text(
                        text = "TOTAL ${formatBytes(totalRxBytes + totalTxBytes)}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accentPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .graphicsLayer()
                ) {
                    val barCount = rxSpectrum.size.coerceAtLeast(1)
                    val maxVal = (rxSpectrum.maxOrNull() ?: 0L)
                        .coerceAtLeast(txSpectrum.maxOrNull() ?: 0L)
                        .coerceAtLeast(32 * 1024L)

                    val spacing = 4.dp.toPx()
                    val totalSpacing = spacing * (barCount - 1)
                    val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2f)

                    for (i in 0 until barCount) {
                        val rxNorm = speedToLogFraction(rxSpectrum[i], maxVal)
                        val txNorm = speedToLogFraction(txSpectrum[i], maxVal)
                        val combined = (rxNorm * 0.7f + txNorm * 0.3f).coerceIn(0.08f, 1f)
                        val barH = if (isProxyActive) size.height * combined else size.height * 0.10f
                        val x = i * (barWidth + spacing)
                        val y = size.height - barH

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = if (isProxyActive) {
                                    listOf(theme.accentPrimary, theme.accentSecondary)
                                } else {
                                    listOf(theme.textDim.copy(alpha = 0.35f), theme.textDim.copy(alpha = 0.15f))
                                }
                            ),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AetherRadialSpeedGauge(
    title: String,
    bytesPerSec: Long,
    peakBytesPerSec: Long,
    totalBytes: Long,
    gaugeColor: Color,
    isProxyActive: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = LocalAetherTheme.current
    // Logarithmic gauge fraction (0.0 .. 1.0) so both KB/s and MB/s animate smoothly
    val targetFraction = if (isProxyActive) speedToLogFraction(bytesPerSec, 50L * 1024L * 1024L) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "gaugeArcFraction"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(theme.inputBg)
            .border(1.dp, theme.strokeSubtle, RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = gaugeColor,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 240° Sweeping Speedometer Dial
        Box(
            modifier = Modifier.size(92.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val strokeW = 7.dp.toPx()
                val pad = strokeW / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                val topLeft = Offset(pad, pad)

                // Background Track Arc (150° to +240°)
                drawArc(
                    color = theme.strokeSubtle,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Active Speed Sweep Arc
                val sweep = (240f * animatedFraction).coerceIn(2f, 240f)
                if (isProxyActive && bytesPerSec > 0) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(gaugeColor.copy(alpha = 0.5f), gaugeColor, theme.accentSecondary)
                        ),
                        startAngle = 150f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                // Outer Scale Tick Marks
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.width / 2f - 1.dp.toPx()
                for (t in 0..8) {
                    val deg = 150.0 + (t * 30.0)
                    val rad = Math.toRadians(deg)
                    val sx = cx + ((outerR - 3.dp.toPx()) * cos(rad)).toFloat()
                    val sy = cy + ((outerR - 3.dp.toPx()) * sin(rad)).toFloat()
                    val ex = cx + (outerR * cos(rad)).toFloat()
                    val ey = cy + (outerR * sin(rad)).toFloat()
                    drawLine(
                        color = if (t / 8f <= animatedFraction && isProxyActive) gaugeColor else theme.textDim.copy(alpha = 0.4f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Center Digital Speed Readout (Stacked vertically so unit never collides)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = splitSpeedValue(bytesPerSec),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textMain,
                    maxLines = 1
                )
                Text(
                    text = splitSpeedUnit(bytesPerSec),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Clean Stacked Footer (No Side-by-Side Collisions!)
        Text(
            text = "Peak: ${formatCompactRate(peakBytesPerSec)}",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.textSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Total: ${formatBytes(totalBytes)}",
            fontSize = 9.sp,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SCREEN 1: NODE CONFIGURATION & URI IMPORT
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherNodeConfigScreen(
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
    val theme = LocalAetherTheme.current
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
        // 1. SPACIOUS STACKED QUICK IMPORT & SOCKET PROBE CARD (NO COLLISION)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.cardBg,
            border = BorderStroke(1.dp, theme.strokeSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "QUICK IMPORT & SOCKET DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.accentPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Import vless://, trojan://, ss://, hy2://, socks5:// or test node reachability",
                    fontSize = 10.5.sp,
                    color = theme.textSub
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
                                Toast.makeText(context, "Clipboard does not contain a supported proxy URI", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.accentSecondary.copy(alpha = 0.6f))
                    ) {
                        Text("📋 Paste URI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.accentSecondary)
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
                            containerColor = theme.accentPrimary,
                            contentColor = if (theme.isLight) Color.White else Color(0xFF06080E)
                        )
                    ) {
                        Text(
                            text = if (isCheckingAlive) "Probing..." else "⚡ Probe Socket",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
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
                                    color = theme.statusLive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            is TestResult.Failure -> {
                                Text(
                                    text = "✕ UNREACHABLE: ${res.error}",
                                    color = theme.statusDanger,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            null -> {}
                        }
                    }
                }
            }
        }

        // 2. NODE PARAMETERS CARD
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.cardBg,
            border = BorderStroke(1.dp, theme.strokeSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NODE P$activeSlot PARAMETERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.accentPrimary
                    )
                    Text(
                        text = "Reset Node",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.statusDanger,
                        modifier = Modifier.clickable { onResetSlot() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSub)
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
                                selectedContainerColor = theme.accentPrimary,
                                selectedLabelColor = if (theme.isLight) Color.White else Color(0xFF06080E)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("TRANSPORT & WEBRTC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSub)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_AND_UDP,
                        onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                        label = { Text("TCP + UDP (TProxy)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.accentPrimary,
                            selectedLabelColor = if (theme.isLight) Color.White else Color(0xFF06080E)
                        )
                    )
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_ONLY,
                        onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                        label = { Text("TCP Only", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.accentPrimary,
                            selectedLabelColor = if (theme.isLight) Color.White else Color(0xFF06080E)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("IP STACK STRATEGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSub)
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
                                selectedContainerColor = theme.accentPrimary,
                                selectedLabelColor = if (theme.isLight) Color.White else Color(0xFF06080E)
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
                    label = "protocolFields"
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
                                                color = theme.accentPrimary,
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
                                            color = theme.accentPrimary,
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
                                            color = theme.accentPrimary,
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
// SCREEN 2: SPLIT TUNNELING & KERNEL BOOT AUTOMATION
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherRoutingScreen(
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
    val theme = LocalAetherTheme.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.cardBg,
            border = BorderStroke(1.dp, theme.strokeSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start on System Boot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                        Text(
                            text = if (startOnBoot) "Armed for Node P$bootSlot (/data/adb/service.d)" else "Manual launch only",
                            fontSize = 10.5.sp,
                            color = if (startOnBoot) theme.statusLive else theme.textSub
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

                HorizontalDivider(color = theme.strokeSubtle, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hotspot & USB Tethering Bridge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                        Text(
                            text = "Route connected Wi-Fi & RNDIS clients through proxy",
                            fontSize = 10.5.sp,
                            color = if (routeHotspot) theme.accentSecondary else theme.textSub
                        )
                    }
                    Switch(
                        checked = routeHotspot,
                        onCheckedChange = onRouteHotspotChange
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.cardBg,
            border = BorderStroke(1.dp, theme.strokeSubtle),
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
                        Text("Global Profile Tunneling", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                        Text(
                            text = if (routeWholeProfile) "All apps in User ${ProfileManager.androidUserId} routed" else "Split Tunneling (${selectedPackages.size} apps active)",
                            fontSize = 10.5.sp,
                            color = theme.textSub
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
                            Text("${selectedPackages.size} selected", fontSize = 10.5.sp, color = theme.textSub)
                            Row {
                                TextButton(onClick = onSelectAll) { Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = onClearAll) { Text("Clear", fontSize = 11.sp, color = theme.statusDanger, fontWeight = FontWeight.Bold) }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.packageName }) { app ->
                                val isChecked = selectedPackages.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isChecked) theme.cardHighlight else Color.Transparent)
                                        .clickable { onToggleAppSelection(app.packageName) }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, color = theme.textMain, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${app.packageName} • UID ${app.uid}", color = theme.textSub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
// SCREEN 3: FULL-SCREEN KERNEL TERMINAL & TELEMETRY LOGS
// -------------------------------------------------------------

@Composable
fun AetherTerminalScreen(
    activeSlot: Int,
    runningSlot: Int?,
    currentLogs: String,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val theme = LocalAetherTheme.current
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = theme.cardBg,
        border = BorderStroke(1.dp, theme.strokeSubtle),
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
                        text = "KERNEL TELEMETRY CONSOLE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.accentPrimary
                    )
                    Text(
                        text = "Slot P${runningSlot ?: activeSlot} • sing-box & netfilter stream",
                        fontSize = 10.5.sp,
                        color = theme.textSub
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", currentLogs))
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.accentSecondary)
                    }
                    TextButton(onClick = onClearLogs) {
                        Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.statusDanger)
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
                    .background(theme.inputBg)
                    .border(1.dp, theme.strokeSubtle, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = currentLogs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = theme.textMain,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// -------------------------------------------------------------
// HELPERS, CHIPS, UPTIME PILL & URI PARSER
// -------------------------------------------------------------

@Composable
fun AetherChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
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
fun AetherUptimePill(sessionStartMs: Long) {
    val theme = LocalAetherTheme.current
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
        color = theme.statusLive.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, theme.statusLive.copy(alpha = 0.5f))
    ) {
        Text(
            text = "⏱ $timeStr",
            fontSize = 10.sp,
            color = theme.statusLive,
            fontWeight = FontWeight.Black,
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
    return (num / den).toFloat().coerceIn(0.04f, 1.0f)
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
