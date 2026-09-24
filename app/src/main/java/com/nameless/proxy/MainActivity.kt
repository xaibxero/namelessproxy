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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

data class AppItem(
    val name: String,
    val packageName: String,
    val uid: Int
)

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var rootState by mutableStateOf(RootState.CHECKING)
    private var rootLabel by mutableStateOf("Checking Root...")
    private var isProxyRunningState by mutableStateOf(false)
    private var activePidState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshRootStatus()
        syncDaemonStatus()
        loadInstalledApps()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncDaemonStatus()
            }
        })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF08090D)
                ) {
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
            rootLabel = "Requesting Root..."
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
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("") }

    var publicIpInfo by remember { mutableStateOf<GeoIpResult?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }

    var selectedUids by remember { mutableStateOf(setOf<Int>()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun triggerPublicIpCheck() {
        isFetchingIp = true
        coroutineScope.launch {
            val result = IpFetcher.getPublicIpInfo()
            publicIpInfo = result
            isFetchingIp = false
        }
    }

    LaunchedEffect(isProxyActive) {
        if (isProxyActive) {
            triggerPublicIpCheck()
        } else {
            publicIpInfo = null
        }
    }

    val buttonBgColor by animateColorAsState(
        targetValue = if (isProxyActive) Color(0xFFD32F2F) else Color(0xFF00E676),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "btnColor"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = if (isProxyActive) Color(0x6600E676) else Color(0x1AFFFFFF),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nameless Proxy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "TProxy Engine • Profile ${ProfileManager.profileId}",
                    fontSize = 12.sp,
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when (rootState) {
                    RootState.GRANTED -> Color(0x1F00E676)
                    RootState.DENIED -> Color(0x1FFF5252)
                    RootState.CHECKING -> Color(0x1FFFD600)
                },
                modifier = Modifier
                    .border(
                        1.dp,
                        when (rootState) {
                            RootState.GRANTED -> Color(0xFF00E676)
                            RootState.DENIED -> Color(0xFFFF5252)
                            RootState.CHECKING -> Color(0xFFFFD600)
                        },
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onRecheckRoot() }
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
                                    RootState.GRANTED -> Color(0xFF00E676)
                                    RootState.DENIED -> Color(0xFFFF5252)
                                    RootState.CHECKING -> Color(0xFFFFD600)
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

        Spacer(modifier = Modifier.height(18.dp))

        // Hero Card (Status & Telemetry)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Status Header with slow pulsing dot
                StatusHeader(isProxyActive = isProxyRunningState)

                Spacer(modifier = Modifier.height(14.dp))

                // Public IP Banner with Flag
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF171A24),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isProxyActive && !isFetchingIp) { triggerPublicIpCheck() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isProxyActive) (publicIpInfo?.flagEmoji ?: "🌐") else "⚪",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isProxyActive) {
                                        if (isFetchingIp) "Detecting location..."
                                        else (publicIpInfo?.ip ?: "Resolving IP...")
                                    } else "Engine Disconnected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProxyActive) Color.White else Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (isProxyActive && publicIpInfo != null && !isFetchingIp) {
                                    Text(
                                        text = publicIpInfo!!.country,
                                        fontSize = 11.sp,
                                        color = Color(0xFF00E676),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (isProxyActive) {
                            Text(
                                text = if (isFetchingIp) "..." else "Refresh",
                                fontSize = 11.sp,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Specs Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pillBg = Modifier
                        .background(Color(0xFF1D212E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)

                    Text(proxyType.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = pillBg)
                    Text(
                        if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP (WebRTC)" else "TCP Only",
                        fontSize = 10.sp,
                        color = Color(0xFF00E676),
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
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        modifier = pillBg
                    )
                    if (activePid != null) {
                        Text("PID: $activePid", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace, modifier = pillBg)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Telemetry Section (Isolated recomposition prevents scroll lag)
                TelemetrySection(isProxyActive = isProxyRunningState)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connect Button
        Button(
            onClick = {
                saveConfig()
                if (isProxyActive) {
                    onStopProxy { /* Synchronized by observer */ }
                } else {
                    val settings = ProxySettings(
                        type = proxyType,
                        transportMode = transportMode,
                        ipMode = ipMode,
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 1080,
                        username = username.trim(),
                        password = password.trim()
                    )
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
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonBgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (isProxyActive) "DISCONNECT PROXY" else "CONNECT TRANSPARENT PROXY",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (isProxyActive) Color.White else Color.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Diagnostic Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    saveConfig()
                    isTesting = true
                    testStatus = "Testing socket..."
                    val settings = ProxySettings(
                        type = proxyType,
                        transportMode = transportMode,
                        ipMode = ipMode,
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 1080,
                        username = username.trim(),
                        password = password.trim()
                    )
                    coroutineScope.launch {
                        when (val res = ProxyTester.testProxy(settings)) {
                            is TestResult.Success -> testStatus = "Valid (⚡ ${res.latencyMs} ms)"
                            is TestResult.Failure -> testStatus = "Test Failed: ${res.error}"
                        }
                        isTesting = false
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = !isTesting
            ) {
                Text(if (isTesting) "Pinging..." else "Test Upstream")
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val logs = ProxyController.getDiagnosticsAndLogs()
                        withContext(Dispatchers.Main) {
                            currentLogs = logs.ifEmpty { "No logs recorded." }
                            showLogsDialog = true
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Core Logs")
            }
        }

        if (testStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = testStatus!!,
                color = if (testStatus!!.startsWith("Valid")) Color(0xFF00E676) else Color(0xFFFF5252),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "CONFIGURATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Start on Boot Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Start on Boot",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Auto-launch daemon after device restart",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = {
                        startOnBoot = it
                        saveConfig()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transport Modes
        Text("Transport Protocols", color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                TransportMode.TCP_AND_UDP to "TCP + UDP (WebRTC Support)",
                TransportMode.TCP_ONLY to "TCP Only"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = transportMode == mode,
                    onClick = {
                        transportMode = mode
                        saveConfig()
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Protocol Selector
        Text("Proxy Protocol", color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ProxyType.SOCKS5, ProxyType.SOCKS4, ProxyType.HTTP).forEach { type ->
                FilterChip(
                    selected = proxyType == type,
                    onClick = {
                        proxyType = type
                        saveConfig()
                    },
                    label = { Text(type.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // IP Mode Selector
        Text("IP Routing Mode", color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                IpMode.IPV4_ONLY to "IPv4 Only",
                IpMode.DUAL_STACK to "Dual-Stack",
                IpMode.IPV6_ONLY to "IPv6 Only"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = ipMode == mode,
                    onClick = {
                        ipMode = mode
                        saveConfig()
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = host,
            onValueChange = {
                host = it
                saveConfig()
            },
            label = { Text("Proxy Host / IP") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = port,
            onValueChange = {
                port = it
                saveConfig()
            },
            label = { Text("Proxy Port") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    saveConfig()
                },
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    saveConfig()
                },
                label = { Text("Password") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Routing Scope Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
            shape = RoundedCornerShape(14.dp)
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
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (routeWholeProfile) "All apps in Profile ${ProfileManager.profileId} redirected" else "Per-App filter active",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = routeWholeProfile,
                        onCheckedChange = {
                            routeWholeProfile = it
                            saveConfig()
                        }
                    )
                }

                if (!routeWholeProfile) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select Target Apps (${selectedUids.size} Selected)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Modal Sheet for App Picker
    if (showAppPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAppPicker = false },
            containerColor = Color(0xFF16171E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Apps (Profile ${ProfileManager.profileId})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUids = if (selectedUids.contains(app.uid)) {
                                        selectedUids - app.uid
                                    } else {
                                        selectedUids + app.uid
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${app.packageName} (UID: ${app.uid})", color = Color.Gray, fontSize = 11.sp)
                            }
                            Checkbox(
                                checked = selectedUids.contains(app.uid),
                                onCheckedChange = { checked ->
                                    selectedUids = if (checked) selectedUids + app.uid else selectedUids - app.uid
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Diagnostics Dialog
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
                        .background(Color(0xFF070709), RoundedCornerShape(8.dp))
                        .padding(10.dp)
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

// Sub-composable: Status header with slow breathing glow
@Composable
fun StatusHeader(isProxyActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

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
                        if (isProxyActive) Color(0xFF00E676) else Color(0xFF757575),
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isProxyActive) "ACTIVE & ROUTING" else "IDLE / DISCONNECTED",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isProxyActive) Color(0xFF00E676) else Color.Gray,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// Sub-composable: Self-contained live speed meter (Eliminates scroll lag)
@Composable
fun TelemetrySection(isProxyActive: Boolean) {
    var downSpeedStr by remember { mutableStateOf("0 B/s") }
    var upSpeedStr by remember { mutableStateOf("0 B/s") }
    var totalDownloadedStr by remember { mutableStateOf("0 B") }
    var totalUploadedStr by remember { mutableStateOf("0 B") }

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

                val rxRate = ((currRx - prevRx) / dt).toLong().coerceAtLeast(0)
                val txRate = ((currTx - prevTx) / dt).toLong().coerceAtLeast(0)

                downSpeedStr = formatSpeed(rxRate)
                upSpeedStr = formatSpeed(txRate)
                totalDownloadedStr = formatBytes((currRx - startRx).coerceAtLeast(0))
                totalUploadedStr = formatBytes((currTx - startTx).coerceAtLeast(0))

                prevRx = currRx
                prevTx = currTx
                prevTime = currTime
            }
        } else {
            downSpeedStr = "0 B/s"
            upSpeedStr = "0 B/s"
            totalDownloadedStr = "0 B"
            totalUploadedStr = "0 B"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Download Box
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF171A24)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("DOWNLOAD", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = downSpeedStr,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Total: $totalDownloadedStr",
                    fontSize = 10.sp,
                    color = Color(0xFF757575)
                )
            }
        }

        // Upload Box
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF171A24)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("UPLOAD", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = upSpeedStr,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Total: $totalUploadedStr",
                    fontSize = 10.sp,
                    color = Color(0xFF757575)
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
