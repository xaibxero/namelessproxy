package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.Proxy
import java.net.Socket

sealed class TestResult {
    data class Success(val latencyMs: Long) : TestResult()
    data class Failure(val error: String) : TestResult()
}

object ProxyTester {

    suspend fun testProxy(settings: ProxySettings, timeoutMs: Int = 5000): TestResult = withContext(Dispatchers.IO) {
        val cleanHost = ConfigGenerator.cleanHost(settings.host)
        if (cleanHost.isEmpty()) {
            return@withContext TestResult.Failure("Host is empty")
        }

        val start = System.currentTimeMillis()

        // If the kernel tunnel is already active on this slot, test through sing-box's local mixed port (10801)
        // so the probe uses the active tunnel cleanly even with Table 2088 app-UID routing active.
        val rSlot = ProxyController.getRunningSlot(ProfileManager.androidUserId)
        if (rSlot != null && rSlot == ProfileManager.activeSlot) {
            val mixedPort = 10800 + (ProfileManager.androidUserId * 100) + (rSlot * 10) + 1
            return@withContext try {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", mixedPort))
                Socket(proxy).use { s ->
                    s.soTimeout = timeoutMs
                    s.connect(InetSocketAddress("1.1.1.1", 80), timeoutMs)
                }
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                TestResult.Success(latency)
            } catch (e: Exception) {
                TestResult.Failure(e.localizedMessage ?: "Tunnel probe timed out")
            }
        }

        if (settings.type == ProxyType.HYSTERIA2) {
            return@withContext try {
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
                        return@withContext TestResult.Failure("UDP Port ${settings.port} unreachable")
                    } catch (_: Exception) {
                    }
                }
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                TestResult.Success(latency)
            } catch (e: Exception) {
                TestResult.Failure(e.localizedMessage ?: "UDP probe failed")
            }
        }

        try {
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

                        if (ver != 5) return@withContext TestResult.Failure("Invalid SOCKS5 version ($ver)")

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
                            if (subStatus != 0) return@withContext TestResult.Failure("SOCKS5 Auth failed")
                        } else if (method != 0x00) {
                            return@withContext TestResult.Failure("Unsupported SOCKS5 auth: $method")
                        }

                        outStream.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0x00, 0x50))
                        outStream.flush()

                        inStream.readByte()
                        val repCode = inStream.readByte().toInt()
                        if (repCode != 0) return@withContext TestResult.Failure("SOCKS5 rejected ($repCode)")
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
                        if (bytesRead <= 0) return@withContext TestResult.Failure("Empty HTTP response")
                        val response = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        if (!response.contains("200")) {
                            return@withContext TestResult.Failure("HTTP failed: ${response.lines().firstOrNull()}")
                        }
                    }

                    ProxyType.SOCKS4 -> {
                        outStream.write(byteArrayOf(0x04, 0x01, 0x00, 0x50, 1, 1, 1, 1, 0x00))
                        outStream.flush()
                        inStream.readByte()
                        val status = inStream.readByte().toInt()
                        if (status != 0x5A) return@withContext TestResult.Failure("SOCKS4 rejected ($status)")
                    }

                    else -> {
                        // TCP handshake completed
                    }
                }
            }
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            TestResult.Success(latency)
        } catch (e: Exception) {
            TestResult.Failure(e.localizedMessage ?: "Connection timed out")
        }
    }
}
