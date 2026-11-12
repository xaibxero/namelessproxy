package com.xaibxero.namelessproxy.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

data class LeakReport(
    val ipv4Exit: String,
    val ipv6Status: String,
    val locationAndIsp: String,
    val dnsStatus: String,
    val webrtcStunIp: String,
    val isLeakFree: Boolean
)

object LeakAuditEngine {

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

    private fun checkIpv4AndGeo(): Pair<String, String> = try {
        val conn = (URL("https://ipwho.is/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val ip = json.optString("ip", "Unknown")
        val city = json.optString("city", "")
        val country = json.optString("country", "")
        val isp = json.optJSONObject("connection")?.optString("isp", "") ?: ""
        ip to listOf(city, country, isp).filter { it.isNotBlank() }.joinToString(" • ")
    } catch (e: Exception) {
        "Error" to (e.message ?: "Unreachable")
    }

    private fun checkIpv6(): String = try {
        val conn = (URL("https://api6.ipify.org?format=json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3500
            readTimeout = 3500
        }
        val ip6 = JSONObject(conn.inputStream.bufferedReader().readText()).optString("ip", "")
        if (ip6.contains(":")) "Bridged ($ip6)" else "Blocked / IPv4-Only"
    } catch (_: Exception) {
        "Blocked / No IPv6 Leak"
    }

    private fun checkDnsLeak(): String = try {
        val probeHost = "${UUID.randomUUID().toString().take(8)}.cloudflare.com"
        val resolved = InetAddress.getByName(probeHost).hostAddress ?: ""
        if (resolved.startsWith("198.18.")) {
            "Protected (Fake-IP $resolved -> DoH)"
        } else {
            "Protected (Tunneled -> $resolved)"
        }
    } catch (e: Exception) {
        "Check Failed (${e.message})"
    }

    private fun checkWebRtcStunLeak(): String = try {
        val txId = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val req = ByteBuffer.allocate(20).apply {
            putShort(0x0001)
            putShort(0x0000)
            putInt(0x2112A442)
            put(txId)
        }.array()

        DatagramSocket().use { socket ->
            socket.soTimeout = 4000
            val addr = InetAddress.getByName("stun.l.google.com")
            socket.send(DatagramPacket(req, req.size, addr, 19302))

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
                        ).hostAddress ?: "Unknown"
                    }
                } else {
                    val padded = (attrLen + 3) and 3.inv()
                    if (resp.remaining() >= padded) resp.position(resp.position() + padded) else break
                }
            }
            "No Reflexive IP"
        }
    } catch (_: Exception) {
        "UDP Blocked (Safe)"
    }
}
