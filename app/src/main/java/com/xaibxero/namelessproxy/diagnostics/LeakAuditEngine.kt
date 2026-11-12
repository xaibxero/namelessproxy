package com.xaibxero.namelessproxy.diagnostics

import android.os.SystemClock
import com.xaibxero.namelessproxy.core.ProxySlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.security.SecureRandom

data class LeakReport(
    val ipv4Exit: String,
    val ipv6Status: String,
    val locationAndIsp: String,
    val dnsStatus: String,
    val webrtcStunIp: String,
    val isLeakFree: Boolean
)

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

    suspend fun runFullAudit(): LeakReport = withContext(Dispatchers.IO) {
        val (ipv4, ispLocation) = checkIpv4AndGeo()
        val ipv6 = checkIpv6()
        val dns = checkDnsLeak()
        val stunIp = checkWebRtcStunLeak()

        val stunClean = stunIp == ipv4 || stunIp == "UDP Blocked (Safe)"
        val dnsClean = dns.startsWith("Protected")
        val ipClean = ipv4 != "Error"

        LeakReport(
            ipv4Exit = ipv4,
            ipv6Status = ipv6,
            locationAndIsp = ispLocation,
            dnsStatus = dns,
            webrtcStunIp = stunIp,
            isLeakFree = ipClean && stunClean && dnsClean
        )
    }

    private fun checkIpv4AndGeo(): Pair<String, String> {
        // Primary: ipwho.is
        try {
            val json = JSONObject(httpGet("https://ipwho.is/", 5000))
            if (json.optBoolean("success", true)) {
                val ip = json.optString("ip", "")
                if (ip.isNotBlank()) {
                    val city = json.optString("city", "")
                    val country = json.optString("country", "")
                    val isp = json.optJSONObject("connection")?.optString("isp", "") ?: ""
                    return ip to listOf(city, country, isp).filter { it.isNotBlank() }.joinToString(" • ")
                }
            }
        } catch (_: Exception) { }

        // Fallback 1: ipapi.co
        try {
            val json = JSONObject(httpGet("https://ipapi.co/json/", 5000))
            val ip = json.optString("ip", "")
            if (ip.isNotBlank()) {
                val city = json.optString("city", "")
                val country = json.optString("country_name", "")
                val org = json.optString("org", "")
                return ip to listOf(city, country, org).filter { it.isNotBlank() }.joinToString(" • ")
            }
        } catch (_: Exception) { }

        // Fallback 2: ipify.org
        return try {
            val json = JSONObject(httpGet("https://api.ipify.org?format=json", 4000))
            val ip = json.optString("ip", "Error")
            ip to "Verified Exit IP"
        } catch (e: Exception) {
            "Error" to (e.message ?: "Unreachable")
        }
    }

    private fun checkIpv6(): String = try {
        val json = JSONObject(httpGet("https://api6.ipify.org?format=json", 3500))
        val ip6 = json.optString("ip", "")
        if (ip6.contains(":")) "Bridged ($ip6)" else "Blocked / IPv4-Only"
    } catch (_: Exception) {
        "Blocked / No IPv6 Leak"
    }

    private fun checkDnsLeak(): String {
        try {
            val json = JSONObject(httpGet("https://edns.ip-api.com/json", 5000))
            val dnsObj = json.optJSONObject("dns")
            val dnsIp = dnsObj?.optString("ip", "") ?: ""
            val dnsGeo = dnsObj?.optString("geo", "") ?: ""
            if (dnsIp.isNotBlank()) {
                return "Protected ($dnsIp • $dnsGeo)"
            }
        } catch (_: Exception) { }

        return try {
            val trace = httpGet("https://1.1.1.1/cdn-cgi/trace", 4000)
            val loc = trace.lines().firstOrNull { it.startsWith("loc=") }?.substringAfter("=") ?: "OK"
            "Protected (DoH Edge • $loc)"
        } catch (e: Exception) {
            "Check Failed (${e.message})"
        }
    }

    private fun checkWebRtcStunLeak(): String {
        val stunServers = listOf(
            "stun.l.google.com" to 19302,
            "stun.cloudflare.com" to 3478
        )
        for ((host, port) in stunServers) {
            val result = queryStunServer(host, port)
            if (result != null) return result
        }
        return "UDP Blocked (Safe)"
    }

    private fun queryStunServer(host: String, port: Int): String? = try {
        val txId = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val req = ByteBuffer.allocate(20).apply {
            putShort(0x0001)
            putShort(0x0000)
            putInt(0x2112A442)
            put(txId)
        }.array()

        DatagramSocket().use { socket ->
            socket.soTimeout = 3500
            val addr = InetAddress.getByName(host)
            socket.send(DatagramPacket(req, req.size, addr, port))

            val buf = ByteArray(256)
            val respPacket = DatagramPacket(buf, buf.size)
            socket.receive(respPacket)

            val resp = ByteBuffer.wrap(respPacket.data, 0, respPacket.length)
            resp.position(20)
            while (resp.remaining() >= 4) {
                val attrType = resp.short.toInt() and 0xFFFF
                val attrLen = resp.short.toInt() and 0xFFFF
                if (attrType == 0x0020 && attrLen >= 8) {
                    resp.get()
                    val family = resp.get().toInt() and 0xFF
                    resp.short
                    if (family == 0x01) {
                        val xorIp = resp.int xor 0x2112A442
                        return@use InetAddress.getByAddress(
                            ByteBuffer.allocate(4).putInt(xorIp).array()
                        ).hostAddress
                    }
                } else {
                    val padded = (attrLen + 3) and 3.inv()
                    if (resp.remaining() >= padded) resp.position(resp.position() + padded) else break
                }
            }
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "namelessproxy/1.0")
        }
        return conn.inputStream.bufferedReader().readText()
    }
}
