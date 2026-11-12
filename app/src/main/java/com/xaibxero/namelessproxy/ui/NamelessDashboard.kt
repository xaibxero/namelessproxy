package com.xaibxero.namelessproxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaibxero.namelessproxy.core.*
import com.xaibxero.namelessproxy.diagnostics.LeakAuditEngine
import com.xaibxero.namelessproxy.diagnostics.LeakReport
import kotlinx.coroutines.launch

private val BubblySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

@Composable
fun NamelessDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(ProfileSettings()) }
    var isConnected by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Idle • User ${ProfileContext.userId} (UID ${ProfileContext.myUid})")
    }
    var leakReport by remember { mutableStateOf<LeakReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.load(context)
        isConnected = RootEngine.isRunning()
        if (isConnected) {
            statusText = "Kernel Active • User ${ProfileContext.userId}"
        }
    }

    val activeSlot = settings.slots[settings.activeSlot]

    val orbInteraction = remember { MutableInteractionSource() }
    val orbPressed by orbInteraction.collectIsPressedAsState()
    val orbScale by animateFloatAsState(
        targetValue = when {
            orbPressed -> 0.88f
            isConnected -> 1.07f
            else -> 1.0f
        },
        animationSpec = BubblySpring,
        label = "orbScale"
    )
    val orbColor by animateColorAsState(
        targetValue = if (isConnected) Color(0xFF2EE59D) else Color(0xFF262B36),
        label = "orbColor"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F1117)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar with Profile UID Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "namelessproxy",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(shape = CircleShape, color = Color(0xFF1C2029)) {
                    Text(
                        text = "USER ${ProfileContext.userId}",
                        color = Color(0xFF2EE59D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bubbly Power Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(138.dp)
                    .scale(orbScale)
                    .clip(CircleShape)
                    .background(orbColor)
                    .clickable(
                        interactionSource = orbInteraction,
                        indication = null,
                        enabled = !isBusy
                    ) {
                        scope.launch {
                            isBusy = true
                            SettingsStore.save(context, settings)
                            if (isConnected) {
                                RootEngine.stopProxy()
                                isConnected = false
                                statusText = "Stopped • User ${ProfileContext.userId}"
                            } else {
                                val res = RootEngine.startProxy(context, activeSlot, settings)
                                isConnected = res.ok
                                statusText = if (res.ok) {
                                    "Kernel Active • ${activeSlot.protocol} (${activeSlot.host})"
                                } else {
                                    res.err.ifBlank { "Failed to start sing-box" }
                                }
                            }
                            isBusy = false
                        }
                    }
            ) {
                Text(
                    text = if (isBusy) "..." else if (isConnected) "ON" else "OFF",
                    color = if (isConnected) Color(0xFF071A12) else Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(text = statusText, color = Color(0xFF9AA3B2), fontSize = 13.sp)

            // 5 Bubbly Proxy Slots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                settings.slots.forEachIndexed { idx, _ ->
                    val selected = idx == settings.activeSlot
                    val pillScale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 0.96f,
                        animationSpec = BubblySpring,
                        label = "slotPill$idx"
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (selected) Color(0xFF2EE59D) else Color(0xFF1C2029),
                        modifier = Modifier
                            .weight(1f)
                            .scale(pillScale)
                            .clip(CircleShape)
                            .clickable {
                                settings = settings.copy(activeSlot = idx)
                                scope.launch { SettingsStore.save(context, settings) }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "S${idx + 1}",
                                color = if (selected) Color(0xFF071A12) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Active Proxy Slot Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171A21)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slot ${settings.activeSlot + 1} Config",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        TextButton(onClick = {
                            scope.launch {
                                SettingsStore.save(context, settings)
                                statusText = "Saved Slot ${settings.activeSlot + 1} to ${ProfileContext.userDir}"
                            }
                        }) {
                            Text("Save Slot", color = Color(0xFF2EE59D), fontSize = 12.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProxyProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = activeSlot.protocol == proto,
                                onClick = {
                                    val updated = settings.slots.toMutableList().apply {
                                        this[settings.activeSlot] = activeSlot.copy(protocol = proto)
                                    }
                                    settings = settings.copy(slots = updated)
                                },
                                label = { Text(proto.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = activeSlot.host,
                        onValueChange = { v ->
                            val updated = settings.slots.toMutableList().apply {
                                this[settings.activeSlot] = activeSlot.copy(host = v)
                            }
                            settings = settings.copy(slots = updated)
                        },
                        label = { Text("Proxy IP or Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (activeSlot.port == 0) "" else activeSlot.port.toString(),
                            onValueChange = { v ->
                                val p = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                                val updated = settings.slots.toMutableList().apply {
                                    this[settings.activeSlot] = activeSlot.copy(port = p)
                                }
                                settings = settings.copy(slots = updated)
                            },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = activeSlot.username,
                            onValueChange = { v ->
                                val updated = settings.slots.toMutableList().apply {
                                    this[settings.activeSlot] = activeSlot.copy(username = v)
                                }
                                settings = settings.copy(slots = updated)
                            },
                            label = { Text("User") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = activeSlot.password,
                            onValueChange = { v ->
                                val updated = settings.slots.toMutableList().apply {
                                    this[settings.activeSlot] = activeSlot.copy(password = v)
                                }
                                settings = settings.copy(slots = updated)
                            },
                            label = { Text("Pass") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Kernel Routing Options
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171A21)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToggleRow("Boot on Startup", settings.bootOnStartup) {
                        settings = settings.copy(bootOnStartup = it)
                        scope.launch { SettingsStore.save(context, settings) }
                    }
                    ToggleRow("Share via Hotspot", settings.shareHotspot) {
                        settings = settings.copy(shareHotspot = it)
                        scope.launch { SettingsStore.save(context, settings) }
                    }
                    Text("IPv6 Mode", color = Color(0xFF9AA3B2), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ipv6Mode.entries.forEach { mode ->
                            val label = when (mode) {
                                Ipv6Mode.BRIDGE_OVER_IPV4 -> "Bridge IPv6"
                                Ipv6Mode.NATIVE_DUAL_STACK -> "Dual-Stack"
                                Ipv6Mode.STRICT_BLOCK -> "Block IPv6"
                            }
                            FilterChip(
                                selected = settings.ipv6Mode == mode,
                                onClick = {
                                    settings = settings.copy(ipv6Mode = mode)
                                    scope.launch { SettingsStore.save(context, settings) }
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Built-in IP / DNS / WebRTC Leak Checker
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171A21)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IP / DNS / WebRTC Audit",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isAuditing = true
                                    leakReport = LeakAuditEngine.runFullAudit()
                                    isAuditing = false
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EE59D))
                        ) {
                            Text(
                                text = if (isAuditing) "Checking..." else "Check Now",
                                color = Color(0xFF071A12),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    leakReport?.let { r ->
                        Text("IPv4 Exit: ${r.ipv4Exit}", color = Color.White, fontSize = 13.sp)
                        Text("Location & ISP: ${r.locationAndIsp}", color = Color(0xFF9AA3B2), fontSize = 12.sp)
                        Text("IPv6: ${r.ipv6Status}", color = Color.White, fontSize = 13.sp)
                        Text("DNS: ${r.dnsStatus}", color = Color.White, fontSize = 13.sp)
                        Text("WebRTC STUN: ${r.webrtcStunIp}", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = if (r.isLeakFree) "PASS — Zero IP, DNS, or WebRTC Leaks" else "WARNING — Check Proxy Route",
                            color = if (r.isLeakFree) Color(0xFF2EE59D) else Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
