package com.nameless.proxy.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import kotlin.concurrent.thread
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/**
 * In-App STUN state & fallback responder that syncs with the standalone `/data/adb/nameless_proxy`
 * `RootStunDaemon` via `/data/adb/nameless_proxy/exit_ip`.
 */
object StunResponder {

    private const val EXIT_IP_FILE = "/data/adb/nameless_proxy/exit_ip"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var localServerJob: Job? = null
    private var hotspotServerJob: Job? = null
    private var ipProbeJob: Job? = null

    @Volatile
    private var localSocket: DatagramSocket? = null

    @Volatile
    private var hotspotSocket: DatagramSocket? = null

    @Volatile
    private var activeStunPort: Int = -1

    @Volatile
    private var activeSocksPort: Int = -1

    @Volatile
    private var activeLocalEnabled: Boolean = false

    @Volatile
    private var activeHotspotEnabled: Boolean = false

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
        val clean = ip.trim()
        currentExitIp = clean
        currentExitIpBytes = bytes
        if (isTunnelVerified) {
            verifiedFromTunnel.set(true)
        }
        // Sync verified IP to /data/adb/nameless_proxy/exit_ip so RootStunDaemon uses it immediately
        scope.launch {
            try {
                val p = ProcessBuilder("su", "-c", "echo '$clean' > $EXIT_IP_FILE && chmod 644 $EXIT_IP_FILE").start()
                p.waitFor()
            } catch (_: Exception) {
            }
        }
    }

    fun getExitIp(): String = currentExitIp

    @Synchronized
    fun ensureRunning(
        stunPort: Int,
        socksPort: Int,
        fallbackHostIp: String? = null,
        enableLocalStun: Boolean = true,
        enableHotspotStun: Boolean = true
    ) {
        if (currentExitIpBytes == null && !fallbackHostIp.isNullOrBlank()) {
            updateExitIp(fallbackHostIp, isTunnelVerified = false)
        }
        if (!verifiedFromTunnel.get()) {
            triggerTunnelIpProbe(socksPort)
        }
        val localOk = !enableLocalStun || (localSocket != null && !localSocket!!.isClosed)
        val hsOk = !enableHotspotStun || (hotspotSocket != null && !hotspotSocket!!.isClosed)
        if (localOk && hsOk &&
            activeStunPort == stunPort &&
            activeLocalEnabled == enableLocalStun &&
            activeHotspotEnabled == enableHotspotStun
        ) {
            return
        }
        start(stunPort, socksPort, fallbackHostIp, enableLocalStun, enableHotspotStun)
    }

    @Synchronized
    fun start(
        stunPort: Int,
        socksPort: Int,
        fallbackHostIp: String? = null,
        enableLocalStun: Boolean = true,
        enableHotspotStun: Boolean = true
    ) {
        val preserveIp = currentExitIp
        val preserveBytes = currentExitIpBytes
        val preserveVerified = verifiedFromTunnel.get() && activeSocksPort == socksPort

        stop()

        activeStunPort = stunPort
        activeSocksPort = socksPort
        activeLocalEnabled = enableLocalStun
        activeHotspotEnabled = enableHotspotStun

        if (!enableLocalStun && !enableHotspotStun) {
            return
        }

        if (preserveVerified && preserveBytes != null) {
            currentExitIp = preserveIp
            currentExitIpBytes = preserveBytes
            verifiedFromTunnel.set(true)
        } else {
            verifiedFromTunnel.set(false)
            if (!fallbackHostIp.isNullOrBlank()) {
                updateExitIp(fallbackHostIp, isTunnelVerified = false)
            }
            triggerTunnelIpProbe(socksPort)
        }

        // If RootStunDaemon in /data/adb/nameless_proxy is already bound to stunPort, startSocketLoop
        // will cleanly yield to RootStunDaemon without throwing any error.
        if (enableLocalStun) {
            localServerJob = startSocketLoop("127.0.0.1", stunPort) { localSocket = it }
        }
        if (enableHotspotStun) {
            hotspotServerJob = startSocketLoop("0.0.0.0", stunPort + 2) { hotspotSocket = it }
        }
    }

    private fun startSocketLoop(
        bindHost: String,
        bindPort: Int,
        assignSocket: (DatagramSocket) -> Unit
    ): Job = scope.launch {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                receiveBufferSize = 262144
                sendBufferSize = 262144
                bind(InetSocketAddress(bindHost, bindPort))
            }
            assignSocket(socket)
            val buf = ByteArray(512)
            val resp = ByteArray(52)
            val crc = CRC32()

            while (isActive && !socket.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val len = packet.length
                if (len < 20) continue

                val msgType = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                if (msgType != 0x0001) continue

                val ipBytes = currentExitIpBytes ?: continue
                val clientAddr = packet.address ?: continue
                val clientPort = packet.port

                resp[0] = 0x01
                resp[1] = 0x01
                resp[2] = 0x00
                resp[3] = 0x20

                System.arraycopy(buf, 4, resp, 4, 16)

                resp[20] = 0x00
                resp[21] = 0x20
                resp[22] = 0x00
                resp[23] = 0x08
                resp[24] = 0x00
                resp[25] = 0x01
                resp[26] = ((clientPort shr 8) xor (buf[4].toInt() and 0xFF)).toByte()
                resp[27] = ((clientPort and 0xFF) xor (buf[5].toInt() and 0xFF)).toByte()
                resp[28] = (ipBytes[0].toInt() xor buf[4].toInt()).toByte()
                resp[29] = (ipBytes[1].toInt() xor buf[5].toInt()).toByte()
                resp[30] = (ipBytes[2].toInt() xor buf[6].toInt()).toByte()
                resp[31] = (ipBytes[3].toInt() xor buf[7].toInt()).toByte()

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

                crc.reset()
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
                    socket.send(DatagramPacket(resp, resp.size, clientAddr, clientPort))
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            // Port is already bound by RootStunDaemon in /data/adb/nameless_proxy
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun triggerTunnelIpProbe(socksPort: Int) {
        ipProbeJob?.cancel()
        ipProbeJob = scope.launch {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", socksPort))
            val fastEndpoints = listOf(
                "http://ip-api.com/json/?fields=status,query",
                "https://api4.ipify.org",
                "https://icanhazip.com",
                "https://ipinfo.io/ip"
            )
            for (round in 1..6) {
                if (verifiedFromTunnel.get()) break
                for (endpoint in fastEndpoints) {
                    if (verifiedFromTunnel.get()) break
                    var conn: HttpURLConnection? = null
                    try {
                        conn = (URL(endpoint).openConnection(proxy) as HttpURLConnection).apply {
                            connectTimeout = 3000
                            readTimeout = 3000
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
                    delay(500)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        ipProbeJob?.cancel()
        ipProbeJob = null
        localServerJob?.cancel()
        localServerJob = null
        hotspotServerJob?.cancel()
        hotspotServerJob = null
        try {
            localSocket?.close()
        } catch (_: Exception) {
        }
        try {
            hotspotSocket?.close()
        } catch (_: Exception) {
        }
        localSocket = null
        hotspotSocket = null
        activeStunPort = -1
        activeLocalEnabled = false
        activeHotspotEnabled = false
        currentExitIp = ""
        currentExitIpBytes = null
        verifiedFromTunnel.set(false)
    }
}


/**
 * Standalone Root STUN Daemon executed inside `/data/adb/nameless_proxy` via `/system/bin/app_process`.
 * Runs as UID 0 (`root`) with `oom_score_adj = -1000` in the root cgroup so it NEVER gets frozen or killed
 * when the user leaves or closes the Android app.
 */
object RootStunDaemon {

    private const val EXIT_IP_FILE = "/data/adb/nameless_proxy/exit_ip"

    @Volatile
    private var currentExitIpBytes: ByteArray? = null

    private val verifiedFromTunnel = AtomicBoolean(false)

    private fun parseIpv4Bytes(ip: String): ByteArray? {
        val cleaned = ip.trim()
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

    private fun updateExitIp(ip: String, verified: Boolean) {
        val bytes = parseIpv4Bytes(ip) ?: return
        currentExitIpBytes = bytes
        if (verified) {
            verifiedFromTunnel.set(true)
        }
        try {
            File(EXIT_IP_FILE).writeText(ip.trim())
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val stunPort = args.getOrNull(0)?.toIntOrNull() ?: 10803
        val socksPort = args.getOrNull(1)?.toIntOrNull() ?: 10801
        val fallbackIp = args.getOrNull(2)?.trim() ?: ""
        val enableHotspot = args.getOrNull(3) != "false"

        try {
            val existing = File(EXIT_IP_FILE).takeIf { it.exists() }?.readText()?.trim() ?: ""
            if (parseIpv4Bytes(existing) != null) {
                currentExitIpBytes = parseIpv4Bytes(existing)
            } else if (parseIpv4Bytes(fallbackIp) != null) {
                updateExitIp(fallbackIp, verified = false)
            }
        } catch (_: Exception) {
            if (parseIpv4Bytes(fallbackIp) != null) {
                updateExitIp(fallbackIp, verified = false)
            }
        }

        thread(isDaemon = true, name = "RootStunIpProbe") {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", socksPort))
            val endpoints = listOf(
                "http://ip-api.com/json/?fields=status,query",
                "https://api4.ipify.org",
                "https://icanhazip.com",
                "https://ipinfo.io/ip"
            )
            while (true) {
                if (!verifiedFromTunnel.get()) {
                    for (urlStr in endpoints) {
                        if (verifiedFromTunnel.get()) break
                        var conn: HttpURLConnection? = null
                        try {
                            conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                                connectTimeout = 3000
                                readTimeout = 3000
                                useCaches = false
                            }
                            if (conn.responseCode == 200) {
                                val body = conn.inputStream.bufferedReader().use { it.readText().trim() }
                                val ip = if (body.startsWith("{")) {
                                    JSONObject(body).optString("query", "").trim()
                                } else {
                                    body.lineSequence().firstOrNull()?.trim() ?: ""
                                }
                                if (parseIpv4Bytes(ip) != null) {
                                    updateExitIp(ip, verified = true)
                                    break
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            conn?.disconnect()
                        }
                    }
                } else {
                    try {
                        val fileIp = File(EXIT_IP_FILE).takeIf { it.exists() }?.readText()?.trim() ?: ""
                        val parsed = parseIpv4Bytes(fileIp)
                        if (parsed != null) {
                            currentExitIpBytes = parsed
                        }
                    } catch (_: Exception) {
                    }
                }
                Thread.sleep(if (verifiedFromTunnel.get()) 3000L else 600L)
            }
        }

        if (enableHotspot) {
            thread(isDaemon = true, name = "RootHotspotStun") {
                runStunSocketLoop("0.0.0.0", stunPort + 2)
            }
        }

        runStunSocketLoop("127.0.0.1", stunPort)
    }

    private fun runStunSocketLoop(bindHost: String, bindPort: Int) {
        while (true) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    receiveBufferSize = 262144
                    sendBufferSize = 262144
                    bind(InetSocketAddress(bindHost, bindPort))
                }
                val buf = ByteArray(512)
                val resp = ByteArray(52)
                val crc = CRC32()

                while (!socket.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    if (packet.length < 20) continue

                    val msgType = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                    if (msgType != 0x0001) continue

                    val ipBytes = currentExitIpBytes ?: continue
                    val clientAddr = packet.address ?: continue
                    val clientPort = packet.port

                    resp[0] = 0x01
                    resp[1] = 0x01
                    resp[2] = 0x00
                    resp[3] = 0x20

                    System.arraycopy(buf, 4, resp, 4, 16)

                    resp[20] = 0x00
                    resp[21] = 0x20
                    resp[22] = 0x00
                    resp[23] = 0x08
                    resp[24] = 0x00
                    resp[25] = 0x01
                    resp[26] = ((clientPort shr 8) xor (buf[4].toInt() and 0xFF)).toByte()
                    resp[27] = ((clientPort and 0xFF) xor (buf[5].toInt() and 0xFF)).toByte()
                    resp[28] = (ipBytes[0].toInt() xor buf[4].toInt()).toByte()
                    resp[29] = (ipBytes[1].toInt() xor buf[5].toInt()).toByte()
                    resp[30] = (ipBytes[2].toInt() xor buf[6].toInt()).toByte()
                    resp[31] = (ipBytes[3].toInt() xor buf[7].toInt()).toByte()

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

                    crc.reset()
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
                        socket.send(DatagramPacket(resp, resp.size, clientAddr, clientPort))
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
            Thread.sleep(500L)
        }
    }
}
