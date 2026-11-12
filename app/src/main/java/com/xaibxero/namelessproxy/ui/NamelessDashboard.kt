package com.xaibxero.namelessproxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xaibxero.namelessproxy.core.*
import com.xaibxero.namelessproxy.diagnostics.LeakAuditEngine
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val AmoledBlack = Color(0xFF000000)
private val CardSurface = Color(0xFF090C12)
private val ChipSurface = Color(0xFF121722)
private val HairlineBorder = Color(0xFF1F2736)
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

    var currentPage by remember { mutableIntStateOf(0) } // 0 = Proxy Engine, 1 = Web Leak Tabs
    var settings by remember { mutableStateOf(ProfileSettings()) }
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
        isConnected = RootEngine.isRunning()
        if (isConnected) {
            isError = false
            statusText = "Kernel Active • User ${ProfileContext.userId}"
        }
        slotLatencies = LeakAuditEngine.pingAllSlots(settings.slots)
    }

    val activeSlot = settings.slots[settings.activeSlot]

    val orbInteraction = remember { MutableInteractionSource() }
    val orbPressed by orbInteraction.collectIsPressedAsState()
    val orbScale by animateFloatAsState(
        targetValue = when {
            orbPressed -> 0.85f
            isConnected -> 1.06f
            else -> 1.0f
        },
        animationSpec = BubblySpring,
        label = "orbScale"
    )
    val orbCoreColor by animateColorAsState(
        targetValue = if (isConnected) NeonMint else ChipSurface,
        label = "orbCoreColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bubbleAnim")
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )
    val bubbleFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubbleFloat"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = AmoledBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top Bar: Brand + User Profile Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "namelessproxy",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
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

            // Bubbly Page Switcher: PROXY ENGINE vs WEB LEAK CHECK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Proxy Engine", "Web Leak Check").forEachIndexed { idx, title ->
                    val active = currentPage == idx
                    val tabScale by animateFloatAsState(
                        targetValue = if (active) 1.0f else 0.96f,
                        animationSpec = BubblySpring,
                        label = "pageTab$idx"
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (active) NeonMint else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .scale(tabScale)
                            .clip(CircleShape)
                            .clickable { currentPage = idx }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (active) AmoledBlack else MutedText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (currentPage == 1) {
                BuiltInWebTabs(modifier = Modifier.weight(1f))
                return@Column
            }

            // Page 0: Proxy Engine Control Center
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bubbly Liquid Canvas Power Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(184.dp)
                        .scale(orbScale)
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
                        val baseRadius = size.minDimension * 0.34f

                        if (isConnected) {
                            // Two staggered expanding liquid sonar rings
                            for (i in 0..1) {
                                val phase = (ripplePhase + i * 0.5f) % 1f
                                val ringRadius = baseRadius + (size.minDimension * 0.16f * phase)
                                val alpha = (1f - phase) * 0.45f
                                drawCircle(
                                    color = NeonMint.copy(alpha = alpha),
                                    radius = ringRadius,
                                    center = center,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }

                        // Outer gradient halo border
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = if (isConnected) {
                                    listOf(NeonMint, ElectricCyan, NeonMint)
                                } else {
                                    listOf(HairlineBorder, ChipSurface, HairlineBorder)
                                }
                            ),
                            radius = baseRadius + 8.dp.toPx(),
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Main Orb Fill
                        drawCircle(
                            color = orbCoreColor,
                            radius = baseRadius,
                            center = center
                        )

                        // Floating ambient micro-bubbles inside the orb
                        val bubbleColor = if (isConnected) {
                            Color.White.copy(alpha = 0.28f)
                        } else {
                            NeonMint.copy(alpha = 0.16f)
                        }
                        val b1 = Offset(
                            center.x + cos(bubbleFloat) * (baseRadius * 0.45f),
                            center.y + sin(bubbleFloat * 1.3f) * (baseRadius * 0.40f)
                        )
                        val b2 = Offset(
                            center.x + sin(bubbleFloat * 0.9f) * (baseRadius * 0.50f),
                            center.y - cos(bubbleFloat * 1.1f) * (baseRadius * 0.42f)
                        )
                        drawCircle(color = bubbleColor, radius = 6.dp.toPx(), center = b1)
                        drawCircle(color = bubbleColor, radius = 4.dp.toPx(), center = b2)
                    }

                    Text(
                        text = if (isBusy) "..." else if (isConnected) "ON" else "OFF",
                        color = if (isConnected) AmoledBlack else Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                // Status / Diagnostic Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
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

                // 5 Equally Aligned Bubbly Slot Cards (S1 - S5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    settings.slots.forEachIndexed { idx, slot ->
                        val selected = idx == settings.activeSlot
                        val hasHost = slot.host.isNotBlank()
                        val pingMs: Int? = slotLatencies[idx]
                        val pillScale by animateFloatAsState(
                            targetValue = if (selected) 1.05f else 0.97f,
                            animationSpec = BubblySpring,
                            label = "slotCard$idx"
                        )
                        Surface(
                            shape = RoundedCornerShape(18.dp),
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
                                .height(58.dp)
                                .scale(pillScale)
                                .clip(RoundedCornerShape(18.dp))
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
                                Spacer(modifier = Modifier.height(2.dp))
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
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Active Slot Editor Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Slot ${settings.activeSlot + 1} • ${activeSlot.label}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MiniPill(
                                    label = if (isPinging) "..." else "Ping",
                                    accent = MutedText
                                ) {
                                    scope.launch {
                                        isPinging = true
                                        slotLatencies = LeakAuditEngine.pingAllSlots(settings.slots)
                                        isPinging = false
                                    }
                                }
                                MiniPill(label = "Paste", accent = ElectricCyan) {
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
                                        statusText = "No valid proxy URI found in clipboard"
                                    }
                                }
                                MiniPill(label = "Save", accent = NeonMint) {
                                    scope.launch {
                                        SettingsStore.save(context, settings)
                                        slotLatencies = LeakAuditEngine.pingAllSlots(settings.slots)
                                        isError = false
                                        statusText = "Saved Slot ${settings.activeSlot + 1} to ${ProfileContext.userDir}"
                                    }
                                }
                            }
                        }

                        // Protocol Selector Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                    shape = CircleShape,
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
                                        label = { Text("Transport") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f)
                                    )
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
                    }
                }

                // Clean Kernel Switches Card (Only Boot & Hotspot)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPill(label: String, accent: Color, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = ChipSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
        )
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
