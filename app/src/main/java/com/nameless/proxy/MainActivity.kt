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
// 6 VIBRANT MULTI-COLOR CHROMA THEMES
// -------------------------------------------------------------

data class ChromaTheme(
    val id: Int,
    val name: String,
    val isLight: Boolean,
    val bgDeep: Color,
    val cardGlass: Color,
    val cardInner: Color,
    val neonPrimary: Color,
    val neonSecondary: Color,
    val neonTertiary: Color,
    val statusOnline: Color,
    val statusWarning: Color,
    val statusAlert: Color,
    val textBright: Color,
    val textSoft: Color,
    val textMuted: Color,
    val borderBase: Color
)

private val CyberpunkTheme = ChromaTheme(
    id = 0,
    name = "CYBERPUNK",
    isLight = false,
    bgDeep = Color(0xFF090414),
    cardGlass = Color(0xFF150B28),
    cardInner = Color(0xFF1F113B),
    neonPrimary = Color(0xFFFF007F),   // Hot Neon Magenta
    neonSecondary = Color(0xFF00F0FF), // Electric Cyan
    neonTertiary = Color(0xFF9D4EDD),  // Hyper Violet
    statusOnline = Color(0xFF00F5D4),
    statusWarning = Color(0xFFFFBE0B),
    statusAlert = Color(0xFFFF0055),
    textBright = Color(0xFFFFFFFF),
    textSoft = Color(0xFFD8B4F8),
    textMuted = Color(0xFF8B6FA8),
    borderBase = Color(0xFF381E63)
)

private val SupernovaTheme = ChromaTheme(
    id = 1,
    name = "SUPERNOVA",
    isLight = false,
    bgDeep = Color(0xFF0E050B),
    cardGlass = Color(0xFF1D0C18),
    cardInner = Color(0xFF2B1224),
    neonPrimary = Color(0xFFFF2A54),   // Supernova Coral Red
    neonSecondary = Color(0xFFFFBE0B), // Solar Gold
    neonTertiary = Color(0xFF8338EC),  // Cosmic Purple
    statusOnline = Color(0xFF06D6A0),
    statusWarning = Color(0xFFFFBE0B),
    statusAlert = Color(0xFFFF0054),
    textBright = Color(0xFFFFF8FA),
    textSoft = Color(0xFFF4B8C8),
    textMuted = Color(0xFF9E6B7B),
    borderBase = Color(0xFF4A1D36)
)

private val EmeraldMatrixTheme = ChromaTheme(
    id = 2,
    name = "HYPER MINT",
    isLight = false,
    bgDeep = Color(0xFF030F0C),
    cardGlass = Color(0xFF09211B),
    cardInner = Color(0xFF0F332A),
    neonPrimary = Color(0xFF00F5A0),   // Hyper Emerald
    neonSecondary = Color(0xFF00D9F5), // Electric Aqua
    neonTertiary = Color(0xFFA3E635),  // Volt Lime
    statusOnline = Color(0xFF00F5A0),
    statusWarning = Color(0xFFFACC15),
    statusAlert = Color(0xFFFB7185),
    textBright = Color(0xFFF0FDF9),
    textSoft = Color(0xFF99F6E4),
    textMuted = Color(0xFF4F8A7E),
    borderBase = Color(0xFF164E41)
)

private val AuroraTheme = ChromaTheme(
    id = 3,
    name = "AURORA",
    isLight = false,
    bgDeep = Color(0xFF05081C),
    cardGlass = Color(0xFF0D1438),
    cardInner = Color(0xFF162052),
    neonPrimary = Color(0xFF4CC9F0),   // Glacial Sky
    neonSecondary = Color(0xFFF72585), // Aurora Pink
    neonTertiary = Color(0xFF4361EE),  // Royal Starlight
    statusOnline = Color(0xFF4CC9F0),
    statusWarning = Color(0xFFFCA311),
    statusAlert = Color(0xFFF72585),
    textBright = Color(0xFFF8FAFF),
    textSoft = Color(0xFFA5B4FC),
    textMuted = Color(0xFF6474B5),
    borderBase = Color(0xFF233275)
)

private val OverdriveTheme = ChromaTheme(
    id = 4,
    name = "OVERDRIVE",
    isLight = false,
    bgDeep = Color(0xFF110606),
    cardGlass = Color(0xFF240D0E),
    cardInner = Color(0xFF361417),
    neonPrimary = Color(0xFFFF5400),   // Nitro Orange
    neonSecondary = Color(0xFFFF0A54), // Laser Crimson
    neonTertiary = Color(0xFF00BBF9),  // Nitrous Blue
    statusOnline = Color(0xFF00F5D4),
    statusWarning = Color(0xFFFFD166),
    statusAlert = Color(0xFFFF0A54),
    textBright = Color(0xFFFFF5F5),
    textSoft = Color(0xFFFCA5A5),
    textMuted = Color(0xFFA86464),
    borderBase = Color(0xFF521E22)
)

private val CrystalDayTheme = ChromaTheme(
    id = 5,
    name = "CRYSTAL",
    isLight = true,
    bgDeep = Color(0xFFEEF2FF),
    cardGlass = Color(0xFFFFFFFF),
    cardInner = Color(0xFFF3F6FF),
    neonPrimary = Color(0xFF4F46E5),   // Royal Indigo
    neonSecondary = Color(0xFFEC4899), // Vivid Rose
    neonTertiary = Color(0xFF06B6D4),  // Cyan Turquoise
    statusOnline = Color(0xFF059669),
    statusWarning = Color(0xFFD97706),
    statusAlert = Color(0xFFE11D48),
    textBright = Color(0xFF0F172A),
    textSoft = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    borderBase = Color(0xFFD8E0F8)
)

private val ChromaPalettes = listOf(
    CyberpunkTheme,
    SupernovaTheme,
    EmeraldMatrixTheme,
    AuroraTheme,
    OverdriveTheme,
    CrystalDayTheme
)

val LocalChromaTheme = compositionLocalOf { CyberpunkTheme }

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
                mutableIntStateOf(globalPrefs.getInt("chroma_theme_idx", 0).coerceIn(0, ChromaPalettes.lastIndex))
            }
            val theme = ChromaPalettes[themeIndex]

            val colorScheme = if (theme.isLight) {
                lightColorScheme(
                    primary = theme.neonPrimary,
                    secondary = theme.neonSecondary,
                    background = theme.bgDeep,
                    surface = theme.cardGlass,
                    onPrimary = Color.White,
                    onSurface = theme.textBright,
                    error = theme.statusAlert
                )
            } else {
                darkColorScheme(
                    primary = theme.neonPrimary,
                    secondary = theme.neonSecondary,
                    background = theme.bgDeep,
                    surface = theme.cardGlass,
                    onPrimary = Color.White,
                    onSurface = theme.textBright,
                    error = theme.statusAlert
                )
            }

            CompositionLocalProvider(LocalChromaTheme provides theme) {
                MaterialTheme(colorScheme = colorScheme) {
                    MainScreen(
                        rootState = rootState,
                        rootLabel = rootLabel,
                        isProxyActive = isProxyRunningState,
                        isTransitioning = isTransitioningState,
                        runningSlot = runningSlotState,
                        activePid = activePidState,
                        selectedThemeIdx = themeIndex,
                        onSelectTheme = { idx ->
                            themeIndex = idx
                            globalPrefs.edit().putInt("chroma_theme_idx", idx).apply()
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
    selectedThemeIdx: Int,
    onSelectTheme: (Int) -> Unit,
    onRecheckRoot: () -> Unit,
    installedApps: List<AppItem>,
    onSyncStatus: () -> Unit,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val theme = LocalChromaTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val globalPrefs = remember { context.getSharedPreferences("nameless_global_config", Context.MODE_PRIVATE) }
    var startOnBoot by remember { mutableStateOf(globalPrefs.getBoolean("start_on_boot", false)) }
    var bootSlot by remember { mutableIntStateOf(globalPrefs.getInt("boot_slot", 0)) }
    var routeHotspot by remember { mutableStateOf(globalPrefs.getBoolean("route_hotspot", true)) }

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    var selectedScreen by remember { mutableIntStateOf(0) }

    // Hoisted Live Session Telemetry
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var rxSpectrum by remember { mutableStateOf(List(28) { 0L }) }
    var txSpectrum by remember { mutableStateOf(List(28) { 0L }) }
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
            rxSpectrum = List(28) { 0L }
            txSpectrum = List(28) { 0L }
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
    var currentLogs by remember { mutableStateOf("Tap Refresh to inspect kernel & sing-box logs...") }

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
        targetValue = theme.bgDeep,
        animationSpec = tween(500),
        label = "chromaBg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBg)
    ) {
        // 1. MULTI-COLOR NEBULA, LASER GRID & FLOATING PLASMA PARTICLES
        ChromaLivingNebula(
            isProxyActive = isProxyActive,
            isTransitioning = isTransitioning || isTesting
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. VIBRANT TOP HEADER + 6-COLOR LIVE SWATCH BAR + NODE SELECTOR
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                ChromaTopHeader(
                    isProxyActive = isProxyActive,
                    runningSlot = runningSlot,
                    activeSlot = activeSlot,
                    rootState = rootState,
                    onRecheckRoot = onRecheckRoot
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 6-Theme Instant Color Swatch Picker Bar
                ChromaThemeSwatchBar(
                    selectedThemeIdx = selectedThemeIdx,
                    onSelectTheme = onSelectTheme
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Glowing Gradient Node Slots P0..P4
                ChromaNodeRibbon(
                    activeSlot = activeSlot,
                    runningSlot = runningSlot,
                    isProxyActive = isProxyActive,
                    startOnBoot = startOnBoot,
                    bootSlot = bootSlot,
                    slotProtocols = slotProtocols,
                    onSelectSlot = { switchSlot(it) }
                )
            }

            // 3. ANIMATED SCREEN VIEWPORT
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
                        ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(170),
                                targetOffsetX = { fullW -> -dir * (fullW / 4) }
                            ) + fadeOut(tween(150))
                        )
                    },
                    label = "chromaScreenTransition"
                ) { screenIdx ->
                    when (screenIdx) {
                        0 -> {
                            ChromaCockpitView(
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
                                            return@ChromaCockpitView
                                        }
                                        saveConfigForSlot(activeSlot, immediateRootSync = true)
                                        val settings = getCurrentSettings()
                                        val targets = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
                                        onStartProxy(settings, targets) { success, error ->
                                            if (!success) {
                                                testStatus = "Error: ${error?.lines()?.lastOrNull() ?: "See Logs tab"}"
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
                                        return@ChromaCockpitView
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
                            ChromaNodeConfigView(
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
                            ChromaRoutingView(
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
                            ChromaTerminalView(
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

            // 4. GRADIENT FLOATING NAVIGATION DOCK
            ChromaBottomDock(
                selectedScreen = selectedScreen,
                onSelectScreen = { selectedScreen = it },
                splitAppsCount = if (routeWholeProfile) null else selectedPackages.size
            )
        }
    }
}

// -------------------------------------------------------------
// LIVE MULTI-COLOR NEBULA, LASER RINGS & FLOATING ORBS CANVAS
// -------------------------------------------------------------

@Composable
fun ChromaLivingNebula(
    isProxyActive: Boolean,
    isTransitioning: Boolean
) {
    val theme = LocalChromaTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "chromaNebula")

    val orbitDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 12000 else 24000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "orbitDeg"
    )

    val streamShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 5500 else 14000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "streamShift"
    )

    val auraBreath by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "auraBreath"
    )

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height
        val rad = Math.toRadians(orbitDeg.toDouble())

        val orb1X = (w * 0.78f) - (sin(rad) * w * 0.22f).toFloat()
        val orb1Y = (h * 0.20f) + (cos(rad) * h * 0.12f).toFloat()

        val orb2X = (w * 0.22f) + (cos(rad * 1.3) * w * 0.20f).toFloat()
        val orb2Y = (h * 0.55f) + (sin(rad * 1.3) * h * 0.14f).toFloat()

        val orb3X = (w * 0.55f) + (sin(rad * 0.8) * w * 0.25f).toFloat()
        val orb3Y = (h * 0.85f) - (cos(rad * 0.8) * h * 0.10f).toFloat()

        val alphaBoost = if (theme.isLight) 0.18f else if (isProxyActive) 0.34f else 0.24f

        // Primary Neon Plasma Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.neonPrimary.copy(alpha = alphaBoost), Color.Transparent),
                center = Offset(orb1X, orb1Y),
                radius = w * 0.78f * auraBreath
            ),
            center = Offset(orb1X, orb1Y),
            radius = w * 0.78f * auraBreath
        )

        // Secondary Neon Plasma Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.neonSecondary.copy(alpha = alphaBoost), Color.Transparent),
                center = Offset(orb2X, orb2Y),
                radius = w * 0.75f
            ),
            center = Offset(orb2X, orb2Y),
            radius = w * 0.75f
        )

        // Tertiary Neon Plasma Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.neonTertiary.copy(alpha = alphaBoost * 0.85f), Color.Transparent),
                center = Offset(orb3X, orb3Y),
                radius = w * 0.70f
            ),
            center = Offset(orb3X, orb3Y),
            radius = w * 0.70f
        )

        // Rising Multi-Color Quantum Sparks
        val sparkCount = 28
        for (i in 0 until sparkCount) {
            val px = (w * ((i * 43 + 11) % 100) / 100f)
            val baseY = (h * ((i * 61 + 19) % 100) / 100f)
            val speed = 0.5f + (i % 4) * 0.25f
            val py = (baseY - (streamShift * speed)) % h
            val actualY = if (py < 0) py + h else py
            val dotR = (1.5f + (i % 3) * 1.2f).dp.toPx()

            val sparkColor = when (i % 3) {
                0 -> theme.neonPrimary
                1 -> theme.neonSecondary
                else -> theme.neonTertiary
            }

            drawCircle(
                color = sparkColor,
                radius = dotR,
                center = Offset(px, actualY),
                alpha = if (isProxyActive) 0.55f else 0.26f
            )
        }
    }
}

// -------------------------------------------------------------
// TOP HEADER + 6-COLOR LIVE THEME SWATCH BAR + NODE RIBBON
// -------------------------------------------------------------

@Composable
fun ChromaTopHeader(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    rootState: RootState,
    onRecheckRoot: () -> Unit
) {
    val theme = LocalChromaTheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(theme.neonPrimary, theme.neonSecondary))
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NAMELESS PROXY",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textBright,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isProxyActive) {
                    "TUNNEL ONLINE • NODE P${runningSlot ?: activeSlot} • PORT ${ProfileManager.localInboundPort}"
                } else {
                    "KERNEL TPROXY • USER ${ProfileManager.androidUserId} • ${theme.name} EDITION"
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isProxyActive) theme.statusOnline else theme.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val rootColor = when (rootState) {
            RootState.GRANTED -> theme.statusOnline
            RootState.DENIED -> theme.statusAlert
            RootState.CHECKING -> theme.statusWarning
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = theme.cardGlass,
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (rootState) {
                        RootState.GRANTED -> "ROOT OK"
                        RootState.DENIED -> "NO ROOT"
                        RootState.CHECKING -> "WAIT"
                    },
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textBright
                )
            }
        }
    }
}

@Composable
fun ChromaThemeSwatchBar(
    selectedThemeIdx: Int,
    onSelectTheme: (Int) -> Unit
) {
    val currentTheme = LocalChromaTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(currentTheme.cardGlass.copy(alpha = 0.85f))
            .border(1.dp, currentTheme.borderBase, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CHROMA",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = currentTheme.textSoft,
            letterSpacing = 1.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChromaPalettes.forEachIndexed { idx, palette ->
                val isSelected = idx == selectedThemeIdx
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.18f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "swatchScale"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(palette.neonPrimary, palette.neonSecondary)
                            )
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.8.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectTheme(idx) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = palette.name.take(5),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ChromaNodeRibbon(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    slotProtocols: Map<Int, String>,
    onSelectSlot: (Int) -> Unit
) {
    val theme = LocalChromaTheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        (0 until ProfileManager.MAX_SLOTS).forEach { slotIdx ->
            val isSelected = slotIdx == activeSlot
            val isRunning = isProxyActive && (runningSlot == slotIdx)
            val isBoot = startOnBoot && (bootSlot == slotIdx)
            val proto = slotProtocols[slotIdx]

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "slotBounce"
            )

            val cardBrush = when {
                isRunning -> Brush.linearGradient(
                    listOf(theme.statusOnline.copy(alpha = 0.32f), theme.neonSecondary.copy(alpha = 0.22f))
                )
                isSelected -> Brush.linearGradient(
                    listOf(theme.neonPrimary.copy(alpha = 0.35f), theme.neonTertiary.copy(alpha = 0.25f))
                )
                else -> Brush.linearGradient(listOf(theme.cardGlass, theme.cardGlass))
            }

            val borderBrush = when {
                isRunning -> Brush.linearGradient(listOf(theme.statusOnline, theme.neonSecondary))
                isSelected -> Brush.linearGradient(listOf(theme.neonPrimary, theme.neonSecondary))
                else -> Brush.linearGradient(listOf(theme.borderBase, theme.borderBase))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(13.dp))
                    .background(cardBrush)
                    .border(if (isSelected || isRunning) 1.6.dp else 1.dp, borderBrush, RoundedCornerShape(13.dp))
                    .clickable { onSelectSlot(slotIdx) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "P$slotIdx",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        isRunning -> theme.statusOnline
                        isSelected -> theme.textBright
                        proto != null -> theme.textSoft
                        else -> theme.textMuted
                    }
                )
                Text(
                    text = when {
                        isRunning -> "LIVE"
                        isBoot -> "BOOT"
                        proto != null -> proto.take(6)
                        else -> "FREE"
                    },
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isRunning -> theme.statusOnline
                        isBoot -> theme.statusWarning
                        isSelected -> theme.neonSecondary
                        else -> theme.textMuted
                    },
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 0: VIBRANT CHROMA COCKPIT (PLASMA TURBINE + DUAL GAUGES)
// -------------------------------------------------------------

@Composable
fun ChromaCockpitView(
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
    val theme = LocalChromaTheme.current
    val isCurrentSlotRunning = isProxyActive && (runningSlot == activeSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. HERO PLASMA TURBINE + EXIT NODE PASSPORT CARD
        ChromaPlasmaHeroCard(
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

        // 2. TWIN NEON SPEEDOMETER GAUGES + LIVE DUAL WAVE & 28-BAR SPECTRUM
        ChromaTelemetryDeck(
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

        // 3. GRADIENT PIPELINE CHIPS & LATENCY PROBE
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.cardGlass,
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(theme.neonPrimary.copy(alpha = 0.5f), theme.neonSecondary.copy(alpha = 0.5f)))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChromaTag(label = proxyType.displayTitle, color = theme.neonPrimary)
                    ChromaTag(
                        label = if (transportMode == TransportMode.TCP_AND_UDP) "TCP + UDP TPROXY" else "TCP ONLY",
                        color = theme.neonSecondary
                    )
                    ChromaTag(label = "PRIVATE DNS 853 READY", color = theme.statusOnline)
                    ChromaTag(
                        label = if (routeWholeProfile) "FULL PROFILE" else "$selectedAppsCount APPS",
                        color = theme.neonTertiary
                    )
                    if (routeHotspot) {
                        ChromaTag(label = "HOTSPOT ON", color = theme.statusWarning)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTestUpstream,
                        enabled = !isTesting && !isTransitioning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.cardInner,
                            contentColor = theme.neonSecondary
                        ),
                        border = BorderStroke(1.dp, theme.neonSecondary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text(
                            text = if (isTesting) "Probing Upstream Socket..." else "⚡ Test Node P$activeSlot Latency & Handshake",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
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
                    val sCol = when {
                        isTesting -> theme.statusWarning
                        testLatencyMs != null -> theme.statusOnline
                        else -> theme.statusAlert
                    }
                    Text(
                        text = testStatus ?: "",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = sCol,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 4. MULTI-STOP NEON GRADIENT LAUNCH BAR
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val btnScale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "launchScale"
        )

        val btnBrush = when {
            isTransitioning -> Brush.horizontalGradient(listOf(theme.statusWarning, theme.neonPrimary))
            isCurrentSlotRunning -> Brush.horizontalGradient(listOf(theme.statusAlert, Color(0xFF991B1B)))
            else -> Brush.horizontalGradient(listOf(theme.neonPrimary, theme.neonTertiary, theme.neonSecondary))
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
                    isTransitioning -> "SYNCHRONIZING KERNEL TUNNEL..."
                    !isProxyActive -> "ENGAGE TUNNEL • NODE P$activeSlot"
                    isCurrentSlotRunning -> "DISCONNECT TUNNEL • NODE P$activeSlot"
                    else -> "SWITCH TO NODE P$activeSlot & ENGAGE"
                },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// HERO PLASMA TURBINE + EXIT NODE PASSPORT CARD
// -------------------------------------------------------------

@Composable
fun ChromaPlasmaHeroCard(
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
    val theme = LocalChromaTheme.current
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "plasmaTurbine")

    val spinOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    isTransitioning -> 1400
                    isProxyActive -> 3600
                    else -> 12000
                },
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "spinOuter"
    )

    val spinInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    isTransitioning -> 1000
                    isProxyActive -> 2400
                    else -> 9000
                },
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "spinInner"
    )

    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "pulseWave"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = theme.cardGlass,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                if (isProxyActive) listOf(theme.statusOnline, theme.neonSecondary, theme.neonPrimary)
                else listOf(theme.neonPrimary.copy(alpha = 0.7f), theme.neonTertiary.copy(alpha = 0.5f))
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left: Interactive Multi-Ring Plasma Turbine (Tap to Toggle)
                Box(
                    modifier = Modifier
                        .size(106.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isTransitioning) { onToggleProxy() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f

                        // Expanding Shockwave Ring
                        if (isProxyActive || isTransitioning) {
                            drawCircle(
                                color = theme.statusOnline.copy(alpha = (1f - pulseWave) * 0.6f),
                                radius = r * pulseWave,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }

                        // Outer Multi-Stop Gradient Ring
                        rotate(degrees = spinOuter, pivot = Offset(cx, cy)) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(theme.neonPrimary, theme.neonSecondary, theme.neonTertiary, theme.neonPrimary)
                                ),
                                startAngle = 0f,
                                sweepAngle = 290f,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // 3 Orbiting Satellite Nodes
                            for (s in 0..2) {
                                val ang = Math.toRadians((s * 120).toDouble())
                                val sx = cx + (r * 0.86f * cos(ang)).toFloat()
                                val sy = cy + (r * 0.86f * sin(ang)).toFloat()
                                drawCircle(
                                    color = theme.neonSecondary,
                                    radius = 3.2.dp.toPx(),
                                    center = Offset(sx, sy)
                                )
                            }
                        }

                        // Inner Counter-Rotating Dashed Plasma Ring
                        rotate(degrees = spinInner, pivot = Offset(cx, cy)) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(theme.neonSecondary, theme.statusOnline, Color.Transparent, theme.neonSecondary)
                                ),
                                startAngle = 20f,
                                sweepAngle = 240f,
                                useCenter = false,
                                topLeft = Offset(r * 0.24f, r * 0.24f),
                                size = Size(r * 1.52f, r * 1.52f),
                                style = Stroke(
                                    width = 2.4.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            )
                        }

                        // Glowing Center Core
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = if (isProxyActive) {
                                    listOf(theme.statusOnline.copy(alpha = 0.45f), theme.neonSecondary.copy(alpha = 0.1f))
                                } else {
                                    listOf(theme.neonPrimary.copy(alpha = 0.35f), Color.Transparent)
                                }
                            ),
                            radius = r * 0.52f,
                            center = Offset(cx, cy)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                isTransitioning -> "SYNC"
                                isProxyActive -> "LIVE"
                                else -> "START"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = theme.textBright
                        )
                        Text(
                            text = "NODE P${if (isProxyActive) (runningSlot ?: activeSlot) else activeSlot}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProxyActive) theme.statusOnline else theme.neonSecondary
                        )
                    }
                }

                // Right: Cleanly Stacked Exit Node & Session Telemetry (Zero Text Collision)
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
                            text = if (isProxyActive) "EXIT TUNNEL" else "CONFIGURED NODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isProxyActive) theme.statusOnline else theme.neonPrimary
                        )

                        if (isProxyActive && sessionStartMs > 0L) {
                            ChromaUptimePill(sessionStartMs = sessionStartMs)
                        }
                    }

                    // Exit IP / Host Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = theme.cardInner,
                        border = BorderStroke(1.dp, theme.borderBase),
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
                                        text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "🛰️",
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
                                            if (host.isNotBlank()) host.trim() else "No Host Set"
                                        },
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = theme.textBright,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isProxyActive) {
                                    Text(
                                        text = if (isFetchingIp) "..." else "↻",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = theme.neonSecondary,
                                        modifier = Modifier
                                            .clickable { onRefreshIp() }
                                            .padding(start = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = if (isProxyActive) {
                                    when {
                                        isFetchingIp -> "Checking exit node via local SOCKS..."
                                        publicIpInfo != null -> "${publicIpInfo.country} • Tap to copy"
                                        ipFetchFailed -> "Tap card to re-verify exit IP"
                                        else -> "Synchronizing..."
                                    }
                                } else {
                                    if (host.isNotBlank()) "Port ${port.trim()} • ${proxyType.displayTitle}" else "Configure in Nodes tab"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ipFetchFailed && isProxyActive) theme.statusAlert else theme.textSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = if (isProxyActive) {
                            "PID ${activePid ?: "Active"} • Cloudflare & Google DoT Ready"
                        } else {
                            "Tap Plasma Core or Launch Bar to start"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TWIN NEON SPEEDOMETER GAUGES + LIVE DUAL WAVE & 28-BAR SPECTRUM
// -------------------------------------------------------------

@Composable
fun ChromaTelemetryDeck(
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
    val theme = LocalChromaTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (isProxyActive) 1500 else 4500, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = theme.cardGlass,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(
                listOf(theme.neonSecondary.copy(alpha = 0.6f), theme.neonPrimary.copy(alpha = 0.6f))
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChromaSpeedGauge(
                    title = "DOWNLOAD",
                    bytesPerSec = currentRxRate,
                    peakBytesPerSec = peakRxRate,
                    totalBytes = totalRxBytes,
                    primaryColor = theme.neonSecondary,
                    secondaryColor = theme.statusOnline,
                    isProxyActive = isProxyActive,
                    modifier = Modifier.weight(1f)
                )

                ChromaSpeedGauge(
                    title = "UPLOAD",
                    bytesPerSec = currentTxRate,
                    peakBytesPerSec = peakTxRate,
                    totalBytes = totalTxBytes,
                    primaryColor = theme.neonPrimary,
                    secondaryColor = theme.neonTertiary,
                    isProxyActive = isProxyActive,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hybrid Live Dual-Wave + 28-Bar Neon Equalizer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.cardInner)
                    .border(1.dp, theme.borderBase, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val w = size.width
                    val h = size.height
                    val barCount = rxSpectrum.size.coerceAtLeast(2)
                    val maxSample = (rxSpectrum.maxOrNull() ?: 0L)
                        .coerceAtLeast(txSpectrum.maxOrNull() ?: 0L)
                        .coerceAtLeast(32 * 1024L)

                    val gap = 3.dp.toPx()
                    val barW = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)

                    val wavePath = Path()

                    for (i in 0 until barCount) {
                        val rxFrac = speedToLogFraction(rxSpectrum[i], maxSample)
                        val txFrac = speedToLogFraction(txSpectrum[i], maxSample)
                        val idleWave = ((sin(wavePhase + i * 0.35f) + 1f) * 0.12f).toFloat()
                        val activeHeightFrac = if (isProxyActive) {
                            (rxFrac * 0.75f + txFrac * 0.25f + idleWave * 0.4f).coerceIn(0.14f, 1f)
                        } else {
                            (0.10f + idleWave * 0.5f).coerceIn(0.10f, 0.32f)
                        }

                        val bh = h * activeHeightFrac
                        val x = i * (barW + gap)
                        val y = h - bh

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(theme.neonSecondary, theme.neonPrimary)
                            ),
                            topLeft = Offset(x, y),
                            size = Size(barW, bh),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                            alpha = if (isProxyActive) 0.85f else 0.35f
                        )

                        val wx = x + barW / 2f
                        val wy = (h * 0.78f) - (rxFrac * h * 0.62f) + (sin(wavePhase + i * 0.4f) * 4f).toFloat()
                        if (i == 0) wavePath.moveTo(wx, wy) else wavePath.lineTo(wx, wy)
                    }

                    drawPath(
                        path = wavePath,
                        brush = Brush.horizontalGradient(
                            listOf(theme.statusOnline, theme.neonSecondary, theme.neonPrimary)
                        ),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun ChromaSpeedGauge(
    title: String,
    bytesPerSec: Long,
    peakBytesPerSec: Long,
    totalBytes: Long,
    primaryColor: Color,
    secondaryColor: Color,
    isProxyActive: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = LocalChromaTheme.current
    val targetFrac = if (isProxyActive) speedToLogFraction(bytesPerSec, 50L * 1024L * 1024L) else 0f
    val animFrac by animateFloatAsState(
        targetValue = targetFrac,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "speedGaugeArc"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(theme.cardInner)
            .border(1.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = primaryColor,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val strokeW = 7.dp.toPx()
                val pad = strokeW / 2f + 3.dp.toPx()
                val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                val topLeft = Offset(pad, pad)

                drawArc(
                    color = theme.borderBase,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                val sweep = (240f * animFrac).coerceIn(4f, 240f)
                if (isProxyActive) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(primaryColor, secondaryColor, primaryColor)
                        ),
                        startAngle = 150f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = splitSpeedValue(bytesPerSec),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textBright,
                    maxLines = 1
                )
                Text(
                    text = splitSpeedUnit(bytesPerSec),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Peak ${formatCompactRate(peakBytesPerSec)}",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.textSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Total ${formatBytes(totalBytes)}",
            fontSize = 9.sp,
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SCREEN 1: NODE CONFIGURATION & URI IMPORTER
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromaNodeConfigView(
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
    val theme = LocalChromaTheme.current
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
            color = theme.cardGlass,
            border = BorderStroke(1.dp, theme.borderBase),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "QUICK IMPORT & SOCKET DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.neonSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Paste vless://, trojan://, ss://, hy2://, socks5:// or test socket",
                    fontSize = 10.5.sp,
                    color = theme.textSoft
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
                        border = BorderStroke(1.dp, theme.neonSecondary)
                    ) {
                        Text("📋 Paste URI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.neonSecondary)
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
                            containerColor = theme.neonPrimary,
                            contentColor = Color.White
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
                                    color = theme.statusOnline,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            is TestResult.Failure -> {
                                Text(
                                    text = "✕ UNREACHABLE: ${res.error}",
                                    color = theme.statusAlert,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black
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
            color = theme.cardGlass,
            border = BorderStroke(1.dp, theme.borderBase),
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
                        color = theme.neonPrimary
                    )
                    Text(
                        text = "Clear P$activeSlot",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.statusAlert,
                        modifier = Modifier.clickable { onResetSlot() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSoft)
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
                                selectedContainerColor = theme.neonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("TRANSPORT & WEBRTC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSoft)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_AND_UDP,
                        onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                        label = { Text("TCP + UDP (TProxy)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.neonPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_ONLY,
                        onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                        label = { Text("TCP Only", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.neonPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("IP STACK STRATEGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSoft)
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
                                selectedContainerColor = theme.neonPrimary,
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
                                                color = theme.neonSecondary,
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
                                            color = theme.neonSecondary,
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
                                            color = theme.neonSecondary,
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
fun ChromaRoutingView(
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
    val theme = LocalChromaTheme.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.cardGlass,
            border = BorderStroke(1.dp, theme.borderBase),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start on System Boot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textBright)
                        Text(
                            text = if (startOnBoot) "Armed for Node P$bootSlot (/data/adb/service.d)" else "Manual launch only",
                            fontSize = 10.5.sp,
                            color = if (startOnBoot) theme.statusOnline else theme.textSoft
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

                HorizontalDivider(color = theme.borderBase, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hotspot & USB Tethering Bridge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textBright)
                        Text(
                            text = "Route connected Wi-Fi & RNDIS clients through proxy",
                            fontSize = 10.5.sp,
                            color = if (routeHotspot) theme.neonSecondary else theme.textSoft
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
            color = theme.cardGlass,
            border = BorderStroke(1.dp, theme.borderBase),
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
                        Text("Global Profile Tunneling", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textBright)
                        Text(
                            text = if (routeWholeProfile) "All apps in User ${ProfileManager.androidUserId} routed" else "Split Tunneling (${selectedPackages.size} apps active)",
                            fontSize = 10.5.sp,
                            color = theme.textSoft
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
                            Text("${selectedPackages.size} selected", fontSize = 10.5.sp, color = theme.textSoft)
                            Row {
                                TextButton(onClick = onSelectAll) { Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = onClearAll) { Text("Clear", fontSize = 11.sp, color = theme.statusAlert, fontWeight = FontWeight.Bold) }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.packageName }) { app ->
                                val isChecked = selectedPackages.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isChecked) theme.cardInner else Color.Transparent)
                                        .clickable { onToggleAppSelection(app.packageName) }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${app.packageName} • UID ${app.uid}", color = theme.textSoft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun ChromaTerminalView(
    activeSlot: Int,
    runningSlot: Int?,
    currentLogs: String,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val theme = LocalChromaTheme.current
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = theme.cardGlass,
        border = BorderStroke(1.dp, theme.borderBase),
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
                        color = theme.neonSecondary
                    )
                    Text(
                        text = "Slot P${runningSlot ?: activeSlot} • sing-box & netfilter stream",
                        fontSize = 10.5.sp,
                        color = theme.textSoft
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SingBoxLogs", currentLogs))
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.neonSecondary)
                    }
                    TextButton(onClick = onClearLogs) {
                        Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.statusAlert)
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
                    .background(theme.cardInner)
                    .border(1.dp, theme.borderBase, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = currentLogs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = theme.textBright,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// -------------------------------------------------------------
// FLOATING NEON BOTTOM NAVIGATION DOCK
// -------------------------------------------------------------

@Composable
fun ChromaBottomDock(
    selectedScreen: Int,
    onSelectScreen: (Int) -> Unit,
    splitAppsCount: Int?
) {
    val theme = LocalChromaTheme.current
    val items = listOf(
        Pair("⚡", "Cockpit"),
        Pair("🛰️", "Nodes"),
        Pair("🛡️", if (splitAppsCount == null) "Routing" else "Split ($splitAppsCount)"),
        Pair("📟", "Logs")
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = theme.cardGlass,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(listOf(theme.neonPrimary, theme.neonSecondary))
        ),
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(listOf(theme.neonPrimary, theme.neonTertiary))
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
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else theme.textSoft,
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
// REUSABLE CHIPS, UPTIME PILL & URI PARSER
// -------------------------------------------------------------

@Composable
fun ChromaTag(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ChromaUptimePill(sessionStartMs: Long) {
    val theme = LocalChromaTheme.current
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
        color = theme.statusOnline.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, theme.statusOnline)
    ) {
        Text(
            text = "⏱ $timeStr",
            fontSize = 10.sp,
            color = theme.statusOnline,
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
