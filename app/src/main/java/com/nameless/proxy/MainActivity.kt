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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    private var activePidState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()

        val prefs = getSharedPreferences("nameless_proxy_config", Context.MODE_PRIVATE)
        val startOnBoot = prefs.getBoolean("start_on_boot", false)
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
                    activePid = activePidState,
                    onRecheckRoot = { refreshRootStatus() },
                    installedApps = installedApps,
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
            val running = ProxyController.isRunning()
            val pid = if (running) ProxyController.getActivePid() else null
            withContext(Dispatchers.Main) {
                isProxyRunningState = running
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
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.contains("chrome") }
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
    activePid: String?,
    onRecheckRoot: () -> Unit,
    installedApps: List<AppItem>,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nameless_proxy_config", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) }

    var proxyType by remember {
        mutableStateOf(
            try {
                ProxyType.valueOf(prefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name)
            } catch (_: Exception) { ProxyType.SOCKS5 }
        )
    }

    var transportMode by remember {
        mutableStateOf(
            try {
                TransportMode.valueOf(prefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name)
            } catch (_: Exception) { TransportMode.TCP_AND_UDP }
        )
    }

    var ipMode by remember {
        mutableStateOf(
            try {
                IpMode.valueOf(prefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name)
            } catch (_: Exception) { IpMode.IPV4_ONLY }
        )
    }

    var host by remember { mutableStateOf(prefs.getString("host", "48.45.153.215") ?: "48.45.153.215") }
    var port by remember { mutableStateOf(prefs.getString("port", "46508") ?: "46508") }
    var username by remember { mutableStateOf(prefs.getString("username", "FgCH4MnS3EQDohq") ?: "FgCH4MnS3EQDohq") }
    var password by remember { mutableStateOf(prefs.getString("password", "SzrAO5ADxzz81RP") ?: "SzrAO5ADxzz81RP") }
    var routeWholeProfile by remember { mutableStateOf(prefs.getBoolean("route_whole_profile", true)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }

    fun getCurrentSettings(): ProxySettings {
        return ProxySettings(
            type = proxyType,
            transportMode = transportMode,
            ipMode = ipMode,
            host = host.trim(),
            port = port.toIntOrNull() ?: 1080,
            username = username.trim(),
            password = password.trim()
        )
    }

    fun saveConfig() {
        prefs.edit()
            .putString("proxy_type", proxyType.name)
            .putString("transport_mode", transportMode.name)
            .putString("ip_mode", ipMode.name)
            .putString("host", host.trim())
            .putString("port", port.trim())
            .putString("username", username.trim())
            .putString("password", password.trim())
            .putBoolean("route_whole_profile", routeWholeProfile)
            .putBoolean("start_on_boot", startOnBoot)
            .apply()

        BootManager.syncBootState(context, startOnBoot, getCurrentSettings())
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("") }

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }
    var ipFetchFailed by remember { mutableStateOf(false) }
    var selectedUids by remember { mutableStateOf(setOf<Int>()) }

    val coroutineScope = rememberCoroutineScope()

    fun triggerPublicIpCheck() {
        isFetchingIp = true
        ipFetchFailed = false
        coroutineScope.launch {
            delay(500)
            var result = IpFetcher.getPublicIpInfo()
            if (result == null) {
                delay(1000)
                result = IpFetcher.getPublicIpInfo()
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

    LaunchedEffect(isProxyActive) {
        if (isProxyActive) {
            triggerPublicIpCheck()
        } else {
            publicIpInfo = null
            ipFetchFailed = false
        }
    }

    // OLED Deep Base with Aurora Top Pools
    val auroraMintAlpha by animateFloatAsState(
        targetValue = if (isProxyActive) 0.18f else 0.04f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "mintAlpha"
    )
    val auroraCyanAlpha by animateFloatAsState(
        targetValue = if (isProxyActive) 0.22f else 0.02f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "cyanAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Ambient Fluid Aurora Pools
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = (-40).dp, y = (-20).dp)
                .alpha(auroraMintAlpha)
                .blur(90.dp)
                .background(Color(0xFF00F5A0), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(380.dp)
                .offset(x = 120.dp, y = 40.dp)
                .alpha(auroraCyanAlpha)
                .blur(100.dp)
                .background(Color(0xFF00D9F5), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Navigation & Root Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nameless",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.8).sp
                    )
                    Text(
                        text = "Profile ${ProfileManager.profileId} • Transparent Core",
                        fontSize = 11.sp,
                        color = if (isProxyActive) Color(0xFF00F5A0) else Color(0xFF475569),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Minimalist Glass Root Capsule
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0x0FFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (rootState) {
                            RootState.GRANTED -> Color(0x3300F5A0)
                            RootState.DENIED -> Color(0x33FF4466)
                            RootState.CHECKING -> Color(0x33FFB74D)
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
                                        RootState.GRANTED -> Color(0xFF00F5A0)
                                        RootState.DENIED -> Color(0xFFFF4466)
                                        RootState.CHECKING -> Color(0xFFFFB74D)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (rootState) {
                                RootState.GRANTED -> "Root Active"
                                RootState.DENIED -> "No Root"
                                RootState.CHECKING -> "Checking..."
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Minimalist Floating Glass Tab Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x0DFFFFFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabTitles = listOf("Tunnel", "Settings", "Apps (${selectedUids.size})")
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val tabBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0x1FFFFFFF) else Color.Transparent,
                            animationSpec = tween(240),
                            label = "tabBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF64748B),
                            animationSpec = tween(240),
                            label = "tabText"
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tabBg,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FluidAuroraDashboard(
                            isProxyActive = isProxyActive,
                            publicIpInfo = publicIpInfo,
                            isFetchingIp = isFetchingIp,
                            ipFetchFailed = ipFetchFailed,
                            activePid = activePid,
                            proxyType = proxyType,
                            transportMode = transportMode,
                            ipMode = ipMode,
                            onRefreshIp = { triggerPublicIpCheck() },
                            onToggleProxy = {
                                saveConfig()
                                if (isProxyActive) {
                                    onStopProxy { }
                                } else {
                                    val settings = getCurrentSettings()
                                    val targets = if (routeWholeProfile) null else selectedUids.toList()
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
                                saveConfig()
                                isTesting = true
                                testStatus = "Pinging..."
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
                                    val logs = ProxyController.getDiagnosticsAndLogs()
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ProxySetupTab(
                            host = host,
                            onHostChange = { host = it; saveConfig() },
                            port = port,
                            onPortChange = { port = it; saveConfig() },
                            username = username,
                            onUsernameChange = { username = it; saveConfig() },
                            password = password,
                            onPasswordChange = { password = it; saveConfig() },
                            proxyType = proxyType,
                            onProxyTypeChange = { proxyType = it; saveConfig() },
                            transportMode = transportMode,
                            onTransportModeChange = { transportMode = it; saveConfig() },
                            ipMode = ipMode,
                            onIpModeChange = { ipMode = it; saveConfig() },
                            startOnBoot = startOnBoot,
                            onStartOnBootChange = {
                                startOnBoot = it
                                saveConfig()
                            }
                        )
                    }
                }

                2 -> {
                    AppFilterTab(
                        installedApps = installedApps,
                        routeWholeProfile = routeWholeProfile,
                        onToggleRouteWhole = { routeWholeProfile = it; saveConfig() },
                        selectedUids = selectedUids,
                        onToggleUid = { uid ->
                            selectedUids = if (selectedUids.contains(uid)) {
                                selectedUids - uid
                            } else {
                                selectedUids + uid
                            }
                        },
                        onSelectAll = {
                            selectedUids = installedApps.map { it.uid }.toSet()
                        },
                        onClearAll = {
                            selectedUids = emptySet()
                        }
                    )
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
                            ProxyController.clearLogs()
                            currentLogs = "Logs cleared."
                        }) {
                            Text("Clear")
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val logs = ProxyController.getDiagnosticsAndLogs()
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

// -------------------------------------------------------------
// FLUID AURORA DASHBOARD (Liquid Orb, Waveform & Floating Glass)
// -------------------------------------------------------------
@Composable
fun FluidAuroraDashboard(
    isProxyActive: Boolean,
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    ipFetchFailed: Boolean,
    activePid: String?,
    proxyType: ProxyType,
    transportMode: TransportMode,
    ipMode: IpMode,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    onTestUpstream: () -> Unit,
    onViewLogs: () -> Unit
) {
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

    Spacer(modifier = Modifier.height(18.dp))

    // 1. THE LIQUID POWER ORB
    LiquidPowerOrb(
        isProxyActive = isProxyActive,
        onClick = onToggleProxy
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 2. LIVE FLUID PULSE WAVEFORM (Reacts dynamically to network throughput)
    DataStreamWaveform(
        isProxyActive = isProxyActive,
        trafficRate = rawRxRate + rawTxRate
    )

    Spacer(modifier = Modifier.height(18.dp))

    // 3. MINIMALIST FLOATING TELEMETRY ISLANDS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Download Floating Glass Capsule
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0x0AFFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x12FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF00F5A0), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DOWNLOAD", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatSpeed(rawRxRate),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Text("Total ${formatBytes(totalRxBytes)}", fontSize = 11.sp, color = Color(0xFF475569))
            }
        }

        // Upload Floating Glass Capsule
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0x0AFFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x12FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF00D9F5), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("UPLOAD", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatSpeed(rawTxRate),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Text("Total ${formatBytes(totalTxBytes)}", fontSize = 11.sp, color = Color(0xFF475569))
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. FLOATING GEOLOCATION & ENDPOINT CAPSULE
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x0DFFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isProxyActive && !isFetchingIp) { onRefreshIp() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "⚪",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (isProxyActive) {
                            when {
                                isFetchingIp -> "Securing tunnel..."
                                publicIpInfo != null -> publicIpInfo.ip
                                ipFetchFailed -> "Connection timeout"
                                else -> "Resolving..."
                            }
                        } else "Disconnected",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProxyActive) Color.White else Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                    if (isProxyActive) {
                        Text(
                            text = when {
                                isFetchingIp -> "Querying route..."
                                publicIpInfo != null -> publicIpInfo.country
                                ipFetchFailed -> "Tap to retry"
                                else -> "Stabilizing..."
                            },
                            fontSize = 11.sp,
                            color = if (ipFetchFailed) Color(0xFFFFB74D) else Color(0xFF00F5A0),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isProxyActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1400F5A0)
                ) {
                    Text(
                        text = if (isFetchingIp) "..." else "Refresh",
                        fontSize = 11.sp,
                        color = Color(0xFF00F5A0),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 5. MINIMALIST ROUTING SPECS PILLS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pillBg = Modifier
            .background(Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)

        Text(proxyType.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = pillBg)
        Text(
            if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP (WebRTC)" else "TCP Only",
            fontSize = 10.sp,
            color = Color(0xFF00F5A0),
            fontWeight = FontWeight.Bold,
            modifier = pillBg
        )
        Text(
            when (ipMode) {
                IpMode.IPV4_ONLY -> "IPv4"
                IpMode.DUAL_STACK -> "Dual-Stack"
                IpMode.IPV6_ONLY -> "IPv6"
            },
            fontSize = 10.sp,
            color = Color(0xFF00D9F5),
            fontWeight = FontWeight.Bold,
            modifier = pillBg
        )
        if (activePid != null) {
            Text("PID $activePid", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace, modifier = pillBg)
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 6. SUBTLE DIAGNOSTIC ACTIONS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0x0AFFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x12FFFFFF)),
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isTesting) { onTestUpstream() }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = if (isTesting) "Testing..." else "Ping Latency",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0x0AFFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x12FFFFFF)),
            modifier = Modifier
                .weight(1f)
                .clickable { onViewLogs() }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Core Logs",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }

    if (testStatus != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = testStatus,
            color = if (testStatus.startsWith("Online")) Color(0xFF00F5A0) else Color(0xFFFF4466),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(30.dp))
}

// -------------------------------------------------------------
// COMPONENT: LIQUID POWER ORB (Interactive Breathing Center)
// -------------------------------------------------------------
@Composable
fun LiquidPowerOrb(
    isProxyActive: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")

    // Breathing scale & glow
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    val coreGradient = if (isProxyActive) {
        listOf(Color(0xFF00F5A0), Color(0xFF00D9F5))
    } else {
        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(190.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Outer Fluid Halo (Connected state)
        if (isProxyActive) {
            Box(
                modifier = Modifier
                    .size((160 * pulseScale).dp)
                    .alpha(haloAlpha)
                    .blur(28.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF00F5A0), Color(0xFF00D9F5), Color.Transparent)),
                        CircleShape
                    )
            )
        }

        // Middle Frosted Rim
        Surface(
            modifier = Modifier.size(136.dp),
            shape = CircleShape,
            color = Color(0x0FFFFFFF),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isProxyActive) Color(0x5500F5A0) else Color(0x1FFFFFFF)
            )
        ) {}

        // Inner Core Orb
        Surface(
            modifier = Modifier.size(108.dp),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = coreGradient,
                            start = Offset(0f, 0f),
                            end = Offset(300f, 300f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Power Glyph
                Canvas(modifier = Modifier.size(34.dp)) {
                    val strokeW = 3.5.dp.toPx()
                    val iconColor = if (isProxyActive) Color(0xFF05170E) else Color(0xFF94A3B8)

                    // Vertical Power Stalk
                    drawLine(
                        color = iconColor,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height * 0.45f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Power Arc
                    drawArc(
                        color = iconColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// COMPONENT: DATA STREAM WAVEFORM (Dynamic Sinusoidal Flow)
// -------------------------------------------------------------
@Composable
fun DataStreamWaveform(
    isProxyActive: Boolean,
    trafficRate: Long
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveFlow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProxyActive) 1400 else 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Modulate wave height with live throughput
    val targetAmplitude = if (!isProxyActive) {
        6f
    } else {
        val mbps = (trafficRate * 8.0) / (1024.0 * 1024.0)
        (12f + (mbps * 2.5f).toFloat()).coerceIn(12f, 32f)
    }

    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "waveAmp"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2

            val wavePath = Path()
            val points = 80
            for (i in 0..points) {
                val x = (i.toFloat() / points) * width
                val normalizedX = (i.toFloat() / points) * (4 * Math.PI).toFloat()
                val y = midY + sin(normalizedX + phase) * animatedAmplitude

                if (i == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
            }

            // Glow Pass
            drawPath(
                path = wavePath,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        if (isProxyActive) Color(0xFF00F5A0) else Color(0x33475569),
                        if (isProxyActive) Color(0xFF00D9F5) else Color(0x33475569),
                        Color.Transparent
                    )
                ),
                style = Stroke(
                    width = if (isProxyActive) 3.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 1: SETTINGS
// -------------------------------------------------------------
@Composable
fun ProxySetupTab(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    proxyType: ProxyType,
    onProxyTypeChange: (ProxyType) -> Unit,
    transportMode: TransportMode,
    onTransportModeChange: (TransportMode) -> Unit,
    ipMode: IpMode,
    onIpModeChange: (IpMode) -> Unit,
    startOnBoot: Boolean,
    onStartOnBootChange: (Boolean) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x0DFFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto-Start on Boot",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Executes via /data/adb/service.d after 5s",
                        fontSize = 11.sp,
                        color = if (startOnBoot) Color(0xFF00F5A0) else Color(0xFF64748B)
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF05170E),
                        checkedTrackColor = Color(0xFF00F5A0)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("TRANSPORT PROTOCOL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                TransportMode.TCP_AND_UDP to "TCP + UDP (WebRTC)",
                TransportMode.TCP_ONLY to "TCP Only"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = transportMode == mode,
                    onClick = { onTransportModeChange(mode) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("PROXY TYPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ProxyType.SOCKS5, ProxyType.SOCKS4, ProxyType.HTTP).forEach { type ->
                FilterChip(
                    selected = proxyType == type,
                    onClick = { onProxyTypeChange(type) },
                    label = { Text(type.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("IP MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                IpMode.IPV4_ONLY to "IPv4",
                IpMode.DUAL_STACK to "Dual-Stack",
                IpMode.IPV6_ONLY to "IPv6"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = ipMode == mode,
                    onClick = { onIpModeChange(mode) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Server Host / IP") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Server Port") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "Hide" else "Show",
                        fontSize = 11.sp,
                        color = Color(0xFF00F5A0),
                        modifier = Modifier
                            .clickable { passwordVisible = !passwordVisible }
                            .padding(end = 12.dp)
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

// -------------------------------------------------------------
// TAB 2: APPS FILTER
// -------------------------------------------------------------
@Composable
fun AppFilterTab(
    installedApps: List<AppItem>,
    routeWholeProfile: Boolean,
    onToggleRouteWhole: (Boolean) -> Unit,
    selectedUids: Set<Int>,
    onToggleUid: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isEmpty()) installedApps
        else installedApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x0DFFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Route Entire Profile",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (routeWholeProfile) "All applications redirected" else "Per-App filter active",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = onToggleRouteWhole,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF05170E),
                            checkedTrackColor = Color(0xFF00F5A0)
                        )
                    )
                }
            }
        }

        if (!routeWholeProfile) {
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search installed applications...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSelectAll) { Text("Select All", color = Color(0xFF00F5A0)) }
                TextButton(onClick = onClearAll) { Text("Clear All", color = Color(0xFF64748B)) }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = selectedUids.contains(app.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleUid(app.uid) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${app.packageName} • UID ${app.uid}",
                                color = Color(0xFF475569),
                                fontSize = 11.sp
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleUid(app.uid) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF00F5A0),
                                checkmarkColor = Color(0xFF05170E)
                            )
                        )
                    }
                    Divider(color = Color(0x0DFFFFFF), thickness = 0.5.dp)
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "All applications in Profile ${ProfileManager.profileId} are currently routed.\nDisable switch above to customize individual apps.",
                    color = Color(0xFF475569),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
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
