package com.nameless.proxy.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/**
 * Local RFC 5389 / RFC 3489 UDP STUN Responder.
 *
 * Synthesizes valid 52-byte STUN Binding Success Responses (0x0101) containing
 * XOR-MAPPED-ADDRESS (0x0020), MAPPED-ADDRESS (0x0001), and FINGERPRINT (0x8028)
 * set to the live Proxy Exit IPv4 address for both the Android phone and all Wi-Fi Hotspot clients.
 */
object StunResponder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var ipProbeJob: Job? = null

    @Volatile
    private var activeSocket: DatagramSocket? = null

    @Volatile
    private var activeStunPort: Int = -1

    @Volatile
    private var activeSocksPort: Int = -1

    @Volatile
    private var currentExitIp: String = ""

    @Volatile
    private var currentExitIpBytes: ByteArray? = null

    private val verifiedFromTunnel = AtomicBoolean(false)

    private fun parseIpv4Bytes(ip: String): ByteArray? {
        val cleaned = ip.trim()
        if (!IptablesManager.isValidIpv4(cleaned) || cleaned.contains("/")) return null
        val parts = cleaned.split(".")
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for (i in 0..3) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            out[i] = v.toByte()
        }
        if (out[0] == 127.toByte() || out[0] == 0.toByte()) return null
        return out
    }

    fun updateExitIp(ip: String, isTunnelVerified: Boolean = true) {
        val bytes = parseIpv4Bytes(ip) ?: return
        currentExitIp = ip.trim()
        currentExitIpBytes = bytes
        if (isTunnelVerified) {
            verifiedFromTunnel.set(true)
        }
        try {
            File("/data/adb/nameless_proxy/exit_ip").writeText(currentExitIp)
        } catch (_: Exception) {
        }
    }

    fun getExitIp(): String = currentExitIp

    @Synchronized
    fun ensureRunning(stunPort: Int, socksPort: Int, fallbackHostIp: String? = null) {
        val sock = activeSocket
        if (sock != null && !sock.isClosed && activeStunPort == stunPort) {
            if (currentExitIpBytes == null && !fallbackHostIp.isNullOrBlank()) {
                updateExitIp(fallbackHostIp, isTunnelVerified = false)
            }
            if (!verifiedFromTunnel.get()) {
                triggerTunnelIpProbe(socksPort)
            }
            return
        }
        start(stunPort, socksPort, fallbackHostIp)
    }

    @Synchronized
    fun start(stunPort: Int, socksPort: Int, fallbackHostIp: String? = null) {
        stop()
        activeStunPort = stunPort
        activeSocksPort = socksPort
        verifiedFromTunnel.set(false)

        if (!fallbackHostIp.isNullOrBlank()) {
            updateExitIp(fallbackHostIp, isTunnelVerified = false)
        } else {
            try {
                val cached = File("/data/adb/nameless_proxy/exit_ip").takeIf { it.exists() }?.readText()?.trim()
                if (!cached.isNullOrBlank()) {
                    updateExitIp(cached, isTunnelVerified = false)
                }
            } catch (_: Exception) {
            }
        }

        triggerTunnelIpProbe(socksPort)

        serverJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", stunPort))
                }
                activeSocket = socket
                val buf = ByteArray(512)

                while (isActive && !socket.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val len = packet.length
                    if (len < 20) continue

                    val msgType = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                    if (msgType != 0x0001) continue

                    val reqCopy = buf.copyOfRange(0, 20)
                    val clientAddr = packet.address
                    val clientPort = packet.port

                    scope.launch {
                        handleStunRequest(socket, reqCopy, clientAddr, clientPort)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun handleStunRequest(
        socket: DatagramSocket,
        reqHeader: ByteArray,
        clientAddr: InetAddress,
        clientPort: Int
    ) {
        var attempts = 0
        while (currentExitIpBytes == null && attempts < 15) {
            delay(25)
            attempts++
        }

        val ipBytes = currentExitIpBytes ?: return

        // 52-byte RFC 5389 / RFC 3489 STUN Binding Success Response (0x0101):
        // Header (20B) + XOR-MAPPED-ADDRESS (12B) + MAPPED-ADDRESS (12B) + FINGERPRINT (8B)
        val resp = ByteArray(52)
        resp[0] = 0x01
        resp[1] = 0x01
        resp[2] = 0x00
        resp[3] = 0x20

        System.arraycopy(reqHeader, 4, resp, 4, 16)

        // Attribute 1: XOR-MAPPED-ADDRESS (Type 0x0020, Length 0x0008)
        resp[20] = 0x00
        resp[21] = 0x20
        resp[22] = 0x00
        resp[23] = 0x08
        resp[24] = 0x00
        resp[25] = 0x01
        resp[26] = ((clientPort shr 8) xor (reqHeader[4].toInt() and 0xFF)).toByte()
        resp[27] = ((clientPort and 0xFF) xor (reqHeader[5].toInt() and 0xFF)).toByte()
        resp[28] = (ipBytes[0].toInt() xor reqHeader[4].toInt()).toByte()
        resp[29] = (ipBytes[1].toInt() xor reqHeader[5].toInt()).toByte()
        resp[30] = (ipBytes[2].toInt() xor reqHeader[6].toInt()).toByte()
        resp[31] = (ipBytes[3].toInt() xor reqHeader[7].toInt()).toByte()

        // Attribute 2: MAPPED-ADDRESS (Type 0x0001, Length 0x0008)
        resp[32] = 0x00
        resp[33] = 0x01
        resp[34] = 0x00
        resp[35] = 0x08
        resp[36] = 0x00
        resp[37] = 0x01
        resp[38] = (clientPort shr 8).toByte()
        resp[39] = (clientPort and 0xFF).toByte()
        resp[40] = ipBytes[0]
        resp[41] = ipBytes[1]
        resp[42] = ipBytes[2]
        resp[43] = ipBytes[3]

        // Attribute 3: FINGERPRINT (Type 0x8028, Length 0x0004) = CRC32(resp[0..43]) XOR 0x5354554e
        val crc = CRC32()
        crc.update(resp, 0, 44)
        val fp = (crc.value.toInt()) xor 0x5354554e
        resp[44] = 0x80.toByte()
        resp[45] = 0x28.toByte()
        resp[46] = 0x00
        resp[47] = 0x04
        resp[48] = (fp ushr 24).toByte()
        resp[49] = (fp ushr 16).toByte()
        resp[50] = (fp ushr 8).toByte()
        resp[51] = (fp and 0xFF).toByte()

        try {
            if (!socket.isClosed) {
                val outPacket = DatagramPacket(resp, resp.size, clientAddr, clientPort)
                socket.send(outPacket)
            }
        } catch (_: Exception) {
        }
    }

    private fun triggerTunnelIpProbe(socksPort: Int) {
        ipProbeJob?.cancel()
        ipProbeJob = scope.launch {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val fastEndpoints = listOf(
                "http://ip-api.com/json/?fields=status,query",
                "https://api4.ipify.org",
                "https://icanhazip.com",
                "https://ipinfo.io/ip"
            )
            for (round in 1..4) {
                if (verifiedFromTunnel.get()) break
                for (endpoint in fastEndpoints) {
                    if (verifiedFromTunnel.get()) break
                    var conn: HttpURLConnection? = null
                    try {
                        conn = (URL(endpoint).openConnection(proxy) as HttpURLConnection).apply {
                            connectTimeout = 2500
                            readTimeout = 2500
                            useCaches = false
                        }
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().use { it.readText().trim() }
                            val candidateIp = if (body.startsWith("{")) {
                                JSONObject(body).optString("query", "").trim()
                            } else {
                                body.lineSequence().firstOrNull()?.trim() ?: ""
                            }
                            if (parseIpv4Bytes(candidateIp) != null) {
                                updateExitIp(candidateIp, isTunnelVerified = true)
                                break
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        conn?.disconnect()
                    }
                }
                if (!verifiedFromTunnel.get()) {
                    delay(400)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        ipProbeJob?.cancel()
        ipProbeJob = null
        serverJob?.cancel()
        serverJob = null
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        activeSocket = null
        activeStunPort = -1
        verifiedFromTunnel.set(false)
    }
}
