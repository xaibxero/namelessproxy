package com.xaibxero.namelessproxy.ui

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaibxero.namelessproxy.core.*
import com.xaibxero.namelessproxy.diagnostics.LeakAuditEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val AmoledBlack = Color(0xFF000000)
private val CardSurface = Color(0xFF0A0D14)
private val ChipSurface = Color(0xFF131822)
private val HairlineBorder = Color(0xFF1E2634)
private val NeonMint = Color(0xFF00F59B)
private val ElectricCyan = Color(0xFF00D2FF)
private val AlertCoral = Color(0xFFFF4D6D)
private val WarnAmber = Color(0xFFFFBE0B)
private val MutedText = Color(0xFF8B95A5)

private val BubblySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun NamelessDashboard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // 0 = Proxy Engine, 1 = IPFighter, 2 = DNS Leak, 3 = WebRTC
    var selectedTab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(ProfileSettings()) }
    var isLoaded by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Ready • User ${ProfileContext.userId} (UID ${ProfileContext.myUid})")
    }
    val emptyLatencies: Map<Int, Int?> = emptyMap()
    var slotLatencies by remember { mutableStateOf(emptyLatencies) }
    var isPinging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.load(context)
        isLoaded = true
        isConnected = RootEngine.isRunning()
        if (isConnected) {
            isError = false
            statusText = "Kernel Active • User ${ProfileContext.userId}"
        }
        slotLatencies = LeakAuditEngine.pingAllSlots(settings.slots)
    }

    // Auto-persist edits to /data/adb/namelessproxy/user<id>/settings.json so tab switches never lose changes
    LaunchedEffect(settings, isLoaded) {
        if (!isLoaded) return@LaunchedEffect
        delay(350)
        SettingsStore.save(context, settings)
    }

    // Instant kernel rule refresh whenever Wi-Fi or Cellular changes while connected
    DisposableEffect(context, isConnected) {
        if (!isConnected) return@DisposableEffect onDispose { }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { RootEngine.refreshRulesNow() }
            }
            override fun onLost(network: Network) {
                scope.launch { RootEngine.refreshRulesNow() }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    // Apply or clear hardware GPU acceleration flag on the window dynamically
    LaunchedEffect(settings.gpuAcceleration) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (settings.gpuAcceleration) {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
    }

    val activeSlot = settings.slots[settings.activeSlot]

    val orbInteraction = remember { MutableInteractionSource() }
    val orbPressed by orbInteraction.collectIsPressedAsState()
    val orbScale by animateFloatAsState(
        targetValue = when {
            orbPressed -> 0.85f
            isConnected -> 1.05f
            else -> 1.0f
        },
        animationSpec = if (settings.gpuAcceleration) BubblySpring else snap(),
        label = "orbScale"
    )
    val orbCoreColor by animateColorAsState(
        targetValue = if (isConnected) NeonMint else ChipSurface,
        label = "orbCoreColor"
    )

    // Smooth GPU RenderThread jelly morph + floating micro-bubbles + sonar pulse
    val pulsePhase: Float
    val jellyPhase: Float
    if (settings.gpuAcceleration) {
        val infinite = rememberInfiniteTransition(label = "gpuBubbly")
        val pAnim by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulsePhase"
        )
        val jAnim by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "jellyPhase"
        )
        pulsePhase = pAnim
        jellyPhase = jAnim
    } else {
        pulsePhase = 0f
        jellyPhase = 0f
    }

    val jellyScaleX = if (settings.gpuAcceleration) {
        orbScale * (1f + 0.025f * sin(jellyPhase))
    } else {
        orbScale
    }
    val jellyScaleY = if (settings.gpuAcceleration) {
        orbScale * (1f + 0.025f * cos(jellyPhase))
    } else {
        orbScale
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AmoledBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Settled Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "namelessproxy",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Surface(
                    shape = CircleShape,
                    color = CardSurface,
                    border = BorderStroke(1.dp, if (isConnected) NeonMint else HairlineBorder)
                ) {
                    Text(
                        text = "USER ${ProfileContext.userId} • UID ${ProfileContext.myUid}",
                        color = if (isConnected) NeonMint else MutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Auto-Open / Auto-Close Top Navigation Strip: Proxy | IPFighter | DNS Leak | WebRTC
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val navTitles = listOf("Proxy", "IPFighter", "DNS Leak", "WebRTC")
                navTitles.forEachIndexed { idx, label ->
                    val isTabActive = selectedTab == idx
                    val tabScale by animateFloatAsState(
                        targetValue = if (isTabActive) 1.0f else 0.96f,
                        animationSpec = if (settings.gpuAcceleration) BubblySpring else snap(),
                        label = "navTab$idx"
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isTabActive) NeonMint else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            }
                            .clip(CircleShape)
                            .clickable { selectedTab = idx }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isTabActive) AmoledBlack else MutedText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Keyed per tab so switching immediately destroys the old WebView and auto-runs the new one
            if (selectedTab in 1..3) {
                key(selectedTab) {
                    SingleAutoLeakWebTab(
                        preset = LeakTabPresets[selectedTab - 1],
                        gpuAccelerated = settings.gpuAcceleration,
                        onCloseTab = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                }
                return@Column
            }

            // Main Proxy Dashboard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // GPU-Accelerated Bubbly Jelly Power Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(172.dp)
                        .graphicsLayer {
                            scaleX = jellyScaleX
                            scaleY = jellyScaleY
                        }
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
                                    isError = false
                                    statusText = "Stopped • User ${ProfileContext.userId}"
                                } else {
                                    val res = RootEngine.startProxy(context, activeSlot, settings)
                                    isConnected = res.ok
                                    isError = !res.ok
                                    statusText = if (res.ok) {
                                        "Active • ${activeSlot.protocol} (${activeSlot.host}:${activeSlot.port})"
                                    } else {
                                        res.err.ifBlank { "Failed to start sing-box" }
                                    }
                                }
                                isBusy = false
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = size.minDimension * 0.35f

                        if (isConnected && settings.gpuAcceleration) {
                            for (i in 0..1) {
                                val phase = (pulsePhase + i * 0.5f) % 1f
                                val ringRadius = baseRadius + (size.minDimension * 0.15f * phase)
                                val alpha = (1f - phase) * 0.42f
                                drawCircle(
                                    color = NeonMint.copy(alpha = alpha),
                                    radius = ringRadius,
                                    center = center,
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }

                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = if (isConnected) {
                                    listOf(NeonMint, ElectricCyan, NeonMint)
                                } else {
                                    listOf(HairlineBorder, ChipSurface, HairlineBorder)
                                }
                            ),
                            radius = baseRadius + 7.dp.toPx(),
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        drawCircle(
                            color = orbCoreColor,
                            radius = baseRadius,
                            center = center
                        )

                        if (settings.gpuAcceleration) {
                            val bubbleTint = if (isConnected) {
                                Color.White.copy(alpha = 0.28f)
                            } else {
                                NeonMint.copy(alpha = 0.16f)
                            }
                            val b1 = Offset(
                                center.x + cos(jellyPhase) * (baseRadius * 0.44f),
                                center.y + sin(jellyPhase * 1.2f) * (baseRadius * 0.38f)
                            )
                            val b2 = Offset(
                                center.x + sin(jellyPhase * 0.8f) * (baseRadius * 0.48f),
                                center.y - cos(jellyPhase * 1.1f) * (baseRadius * 0.40f)
                            )
                            drawCircle(color = bubbleTint, radius = 5.5.dp.toPx(), center = b1)
                            drawCircle(color = bubbleTint, radius = 3.5.dp.toPx(), center = b2)
                        }
                    }

                    Text(
                        text = if (isBusy) "..." else if (isConnected) "ON" else "OFF",
                        color = if (isConnected) AmoledBlack else Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                // Settled Status Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isError -> AlertCoral.copy(alpha = 0.14f)
                        isConnected -> NeonMint.copy(alpha = 0.12f)
                        else -> CardSurface
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isError -> AlertCoral
                            isConnected -> NeonMint.copy(alpha = 0.5f)
                            else -> HairlineBorder
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusText,
                        color = when {
                            isError -> AlertCoral
                            isConnected -> NeonMint
                            else -> MutedText
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                // 5 Symmetrical Proxy Slot Cards (S1 - S5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    settings.slots.forEachIndexed { idx, slot ->
                        val selected = idx == settings.activeSlot
                        val hasHost = slot.host.isNotBlank()
                        val pingMs: Int? = slotLatencies[idx]
                        val pillScale by animateFloatAsState(
                            targetValue = if (selected) 1.04f else 0.98f,
                            animationSpec = if (settings.gpuAcceleration) BubblySpring else snap(),
                            label = "slotCard$idx"
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
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
                                .height(54.dp)
                                .graphicsLayer {
                                    scaleX = pillScale
                                    scaleY = pillScale
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val next = settings.copy(activeSlot = idx)
                                    settings = next
                                    scope.launch { SettingsStore.save(context, next) }
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "S${idx + 1}",
                                    color = if (selected) AmoledBlack else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                val pingLabel = when {
                                    pingMs == null -> "--"
                                    pingMs < 0 -> "OFF"
                                    else -> "${pingMs}ms"
                                }
                                val pingColor = when {
                                    selected -> AmoledBlack.copy(alpha = 0.78f)
                                    pingMs == null -> MutedText
                                    pingMs < 0 -> AlertCoral
                                    pingMs < 180 -> NeonMint
                                    else -> WarnAmber
                                }
                                Text(
                                    text = pingLabel,
                                    color = pingColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Active Proxy Slot Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val defaultSlotName = "Slot ${settings.activeSlot + 1}"
                        val slotHeader = if (activeSlot.label == defaultSlotName || activeSlot.label.isBlank()) {
                            "$defaultSlotName Config"
                        } else {
                            "$defaultSlotName • ${activeSlot.label}"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = slotHeader,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = CircleShape,
                                color = ChipSurface,
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            isPinging = true
                                            slotLatencies = LeakAuditEngine.pingAllSlots(settings.slots)
                                            isPinging = false
                                        }
                                    }
                            ) {
                                Text(
                                    text = if (isPinging) "Pinging..." else "Ping Slots",
                                    color = MutedText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // 2x4 Grid so ALL 8 Protocols are visible with zero cutoff
                        val protocolRows = ProxyProtocol.entries.chunked(4)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            protocolRows.forEach { rowProtos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowProtos.forEach { proto ->
                                        val isSelected = activeSlot.protocol == proto
                                        val shortLabel = when (proto) {
                                            ProxyProtocol.SHADOWSOCKS -> "SS"
                                            else -> proto.name
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) NeonMint else ChipSurface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) NeonMint else HairlineBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val updated = settings.slots.toMutableList().apply {
                                                        this[settings.activeSlot] = activeSlot.copy(protocol = proto)
                                                    }
                                                    settings = settings.copy(slots = updated)
                                                }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = shortLabel,
                                                    color = if (isSelected) AmoledBlack else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
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

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = activeSlot.host,
                                onValueChange = { v ->
                                    val updated = settings.slots.toMutableList().apply {
                                        this[settings.activeSlot] = activeSlot.copy(host = v)
                                    }
                                    settings = settings.copy(slots = updated)
                                },
                                label = { Text("Host / IP") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.weight(2.1f)
                            )
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
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        when (activeSlot.protocol) {
                            ProxyProtocol.SHADOWSOCKS -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = activeSlot.methodOrFlow,
                                        onValueChange = { v ->
                                            val updated = settings.slots.toMutableList().apply {
                                                this[settings.activeSlot] = activeSlot.copy(methodOrFlow = v)
                                            }
                                            settings = settings.copy(slots = updated)
                                        },
                                        label = { Text("Cipher") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
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
                                        label = { Text("Password") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            ProxyProtocol.VLESS, ProxyProtocol.TROJAN -> {
                                OutlinedTextField(
                                    value = activeSlot.password,
                                    onValueChange = { v ->
                                        val updated = settings.slots.toMutableList().apply {
                                            this[settings.activeSlot] = activeSlot.copy(password = v)
                                        }
                                        settings = settings.copy(slots = updated)
                                    },
                                    label = { Text(if (activeSlot.protocol == ProxyProtocol.VLESS) "UUID" else "Password") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (activeSlot.protocol == ProxyProtocol.VLESS) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("none", "tls", "reality").forEach { sec ->
                                            val isSec = activeSlot.security.equals(sec, true)
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSec) NeonMint else ChipSurface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSec) NeonMint else HairlineBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        val updated = settings.slots.toMutableList().apply {
                                                            this[settings.activeSlot] = activeSlot.copy(security = sec)
                                                        }
                                                        settings = settings.copy(slots = updated)
                                                    }
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(vertical = 7.dp)
                                                ) {
                                                    Text(
                                                        text = sec.uppercase(),
                                                        color = if (isSec) AmoledBlack else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = activeSlot.sni,
                                        onValueChange = { v ->
                                            val updated = settings.slots.toMutableList().apply {
                                                this[settings.activeSlot] = activeSlot.copy(sni = v)
                                            }
                                            settings = settings.copy(slots = updated)
                                        },
                                        label = { Text("SNI") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = activeSlot.transport,
                                        onValueChange = { v ->
                                            val updated = settings.slots.toMutableList().apply {
                                                this[settings.activeSlot] = activeSlot.copy(transport = v)
                                            }
                                            settings = settings.copy(slots = updated)
                                        },
                                        label = { Text("tcp / ws / grpc") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (activeSlot.protocol == ProxyProtocol.VLESS && activeSlot.security.equals("reality", true)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = activeSlot.realityPublicKey,
                                            onValueChange = { v ->
                                                val updated = settings.slots.toMutableList().apply {
                                                    this[settings.activeSlot] = activeSlot.copy(realityPublicKey = v)
                                                }
                                                settings = settings.copy(slots = updated)
                                            },
                                            label = { Text("Reality Public Key") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = fieldColors,
                                            modifier = Modifier.weight(1.4f)
                                        )
                                        OutlinedTextField(
                                            value = activeSlot.realityShortId,
                                            onValueChange = { v ->
                                                val updated = settings.slots.toMutableList().apply {
                                                    this[settings.activeSlot] = activeSlot.copy(realityShortId = v)
                                                }
                                                settings = settings.copy(slots = updated)
                                            },
                                            label = { Text("Short ID") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = fieldColors,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                }
                            }

                            else -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = activeSlot.username,
                                        onValueChange = { v ->
                                            val updated = settings.slots.toMutableList().apply {
                                                this[settings.activeSlot] = activeSlot.copy(username = v)
                                            }
                                            settings = settings.copy(slots = updated)
                                        },
                                        label = { Text("Username (Opt)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
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
                                        label = { Text("Password (Opt)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Balanced Full-Width Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipText = clipboard.getText()?.text.orEmpty()
                                    val parsed = ProxyUriParser.parseIntoSlot(clipText, activeSlot)
                                    if (parsed != null) {
                                        val updated = settings.slots.toMutableList().apply {
                                            this[settings.activeSlot] = parsed
                                        }
                                        val next = settings.copy(slots = updated)
                                        settings = next
                                        isError = false
                                        statusText = "Imported ${parsed.protocol} (${parsed.host}:${parsed.port})"
                                        scope.launch {
                                            SettingsStore.save(context, next)
                                            slotLatencies = LeakAuditEngine.pingAllSlots(next.slots)
                                        }
                                    } else {
                                        isError = true
                                        statusText = "No valid proxy URI in clipboard"
                                    }
                                },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "Paste URI",
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        SettingsStore.save(context, settings)
                                        slotLatencies = LeakAuditEngine.pingAllSlots(settings.slots)
                                        isError = false
                                        statusText = "Saved Slot ${settings.activeSlot + 1}"
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "Save Slot",
                                    color = AmoledBlack,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // System & Performance Switches Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        CleanToggleRow("Boot on Startup", settings.bootOnStartup) {
                            val next = settings.copy(bootOnStartup = it)
                            settings = next
                            scope.launch { SettingsStore.save(context, next) }
                        }
                        HorizontalDivider(color = HairlineBorder, thickness = 0.5.dp)
                        CleanToggleRow("Share via Hotspot", settings.shareHotspot) {
                            val next = settings.copy(shareHotspot = it)
                            settings = next
                            scope.launch {
                                SettingsStore.save(context, next)
                                if (isConnected) {
                                    RootEngine.startProxy(context, activeSlot, next)
                                }
                            }
                        }
                        HorizontalDivider(color = HairlineBorder, thickness = 0.5.dp)
                        CleanToggleRow("GPU Acceleration", settings.gpuAcceleration) {
                            val next = settings.copy(gpuAcceleration = it)
                            settings = next
                            scope.launch { SettingsStore.save(context, next) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
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
