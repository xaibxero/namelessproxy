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

    // Dynamic Multi-Layered Aurora Ambient Background
    val infiniteTransition = rememberInfiniteTransition(label = "auroraMotion")
    val auroraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )

    val activeAuraAlpha by animateFloatAsState(
        targetValue = if (isProxyActive) 0.35f else 0.08f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "activeAuraAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        // Living Dynamic Gradient Orbs
        val xOffset1 = (sin(auroraPhase.toDouble()) * 80).dp
        val yOffset1 = (cos(auroraPhase.toDouble()) * 40).dp
        val xOffset2 = (cos(auroraPhase.toDouble()) * -80).dp
        val yOffset2 = (sin(auroraPhase.toDouble()) * 50).dp

        Box(
            modifier = Modifier
                .size(360.dp)
                .offset(x = (-20).dp + xOffset1, y = (-40).dp + yOffset1)
                .alpha(activeAuraAlpha)
                .blur(95.dp)
                .background(
                    if (isProxyActive) Color(0xFF00F5A0) else Color(0xFF38BDF8),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = 100.dp + xOffset2, y = 140.dp + yOffset2)
                .alpha(activeAuraAlpha)
                .blur(110.dp)
                .background(
                    if (isProxyActive) Color(0xFF00D9F5) else Color(0xFF6366F1),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NAMELESS",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "PROFILE ${ProfileManager.profileId} • KERNEL TPROXY",
                        fontSize = 10.sp,
                        color = if (isProxyActive) Color(0xFF00F5A0) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Root Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x1AFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (rootState) {
                            RootState.GRANTED -> Color(0x5500F5A0)
                            RootState.DENIED -> Color(0x55F43F5E)
                            RootState.CHECKING -> Color(0x55F59E0B)
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

            Spacer(modifier = Modifier.height(14.dp))

            // Capsule Tabs Switcher
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x22111827),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabTitles = listOf("Console", "Config", "Apps (${selectedUids.size})")
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val tabBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0x28FFFFFF) else Color.Transparent,
                            animationSpec = tween(250),
                            label = "tabBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                            animationSpec = tween(250),
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
                                modifier = Modifier.padding(vertical = 10.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ConsoleHUDTab(
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
// UNIFIED CYBER COMMAND DECK (Hero HUD, Rolling Graph & Action)
// -------------------------------------------------------------
@Composable
fun ConsoleHUDTab(
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
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderGlow by animateColorAsState(
        targetValue = if (isProxyActive) Color(0x6600F5A0) else Color(0x1AFFFFFF),
        animationSpec = tween(600),
        label = "borderGlow"
    )

    // Primary Unified Glass Deck
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderGlow, RoundedCornerShape(26.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x28111827)),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Deck Header Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .alpha(if (isProxyActive) pulseAlpha else 1f)
                            .background(
                                if (isProxyActive) Color(0xFF00F5A0) else Color(0xFF64748B),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyActive) "TRANSPARENT TUNNEL ACTIVE" else "TUNNEL OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isProxyActive) Color(0xFF00F5A0) else Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                }

                if (isProxyActive) {
                    ActiveTimer()
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Real-Time Rolling Throughput Engine
            LiveThroughputEngine(isProxyActive = isProxyActive)

            Spacer(modifier = Modifier.height(16.dp))

            // Integrated Location & Endpoint Surface
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x330F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isProxyActive && !isFetchingIp) { onRefreshIp() }
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isProxyActive) {
                                    when {
                                        isFetchingIp -> "Securing route..."
                                        publicIpInfo != null -> publicIpInfo.ip
                                        ipFetchFailed -> "Timeout - Tap to retry"
                                        else -> "Resolving..."
                                    }
                                } else "Native Connection",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProxyActive) Color.White else Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace
                            )
                            if (isProxyActive) {
                                Text(
                                    text = when {
                                        isFetchingIp -> "Querying route telemetry..."
                                        publicIpInfo != null -> publicIpInfo.country
                                        ipFetchFailed -> "Tap to retry"
                                        else -> "Stabilizing..."
                                    },
                                    fontSize = 11.sp,
                                    color = if (ipFetchFailed) Color(0xFFFBBF24) else Color(0xFF00D9F5),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (isProxyActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x1A00F5A0)
                        ) {
                            Text(
                                text = if (isFetchingIp) "..." else "Refresh",
                                fontSize = 11.sp,
                                color = Color(0xFF00F5A0),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Specs Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pillModifier = Modifier
                    .background(Color(0x331F2937), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)

                Text(proxyType.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = pillModifier)
                Text(
                    if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP (WebRTC)" else "TCP Only",
                    fontSize = 10.sp,
                    color = Color(0xFF00F5A0),
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
                    color = Color(0xFF00D9F5),
                    fontWeight = FontWeight.Bold,
                    modifier = pillModifier
                )
                if (activePid != null) {
                    Text("PID $activePid", fontSize = 10.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, modifier = pillModifier)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Fluid Neon Master Action Capsule
    val buttonBrush = if (isProxyActive) {
        Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFF43F5E)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF00F5A0), Color(0xFF00D9F5)))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onToggleProxy() },
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(buttonBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isProxyActive) "DISCONNECT PROXY" else "CONNECT TRANSPARENT PROXY",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (isProxyActive) Color.White else Color(0xFF030712),
                letterSpacing = 0.5.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Diagnostics Bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onTestUpstream,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D9F5)),
            enabled = !isTesting
        ) {
            Text(if (isTesting) "Pinging..." else "Ping Latency")
        }

        OutlinedButton(
            onClick = onViewLogs,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
        ) {
            Text("Core Logs")
        }
    }

    if (testStatus != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = testStatus,
            color = if (testStatus.startsWith("Online")) Color(0xFF00F5A0) else Color(0xFFF43F5E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
}

// -------------------------------------------------------------
// LIVE ROLLING THROUGHPUT AREA CHART (Sparkline + Gauges)
// -------------------------------------------------------------
@Composable
fun LiveThroughputEngine(isProxyActive: Boolean) {
    var rawRxRate by remember { mutableLongStateOf(0L) }
    var rawTxRate by remember { mutableLongStateOf(0L) }
    var totalRxBytes by remember { mutableLongStateOf(0L) }
    var totalTxBytes by remember { mutableLongStateOf(0L) }

    // Rolling History Buffer (last 24 seconds)
    val historyPoints = remember { mutableStateListOf<Float>().apply { repeat(24) { add(0f) } } }

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

                // Push new data point onto rolling graph
                val totalRateKb = (rawRxRate + rawTxRate) / 1024f
                if (historyPoints.size >= 24) {
                    historyPoints.removeAt(0)
                }
                historyPoints.add(totalRateKb)

                prevRx = currRx
                prevTx = currTx
                prevTime = currTime
            }
        } else {
            rawRxRate = 0L
            rawTxRate = 0L
            totalRxBytes = 0L
            totalTxBytes = 0L
            historyPoints.clear()
            repeat(24) { historyPoints.add(0f) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Massive Live Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "DOWNLOAD STREAM",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = formatSpeed(rawRxRate),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "UPLOAD STREAM",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = formatSpeed(rawTxRate),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00D9F5),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Area Sparkline Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x330B1120))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                val width = size.width
                val height = size.height
                val maxVal = (historyPoints.maxOrNull() ?: 10f).coerceAtLeast(20f)

                val linePath = Path()
                val fillPath = Path()

                val stepX = width / (historyPoints.size - 1).coerceAtLeast(1)

                historyPoints.forEachIndexed { i, value ->
                    val normY = height - ((value / maxVal) * (height * 0.85f))
                    val x = i * stepX

                    if (i == 0) {
                        linePath.moveTo(x, normY)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, normY)
                    } else {
                        linePath.lineTo(x, normY)
                        fillPath.lineTo(x, normY)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw Gradient Fill under line
                if (isProxyActive) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(Color(0x3300F5A0), Color.Transparent)
                        )
                    )
                }

                // Draw Smooth Line
                drawPath(
                    path = linePath,
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (isProxyActive) Color(0xFF00F5A0) else Color(0x33475569),
                            if (isProxyActive) Color(0xFF00D9F5) else Color(0x33475569)
                        )
                    ),
                    style = Stroke(
                        width = if (isProxyActive) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Session Totals Footnote
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Total Down: ${formatBytes(totalRxBytes)}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Total Up: ${formatBytes(totalTxBytes)}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
            )
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
        color = Color(0xFF00F5A0),
        fontWeight = FontWeight.Bold
    )
}

// -------------------------------------------------------------
// TAB 1: PROXY CONFIGURATION
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
            color = Color(0x1A1F2937),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
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
                        text = "Runs via /data/adb/service.d after 5s",
                        fontSize = 11.sp,
                        color = if (startOnBoot) Color(0xFF00F5A0) else Color(0xFF64748B)
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF030712),
                        checkedTrackColor = Color(0xFF00F5A0)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text("TRANSPORT PROTOCOL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
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

        Spacer(modifier = Modifier.height(14.dp))

        Text("PROXY TYPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
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

        Spacer(modifier = Modifier.height(14.dp))

        Text("IP MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Server Host / IP") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Server Port") },
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
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(14.dp),
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
            color = Color(0x1A1F2937),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
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
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = onToggleRouteWhole,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF030712),
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
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSelectAll) { Text("Select All", color = Color(0xFF00F5A0)) }
                TextButton(onClick = onClearAll) { Text("Clear All", color = Color(0xFF94A3B8)) }
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
                                text = "${app.packageName} • UID: ${app.uid}",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleUid(app.uid) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF00F5A0),
                                checkmarkColor = Color(0xFF030712)
                            )
                        )
                    }
                    Divider(color = Color(0x10FFFFFF), thickness = 0.5.dp)
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
                    text = "All applications in this profile are currently routed.\nDisable switch above to choose specific apps.",
                    color = Color(0xFF64748B),
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
        bytes >= 1024 -> String.format("%.1f KB", bytes / (1024.0 * 1024.0))
        else -> "$bytes B"
    }
}
