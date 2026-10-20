package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.Socket

sealed class TestResult {
    data class Success(
        val latencyMs: Long,
        val detectedType: ProxyType,
        val detectedIpMode: IpMode
    ) : TestResult()

    data class Failure(val error: String) : TestResult()
}

object ProxyTester {

    fun inferIpMode(host: String): IpMode {
        val clean = ConfigGenerator.cleanHost(host)
        if (clean.isEmpty()) return IpMode.IPV4_ONLY
        if (IptablesManager.isValidIpv6(clean)) return IpMode.IPV6_ONLY
        if (IptablesManager.isValidIpv4(clean)) return IpMode.IPV4_ONLY
        return try {
            val addr = InetAddress.getByName(clean)
            if (addr is Inet6Address) IpMode.IPV6_ONLY else IpMode.IPV4_ONLY
        } catch (_: Exception) {
            IpMode.IPV4_ONLY
        }
    }

    /**
     * Automatically tests the configured proxy protocol first, and if handshake fails on standard
     * proxy types (SOCKS5 / HTTP / SOCKS4), auto-probes alternative protocols to detect the true proxy type.
     */
    suspend fun testProxy(settings: ProxySettings, timeoutMs: Int = 4500): TestResult = withContext(Dispatchers.IO) {
        val cleanHost = ConfigGenerator.cleanHost(settings.host)
        if (cleanHost.isEmpty()) {
            return@withContext TestResult.Failure("Host is empty")
        }

        val autoIpMode = inferIpMode(cleanHost)

        // 1. Try the currently selected protocol first
        val primary = probeSingleProtocol(settings.copy(ipMode = autoIpMode), timeoutMs)
        if (primary is TestResult.Success) {
            return@withContext primary
        }

        // 2. If the user entered a standard proxy (SOCKS5 / HTTP / SOCKS4), auto-detect across SOCKS5, HTTP, SOCKS4
        val fallbackOrder = listOf(ProxyType.SOCKS5, ProxyType.HTTP, ProxyType.SOCKS4).filter { it != settings.type }
        for (candidate in fallbackOrder) {
            val res = probeSingleProtocol(settings.copy(type = candidate, ipMode = autoIpMode), 2500)
            if (res is TestResult.Success) {
                return@withContext res
            }
        }

        primary
    }

    private fun probeSingleProtocol(settings: ProxySettings, timeoutMs: Int): TestResult {
        val cleanHost = ConfigGenerator.cleanHost(settings.host)
        val start = System.currentTimeMillis()
        val autoIpMode = settings.ipMode

        // Hysteria2 and TUIC use QUIC over UDP exclusively
        if (settings.type == ProxyType.HYSTERIA2 || settings.type == ProxyType.TUIC) {
            return try {
                val address = InetAddress.getByName(cleanHost)
                DatagramSocket().use { udpSocket ->
                    udpSocket.soTimeout = 1200
                    udpSocket.connect(address, settings.port)
                    val probe = ByteArray(64).apply { this[0] = 0xC0.toByte() }
                    udpSocket.send(DatagramPacket(probe, probe.size))
                    val recvBuf = ByteArray(128)
                    try {
                        udpSocket.receive(DatagramPacket(recvBuf, recvBuf.size))
                    } catch (e: PortUnreachableException) {
                        return TestResult.Failure("UDP Port ${settings.port} unreachable")
                    } catch (_: Exception) {
                        // Open/filtered QUIC listener
                    }
                }
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                TestResult.Success(latency, settings.type, autoIpMode)
            } catch (e: Exception) {
                TestResult.Failure(e.localizedMessage ?: "UDP resolution/probe failed")
            }
        }

        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(cleanHost, settings.port), timeoutMs)

                val outStream = DataOutputStream(socket.getOutputStream())
                val inStream = DataInputStream(socket.getInputStream())

                when (settings.type) {
                    ProxyType.SOCKS5 -> {
                        val hasAuth = settings.username.isNotEmpty()
                        if (hasAuth) {
                            outStream.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
                        } else {
                            outStream.write(byteArrayOf(0x05, 0x01, 0x00))
                        }
                        outStream.flush()

                        val ver = inStream.readByte().toInt()
                        val method = inStream.readByte().toInt() and 0xFF

                        if (ver != 5) return TestResult.Failure("Invalid SOCKS5 version ($ver)")

                        if (method == 0x02) {
                            val uBytes = settings.username.toByteArray(Charsets.UTF_8)
                            val pBytes = settings.password.toByteArray(Charsets.UTF_8)
                            outStream.writeByte(0x01)
                            outStream.writeByte(uBytes.size)
                            outStream.write(uBytes)
                            outStream.writeByte(pBytes.size)
                            outStream.write(pBytes)
                            outStream.flush()

                            inStream.readByte()
                            val subStatus = inStream.readByte().toInt()
                            if (subStatus != 0) return TestResult.Failure("SOCKS5 Auth failed (Wrong credentials)")
                        } else if (method != 0x00) {
                            return TestResult.Failure("Unsupported SOCKS5 auth method: $method")
                        }

                        // SOCKS5 CONNECT to 1.1.1.1:80
                        outStream.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0x00, 0x50))
                        outStream.flush()

                        inStream.readByte()
                        val repCode = inStream.readByte().toInt()
                        if (repCode != 0) return TestResult.Failure("SOCKS5 Connect rejected (Code: $repCode)")
                    }

                    ProxyType.HTTP -> {
                        val authHeader = if (settings.username.isNotEmpty()) {
                            val authStr = "${settings.username}:${settings.password}"
                            val encoded = android.util.Base64.encodeToString(authStr.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                            "Proxy-Authorization: Basic $encoded\r\n"
                        } else ""

                        val req = "CONNECT 1.1.1.1:80 HTTP/1.1\r\nHost: 1.1.1.1:80\r\n${authHeader}\r\n"
                        outStream.write(req.toByteArray(Charsets.UTF_8))
                        outStream.flush()

                        val buffer = ByteArray(256)
                        val bytesRead = inStream.read(buffer)
                        if (bytesRead <= 0) return TestResult.Failure("Empty response from HTTP proxy")
                        val response = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        if (!response.contains("200")) {
                            return TestResult.Failure("HTTP Tunnel failed: ${response.lines().firstOrNull()}")
                        }
                    }

                    ProxyType.SOCKS4 -> {
                        outStream.write(byteArrayOf(0x04, 0x01, 0x00, 0x50, 1, 1, 1, 1, 0x00))
                        outStream.flush()
                        inStream.readByte()
                        val status = inStream.readByte().toInt()
                        if (status != 0x5A) return TestResult.Failure("SOCKS4 rejected (Status: $status)")
                    }

                    ProxyType.SHADOWSOCKS,
                    ProxyType.VLESS,
                    ProxyType.VMESS,
                    ProxyType.TROJAN,
                    ProxyType.HYSTERIA2,
                    ProxyType.TUIC -> {
                        // TCP/TLS handshake completed
                    }
                }
            }
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            TestResult.Success(latency, settings.type, autoIpMode)
        } catch (e: Exception) {
            TestResult.Failure(e.localizedMessage ?: "Connection timed out")
        }
    }
}
