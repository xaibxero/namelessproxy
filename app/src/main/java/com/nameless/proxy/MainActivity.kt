package com.nameless.proxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Bundle
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.nameless.proxy.core.*
import kotlinx.coroutines.Dispatchers
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

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var rootState by mutableStateOf(RootState.CHECKING)
    private var rootLabel by mutableStateOf("Checking...")
    private var isProxyRunningState by mutableStateOf(false)
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(
                    rootState = rootState,
                    rootLabel = rootLabel,
                    isProxyActive = isProxyRunningState,
                    runningSlot = runningSlotState,
                    activePid = activePidState,
                    onRecheckRoot = { refreshRootStatus() },
                    installedApps = installedApps,
                    onSyncStatus = { syncDaemonStatus() },
                    onStartProxy = { settings: ProxySettings, selectedUids: List<Int>?, onResult: (Boolean, String?) -> Unit ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = ProxyController.startProxy(this@MainActivity, settings, selectedUids)
                            withContext(Dispatchers.Main) {
                                syncDaemonStatus()
                                onResult(result.success, result.errorMessage)
                            }
                        }
                    },
                    onStopProxy = { onResult: (Boolean) -> Unit ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val success = ProxyController.stopProxy(this@MainActivity)
                            withContext(Dispatchers.Main) {
                                syncDaemonStatus()
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
            rootLabel = "Checking..."
            val (state, label) = RootChecker.verifyRoot()
            rootState = state
            rootLabel = label
        }
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.contains("chrome") || it.packageName.contains("edge") }
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
                pType = try { ProxyType.valueOf(prefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name) } catch (e: Exception) { ProxyType.SOCKS5 },
                pTransport = try { TransportMode.valueOf(prefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name) } catch (e: Exception) { TransportMode.TCP_AND_UDP },
                pIpMode = try { IpMode.valueOf(prefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name) } catch (e: Exception) { IpMode.IPV4_ONLY },
                pRouteWhole = prefs.getBoolean("route_whole_profile", true),
                pPkgs = prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
            )
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
                    val bType = try { ProxyType.valueOf(backup.optString("proxy_type", ProxyType.SOCKS5.name)) } catch (e: Exception) { ProxyType.SOCKS5 }
                    val bTrans = try { TransportMode.valueOf(backup.optString("transport_mode", TransportMode.TCP_AND_UDP.name)) } catch (e: Exception) { TransportMode.TCP_AND_UDP }
                    val bIp = try { IpMode.valueOf(backup.optString("ip_mode", IpMode.IPV4_ONLY.name)) } catch (e: Exception) { IpMode.IPV4_ONLY }
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
                            .putStringSet("selected_packages", bPkgs)
                            .apply()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        applySettingsToState("", "1080", "", "", "", "2022-blake3-aes-128-gcm", "", "", ProxyType.SOCKS5, TransportMode.TCP_AND_UDP, IpMode.IPV4_ONLY, true, emptySet())
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
            port = port.toIntOrNull() ?: 1080,
            username = username.trim(),
            password = password.trim(),
            routeHotspot = routeHotspot,
            sni = sni.trim(),
            ssMethod = ssMethod.trim(),
            realityPublicKey = realityPublicKey.trim(),
            realityShortId = realityShortId.trim()
        )
    }

    fun saveConfigForSlot(slot: Int) {
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
            .putStringSet("selected_packages", selectedPackages)
            .apply()

        globalPrefs.edit()
            .putBoolean("start_on_boot", startOnBoot)
            .putInt("boot_slot", bootSlot)
            .putBoolean("route_hotspot", routeHotspot)
            .apply()

        val settings = getCurrentSettings()
        val uids = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }

        if (startOnBoot && slot == bootSlot) {
            BootManager.syncBootState(context, true, settings, uids)
        }

        if (settings.host.isNotEmpty()) {
            coroutineScope.launch {
                PersistentStorage.saveBackup(
                    context,
                    slot,
                    settings,
                    startOnBoot && (slot == bootSlot),
                    routeWholeProfile,
                    selectedPackages,
                    ProfileManager.androidUserId
                )
            }
        }
    }

    fun switchSlot(newSlot: Int) {
        if (newSlot == activeSlot) return
        saveConfigForSlot(activeSlot)
        ProfileManager.activeSlot = newSlot
        activeSlot = newSlot
        loadSlotData(newSlot)
        onSyncStatus()
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("") }

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }
    var ipFetchFailed by remember { mutableStateOf(false) }

    fun triggerPublicIpCheck() {
        isFetchingIp = true
        ipFetchFailed = false
        coroutineScope.launch {
            delay(700)
            val currentRunning = ProxyController.getRunningSlot(ProfileManager.androidUserId) ?: activeSlot
            val socksPort = 10800 + (ProfileManager.androidUserId * 100) + (currentRunning * 10) + 1
            var result = IpFetcher.getPublicIpInfo(socksPort)
            if (result == null) {
                delay(1200)
                result = IpFetcher.getPublicIpInfo(socksPort)
            }
            if (result != null) {
                publicIpInfo = result
                ipFetchFailed = false
            } else {
                ipFetchFailed = true
            }
            isFetchingIp = false
        }
    }

    LaunchedEffect(isProxyActive, activeSlot) {
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
            .background(Color(0xFF07090E))
    ) {
        // High-Tech Cyber Space Particle Canvas (Zero Green)
        CyberSpaceCanvas(isProxyActive = isProxyActive)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. TOP HEADER (Cyan & Cobalt Lighting)
            CyberTopHeader(
                activeSlot = activeSlot,
                isProxyActive = isProxyActive,
                rootState = rootState,
                onRecheckRoot = onRecheckRoot
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. SLOTS RIBBON (P0 - P4)
            CyberSlotsRibbon(
                activeSlot = activeSlot,
                runningSlot = runningSlot,
                isProxyActive = isProxyActive,
                startOnBoot = startOnBoot,
                bootSlot = bootSlot,
                onSelectSlot = { switchSlot(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. SEGMENTED TABS
            CyberPillTabs(
                selectedTab = selectedNavTab,
                onTabSelected = { selectedNavTab = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. MAIN VIEWS
            Box(modifier = Modifier.weight(1f)) {
                when (selectedNavTab) {
                    0 -> {
                        // CONSOLE HUD VIEW
                        ConsoleHudView(
                            isProxyActive = isProxyActive,
                            runningSlot = runningSlot,
                            activeSlot = activeSlot,
                            publicIpInfo = publicIpInfo,
                            isFetchingIp = isFetchingIp,
                            ipFetchFailed = ipFetchFailed,
                            activePid = activePid,
                            proxyType = proxyType,
                            transportMode = transportMode,
                            ipMode = ipMode,
                            routeHotspot = routeHotspot,
                            host = host,
                            onRefreshIp = { triggerPublicIpCheck() },
                            onToggleProxy = {
                                if (isProxyActive && runningSlot == activeSlot) {
                                    onStopProxy { }
                                } else {
                                    if (host.trim().isEmpty()) {
                                        testStatus = "Please enter Server Host/IP in Config Deck"
                                        selectedNavTab = 1
                                        return@ConsoleHudView
                                    }
                                    saveConfigForSlot(activeSlot)
                                    val settings = getCurrentSettings()
                                    val targets = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }
                                    onStartProxy(settings, targets) { success, error ->
                                        if (!success) {
                                            testStatus = "Start Failed: ${error ?: "Unknown error"}"
                                        } else {
                                            testStatus = null
                                        }
                                    }
                                }
                            },
                            isTesting = isTesting,
                            testStatus = testStatus,
                            onTestUpstream = {
                                if (host.trim().isEmpty()) {
                                    testStatus = "Please enter Server Host/IP first"
                                    selectedNavTab = 1
                                    return@ConsoleHudView
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testStatus = "Testing..."
                                val settings = getCurrentSettings()
                                coroutineScope.launch {
                                    when (val res = ProxyTester.testProxy(settings)) {
                                        is TestResult.Success -> testStatus = "Online (⚡ ${res.latencyMs} ms)"
                                        is TestResult.Failure -> testStatus = "Failed: ${res.error}"
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
                        // SERVER & CONFIG DECK
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
                            onResetSlot = {
                                val prefs = getSlotPrefs(activeSlot)
                                prefs.edit().clear().apply()
                                PersistentStorage.clearBackup(activeSlot, ProfileManager.androidUserId)
                                loadSlotData(activeSlot)
                                Toast.makeText(context, "P$activeSlot reset to empty", Toast.LENGTH_SHORT).show()
                            },
                            getCurrentSettings = { getCurrentSettings() }
                        )
                    }

                    2 -> {
                        // APPS ROUTING & MASTER SETTINGS
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
                                    saveConfigForSlot(activeSlot)
                                    Toast.makeText(context, "P$activeSlot set as startup target", Toast.LENGTH_SHORT).show()
                                } else {
                                    globalPrefs.edit().putBoolean("start_on_boot", false).apply()
                                    BootManager.removeBootScript()
                                }
                            },
                            onBootSlotChange = { s ->
                                bootSlot = s
                                globalPrefs.edit().putInt("boot_slot", s).apply()
                                saveConfigForSlot(activeSlot)
                                Toast.makeText(context, "P$s designated as startup profile", Toast.LENGTH_SHORT).show()
                            },
                            onRouteHotspotChange = {
                                routeHotspot = it
                                globalPrefs.edit().putBoolean("route_hotspot", it).apply()
                                saveConfigForSlot(activeSlot)
                            },
                            onRouteWholeProfileChange = {
                                routeWholeProfile = it
                                saveConfigForSlot(activeSlot)
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
                            Text("Copy", color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            val targetSlot = runningSlot ?: activeSlot
                            ProxyController.clearLogs(ProfileManager.androidUserId, targetSlot)
                            currentLogs = "Logs cleared."
                        }) {
                            Text("Clear", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
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
                            Text("Refresh", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showLogsDialog = false }) {
                            Text("Close", color = Color(0xFF94A3B8))
                        }
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF00F0FF), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("KERNEL CORE LOGS & TELEMETRY", fontSize = 13.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            },
            containerColor = Color(0xFF0F1420),
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color(0xFF090C14), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x3300F0FF), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = currentLogs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        )
    }
}

// -------------------------------------------------------------
// LIVE CYBER SPACE PARTICLE CANVAS (CLEAN ELECTRIC CYAN & COBALT)
// -------------------------------------------------------------

@Composable
fun CyberSpaceCanvas(isProxyActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyberSpaceEngine")

    val driftAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart),
        label = "spaceDrift"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "spaceAura"
    )

    val motesOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(if (isProxyActive) 9000 else 18000, easing = LinearEasing), RepeatMode.Restart),
        label = "spaceMotes"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer()
    ) {
        val w = size.width
        val h = size.height
        val rad = Math.toRadians(driftAngle.toDouble())

        // 1. Dual Electric Cyan & Cobalt Nebula Spheres
        val orb1X = (w * 0.30f) + (cos(rad) * 140f).toFloat()
        val orb1Y = (h * 0.22f) + (sin(rad) * 100f).toFloat()

        val orb2X = (w * 0.74f) - (sin(rad) * 150f).toFloat()
        val orb2Y = (h * 0.48f) + (cos(rad) * 110f).toFloat()

        val orb3X = (w * 0.46f) + (sin(rad * 1.3) * 100f).toFloat()
        val orb3Y = (h * 0.80f) - (cos(rad * 1.3) * 80f).toFloat()

        if (isProxyActive) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3500F0FF), Color(0x0C0070F3), Color.Transparent),
                    center = Offset(orb1X, orb1Y),
                    radius = w * 0.85f
                ),
                center = Offset(orb1X, orb1Y),
                radius = w * 0.85f,
                alpha = auraPulse
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x303B82F6), Color(0x0A1D4ED8), Color.Transparent),
                    center = Offset(orb2X, orb2Y),
                    radius = w * 0.90f
                ),
                center = Offset(orb2X, orb2Y),
                radius = w * 0.90f,
                alpha = auraPulse
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x246366F1), Color(0x064F46E5), Color.Transparent),
                    center = Offset(orb3X, orb3Y),
                    radius = w * 0.75f
                ),
                center = Offset(orb3X, orb3Y),
                radius = w * 0.75f,
                alpha = auraPulse
            )
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x181E293B), Color.Transparent),
                    center = Offset(orb1X, orb1Y),
                    radius = w * 0.70f
                ),
                center = Offset(orb1X, orb1Y),
                radius = w * 0.70f
            )
        }

        // 2. High-Tech Floating Cosmic Dust Motes
        val moteCount = 32
        for (i in 0 until moteCount) {
            val px = (w * ((i * 37) % 100) / 100f)
            val baseY = (h * ((i * 53) % 100) / 100f)
            val py = (baseY - (motesOffset * (0.5f + (i % 5) * 0.2f))) % h
            val actualY = if (py < 0) py + h else py
            val sizeDot = (1.4f + (i % 3) * 1.1f).dp.toPx()

            drawCircle(
                color = when (i % 3) {
                    0 -> Color(0xFF00F0FF)
                    1 -> Color(0xFF38BDF8)
                    else -> Color(0xFF818CF8)
                },
                radius = sizeDot,
                center = Offset(px, actualY),
                alpha = if (isProxyActive) (0.35f + (i % 4) * 0.15f) * auraPulse else 0.12f
            )
        }
    }
}

// -------------------------------------------------------------
// TOP HEADER
// -------------------------------------------------------------

@Composable
fun CyberTopHeader(
    activeSlot: Int,
    isProxyActive: Boolean,
    rootState: RootState,
    onRecheckRoot: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseBadge")
    val badgeGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "badgeGlow"
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isProxyActive) Color(0xFF00F0FF) else Color(0xFF475569), CircleShape)
                        .graphicsLayer { alpha = if (isProxyActive) badgeGlow else 1f }
                )
            }
            Text(
                text = "USER ${ProfileManager.androidUserId} • SLOT P$activeSlot • KERNEL TUNNEL",
                fontSize = 10.sp,
                color = if (isProxyActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x18FFFFFF),
            border = BorderStroke(
                1.dp,
                when (rootState) {
                    RootState.GRANTED -> Color(0xFF00F0FF)
                    RootState.DENIED -> Color(0xFFF43F5E)
                    RootState.CHECKING -> Color(0xFFFBBF24)
                }
            ),
            modifier = Modifier.clickable { onRecheckRoot() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            when (rootState) {
                                RootState.GRANTED -> Color(0xFF00F0FF)
                                RootState.DENIED -> Color(0xFFF43F5E)
                                RootState.CHECKING -> Color(0xFFFBBF24)
                            },
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (rootState) {
                        RootState.GRANTED -> "ROOT ON"
                        RootState.DENIED -> "NO ROOT"
                        RootState.CHECKING -> "CHECKING"
                    },
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SLOTS RIBBON (P0 - P4)
// -------------------------------------------------------------

@Composable
fun CyberSlotsRibbon(
    activeSlot: Int,
    runningSlot: Int?,
    isProxyActive: Boolean,
    startOnBoot: Boolean,
    bootSlot: Int,
    onSelectSlot: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0..4).forEach { slotIdx ->
            val isSelected = slotIdx == activeSlot
            val isRunning = isProxyActive && (runningSlot == slotIdx)
            val isBootTarget = startOnBoot && (slotIdx == bootSlot)

            val animatedBorder by animateColorAsState(
                targetValue = when {
                    isRunning -> Color(0xFF00F0FF)
                    isSelected -> Color(0xFF38BDF8)
                    else -> Color(0x18FFFFFF)
                },
                animationSpec = tween(250),
                label = "slotBorder"
            )

            val animatedBg by animateColorAsState(
                targetValue = when {
                    isRunning -> Color(0x3500F0FF)
                    isSelected -> Color(0x2838BDF8)
                    else -> Color(0x0CFFFFFF)
                },
                animationSpec = tween(250),
                label = "slotBg"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(animatedBg)
                    .border(1.2.dp, animatedBorder, RoundedCornerShape(14.dp))
                    .clickable { onSelectSlot(slotIdx) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "P$slotIdx",
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isRunning) FontWeight.Black else FontWeight.Bold,
                        color = when {
                            isRunning -> Color(0xFF00F0FF)
                            isSelected -> Color(0xFF38BDF8)
                            else -> Color(0xFF94A3B8)
                        },
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isRunning -> "LIVE"
                            isBootTarget -> "BOOT"
                            isSelected -> "EDIT"
                            else -> "IDLE"
                        },
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            isRunning -> Color(0xFF00F0FF)
                            isBootTarget -> Color(0xFF38BDF8)
                            isSelected -> Color(0xFF00F0FF)
                            else -> Color(0xFF475569)
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SEGMENTED TABS
// -------------------------------------------------------------

@Composable
fun CyberPillTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Console HUD", "Config Deck", "App Routing")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0x0EFFFFFF),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index

                val bg by animateColorAsState(
                    targetValue = if (isSelected) Color(0x3500F0FF) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "tabBg"
                )
                val borderCol by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF00F0FF) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "tabBorder"
                )
                val textCol by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
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
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = textCol,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VIEW 1: CONSOLE HUD VIEW
// -------------------------------------------------------------

@Composable
fun ConsoleHudView(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    ipFetchFailed: Boolean,
    activePid: String?,
    proxyType: ProxyType,
    transportMode: TransportMode,
    ipMode: IpMode,
    routeHotspot: Boolean,
    host: String,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    onTestUpstream: () -> Unit,
    onViewLogs: () -> Unit
) {
    val context = LocalContext.current
    val isCurrentSlotRunning = isProxyActive && (runningSlot == activeSlot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // QUANTUM SHIELD GYRO HUD CARD
        QuantumGyroCard(
            isProxyActive = isProxyActive,
            runningSlot = runningSlot,
            activeSlot = activeSlot,
            activePid = activePid,
            publicIpInfo = publicIpInfo,
            isFetchingIp = isFetchingIp,
            ipFetchFailed = ipFetchFailed,
            onRefreshIp = onRefreshIp
        )

        // REAL-TIME LASER OSCILLOSCOPE (CYAN & COBALT)
        QuantumLaserOscilloscope(isProxyActive = isProxyActive)

        // STATUS BADGES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuantumStatusBadge(label = proxyType.name, color = Color.White)
            QuantumStatusBadge(
                label = if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP (WEBRTC)" else "TCP ONLY",
                color = Color(0xFF00F0FF)
            )
            QuantumStatusBadge(
                label = when (ipMode) {
                    IpMode.IPV4_ONLY -> "IPV4"
                    IpMode.DUAL_STACK -> "DUAL-STACK"
                    IpMode.IPV6_ONLY -> "IPV6"
                },
                color = Color(0xFF38BDF8)
            )
            if (routeHotspot) {
                QuantumStatusBadge(label = "HOTSPOT ACTIVE", color = Color(0xFF818CF8))
            }
        }

        // ACTION BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTestUpstream,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x2200F0FF)),
                border = BorderStroke(1.dp, Color(0xFF00F0FF)),
                enabled = !isTesting
            ) {
                Text(
                    text = if (isTesting) "Scanning..." else "⚡ Check If Alive",
                    color = Color(0xFF00F0FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            OutlinedButton(
                onClick = onViewLogs,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1))
            ) {
                Text("Core Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (testStatus != null) {
            Text(
                text = testStatus,
                color = if (testStatus.startsWith("Online")) Color(0xFF00F0FF) else Color(0xFFF43F5E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // MASTER LAUNCH BUTTON
        val buttonText = when {
            !isProxyActive -> "LAUNCH TRANSPARENT TUNNEL (P$activeSlot)"
            isCurrentSlotRunning -> "TERMINATE TUNNEL (DISCONNECT)"
            else -> "SWITCH TO P$activeSlot & ACTIVATE"
        }

        val buttonGrad = if (isCurrentSlotRunning) {
            Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFBE123C)))
        } else {
            Brush.horizontalGradient(listOf(Color(0xFF0070F3), Color(0xFF00F0FF), Color(0xFF38BDF8)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onToggleProxy() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(buttonGrad)
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isCurrentSlotRunning) Color.White else Color(0xFF07090E),
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// -------------------------------------------------------------
// QUANTUM SHIELD GYROSCOPE HUD CARD
// -------------------------------------------------------------

@Composable
fun QuantumGyroCard(
    isProxyActive: Boolean,
    runningSlot: Int?,
    activeSlot: Int,
    activePid: String?,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    ipFetchFailed: Boolean,
    onRefreshIp: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "gyroAnimation")

    val outerRingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (isProxyActive) 4500 else 16000, easing = LinearEasing), RepeatMode.Restart),
        label = "outerGyro"
    )

    val innerRingAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(if (isProxyActive) 2800 else 11000, easing = LinearEasing), RepeatMode.Restart),
        label = "innerGyro"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0x18111622),
        border = BorderStroke(1.2.dp, if (isProxyActive) Color(0xFF00F0FF) else Color(0x22FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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
                            .size(9.dp)
                            .background(if (isProxyActive) Color(0xFF00F0FF) else Color(0xFF475569), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyActive) "CORE RUNNING • NODE P$runningSlot" else "CORE STANDBY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isProxyActive) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                    if (isProxyActive && activePid != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PID $activePid",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isProxyActive) {
                    QuantumActiveTimer()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ANIMATED ORBITAL GYROSCOPE CORE
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val r = size.width / 2

                    // Outer Sweeping Cyan Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF00F0FF), Color(0xFF3B82F6), Color.Transparent, Color(0xFF00F0FF))
                        ),
                        startAngle = outerRingAngle,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Inner Counter-Rotating Cobalt Dashed Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color.Transparent)
                        ),
                        startAngle = innerRingAngle,
                        sweepAngle = 200f,
                        useCenter = false,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                        )
                    )

                    // Pulsing Core
                    drawCircle(
                        color = if (isProxyActive) Color(0x3500F0FF) else Color(0x10FFFFFF),
                        radius = (r * 0.46f) * if (isProxyActive) corePulse else 1f,
                        center = center
                    )
                    drawCircle(
                        color = if (isProxyActive) Color(0xFF00F0FF) else Color(0xFF475569),
                        radius = r * 0.18f,
                        center = center
                    )
                }

                Text(
                    text = if (isProxyActive) "ON" else "OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // IP & TELEMETRY TILE
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x14FFFFFF),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isProxyActive && publicIpInfo != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ProxyIP", publicIpInfo.ip))
                            Toast.makeText(context, "IP Copied: ${publicIpInfo.ip}", Toast.LENGTH_SHORT).show()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "⚪",
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isProxyActive) {
                                    when {
                                        isFetchingIp -> "Securing route..."
                                        publicIpInfo != null -> publicIpInfo.ip
                                        ipFetchFailed -> "Tap to retry"
                                        else -> "Resolving..."
                                    }
                                } else "Native Device Interface",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isProxyActive) {
                                Text(
                                    text = when {
                                        isFetchingIp -> "Querying route telemetry..."
                                        publicIpInfo != null -> "${publicIpInfo.country} • Tap to copy"
                                        ipFetchFailed -> "Timeout - Tap to retry"
                                        else -> "Synchronizing..."
                                    },
                                    fontSize = 11.sp,
                                    color = if (ipFetchFailed) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isProxyActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x2200F0FF),
                            modifier = Modifier.clickable { onRefreshIp() }
                        ) {
                            Text(
                                text = if (isFetchingIp) "..." else "Refresh",
                                fontSize = 11.sp,
                                color = Color(0xFF00F0FF),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DUAL LASER RIVER OSCILLOSCOPE (CYAN & COBALT)
// -------------------------------------------------------------

@Composable
fun QuantumLaserOscilloscope(isProxyActive: Boolean) {
    var rawRxRate by remember { mutableLongStateOf(0L) }
    var rawTxRate by remember { mutableLongStateOf(0L) }
    var totalRxBytes by remember { mutableLongStateOf(0L) }
    var totalTxBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isProxyActive) {
        if (isProxyActive) {
            var prevRx = TrafficStats.getTotalRxBytes()
            var prevTx = TrafficStats.getTotalTxBytes()
            val startRx = prevRx
            val startTx = prevTx
            var prevTime = System.currentTimeMillis()

            while (isActive) {
                delay(1000)
                val currRx = TrafficStats.getTotalRxBytes()
                val currTx = TrafficStats.getTotalTxBytes()
                val currTime = System.currentTimeMillis()
                val dt = (currTime - prevTime).coerceAtLeast(1) / 1000.0

                rawRxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0)
                rawTxRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0)
                totalRxBytes = (currRx - startRx).coerceAtLeast(0)
                totalTxBytes = (currTx - startTx).coerceAtLeast(0)

                prevRx = currRx
                prevTx = currTx
                prevTime = currTime
            }
        } else {
            rawRxRate = 0L; rawTxRate = 0L; totalRxBytes = 0L; totalTxBytes = 0L
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "laserWaveMotion")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (isProxyActive) 1400 else 4500, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -(2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (isProxyActive) 2100 else 6000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2"
    )

    val mbps = ((rawRxRate + rawTxRate) * 8.0) / (1024.0 * 1024.0)
    val dynamicAmp = if (!isProxyActive) 4f else (7f + (mbps * 2.2f).toFloat()).coerceIn(7f, 26f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x18111622),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1800F0FF),
                    border = BorderStroke(1.dp, Color(0x3300F0FF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("DOWNLOAD", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF00F0FF), letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = splitSpeedValue(rawRxRate),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = splitSpeedUnit(rawRxRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F0FF),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Total: ${formatBytes(totalRxBytes)}",
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1838BDF8),
                    border = BorderStroke(1.dp, Color(0x3338BDF8)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("UPLOAD", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF38BDF8), letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = splitSpeedValue(rawTxRate),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = splitSpeedUnit(rawTxRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Total: ${formatBytes(totalTxBytes)}",
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Dual Sine Wave River Canvas (Cyan & Cobalt)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0EFFFFFF))
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val w = size.width
                    val h = size.height
                    val midY = h * 0.50f

                    val path1 = Path()
                    val fill1 = Path()
                    fill1.moveTo(0f, h)

                    val steps = 50
                    for (i in 0..steps) {
                        val x = (i.toFloat() / steps) * w
                        val nx = (i.toFloat() / steps) * (3 * PI).toFloat()
                        val y = midY + (sin(nx + phase2) * (dynamicAmp * 0.70f)).toFloat()
                        if (i == 0) {
                            path1.moveTo(x, y)
                            fill1.lineTo(x, y)
                        } else {
                            path1.lineTo(x, y)
                            fill1.lineTo(x, y)
                        }
                    }
                    fill1.lineTo(w, h)
                    fill1.close()

                    if (isProxyActive) {
                        drawPath(fill1, Brush.verticalGradient(listOf(Color(0x2238BDF8), Color.Transparent)))
                    }
                    drawPath(
                        path = path1,
                        color = if (isProxyActive) Color(0xFF38BDF8) else Color(0x22475569),
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val path2 = Path()
                    val fill2 = Path()
                    fill2.moveTo(0f, h)

                    for (i in 0..steps) {
                        val x = (i.toFloat() / steps) * w
                        val nx = (i.toFloat() / steps) * (4 * PI).toFloat()
                        val y = midY + (sin(nx + phase1) * dynamicAmp).toFloat()
                        if (i == 0) {
                            path2.moveTo(x, y)
                            fill2.lineTo(x, y)
                        } else {
                            path2.lineTo(x, y)
                            fill2.lineTo(x, y)
                        }
                    }
                    fill2.lineTo(w, h)
                    fill2.close()

                    if (isProxyActive) {
                        drawPath(fill2, Brush.verticalGradient(listOf(Color(0x3500F0FF), Color.Transparent)))
                    }
                    drawPath(
                        path = path2,
                        brush = Brush.horizontalGradient(
                            listOf(
                                if (isProxyActive) Color(0xFF00F0FF) else Color(0x33475569),
                                if (isProxyActive) Color(0xFF38BDF8) else Color(0x33475569)
                            )
                        ),
                        style = Stroke(width = if (isProxyActive) 2.2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VIEW 2: SERVER & CONFIG DECK
// -------------------------------------------------------------

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
    onResetSlot: () -> Unit,
    getCurrentSettings: () -> ProxySettings
) {
    val coroutineScope = rememberCoroutineScope()
    var isCheckingAlive by remember { mutableStateOf(false) }
    var aliveResult by remember { mutableStateOf<TestResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x18111622),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Server Health Check", fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Direct socket test without starting tunnel", fontSize = 10.5.sp, color = Color(0xFF64748B))
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
                                val res = ProxyTester.testProxy(getCurrentSettings())
                                aliveResult = res
                                isCheckingAlive = false
                            }
                        },
                        enabled = !isCheckingAlive,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF), contentColor = Color(0xFF07090E))
                    ) {
                        Text(
                            text = if (isCheckingAlive) "Testing..." else "Test Socket",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (aliveResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    when (val res = aliveResult) {
                        is TestResult.Success -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x2200F0FF),
                                border = BorderStroke(1.dp, Color(0xFF00F0FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "PROXY ALIVE • ⚡ ${res.latencyMs} ms",
                                    color = Color(0xFF00F0FF),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        is TestResult.Failure -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x22F43F5E),
                                border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "PROXY DEAD • ${res.error}",
                                    color = Color(0xFFF43F5E),
                                    fontSize = 11.5.sp,
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

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x18111622),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SLOT P$activeSlot PARAMETERS", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF00F0FF), letterSpacing = 1.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x22F43F5E),
                        border = BorderStroke(1.dp, Color(0x55F43F5E)),
                        modifier = Modifier.clickable { onResetSlot() }
                    ) {
                        Text(
                            text = "Reset P$activeSlot",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFF43F5E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B), letterSpacing = 0.8.sp)
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
                            label = { Text(t.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00F0FF),
                                selectedLabelColor = Color(0xFF07090E)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("TRANSPORT (WEBRTC INTEGRITY)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B), letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_AND_UDP,
                        onClick = { onTransportModeChange(TransportMode.TCP_AND_UDP); onConfigChanged() },
                        label = { Text("TCP + UDP (WebRTC)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00F0FF),
                            selectedLabelColor = Color(0xFF07090E)
                        )
                    )
                    FilterChip(
                        selected = transportMode == TransportMode.TCP_ONLY,
                        onClick = { onTransportModeChange(TransportMode.TCP_ONLY); onConfigChanged() },
                        label = { Text("TCP Only", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("IP STACK MODE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B), letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ipModes = listOf(
                        Pair(IpMode.IPV4_ONLY, "IPv4"),
                        Pair(IpMode.DUAL_STACK, "Dual-Stack"),
                        Pair(IpMode.IPV6_ONLY, "IPv6")
                    )
                    ipModes.forEach { (mode, lbl) ->
                        FilterChip(
                            selected = ipMode == mode,
                            onClick = { onIpModeChange(mode); onConfigChanged() },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { onHostChange(it); onConfigChanged() },
                    label = { Text("Server Host / IP") },
                    placeholder = { Text("e.g. 48.45.153.215 or proxy.domain.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = { onPortChange(it); onConfigChanged() },
                    label = { Text("Server Port") },
                    placeholder = { Text("1080") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                when (proxyType) {
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
                                        color = Color(0xFF00F0FF),
                                        modifier = Modifier.clickable { passVisible = !passVisible }.padding(end = 10.dp)
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
                            placeholder = { Text("2022-blake3-aes-128-gcm or aes-128-gcm") },
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
                                    color = Color(0xFF00F0FF),
                                    modifier = Modifier.clickable { passVisible = !passVisible }.padding(end = 10.dp)
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
                            label = { Text("UUID") },
                            placeholder = { Text("e.g. 550e8400-e29b-41d4-a716-446655440000") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { onSniChange(it); onConfigChanged() },
                            label = { Text("SNI / Server Name") },
                            placeholder = { Text("e.g. gateway.cloudflare.com") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = realityPublicKey,
                                onValueChange = { onRealityPublicKeyChange(it); onConfigChanged() },
                                label = { Text("Reality PK") },
                                placeholder = { Text("Optional") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = realityShortId,
                                onValueChange = { onRealityShortIdChange(it); onConfigChanged() },
                                label = { Text("Reality SID") },
                                placeholder = { Text("Optional") },
                                modifier = Modifier.weight(1f),
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
                            label = { Text("Auth Password") },
                            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Text(
                                    text = if (passVisible) "Hide" else "Show",
                                    fontSize = 11.sp,
                                    color = Color(0xFF00F0FF),
                                    modifier = Modifier.clickable { passVisible = !passVisible }.padding(end = 10.dp)
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
                            label = { Text("SNI / Server Name") },
                            placeholder = { Text("e.g. yourdomain.com") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    ProxyType.SOCKS4 -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// -------------------------------------------------------------
// VIEW 3: APPS ROUTING & MASTER SETTINGS
// -------------------------------------------------------------

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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x18111622),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("GLOBAL SYSTEM RULES", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF00F0FF), letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start on Boot", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (startOnBoot) "P$bootSlot starts automatically on system boot" else "Disabled — tunnel starts manually",
                            fontSize = 11.sp,
                            color = if (startOnBoot) Color(0xFF00F0FF) else Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBootChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF07090E),
                            checkedTrackColor = Color(0xFF00F0FF)
                        )
                    )
                }

                if (startOnBoot) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("STARTUP PROFILE TARGET", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B), letterSpacing = 0.8.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (0..4).forEach { s ->
                            FilterChip(
                                selected = bootSlot == s,
                                onClick = { onBootSlotChange(s) },
                                label = { Text("P$s", fontSize = 11.sp, fontWeight = FontWeight.Black) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00F0FF),
                                    selectedLabelColor = Color(0xFF07090E)
                                )
                            )
                        }
                    }
                }

                Divider(color = Color(0x14FFFFFF), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share via Hotspot / Tethering", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = "Route laptop & connected Wi-Fi/USB clients",
                            fontSize = 11.sp,
                            color = if (routeHotspot) Color(0xFF00F0FF) else Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = routeHotspot,
                        onCheckedChange = onRouteHotspotChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF07090E),
                            checkedTrackColor = Color(0xFF00F0FF)
                        )
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x18111622),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Route Entire Profile", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (routeWholeProfile) "All applications tunneled" else "Per-App filter active (${selectedPackages.size} selected)",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = onRouteWholeProfileChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF07090E),
                            checkedTrackColor = Color(0xFF00F0FF)
                        )
                    )
                }

                if (!routeWholeProfile) {
                    Spacer(modifier = Modifier.height(10.dp))

                    var query by remember { mutableStateOf("") }
                    val filtered = remember(query, installedApps) {
                        if (query.isEmpty()) installedApps else installedApps.filter {
                            it.name.contains(query, true) || it.packageName.contains(query, true)
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Filter applications...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onSelectAll) { Text("Select All", color = Color(0xFF00F0FF), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = onClearAll) { Text("Clear All", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.packageName }) { app ->
                            val isChecked = selectedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleAppSelection(app.packageName) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${app.packageName} • UID ${app.uid}", color = Color(0xFF64748B), fontSize = 10.sp)
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleAppSelection(app.packageName) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00F0FF),
                                        checkmarkColor = Color(0xFF07090E)
                                    )
                                )
                            }
                            Divider(color = Color(0x0EFFFFFF), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

// -------------------------------------------------------------
// REUSABLE BADGE & TIMERS
// -------------------------------------------------------------

@Composable
fun QuantumStatusBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0x18FFFFFF),
        border = BorderStroke(1.dp, Color(0x22FFFFFF))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun QuantumActiveTimer() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (isActive) {
            delay(1000)
            seconds = (System.currentTimeMillis() - start) / 1000
        }
    }

    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    val timeStr = if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs) else String.format("%02d:%02d", mins, secs)

    Text(
        text = timeStr,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = Color(0xFF00F0FF),
        fontWeight = FontWeight.Black
    )
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
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
