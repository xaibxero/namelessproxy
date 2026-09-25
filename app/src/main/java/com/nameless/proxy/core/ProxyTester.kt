package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

sealed class TestResult {
    data class Success(val latencyMs: Long) : TestResult()
    data class Failure(val error: String) : TestResult()
}

object ProxyTester {

    suspend fun testProxy(settings: ProxySettings, timeoutMs: Int = 5000): TestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val socket = Socket()

        try {
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(settings.host, settings.port), timeoutMs)

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
                    val method = inStream.readByte().toInt()

                    if (ver != 5) return@withContext TestResult.Failure("Invalid SOCKS5 version")

                    if (method == 0x02) {
                        val uBytes = settings.username.toByteArray()
                        val pBytes = settings.password.toByteArray()
                        outStream.writeByte(0x01)
                        outStream.writeByte(uBytes.size)
                        outStream.write(uBytes)
                        outStream.writeByte(pBytes.size)
                        outStream.write(pBytes)
                        outStream.flush()

                        val subVer = inStream.readByte().toInt()
                        val subStatus = inStream.readByte().toInt()
                        if (subStatus != 0) return@withContext TestResult.Failure("Auth failed (Wrong credentials)")
                    } else if (method != 0x00) {
                        return@withContext TestResult.Failure("Unsupported auth method: $method")
                    }

                    // SOCKS5 CONNECT to 1.1.1.1:80
                    outStream.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0x00, 0x50))
                    outStream.flush()

                    val repVer = inStream.readByte().toInt()
                    val repCode = inStream.readByte().toInt()
                    if (repCode != 0) return@withContext TestResult.Failure("SOCKS5 Connect rejected (Code: $repCode)")
                }

                ProxyType.HTTP -> {
                    val authHeader = if (settings.username.isNotEmpty()) {
                        val authStr = "${settings.username}:${settings.password}"
                        val encoded = android.util.Base64.encodeToString(authStr.toByteArray(), android.util.Base64.NO_WRAP)
                        "Proxy-Authorization: Basic $encoded\r\n"
                    } else ""

                    val req = "CONNECT 1.1.1.1:80 HTTP/1.1\r\nHost: 1.1.1.1:80\r\n${authHeader}\r\n"
                    outStream.write(req.toByteArray())
                    outStream.flush()

                    val buffer = ByteArray(256)
                    val bytesRead = inStream.read(buffer)
                    val response = String(buffer, 0, bytesRead)
                    if (!response.contains("200")) {
                        return@withContext TestResult.Failure("HTTP Tunnel failed: ${response.lines().firstOrNull()}")
                    }
                }

                ProxyType.SOCKS4 -> {
                    outStream.write(byteArrayOf(0x04, 0x01, 0x00, 0x50, 1, 1, 1, 1, 0x00))
                    outStream.flush()
                    inStream.readByte()
                    val status = inStream.readByte().toInt()
                    if (status != 0x5A) return@withContext TestResult.Failure("SOCKS4 rejected (Status: $status)")
                }

                // Shadowsocks, VLESS, Trojan, Hysteria2: TCP handshake verifies host:port reachability
                ProxyType.SHADOWSOCKS, ProxyType.VLESS, ProxyType.TROJAN, ProxyType.HYSTERIA2 -> {
                    socket.close()
                    val latency = System.currentTimeMillis() - start
                    return@withContext TestResult.Success(latency)
                }
            }

            socket.close()
            val latency = System.currentTimeMillis() - start
            TestResult.Success(latency)

        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
            TestResult.Failure(e.localizedMessage ?: "Connection timed out")
        }
    }
}
