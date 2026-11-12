package com.xaibxero.namelessproxy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaibxero.namelessproxy.core.*
import com.xaibxero.namelessproxy.diagnostics.LeakAuditEngine
import com.xaibxero.namelessproxy.diagnostics.LeakReport
import kotlinx.coroutines.launch

private val AmoledBlack = Color(0xFF000000)
private val CardSurface = Color(0xFF07090D)
private val ChipSurface = Color(0xFF0E1219)
private val HairlineBorder = Color(0xFF1D2432)
private val NeonMint = Color(0xFF00F59B)
private val AlertCoral = Color(0xFFFF4D6D)
private val MutedText = Color(0xFF8B95A5)

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
        mutableStateOf("IDLE • USER ${ProfileContext.userId} (UID ${ProfileContext.myUid})")
    }
    var leakReport by remember { mutableStateOf<LeakReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.load(context)
        isConnected = RootEngine.isRunning()
        if (isConnected) {
            statusText = "KERNEL ACTIVE • USER ${ProfileContext.userId}"
        }
    }

    val activeSlot = settings.slots[settings.activeSlot]

    // Bubbly spring physics for the center power orb
    val orbInteraction = remember { MutableInteractionSource() }
    val orbPressed by orbInteraction.collectIsPressedAsState()
    val orbScale by animateFloatAsState(
        targetValue = when {
            orbPressed -> 0.86f
            isConnected -> 1.07f
            else -> 1.0f
        },
        animationSpec = BubblySpring,
        label = "orbScale"
    )
    val orbFill by animateColorAsState(
        targetValue = if (isConnected) NeonMint else CardSurface,
        label = "orbFill"
    )
    val orbBorder by animateColorAsState(
        targetValue = if (isConnected) NeonMint else HairlineBorder,
        label = "orbBorder"
    )

    // Subtle breathing halo ring when proxy is active
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = AmoledBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Tactical Header with Isolated User UID Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "namelessproxy",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Surface(
                    shape = CircleShape,
                    color = CardSurface,
                    border = BorderStroke(1.dp, if (isConnected) NeonMint else HairlineBorder)
                ) {
                    Text(
                        text = "USER ${ProfileContext.userId} • UID ${ProfileContext.myUid}",
                        color = if (isConnected) NeonMint else MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Center Bubbly Power Orb with Pulse Halo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(144.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .border(1.5.dp, NeonMint.copy(alpha = 0.28f), CircleShape)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(136.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(orbFill)
                        .border(1.5.dp, orbBorder, CircleShape)
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
                                    statusText = "STOPPED • USER ${ProfileContext.userId}"
                                } else {
                                    val res = RootEngine.startProxy(context, activeSlot, settings)
                                    isConnected = res.ok
                                    statusText = if (res.ok) {
                                        "ACTIVE • ${activeSlot.protocol} -> ${activeSlot.host}:${activeSlot.port}"
                                    } else {
                                        res.err.ifBlank { "FAILED TO START SING-BOX" }.uppercase()
                                    }
                                }
                                isBusy = false
                            }
                        }
                ) {
                    Text(
                        text = if (isBusy) "..." else if (isConnected) "ON" else "OFF",
                        color = if (isConnected) AmoledBlack else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Text(
                text = statusText,
                color = if (isConnected) NeonMint else MutedText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )

            // 5 Bubbly Proxy Slot Pills (S1 - S5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                settings.slots.forEachIndexed { idx, slot ->
                    val selected = idx == settings.activeSlot
                    val hasHost = slot.host.isNotBlank()
                    val pillScale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 0.96f,
                        animationSpec = BubblySpring,
                        label = "slotPill$idx"
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (selected) NeonMint else CardSurface,
                        border = BorderStroke(
                            1.dp,
                            when {
                                selected -> NeonMint
                                hasHost -> NeonMint.copy(alpha = 0.45f)
                                else -> HairlineBorder
                            }
                        ),
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
                                color = if (selected) AmoledBlack else Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Slot Configuration Card (Hairline AMOLED Border)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, HairlineBorder),
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
                            text = "SLOT ${settings.activeSlot + 1} ENDPOINT",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = ChipSurface,
                            border = BorderStroke(1.dp, NeonMint.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    scope.launch {
                                        SettingsStore.save(context, settings)
                                        statusText = "SAVED SLOT ${settings.activeSlot + 1} -> ${ProfileContext.userDir}"
                                    }
                                }
                        ) {
                            Text(
                                text = "SAVE",
                                color = NeonMint,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProxyProtocol.entries.forEach { proto ->
                            val isSelected = activeSlot.protocol == proto
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated = settings.slots.toMutableList().apply {
                                        this[settings.activeSlot] = activeSlot.copy(protocol = proto)
                                    }
                                    settings = settings.copy(slots = updated)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonMint,
                                    selectedLabelColor = AmoledBlack,
                                    containerColor = ChipSurface,
                                    labelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = HairlineBorder,
                                    selectedBorderColor = NeonMint
                                ),
                                label = {
                                    Text(
                                        text = proto.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonMint,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = NeonMint,
                        unfocusedLabelColor = MutedText,
                        cursorColor = NeonMint
                    )

                    OutlinedTextField(
                        value = activeSlot.host,
                        onValueChange = { v ->
                            val updated = settings.slots.toMutableList().apply {
                                this[settings.activeSlot] = activeSlot.copy(host = v)
                            }
                            settings = settings.copy(slots = updated)
                        },
                        label = { Text("Proxy IP / Host") },
                        singleLine = true,
                        colors = fieldColors,
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
                            colors = fieldColors,
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
                            colors = fieldColors,
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
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Kernel Routing Toggles Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmoledToggleRow("Boot on Startup", settings.bootOnStartup) {
                        settings = settings.copy(bootOnStartup = it)
                        scope.launch { SettingsStore.save(context, settings) }
                    }
                    AmoledToggleRow("Share via Hotspot", settings.shareHotspot) {
                        settings = settings.copy(shareHotspot = it)
                        scope.launch { SettingsStore.save(context, settings) }
                    }
                    Text(
                        text = "IPV6 ENGINE MODE",
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ipv6Mode.entries.forEach { mode ->
                            val isSelected = settings.ipv6Mode == mode
                            val label = when (mode) {
                                Ipv6Mode.BRIDGE_OVER_IPV4 -> "Bridge IPv6"
                                Ipv6Mode.NATIVE_DUAL_STACK -> "Dual-Stack"
                                Ipv6Mode.STRICT_BLOCK -> "Block IPv6"
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    settings = settings.copy(ipv6Mode = mode)
                                    scope.launch { SettingsStore.save(context, settings) }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonMint,
                                    selectedLabelColor = AmoledBlack,
                                    containerColor = ChipSurface,
                                    labelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = HairlineBorder,
                                    selectedBorderColor = NeonMint
                                ),
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
            }

            // Built-in IP / DNS / WebRTC Leak Audit Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardSurface,
                border = BorderStroke(
                    1.dp,
                    when (leakReport?.isLeakFree) {
                        true -> NeonMint.copy(alpha = 0.6f)
                        false -> AlertCoral.copy(alpha = 0.6f)
                        null -> HairlineBorder
                    }
                ),
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
                            text = "IP / DNS / WEBRTC AUDIT",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        val auditBtnScale by animateFloatAsState(
                            targetValue = if (isAuditing) 0.94f else 1.0f,
                            animationSpec = BubblySpring,
                            label = "auditBtnScale"
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
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                            modifier = Modifier.scale(auditBtnScale)
                        ) {
                            Text(
                                text = if (isAuditing) "PROBING..." else "CHECK NOW",
                                color = AmoledBlack,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    leakReport?.let { r ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MonospaceAuditLine("EXIT IP", r.ipv4Exit)
                            MonospaceAuditLine("GEO/ISP", r.locationAndIsp)
                            MonospaceAuditLine("IPV6", r.ipv6Status)
                            MonospaceAuditLine("DNS", r.dnsStatus)
                            MonospaceAuditLine("WEBRTC", r.webrtcStunIp)
                        }

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + scaleIn(animationSpec = BubblySpring)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (r.isLeakFree) NeonMint.copy(alpha = 0.16f) else AlertCoral.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, if (r.isLeakFree) NeonMint else AlertCoral),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (r.isLeakFree) "PASS • ZERO IP, DNS, OR WEBRTC LEAKS" else "LEAK WARNING • VERIFY PROXY ROUTE",
                                        color = if (r.isLeakFree) NeonMint else AlertCoral,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonospaceAuditLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AmoledToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = NeonMint,
                uncheckedThumbColor = MutedText,
                uncheckedTrackColor = ChipSurface,
                uncheckedBorderColor = HairlineBorder
            )
        )
    }
}
