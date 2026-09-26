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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
                    onStartProxy = { settings, selectedUids, onResult ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = ProxyController.startProxy(this@MainActivity, settings, selectedUids)
                            withContext(Dispatchers.Main) {
                                syncDaemonStatus()
                                onResult(result.success, result.errorMessage)
                            }
                        }
                    },
                    onStopProxy = { onResult ->
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
            rootLabel = "Requesting..."
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

    fun loadSlotData(slot: Int) {
        val prefs = getSlotPrefs(slot)
        host = prefs.getString("host", "") ?: ""
        port = prefs.getString("port", "1080") ?: "1080"
        username = prefs.getString("username", "") ?: ""
        password = prefs.getString("password", "") ?: ""
        sni = prefs.getString("sni", "") ?: ""
        ssMethod = prefs.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm"
        realityPublicKey = prefs.getString("reality_pk", "") ?: ""
        realityShortId = prefs.getString("reality_sid", "") ?: ""
        proxyType = try { ProxyType.valueOf(prefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name) } catch (e: Exception) { ProxyType.SOCKS5 }
        transportMode = try { TransportMode.valueOf(prefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name) } catch (e: Exception) { TransportMode.TCP_AND_UDP }
        ipMode = try { IpMode.valueOf(prefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name) } catch (e: Exception) { IpMode.IPV4_ONLY }
        routeWholeProfile = prefs.getBoolean("route_whole_profile", true)
        selectedPackages = prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
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
            delay(800)
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
            .background(Color(0xFF07080D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. TACTICAL TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NAMELESS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "USER ${ProfileManager.androidUserId} • VIEWING P$activeSlot • KERNEL TUNNEL",
                        fontSize = 10.sp,
                        color = if (isProxyActive) Color(0xFF10B981) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x18FFFFFF),
                    border = BorderStroke(
                        1.dp,
                        when (rootState) {
                            RootState.GRANTED -> Color(0x8810B981)
                            RootState.DENIED -> Color(0x88F43F5E)
                            RootState.CHECKING -> Color(0x88F59E0B)
                        }
                    ),
                    modifier = Modifier.clickable { onRecheckRoot() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = when (rootState) {
                                        RootState.GRANTED -> Color(0xFF10B981)
                                        RootState.DENIED -> Color(0xFFF43F5E)
                                        RootState.CHECKING -> Color(0xFFF59E0B)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (rootState) {
                                RootState.GRANTED -> "ROOT ON"
                                RootState.DENIED -> "NO ROOT"
                                RootState.CHECKING -> "CHECKING"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. PROFILE SLOT RIBBON (P0 - P4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..4).forEach { slotIndex ->
                    val isSelected = slotIndex == activeSlot
                    val isRunning = isProxyActive && (runningSlot == slotIndex)
                    val isBootTarget = startOnBoot && (slotIndex == bootSlot)
                    val slotBg by animateColorAsState(
                        targetValue = when {
                            isRunning -> Color(0x3310B981)
                            isSelected -> Color(0x228B5CF6)
                            else -> Color(0x14FFFFFF)
                        },
                        animationSpec = tween(200),
                        label = "slotBg"
                    )
                    val borderColor = when {
                        isRunning -> Color(0xFF10B981)
                        isSelected -> Color(0xFFA855F7)
                        else -> Color(0x1AFFFFFF)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = slotBg,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { switchSlot(slotIndex) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "P$slotIndex",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || isRunning) FontWeight.Black else FontWeight.Bold,
                                color = when {
                                    isRunning -> Color(0xFF10B981)
                                    isSelected -> Color(0xFFA855F7)
                                    else -> Color(0xFF94A3B8)
                                }
                            )
                            if (isRunning) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981)
                                )
                            } else if (isBootTarget) {
                                Text(
                                    text = "BOOT",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. iOS SEGMENTED GLASS BAR (Lag-Free Isolated Rendering)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x12FFFFFF),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("Console", "Server & Config", "Apps & Master")
                    tabs.forEachIndexed { index, title ->
                        val isTabSelected = selectedNavTab == index
                        val tabBg by animateColorAsState(
                            targetValue = if (isTabSelected) Color(0x358B5CF6) else Color.Transparent,
                            label = "tabBg"
                        )
                        val tabTextColor by animateColorAsState(
                            targetValue = if (isTabSelected) Color.White else Color(0xFF94A3B8),
                            label = "tabText"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tabBg)
                                .border(
                                    1.dp,
                                    if (isTabSelected) Color(0x55A855F7) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedNavTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tabTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. TAB VIEWS (Fluid 120Hz Hardware Accelerated)
            when (selectedNavTab) {
                0 -> {
                    // CONSOLE TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ConsoleHUDTab(
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
                                        testStatus = "Please enter Server Host/IP in Server & Config for P$activeSlot"
                                        selectedNavTab = 1
                                        return@ConsoleHUDTab
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
                                    return@ConsoleHUDTab
                                }
                                saveConfigForSlot(activeSlot)
                                isTesting = true
                                testStatus = "Testing socket..."
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
                }

                1 -> {
                    // SERVER & CONFIG TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // SERVER HEALTH CHECK CARD (Check If Alive)
                        var isCheckingAlive by remember { mutableStateOf(false) }
                        var aliveCheckResult by remember { mutableStateOf<TestResult?>(null) }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x14FFFFFF),
                            border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Server Health Check",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Direct socket test without starting tunnel",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (host.trim().isEmpty()) {
                                                aliveCheckResult = TestResult.Failure("Host is empty")
                                                return@Button
                                            }
                                            isCheckingAlive = true
                                            aliveCheckResult = null
                                            coroutineScope.launch {
                                                val currentSettings = getCurrentSettings()
                                                val res = ProxyTester.testProxy(currentSettings)
                                                aliveCheckResult = res
                                                isCheckingAlive = false
                                            }
                                        },
                                        enabled = !isCheckingAlive,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color(0xFF020408)
                                        )
                                    ) {
                                        Text(
                                            text = if (isCheckingAlive) "Checking..." else "Check If Alive",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (aliveCheckResult != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    when (val result = aliveCheckResult) {
                                        is TestResult.Success -> {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0x2210B981),
                                                border = BorderStroke(1.dp, Color(0x6610B981)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "PROXY ALIVE • Latency: ${result.latencyMs} ms",
                                                        color = Color(0xFF10B981),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                        is TestResult.Failure -> {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0x22F43F5E),
                                                border = BorderStroke(1.dp, Color(0x66F43F5E)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFF43F5E), CircleShape))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "PROXY DEAD • ${result.error}",
                                                        color = Color(0xFFF43F5E),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                        null -> {}
                                    }
                                }
                            }
                        }

                        // PROFILE SETTINGS CARD
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x14FFFFFF),
                            border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("PROFILE P$activeSlot SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFA855F7), letterSpacing = 1.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0x22F43F5E),
                                            border = BorderStroke(1.dp, Color(0x44F43F5E)),
                                            modifier = Modifier.clickable {
                                                val prefs = getSlotPrefs(activeSlot)
                                                prefs.edit().clear().apply()
                                                PersistentStorage.clearBackup(activeSlot, ProfileManager.androidUserId)
                                                loadSlotData(activeSlot)
                                                Toast.makeText(context, "P$activeSlot reset to empty", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text(
                                                text = "Reset P$activeSlot",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF43F5E),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0x228B5CF6),
                                            border = BorderStroke(1.dp, Color(0x44A855F7))
                                        ) {
                                            Text(
                                                text = "SLOT $activeSlot",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFA855F7),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text("PROTOCOL TYPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProxyType.values().forEach { type ->
                                        FilterChip(
                                            selected = proxyType == type,
                                            onClick = { proxyType = type; saveConfigForSlot(activeSlot) },
                                            label = { Text(type.name) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("TRANSPORT PROTOCOL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val transportOptions = listOf(
                                        Pair(TransportMode.TCP_AND_UDP, "TCP + UDP (WebRTC)"),
                                        Pair(TransportMode.TCP_ONLY, "TCP Only")
                                    )
                                    transportOptions.forEach { item ->
                                        val mode = item.first
                                        val label = item.second
                                        FilterChip(
                                            selected = transportMode == mode,
                                            onClick = { transportMode = mode; saveConfigForSlot(activeSlot) },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("IP MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val ipOptions = listOf(
                                        Pair(IpMode.IPV4_ONLY, "IPv4"),
                                        Pair(IpMode.DUAL_STACK, "Dual-Stack"),
                                        Pair(IpMode.IPV6_ONLY, "IPv6")
                                    )
                                    ipOptions.forEach { item ->
                                        val mode = item.first
                                        val label = item.second
                                        FilterChip(
                                            selected = ipMode == mode,
                                            onClick = { ipMode = mode; saveConfigForSlot(activeSlot) },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it; saveConfigForSlot(activeSlot) },
                                    label = { Text("Server Host / IP") },
                                    placeholder = { Text("e.g. 192.168.1.100 or proxy.domain.com") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = port,
                                    onValueChange = { port = it; saveConfigForSlot(activeSlot) },
                                    label = { Text("Server Port") },
                                    placeholder = { Text("1080") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                when (proxyType) {
                                    ProxyType.SOCKS5, ProxyType.HTTP -> {
                                        var passwordVisible by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = username,
                                                onValueChange = { username = it; saveConfigForSlot(activeSlot) },
                                                label = { Text("Username") },
                                                placeholder = { Text("Optional") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = password,
                                                onValueChange = { password = it; saveConfigForSlot(activeSlot) },
                                                label = { Text("Password") },
                                                placeholder = { Text("Optional") },
                                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    Text(
                                                        text = if (passwordVisible) "Hide" else "Show",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF10B981),
                                                        modifier = Modifier
                                                            .clickable { passwordVisible = !passwordVisible }
                                                            .padding(end = 12.dp)
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                singleLine = true
                                            )
                                        }
                                    }

                                    ProxyType.SHADOWSOCKS -> {
                                        var passwordVisible by remember { mutableStateOf(false) }
                                        OutlinedTextField(
                                            value = ssMethod,
                                            onValueChange = { ssMethod = it; saveConfigForSlot(activeSlot) },
                                            label = { Text("Cipher / Method") },
                                            placeholder = { Text("2022-blake3-aes-128-gcm or aes-128-gcm") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it; saveConfigForSlot(activeSlot) },
                                            label = { Text("Password / Pre-Shared Key") },
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                Text(
                                                    text = if (passwordVisible) "Hide" else "Show",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF10B981),
                                                    modifier = Modifier
                                                        .clickable { passwordVisible = !passwordVisible }
                                                        .padding(end = 12.dp)
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
                                            onValueChange = { password = it; saveConfigForSlot(activeSlot) },
                                            label = { Text("UUID") },
                                            placeholder = { Text("e.g. 550e8400-e29b-41d4-a716-446655440000") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = sni,
                                            onValueChange = { sni = it; saveConfigForSlot(activeSlot) },
                                            label = { Text("SNI / Server Name") },
                                            placeholder = { Text("e.g. gateway.cloudflare.com") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = realityPublicKey,
                                                onValueChange = { realityPublicKey = it; saveConfigForSlot(activeSlot) },
                                                label = { Text("Reality Public Key") },
                                                placeholder = { Text("Optional") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = realityShortId,
                                                onValueChange = { realityShortId = it; saveConfigForSlot(activeSlot) },
                                                label = { Text("Reality Short ID") },
                                                placeholder = { Text("Optional") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                singleLine = true
                                            )
                                        }
                                    }

                                    ProxyType.TROJAN, ProxyType.HYSTERIA2 -> {
                                        var passwordVisible by remember { mutableStateOf(false) }
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it; saveConfigForSlot(activeSlot) },
                                            label = { Text("Auth Password") },
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                Text(
                                                    text = if (passwordVisible) "Hide" else "Show",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF10B981),
                                                    modifier = Modifier
                                                        .clickable { passwordVisible = !passwordVisible }
                                                        .padding(end = 12.dp)
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = sni,
                                            onValueChange = { sni = it; saveConfigForSlot(activeSlot) },
                                            label = { Text("SNI / Server Name") },
                                            placeholder = { Text("e.g. yourdomain.com") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
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

                2 -> {
                    // APPS & MASTER CONTROLS TAB
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // GLOBAL MASTER SETTINGS CARD
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x14FFFFFF),
                            border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("GLOBAL MASTER SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Start on Boot",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (startOnBoot) "P$bootSlot starts automatically on system boot" else "Disabled — proxy will not run on boot",
                                            fontSize = 11.sp,
                                            color = if (startOnBoot) Color(0xFF10B981) else Color(0xFF64748B)
                                        )
                                    }
                                    Switch(
                                        checked = startOnBoot,
                                        onCheckedChange = { enabled ->
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
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF020408),
                                            checkedTrackColor = Color(0xFF10B981)
                                        )
                                    )
                                }

                                if (startOnBoot) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("DESIGNATE STARTUP PROFILE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 0.8.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        (0..4).forEach { s ->
                                            val isTarget = bootSlot == s
                                            FilterChip(
                                                selected = isTarget,
                                                onClick = {
                                                    bootSlot = s
                                                    globalPrefs.edit().putInt("boot_slot", s).apply()
                                                    saveConfigForSlot(activeSlot)
                                                    Toast.makeText(context, "P$s will start on boot", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text("P$s") },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF38BDF8),
                                                    selectedLabelColor = Color(0xFF020408)
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
                                        Text(
                                            text = "Share via Hotspot / Tethering",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Route connected Wi-Fi AP & USB tethered devices",
                                            fontSize = 11.sp,
                                            color = if (routeHotspot) Color(0xFF10B981) else Color(0xFF64748B)
                                        )
                                    }
                                    Switch(
                                        checked = routeHotspot,
                                        onCheckedChange = {
                                            routeHotspot = it
                                            globalPrefs.edit().putBoolean("route_hotspot", it).apply()
                                            saveConfigForSlot(activeSlot)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF020408),
                                            checkedTrackColor = Color(0xFF10B981)
                                        )
                                    )
                                }
                            }
                        }

                        // PER-APP ROUTING FILTER CARD
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x14FFFFFF),
                            border = BorderStroke(1.dp, Color(0x24FFFFFF)),
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
                                        Text(
                                            text = "Route Entire Profile",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (routeWholeProfile) "All applications redirected" else "Per-App filter active (${selectedPackages.size} selected)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Switch(
                                        checked = routeWholeProfile,
                                        onCheckedChange = {
                                            routeWholeProfile = it
                                            saveConfigForSlot(activeSlot)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF020408),
                                            checkedTrackColor = Color(0xFF10B981)
                                        )
                                    )
                                }

                                if (!routeWholeProfile) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    var appSearchQuery by remember { mutableStateOf("") }
                                    val filteredApps = remember(appSearchQuery, installedApps) {
                                        if (appSearchQuery.isEmpty()) installedApps
                                        else installedApps.filter {
                                            it.name.contains(appSearchQuery, ignoreCase = true) || it.packageName.contains(appSearchQuery, ignoreCase = true)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = appSearchQuery,
                                        onValueChange = { appSearchQuery = it },
                                        label = { Text("Search applications...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(onClick = {
                                            selectedPackages = installedApps.map { it.packageName }.toSet()
                                            saveConfigForSlot(activeSlot)
                                        }) { Text("Select All", color = Color(0xFF10B981)) }
                                        TextButton(onClick = {
                                            selectedPackages = emptySet()
                                            saveConfigForSlot(activeSlot)
                                        }) { Text("Clear All", color = Color(0xFF94A3B8)) }
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filteredApps, key = { it.packageName }) { app ->
                                            val isChecked = selectedPackages.contains(app.packageName)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedPackages = if (isChecked) selectedPackages - app.packageName else selectedPackages + app.packageName
                                                        saveConfigForSlot(activeSlot)
                                                    }
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = app.name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "${app.packageName} • UID: ${app.uid}",
                                                        color = Color(0xFF64748B),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        selectedPackages = if (it) selectedPackages + app.packageName else selectedPackages - app.packageName
                                                        saveConfigForSlot(activeSlot)
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color(0xFF10B981),
                                                        checkmarkColor = Color(0xFF020408)
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
                            Text("Copy")
                        }
                        TextButton(onClick = {
                            val targetSlot = runningSlot ?: activeSlot
                            ProxyController.clearLogs(ProfileManager.androidUserId, targetSlot)
                            currentLogs = "Logs cleared."
                        }) {
                            Text("Clear")
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
                            Text("Refresh")
                        }
                        TextButton(onClick = { showLogsDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            },
            title = { Text("Core Diagnostics & Live Logs") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color(0xFF08080C), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = currentLogs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFC0C0C5),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        )
    }
}

@Composable
fun ConsoleHUDTab(
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

    val borderBrush = if (isProxyActive) {
        Brush.sweepGradient(
            colors = listOf(Color(0xFF10B981), Color(0xFF06B6D4), Color(0x3310B981), Color(0xFF10B981))
        )
    } else {
        Brush.sweepGradient(
            colors = listOf(Color(0x22FFFFFF), Color(0x338B5CF6), Color(0x11FFFFFF), Color(0x22FFFFFF))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, borderBrush, RoundedCornerShape(24.dp))
            .background(Color(0x14FFFFFF))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isProxyActive) Color(0xFF10B981) else Color(0xFF64748B), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isCurrentSlotRunning -> "TUNNEL ONLINE (P$activeSlot)"
                            isProxyActive -> "TUNNEL ONLINE (P$runningSlot ACTIVE)"
                            else -> "TUNNEL OFFLINE"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isProxyActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )
                    if (isProxyActive && activePid != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• PID $activePid",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isProxyActive) {
                    ActiveTimer()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LiveThroughputRiverEngine(isProxyActive = isProxyActive)

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                        ipFetchFailed -> "Timeout - Tap to retry"
                                        else -> "Resolving..."
                                    }
                                } else "Native Device Interface",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProxyActive) Color.White else Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace
                            )
                            if (isProxyActive) {
                                Text(
                                    text = when {
                                        isFetchingIp -> "Querying route telemetry..."
                                        publicIpInfo != null -> "${publicIpInfo.country} • Tap to copy"
                                        ipFetchFailed -> "Timeout - Tap to retry"
                                        else -> "Stabilizing..."
                                    },
                                    fontSize = 11.sp,
                                    color = if (ipFetchFailed) Color(0xFFFBBF24) else Color(0xFF06B6D4),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (isProxyActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x2210B981),
                            modifier = Modifier.clickable { onRefreshIp() }
                        ) {
                            Text(
                                text = if (isFetchingIp) "..." else "Refresh",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pillModifier = Modifier
                    .background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)

                Text(proxyType.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = pillModifier)
                Text(
                    if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP" else "TCP Only",
                    fontSize = 10.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    modifier = pillModifier
                )
                Text(
                    when (ipMode) {
                        IpMode.IPV4_ONLY -> "IPv4"
                        IpMode.DUAL_STACK -> "Dual-Stack"
                        IpMode.IPV6_ONLY -> "IPv6"
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF06B6D4),
                    fontWeight = FontWeight.Bold,
                    modifier = pillModifier
                )
                if (routeHotspot) {
                    Text(
                        "HOTSPOT ON",
                        fontSize = 10.sp,
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        modifier = pillModifier
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // LIVE "CHECK IF ALIVE" AND ACTION BUTTON ON DASHBOARD
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onTestUpstream,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x228B5CF6)),
            border = BorderStroke(1.dp, Color(0x66A855F7)),
            enabled = !isTesting
        ) {
            Text(
                if (isTesting) "Checking..." else "⚡ Check If Alive",
                color = Color(0xFFA855F7),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onViewLogs,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
        ) {
            Text("Core Logs", fontSize = 12.sp)
        }
    }

    if (testStatus != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = testStatus,
            color = if (testStatus.startsWith("Online")) Color(0xFF10B981) else Color(0xFFF43F5E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    val buttonText = when {
        !isProxyActive -> "CONNECT TRANSPARENT PROXY (P$activeSlot)"
        isCurrentSlotRunning -> "DISCONNECT PROXY"
        else -> "SWITCH TO P$activeSlot & CONNECT"
    }

    val isDisconnecting = isCurrentSlotRunning

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onToggleProxy() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDisconnecting) {
                        Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFE11D48)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF06B6D4), Color(0xFF10B981)))
                    }
                )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 0.8.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun LiveThroughputRiverEngine(isProxyActive: Boolean) {
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
            rawRxRate = 0L
            rawTxRate = 0L
            totalRxBytes = 0L
            totalTxBytes = 0L
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "riverMotion")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProxyActive) 1500 else 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -(2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProxyActive) 2200 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val mbps = ((rawRxRate + rawTxRate) * 8.0) / (1024.0 * 1024.0)
    val dynamicAmp = if (!isProxyActive) 4f else (8f + (mbps * 2f).toFloat()).coerceIn(8f, 26f)

    Column(modifier = Modifier.fillMaxWidth().graphicsLayer()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x14FFFFFF),
                border = BorderStroke(1.dp, Color(0x2210B981)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF10B981), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DOWNLOAD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = splitSpeedValue(rawRxRate),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = splitSpeedUnit(rawRxRate),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Total: ${formatBytes(totalRxBytes)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x14FFFFFF),
                border = BorderStroke(1.dp, Color(0x2206B6D4)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF06B6D4), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPLOAD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF06B6D4),
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = splitSpeedValue(rawTxRate),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = splitSpeedUnit(rawTxRate),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF06B6D4),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Total: ${formatBytes(totalTxBytes)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val w = size.width
                val h = size.height
                val midY = h * 0.50f

                val path1 = Path()
                val fill1 = Path()
                fill1.moveTo(0f, h)

                val points = 50
                for (i in 0..points) {
                    val x = (i.toFloat() / points) * w
                    val nx = (i.toFloat() / points) * (3 * PI).toFloat()
                    val y = midY + (sin(nx + wavePhase2) * (dynamicAmp * 0.70f)).toFloat()

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
                    drawPath(
                        path = fill1,
                        brush = Brush.verticalGradient(listOf(Color(0x2006B6D4), Color.Transparent))
                    )
                }

                drawPath(
                    path = path1,
                    color = if (isProxyActive) Color(0x6606B6D4) else Color(0x1A475569),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )

                val path2 = Path()
                val fill2 = Path()
                fill2.moveTo(0f, h)

                for (i in 0..points) {
                    val x = (i.toFloat() / points) * w
                    val nx = (i.toFloat() / points) * (4 * PI).toFloat()
                    val y = midY + (sin(nx + wavePhase1) * dynamicAmp).toFloat()

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
                    drawPath(
                        path = fill2,
                        brush = Brush.verticalGradient(listOf(Color(0x3010B981), Color.Transparent))
                    )
                }

                drawPath(
                    path = path2,
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (isProxyActive) Color(0xFF10B981) else Color(0x33475569),
                            if (isProxyActive) Color(0xFF06B6D4) else Color(0x33475569)
                        )
                    ),
                    style = Stroke(width = if (isProxyActive) 2.2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun ActiveTimer() {
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
        color = Color(0xFF10B981),
        fontWeight = FontWeight.Bold
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
