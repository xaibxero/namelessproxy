package com.xaibxero.namelessproxy.diagnostics

import android.os.SystemClock
import com.xaibxero.namelessproxy.core.ProxySlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.InetSocketAddress
import java.net.Socket

object LeakAuditEngine {

    suspend fun pingAllSlots(slots: List<ProxySlot>): Map<Int, Int?> = coroutineScope {
        slots.map { slot ->
            async(Dispatchers.IO) {
                slot.index to pingSlot(slot)
            }
        }.awaitAll().toMap()
    }

    fun pingSlot(slot: ProxySlot): Int? {
        if (slot.host.isBlank() || slot.port !in 1..65535) return null
        return try {
            val start = SystemClock.elapsedRealtime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(slot.host.trim(), slot.port), 2500)
            }
            (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            -1
        }
    }
}
