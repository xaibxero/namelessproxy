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
                        onStartProxy = { settings, selectedUids ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val success = ProxyController.startProxy(this@MainActivity, settings, selectedUids)
                                withContext(Dispatchers.Main) {
                                    val msg = if (success) "Proxy activated at kernel level" else "Failed to apply root rules"
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onStopProxy = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val success = ProxyController.stopProxy(this@MainActivity)
                                withContext(Dispatchers.Main) {
                                    val msg = if (success) "Proxy stopped and rules cleared" else "Failed to flush rules"
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
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
    onStartProxy: (ProxySettings, List<Int>?) -> Unit,
    onStopProxy: () -> Unit
) {
    var isConnected by remember { mutableStateOf(false) }
    var proxyType by remember { mutableStateOf(ProxyType.SOCKS5) }
    var ipMode by remember { mutableStateOf(IpMode.IPV4_ONLY) }
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("1080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var routeWholeProfile by remember { mutableStateOf(true) }
    var selectedUids by remember { mutableStateOf(setOf<Int>()) }
    var showAppPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Profile Status Pill
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
                        color = Color(0xFF00E676)
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
                    text = "UID: ${ProfileManager.uidStart} - ${ProfileManager.uidEnd}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Master Toggle Switch
        Button(
            onClick = {
                if (isConnected) {
                    onStopProxy()
                    isConnected = false
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
                    onStartProxy(settings, targets)
                    isConnected = true
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
                text = if (isConnected) "DISCONNECT PROXY" else "CONNECT TRANSPARENT PROXY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) Color.White else Color.Black
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Protocol Selection
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

        // 4. IP Mode Selection
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

        // 5. Host & Port Inputs
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

        // 6. Optional Auth
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

        // 7. Routing Scope
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
