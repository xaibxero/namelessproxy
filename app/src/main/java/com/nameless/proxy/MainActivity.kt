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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    // Dynamic Electric Background Palette
    val bgTopColor by animateColorAsState(
        targetValue = if (isProxyActive) Color(0xFF1E1038) else Color(0xFF0F111D),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "bgTop"
    )
    val bgMidColor by animateColorAsState(
        targetValue = if (isProxyActive) Color(0xFF120E26) else Color(0xFF0A0C16),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "bgMid"
    )
    val bgBottomColor by animateColorAsState(
        targetValue = if (isProxyActive) Color(0xFF0B0D18) else Color(0xFF06070D),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "bgBottom"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "auroraGlow")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(bgTopColor, bgMidColor, bgBottomColor)
                )
            )
    ) {
        if (isProxyActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .alpha(auraAlpha)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x557C3AED), Color(0x2206B6D4), Color.Transparent),
                            center = Offset(Float.POSITIVE_INFINITY / 2f, 160f),
                            radius = 700f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Row
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
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Profile ${ProfileManager.profileId} • Direct Tunnel",
                        fontSize = 11.sp,
                        color = if (isProxyActive) Color(0xFFA78BFA) else Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (rootState) {
                        RootState.GRANTED -> Color(0x257C3AED)
                        RootState.DENIED -> Color(0x25F43F5E)
                        RootState.CHECKING -> Color(0x25F59E0B)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (rootState) {
                            RootState.GRANTED -> Color(0xFF8B5CF6)
                            RootState.DENIED -> Color(0xFFF43F5E)
                            RootState.CHECKING -> Color(0xFFF59E0B)
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
                                .size(7.dp)
                                .background(
                                    color = when (rootState) {
                                        RootState.GRANTED -> Color(0xFFA78BFA)
                                        RootState.DENIED -> Color(0xFFF43F5E)
                                        RootState.CHECKING -> Color(0xFFF59E0B)
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
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capsule Tabs
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x401E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabTitles = listOf("Cockpit", "Config", "Apps (${selectedUids.size})")
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val tabBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF6366F1) else Color.Transparent,
                            animationSpec = tween(280),
                            label = "tabBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                            animationSpec = tween(280),
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
                                    fontWeight = FontWeight.Bold,
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
                            .verticalScroll(rememberScrollState())
                    ) {
                        DashboardTab(
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
                        .background(Color(0xFF070709), RoundedCornerShape(12.dp))
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
fun DashboardTab(
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
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = if (isProxyActive) Color(0x668B5CF6) else Color(0x1AFFFFFF),
        animationSpec = tween(500),
        label = "cardBorder"
    )

    // Cockpit Gauge Surface Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, cardBorderColor, RoundedCornerShape(26.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x441E293B)),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cockpit System Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(if (isProxyActive) alphaAnim else 1f)
                            .background(
                                if (isProxyActive) Color(0xFF06B6D4) else Color(0xFF64748B),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyActive) "ENGINE RUNNING" else "IGNITION OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isProxyActive) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                }

                if (isProxyActive) {
                    ActiveTimer()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // THE SUPERBIKE TACHOMETER / SPEEDOMETER GAUGE
            SuperbikeSpeedometerGauge(isProxyActive = isProxyActive)

            Spacer(modifier = Modifier.height(10.dp))

            // Geolocation Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0x660F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isProxyActive && !isFetchingIp) { onRefreshIp() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "⚪",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isProxyActive) {
                                    when {
                                        isFetchingIp -> "Locking telemetry..."
                                        publicIpInfo != null -> publicIpInfo.ip
                                        ipFetchFailed -> "Connection timeout"
                                        else -> "Resolving..."
                                    }
                                } else "Native Interface",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProxyActive) Color.White else Color(0xFF94A3B8),
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
                                    color = if (ipFetchFailed) Color(0xFFFBBF24) else Color(0xFFA78BFA),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (isProxyActive) {
                        Text(
                            text = if (isFetchingIp) "..." else "Refresh",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specs Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pillModifier = Modifier
                    .background(Color(0x55334155), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)

                Text(proxyType.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = pillModifier)
                Text(
                    if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP" else "TCP",
                    fontSize = 10.sp,
                    color = Color(0xFF38BDF8),
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
                    color = Color(0xFFA78BFA),
                    fontWeight = FontWeight.Bold,
                    modifier = pillModifier
                )
                if (activePid != null) {
                    Text("PID: $activePid", fontSize = 10.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, modifier = pillModifier)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Ignition / Disconnect Action Button
    val buttonBrush = if (isProxyActive) {
        Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFF43F5E)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))
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
                text = if (isProxyActive) "Kill Engine (Disconnect)" else "Ignition (Connect Transparent)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Quick Diagnostics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onTestUpstream,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA)),
            enabled = !isTesting
        ) {
            Text(if (isTesting) "Testing..." else "Ping Latency")
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
            color = if (testStatus.startsWith("Online")) Color(0xFF38BDF8) else Color(0xFFF43F5E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
}

// -------------------------------------------------------------
// BIKE COCKPIT SPEEDOMETER GAUGE COMPONENT
// -------------------------------------------------------------
@Composable
fun SuperbikeSpeedometerGauge(isProxyActive: Boolean) {
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

    // Convert speed to a normalized gauge fraction (0.0 to 1.0)
    // 0 to 50 MB/s dynamic scaling
    val currentMbps = (rawRxRate * 8.0) / (1024.0 * 1024.0)
    val gaugeFraction = (currentMbps / 60.0).toFloat().coerceIn(0f, 1f)

    // Bouncy spring animation mimicking physical throttle and tachometer response
    val animatedSweepFraction by animateFloatAsState(
        targetValue = if (isProxyActive) gaugeFraction else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tachometerSpring"
    )

    // Gauge Angles: sweeps 240 degrees from 150° (bottom-left) to 390° (bottom-right)
    val startAngle = 150f
    val totalSweep = 240f
    val activeSweep = totalSweep * animatedSweepFraction

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // High-precision Canvas drawing for track, redline, dial ticks, and rev needle
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidth * 2) / 2
            val centerOffset = Offset(size.width / 2, size.height / 2)

            // 1. Muted Background Track Arc
            drawArc(
                color = Color(0x33334155),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Redline Zone Background Accent (top 20% of scale)
            drawArc(
                color = Color(0x33F43F5E),
                startAngle = startAngle + (totalSweep * 0.8f),
                sweepAngle = totalSweep * 0.2f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 3. Dial Hash Ticks (Motorcycle Tachometer Style)
            val totalTicks = 24
            for (i in 0..totalTicks) {
                val tickAngle = startAngle + (i.toFloat() / totalTicks) * totalSweep
                val rad = Math.toRadians(tickAngle.toDouble())
                val isRedline = i >= (totalTicks * 0.8f)

                val tickLength = if (i % 4 == 0) 10.dp.toPx() else 5.dp.toPx()
                val tickColor = if (isRedline) Color(0xFFF43F5E) else Color(0x6694A3B8)
                val tickStroke = if (i % 4 == 0) 2.dp.toPx() else 1.dp.toPx()

                val startRadius = radius + (strokeWidth / 2) + 3.dp.toPx()
                val endRadius = startRadius + tickLength

                val startX = centerOffset.x + (startRadius * cos(rad)).toFloat()
                val startY = centerOffset.y + (startRadius * sin(rad)).toFloat()
                val endX = centerOffset.x + (endRadius * cos(rad)).toFloat()
                val endY = centerOffset.y + (endRadius * sin(rad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickStroke,
                    cap = StrokeCap.Round
                )
            }

            // 4. Active Sweeping Gradient Arc
            if (activeSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color(0xFF06B6D4),
                        0.5f to Color(0xFF8B5CF6),
                        0.85f to Color(0xFFF43F5E),
                        1.0f to Color(0xFFFF2E63)
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 5. Radial Needle Head Indicator Dot
            if (activeSweep > 0.5f) {
                val needleAngle = startAngle + activeSweep
                val needleRad = Math.toRadians(needleAngle.toDouble())
                val dotCenter = Offset(
                    centerOffset.x + (radius * cos(needleRad)).toFloat(),
                    centerOffset.y + (radius * sin(needleRad)).toFloat()
                )
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = dotCenter)
                drawCircle(color = Color(0xFF38BDF8), radius = 2.5.dp.toPx(), center = dotCenter)
            }
        }

        // Center Digital Display (Tachometer Cockpit Readout)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            // Speed Number
            val formattedSpeed = formatSpeedValueOnly(rawRxRate)
            Text(
                text = if (isProxyActive) formattedSpeed.first else "0.0",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1.5).sp
            )

            // Speed Unit
            Text(
                text = if (isProxyActive) formattedSpeed.second else "KB/s",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF38BDF8),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Superbike Gear Indicator Pill
            val gearText = when {
                !isProxyActive -> "NEUTRAL"
                currentMbps > 40.0 -> "GEAR 6 • REDLINE"
                currentMbps > 25.0 -> "GEAR 5 • OVERDRIVE"
                currentMbps > 10.0 -> "GEAR 4 • CRUISE"
                currentMbps > 3.0 -> "GEAR 3 • ACCEL"
                currentMbps > 0.5 -> "GEAR 2 • STEADY"
                else -> "GEAR 1 • IDLE"
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (currentMbps > 40.0) Color(0x33F43F5E) else Color(0x44334155),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (currentMbps > 40.0) Color(0xFFF43F5E) else Color(0x22FFFFFF)
                )
            ) {
                Text(
                    text = gearText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentMbps > 40.0) Color(0xFFF43F5E) else Color(0xFFA78BFA),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Secondary Telemetry Sub-Cluster (Upload Boost & Session Totals)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("UPLOAD BOOST", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
            Text(
                text = formatSpeed(rawTxRate),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFA78BFA),
                fontFamily = FontFamily.Monospace
            )
            Text("Total: ${formatBytes(totalTxBytes)}", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("DOWN TOTAL", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
            Text(
                text = formatBytes(totalRxBytes),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Text("Bandwidth Used", fontSize = 10.sp, color = Color(0xFF94A3B8))
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
        color = Color(0xFFA78BFA),
        fontWeight = FontWeight.Bold
    )
}

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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x441E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                        color = if (startOnBoot) Color(0xFFA78BFA) else Color(0xFF64748B)
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6366F1)
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
                        color = Color(0xFFA78BFA),
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x441E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
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
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6366F1)
                        )
                    )
                }
            }
        }

        if (!routeWholeProfile) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search apps...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSelectAll) { Text("Select All", color = Color(0xFFA78BFA)) }
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
                                checkedColor = Color(0xFF6366F1),
                                checkmarkColor = Color.White
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

private fun formatSpeedValueOnly(bytesPerSec: Long): Pair<String, String> {
    return when {
        bytesPerSec >= 1024 * 1024 -> Pair(String.format("%.1f", bytesPerSec / (1024.0 * 1024.0)), "MB/s")
        bytesPerSec >= 1024 -> Pair(String.format("%.1f", bytesPerSec / 1024.0), "KB/s")
        else -> Pair("$bytesPerSec", "B/s")
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
