package com.nameless.proxy

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.nameless.proxy.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val name: String,
    val packageName: String,
    val uid: Int
)

class MainActivity : ComponentActivity() {

    private var installedApps by mutableStateOf<List<AppItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadInstalledApps()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F0F12)
                ) {
                    MainScreen(
                        installedApps = installedApps,
                        onStartProxy = { settings, selectedUids, onResult ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val result = ProxyController.startProxy(this@MainActivity, settings, selectedUids)
                                withContext(Dispatchers.Main) {
                                    onResult(result.success, result.errorMessage)
                                }
                            }
                        },
                        onStopProxy = { onResult ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val success = ProxyController.stopProxy(this@MainActivity)
                                withContext(Dispatchers.Main) {
                                    onResult(success)
                                }
                            }
                        }
                    )
                }
            }
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
    installedApps: List<AppItem>,
    onStartProxy: (ProxySettings, List<Int>?, (Boolean, String?) -> Unit) -> Unit,
    onStopProxy: ((Boolean) -> Unit) -> Unit
) {
    var isConnected by remember { mutableStateOf(false) }
    var proxyType by remember { mutableStateOf(ProxyType.SOCKS5) }
    var ipMode by remember { mutableStateOf(IpMode.IPV4_ONLY) }
    var host by remember { mutableStateOf("48.45.153.215") }
    var port by remember { mutableStateOf("46508") }
    var username by remember { mutableStateOf("FgCH4MnS3EQDohq") }
    var password by remember { mutableStateOf("SzrAO5ADxzz81RP") }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var currentLogs by remember { mutableStateOf("") }

    var routeWholeProfile by remember { mutableStateOf(true) }
    var selectedUids by remember { mutableStateOf(setOf<Int>()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Profile Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile ${ProfileManager.profileId}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color(0xFF00E676) else Color.White
                    )
                    Text(
                        text = "Port: ${ProfileManager.localInboundPort}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isConnected) "● Daemon Running & Routing" else "○ Engine Disconnected",
                    color = if (isConnected) Color(0xFF00E676) else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Connect / Disconnect Toggle Button
        Button(
            onClick = {
                if (isConnected) {
                    onStopProxy {
                        isConnected = false
                    }
                } else {
                    val settings = ProxySettings(
                        type = proxyType,
                        ipMode = ipMode,
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 1080,
                        username = username.trim(),
                        password = password.trim()
                    )
                    val targets = if (routeWholeProfile) null else selectedUids.toList()
                    onStartProxy(settings, targets) { success, error ->
                        if (success) {
                            isConnected = true
                        } else {
                            isConnected = false
                            testStatus = "Start Failed: ${error ?: "Unknown error"}"
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFFD32F2F) else Color(0xFF00E676)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isConnected) "DISCONNECT TRANSPARENT PROXY" else "CONNECT TRANSPARENT PROXY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) Color.White else Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Test Proxy & View Logs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isTesting = true
                    testStatus = "Testing..."
                    val settings = ProxySettings(
                        type = proxyType,
                        ipMode = ipMode,
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 1080,
                        username = username.trim(),
                        password = password.trim()
                    )
                    coroutineScope.launch {
                        when (val res = ProxyTester.testProxy(settings)) {
                            is TestResult.Success -> {
                                testStatus = "Valid (⚡ ${res.latencyMs} ms)"
                            }
                            is TestResult.Failure -> {
                                testStatus = "Failed: ${res.error}"
                            }
                        }
                        isTesting = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isTesting
            ) {
                Text(if (isTesting) "Testing..." else "Test Proxy")
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val logs = ProxyController.getRecentLogs()
                        withContext(Dispatchers.Main) {
                            currentLogs = if (logs.isNotEmpty()) logs else "No logs found."
                            showLogsDialog = true
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("View Core Logs")
            }
        }

        if (testStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = testStatus!!,
                color = if (testStatus!!.startsWith("Valid")) Color(0xFF00E676) else Color(0xFFFF5252),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Protocol Selection
        Text("Proxy Protocol", color = Color.Gray, fontSize = 13.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ProxyType.SOCKS5, ProxyType.SOCKS4, ProxyType.HTTP).forEach { type ->
                FilterChip(
                    selected = proxyType == type,
                    onClick = { proxyType = type },
                    label = { Text(type.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. IP Routing Mode
        Text("IP Routing Mode", color = Color.Gray, fontSize = 13.sp)
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
                    onClick = { ipMode = mode },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Host & Port
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Server Host / IP") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Server Port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 7. Username & Password
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Routing Scope
        Text("Routing Scope", color = Color.Gray, fontSize = 13.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { routeWholeProfile = !routeWholeProfile }
        ) {
            Switch(
                checked = routeWholeProfile,
                onCheckedChange = { routeWholeProfile = it }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (routeWholeProfile) "Route Entire User Profile" else "Filter Apps (Per-App Proxy)",
                color = Color.White
            )
        }

        if (!routeWholeProfile) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Apps (${selectedUids.size} Selected)")
            }
        }
    }

    // Dialog for inspecting sing-box output
    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close")
                }
            },
            title = { Text("sing-box Core Logs") },
            text = {
                Text(
                    text = currentLogs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        )
    }

    if (showAppPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAppPicker = false },
            containerColor = Color(0xFF16161D)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Per-App Routing (Profile ${ProfileManager.profileId})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
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
}
