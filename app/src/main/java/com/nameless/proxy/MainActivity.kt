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
                MainScreen(
                    rootState = rootState,
                    rootLabel = rootLabel,
                    isProxyActive = isProxyRunningState,
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
            val running = ProxyController.isRunning(ProfileManager.profileId)
            val pid = if (running) ProxyController.getActivePid(ProfileManager.profileId) else null
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
    onSyncStatus: () -> Unit,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeSlot by remember { mutableIntStateOf(ProfileManager.activeSlot) }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun getSlotPrefs(slot: Int) = context.getSharedPreferences("nameless_slot_$slot", Context.MODE_PRIVATE)

    var currentPrefs by remember(activeSlot) { mutableStateOf(getSlotPrefs(activeSlot)) }

    // Active Slot States
    var proxyType by remember(activeSlot) {
        mutableStateOf(
            try {
                ProxyType.valueOf(currentPrefs.getString("proxy_type", ProxyType.SOCKS5.name) ?: ProxyType.SOCKS5.name)
            } catch (_: Exception) { ProxyType.SOCKS5 }
        )
    }

    var transportMode by remember(activeSlot) {
        mutableStateOf(
            try {
                TransportMode.valueOf(currentPrefs.getString("transport_mode", TransportMode.TCP_AND_UDP.name) ?: TransportMode.TCP_AND_UDP.name)
            } catch (_: Exception) { TransportMode.TCP_AND_UDP }
        )
    }

    var ipMode by remember(activeSlot) {
        mutableStateOf(
            try {
                IpMode.valueOf(currentPrefs.getString("ip_mode", IpMode.IPV4_ONLY.name) ?: IpMode.IPV4_ONLY.name)
            } catch (_: Exception) { IpMode.IPV4_ONLY }
        )
    }

    var host by remember(activeSlot) { mutableStateOf(currentPrefs.getString("host", "") ?: "") }
    var port by remember(activeSlot) { mutableStateOf(currentPrefs.getString("port", "1080") ?: "1080") }
    var username by remember(activeSlot) { mutableStateOf(currentPrefs.getString("username", "") ?: "") }
    var password by remember(activeSlot) { mutableStateOf(currentPrefs.getString("password", "") ?: "") }
    var sni by remember(activeSlot) { mutableStateOf(currentPrefs.getString("sni", "") ?: "") }
    var ssMethod by remember(activeSlot) { mutableStateOf(currentPrefs.getString("ss_method", "2022-blake3-aes-128-gcm") ?: "2022-blake3-aes-128-gcm") }
    var realityPublicKey by remember(activeSlot) { mutableStateOf(currentPrefs.getString("reality_pk", "") ?: "") }
    var realityShortId by remember(activeSlot) { mutableStateOf(currentPrefs.getString("reality_sid", "") ?: "") }

    var routeWholeProfile by remember(activeSlot) { mutableStateOf(currentPrefs.getBoolean("route_whole_profile", true)) }
    var routeHotspot by remember(activeSlot) { mutableStateOf(currentPrefs.getBoolean("route_hotspot", true)) }
    var startOnBoot by remember(activeSlot) { mutableStateOf(currentPrefs.getBoolean("start_on_boot", false)) }

    // Persistent Selected Packages Set
    var selectedPackages by remember(activeSlot) {
        mutableStateOf(currentPrefs.getStringSet("selected_packages", emptySet()) ?: emptySet())
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

    fun saveConfig() {
        currentPrefs.edit()
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
            .putBoolean("route_hotspot", routeHotspot)
            .putBoolean("start_on_boot", startOnBoot)
            .putStringSet("selected_packages", selectedPackages)
            .apply()

        val settings = getCurrentSettings()
        val uids = if (routeWholeProfile) null else installedApps.filter { selectedPackages.contains(it.packageName) }.map { it.uid }

        BootManager.syncBootState(context, startOnBoot, settings, uids)

        if (settings.host.isNotEmpty()) {
            coroutineScope.launch {
                PersistentStorage.saveBackup(
                    context,
                    ProfileManager.profileId,
                    settings,
                    startOnBoot,
                    routeWholeProfile,
                    selectedPackages
                )
            }
        }
    }

    // Switch Slot Function
    fun switchSlot(newSlot: Int) {
        if (newSlot == activeSlot) return
        saveConfig()
        ProfileManager.activeSlot = newSlot
        activeSlot = newSlot
        currentPrefs = getSlotPrefs(newSlot)
        onSyncStatus()
    }

    // Restore from persistent storage backup on boot/granted
    LaunchedEffect(rootState, activeSlot) {
        if (rootState == RootState.GRANTED && host.isEmpty()) {
            val backup = PersistentStorage.loadBackup(activeSlot)
            if (backup != null) {
                host = backup.optString("host", "")
                port = backup.optString("port", "1080")
                username = backup.optString("username", "")
                password = backup.optString("password", "")
                sni = backup.optString("sni", "")
                ssMethod = backup.optString("ss_method", "2022-blake3-aes-128-gcm")
                realityPublicKey = backup.optString("reality_pk", "")
                realityShortId = backup.optString("reality_sid", "")
                startOnBoot = backup.optBoolean("start_on_boot", false)
                routeWholeProfile = backup.optBoolean("route_whole_profile", true)
                routeHotspot = backup.optBoolean("route_hotspot", true)

                val pkgsArray = backup.optJSONArray("selected_packages")
                if (pkgsArray != null) {
                    val set = mutableSetOf<String>()
                    for (i in 0 until pkgsArray.length()) {
                        set.add(pkgsArray.getString(i))
                    }
                    selectedPackages = set
                }

                try { proxyType = ProxyType.valueOf(backup.optString("proxy_type", ProxyType.SOCKS5.name)) } catch (_: Exception) {}
                try { transportMode = TransportMode.valueOf(backup.optString("transport_mode", TransportMode.TCP_AND_UDP.name)) } catch (_: Exception) {}
                try { ipMode = IpMode.valueOf(backup.optString("ip_mode", IpMode.IPV4_ONLY.name)) } catch (_: Exception) {}

                saveConfig()
            }
        }
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
            delay(1000)
            val socksPort = ProfileManager.localMixedPort
            var result = IpFetcher.getPublicIpInfo(socksPort)
            if (result == null) {
                delay(1500)
                result = IpFetcher.getPublicIpInfo(socksPort)
            }
            if (result == null) {
                result = IpFetcher.getPublicIpInfo(null)
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

    val infiniteTransition = rememberInfiniteTransition(label = "livingAurora")
    val auroraAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraAngle"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020408))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(if (isProxyActive) auraPulse else 0.20f)) {
            val w = size.width
            val h = size.height
            val rad = Math.toRadians(auroraAngle.toDouble())

            val orb1X = (w * 0.30f) + (cos(rad) * 120f).toFloat()
            val orb1Y = (h * 0.20f) + (sin(rad) * 80f).toFloat()

            val orb2X = (w * 0.70f) - (sin(rad) * 140f).toFloat()
            val orb2Y = (h * 0.45f) + (cos(rad) * 100f).toFloat()

            val orb3X = (w * 0.45f) + (sin(rad * 1.3) * 100f).toFloat()
            val orb3Y = (h * 0.75f) - (cos(rad * 1.3) * 70f).toFloat()

            if (isProxyActive) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x5500FF88), Color(0x1800FF88), Color.Transparent),
                        center = Offset(orb1X, orb1Y),
                        radius = w * 0.75f
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = w * 0.75f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x4400E5FF), Color(0x1200E5FF), Color.Transparent),
                        center = Offset(orb2X, orb2Y),
                        radius = w * 0.85f
                    ),
                    center = Offset(orb2X, orb2Y),
                    radius = w * 0.85f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x35059669), Color(0x0C059669), Color.Transparent),
                        center = Offset(orb3X, orb3Y),
                        radius = w * 0.70f
                    ),
                    center = Offset(orb3X, orb3Y),
                    radius = w * 0.70f
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x203B82F6), Color.Transparent),
                        center = Offset(orb1X, orb1Y),
                        radius = w * 0.65f
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = w * 0.65f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Tactical Top App Bar
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
                        text = "PROFILE $activeSlot • KERNEL TUNNEL",
                        fontSize = 10.sp,
                        color = if (isProxyActive) Color(0xFF00FF88) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x1AFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        when (rootState) {
                            RootState.GRANTED -> Color(0x8800FF88)
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
                                        RootState.GRANTED -> Color(0xFF00FF88)
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

            // 5-PROFILE SLOTS SELECTOR RIBBON
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0 until ProfileManager.MAX_PROFILES).forEach { slotIndex ->
                    val isSelected = slotIndex == activeSlot
                    val slotBg by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF00FF88).copy(alpha = 0.18f) else Color(0x22111827),
                        animationSpec = tween(200),
                        label = "slotBg"
                    )
                    val borderColor = if (isSelected) Color(0xFF00FF88) else Color(0x1FFFFFFF)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = slotBg,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { switchSlot(slotIndex) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "P$slotIndex",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) Color(0xFF00FF88) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capsule Tabs Switcher
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x330B1120),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabTitles = listOf("Console", "Config", "Apps (${selectedPackages.size})")
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val tabBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0x3DFFFFFF) else Color.Transparent,
                            animationSpec = tween(220),
                            label = "tabBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                            animationSpec = tween(220),
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
                            routeHotspot = routeHotspot,
                            onRefreshIp = { triggerPublicIpCheck() },
                            onToggleProxy = {
                                if (!isProxyActive && host.trim().isEmpty()) {
                                    testStatus = "Please enter Server Host/IP in Config"
                                    selectedTab = 1
                                    return@ConsoleHUDTab
                                }

                                saveConfig()
                                if (isProxyActive) {
                                    onStopProxy { }
                                } else {
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
                                    selectedTab = 1
                                    return@ConsoleHUDTab
                                }
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
                                    val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.profileId)
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
                            sni = sni,
                            onSniChange = { sni = it; saveConfig() },
                            ssMethod = ssMethod,
                            onSsMethodChange = { ssMethod = it; saveConfig() },
                            realityPublicKey = realityPublicKey,
                            onRealityPublicKeyChange = { realityPublicKey = it; saveConfig() },
                            realityShortId = realityShortId,
                            onRealityShortIdChange = { realityShortId = it; saveConfig() },
                            proxyType = proxyType,
                            onProxyTypeChange = { proxyType = it; saveConfig() },
                            transportMode = transportMode,
                            onTransportModeChange = { transportMode = it; saveConfig() },
                            ipMode = ipMode,
                            onIpModeChange = { ipMode = it; saveConfig() },
                            routeHotspot = routeHotspot,
                            onRouteHotspotChange = { routeHotspot = it; saveConfig() },
                            startOnBoot = startOnBoot,
                            onStartOnBootChange = { startOnBoot = it; saveConfig() }
                        )
                    }
                }

                2 -> {
                    AppFilterTab(
                        installedApps = installedApps,
                        routeWholeProfile = routeWholeProfile,
                        onToggleRouteWhole = { routeWholeProfile = it; saveConfig() },
                        selectedPackages = selectedPackages,
                        onTogglePackage = { pkg ->
                            selectedPackages = if (selectedPackages.contains(pkg)) {
                                selectedPackages - pkg
                            } else {
                                selectedPackages + pkg
                            }
                            saveConfig()
                        },
                        onSelectAll = {
                            selectedPackages = installedApps.map { it.packageName }.toSet()
                            saveConfig()
                        },
                        onClearAll = {
                            selectedPackages = emptySet()
                            saveConfig()
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
                            ProxyController.clearLogs(ProfileManager.profileId)
                            currentLogs = "Logs cleared."
                        }) {
                            Text("Clear")
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val logs = ProxyController.getDiagnosticsAndLogs(ProfileManager.profileId)
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
    publicIpInfo: GeoIpResult?,
    isFetchingIp: Boolean,
    ipFetchFailed: Boolean,
    activePid: String?,
    proxyType: ProxyType,
    transportMode: TransportMode,
    ipMode: IpMode,
    routeHotspot: Boolean,
    onRefreshIp: () -> Unit,
    onToggleProxy: () -> Unit,
    isTesting: Boolean,
    testStatus: String?,
    onTestUpstream: () -> Unit,
    onViewLogs: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderBrush = if (isProxyActive) {
        Brush.sweepGradient(
            colors = listOf(Color(0xFF00FF88), Color(0xFF00E5FF), Color(0x2200FF88), Color(0xFF00FF88))
        )
    } else {
        Brush.sweepGradient(
            colors = listOf(Color(0x22FFFFFF), Color(0x3338BDF8), Color(0x11FFFFFF), Color(0x22FFFFFF))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, borderBrush, RoundedCornerShape(24.dp))
            .background(Color(0x4D070D19))
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
                            .alpha(if (isProxyActive) pulseAlpha else 1f)
                            .background(if (isProxyActive) Color(0xFF00FF88) else Color(0xFF64748B), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProxyActive) "TUNNEL ONLINE" else "TUNNEL OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isProxyActive) Color(0xFF00FF88) else Color(0xFF94A3B8),
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
                color = Color(0x400F172A),
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
                                        ipFetchFailed -> "Tap to retry"
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
                                    color = if (ipFetchFailed) Color(0xFFFBBF24) else Color(0xFF00E5FF),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (isProxyActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x2200FF88),
                            modifier = Modifier.clickable { onRefreshIp() }
                        ) {
                            Text(
                                text = if (isFetchingIp) "..." else "Refresh",
                                fontSize = 11.sp,
                                color = Color(0xFF00FF88),
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
                    .background(Color(0x331F2937), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)

                Text(proxyType.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = pillModifier)
                Text(
                    if (transportMode == TransportMode.TCP_AND_UDP) "TCP+UDP" else "TCP Only",
                    fontSize = 10.sp,
                    color = Color(0xFF00FF88),
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
                    color = Color(0xFF00E5FF),
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

    Spacer(modifier = Modifier.height(16.dp))

    LivingRadarMasterButton(
        isProxyActive = isProxyActive,
        onClick = onToggleProxy
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onTestUpstream,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
            border = BorderStroke(1.dp, Color(0x3300E5FF)),
            enabled = !isTesting
        ) {
            Text(if (isTesting) "Pinging..." else "Ping Latency", fontSize = 13.sp)
        }

        OutlinedButton(
            onClick = onViewLogs,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
        ) {
            Text("Core Logs", fontSize = 13.sp)
        }
    }

    if (testStatus != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = testStatus,
            color = if (testStatus.startsWith("Online")) Color(0xFF00FF88) else Color(0xFFF43F5E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x2B059669),
                border = BorderStroke(1.dp, Color(0x3300FF88)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF00FF88), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DOWNLOAD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FF88),
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
                            color = Color(0xFF00FF88),
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
                color = Color(0x2B0284C7),
                border = BorderStroke(1.dp, Color(0x3300E5FF)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF00E5FF), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPLOAD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF),
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
                            color = Color(0xFF00E5FF),
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
                .background(Color(0x26000000))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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
                        brush = Brush.verticalGradient(listOf(Color(0x2000E5FF), Color.Transparent))
                    )
                }

                drawPath(
                    path = path1,
                    color = if (isProxyActive) Color(0x6600E5FF) else Color(0x1A475569),
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
                        brush = Brush.verticalGradient(listOf(Color(0x3000FF88), Color.Transparent))
                    )
                }

                drawPath(
                    path = path2,
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (isProxyActive) Color(0xFF00FF88) else Color(0x33475569),
                            if (isProxyActive) Color(0xFF00D9F5) else Color(0x33475569)
                        )
                    ),
                    style = Stroke(width = if (isProxyActive) 2.2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun LivingRadarMasterButton(
    isProxyActive: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "btnMotion")
    val sweepOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepOffset"
    )

    val activeGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "activeGlowAlpha"
    )

    val gradientBrush = if (isProxyActive) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFE11D48)),
            startX = sweepOffset * 1000f,
            endX = (sweepOffset * 1000f) + 600f
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF00FF88), Color(0xFF00E5FF), Color(0xFF00FF88)),
            startX = sweepOffset * 1000f,
            endX = (sweepOffset * 1000f) + 600f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isProxyActive) activeGlowAlpha else 0.4f)
                .background(gradientBrush)
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isProxyActive) "DISCONNECT PROXY" else "CONNECT TRANSPARENT PROXY",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (isProxyActive) Color.White else Color(0xFF020408),
                letterSpacing = 0.8.sp
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
        color = Color(0xFF00FF88),
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
    sni: String,
    onSniChange: (String) -> Unit,
    ssMethod: String,
    onSsMethodChange: (String) -> Unit,
    realityPublicKey: String,
    onRealityPublicKeyChange: (String) -> Unit,
    realityShortId: String,
    onRealityShortIdChange: (String) -> Unit,
    proxyType: ProxyType,
    onProxyTypeChange: (ProxyType) -> Unit,
    transportMode: TransportMode,
    onTransportModeChange: (TransportMode) -> Unit,
    ipMode: IpMode,
    onIpModeChange: (IpMode) -> Unit,
    routeHotspot: Boolean,
    onRouteHotspotChange: (Boolean) -> Unit,
    startOnBoot: Boolean,
    onStartOnBootChange: (Boolean) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var isCheckingAlive by remember { mutableStateOf(false) }
    var aliveCheckResult by remember { mutableStateOf<TestResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x330B1120),
            border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
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
                            text = "Auto-Start on Boot",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Runs via /data/adb/service.d after 5s",
                            fontSize = 11.sp,
                            color = if (startOnBoot) Color(0xFF00FF88) else Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBootChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF020408),
                            checkedTrackColor = Color(0xFF00FF88)
                        )
                    )
                }

                Divider(color = Color(0x14FFFFFF), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 10.dp))

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
                            color = if (routeHotspot) Color(0xFF00FF88) else Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = routeHotspot,
                        onCheckedChange = onRouteHotspotChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF020408),
                            checkedTrackColor = Color(0xFF00FF88)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    onClick = { onProxyTypeChange(type) },
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

        Spacer(modifier = Modifier.height(12.dp))

        Text("IP MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(6.dp))
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

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Server Host / IP") },
            placeholder = { Text("e.g. 192.168.1.100 or proxy.domain.com") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Server Port") },
            placeholder = { Text("1080") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Protocol Inputs
        when (proxyType) {
            ProxyType.SOCKS5, ProxyType.HTTP -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Username") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        placeholder = { Text("Optional") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (passwordVisible) "Hide" else "Show",
                                fontSize = 11.sp,
                                color = Color(0xFF00FF88),
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
                OutlinedTextField(
                    value = ssMethod,
                    onValueChange = onSsMethodChange,
                    label = { Text("Cipher / Method") },
                    placeholder = { Text("2022-blake3-aes-128-gcm or aes-128-gcm") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password / Pre-Shared Key") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            fontSize = 11.sp,
                            color = Color(0xFF00FF88),
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
                    onValueChange = onPasswordChange,
                    label = { Text("UUID") },
                    placeholder = { Text("e.g. 550e8400-e29b-41d4-a716-446655440000") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = sni,
                    onValueChange = onSniChange,
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
                        onValueChange = onRealityPublicKeyChange,
                        label = { Text("Reality Public Key") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = realityShortId,
                        onValueChange = onRealityShortIdChange,
                        label = { Text("Reality Short ID") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            }

            ProxyType.TROJAN, ProxyType.HYSTERIA2 -> {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Auth Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            fontSize = 11.sp,
                            color = Color(0xFF00FF88),
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
                    onValueChange = onSniChange,
                    label = { Text("SNI / Server Name") },
                    placeholder = { Text("e.g. yourdomain.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            ProxyType.SOCKS4 -> {}
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x330B1120),
            border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
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
                                val currentSettings = ProxySettings(
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
                                val res = ProxyTester.testProxy(currentSettings)
                                aliveCheckResult = res
                                isCheckingAlive = false
                            }
                        },
                        enabled = !isCheckingAlive,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF88),
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
                                color = Color(0x2200FF88),
                                border = BorderStroke(1.dp, Color(0x6600FF88)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF00FF88), CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PROXY ALIVE • Latency: ${result.latencyMs} ms",
                                        color = Color(0xFF00FF88),
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

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AppFilterTab(
    installedApps: List<AppItem>,
    routeWholeProfile: Boolean,
    onToggleRouteWhole: (Boolean) -> Unit,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
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
            color = Color(0x330B1120),
            border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
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
                            checkedThumbColor = Color(0xFF020408),
                            checkedTrackColor = Color(0xFF00FF88)
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
                TextButton(onClick = onSelectAll) { Text("Select All", color = Color(0xFF00FF88)) }
                TextButton(onClick = onClearAll) { Text("Clear All", color = Color(0xFF94A3B8)) }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = selectedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePackage(app.packageName) }
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
                            onCheckedChange = { onTogglePackage(app.packageName) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF00FF88),
                                checkmarkColor = Color(0xFF020408)
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
                    text = "All applications in Profile ${ProfileManager.profileId} are currently routed.\nDisable switch above to choose specific apps.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
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
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
