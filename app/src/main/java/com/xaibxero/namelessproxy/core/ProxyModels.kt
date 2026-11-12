package com.xaibxero.namelessproxy.core

import android.util.Base64
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder

@Serializable
enum class ProxyProtocol {
    SOCKS5, SOCKS4, SOCKS4A, HTTP, HTTPS, SHADOWSOCKS, VLESS, TROJAN
}

@Serializable
enum class Ipv6Mode {
    BRIDGE_OVER_IPV4,
    NATIVE_DUAL_STACK,
    STRICT_BLOCK
}

@Serializable
data class ProxySlot(
    val index: Int,
    val label: String = "Slot ${index + 1}",
    val protocol: ProxyProtocol = ProxyProtocol.SOCKS5,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val methodOrFlow: String = "2022-blake3-aes-128-gcm",
    val sni: String = "",
    val security: String = "tls",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val transport: String = "tcp",
    val wsPathOrService: String = ""
)

@Serializable
data class ProfileSettings(
    val activeSlot: Int = 0,
    val bootOnStartup: Boolean = false,
    val shareHotspot: Boolean = false,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.BRIDGE_OVER_IPV4,
    val slots: List<ProxySlot> = List(5) { ProxySlot(index = it) }
)

object ProxyUriParser {

    fun parseIntoSlot(rawInput: String, current: ProxySlot): ProxySlot? {
        val text = rawInput.trim()
        if (text.isBlank()) return null

        return try {
            when {
                text.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(text, current)
                text.startsWith("vless://", ignoreCase = true) -> parseVless(text, current)
                text.startsWith("trojan://", ignoreCase = true) -> parseTrojan(text, current)
                text.startsWith("socks5://", ignoreCase = true) ||
                text.startsWith("socks://", ignoreCase = true) -> parseStandardUri(text, ProxyProtocol.SOCKS5, current)
                text.startsWith("socks4a://", ignoreCase = true) -> parseStandardUri(text, ProxyProtocol.SOCKS4A, current)
                text.startsWith("socks4://", ignoreCase = true) -> parseStandardUri(text, ProxyProtocol.SOCKS4, current)
                text.startsWith("https://", ignoreCase = true) -> parseStandardUri(text, ProxyProtocol.HTTPS, current)
                text.startsWith("http://", ignoreCase = true) -> parseStandardUri(text, ProxyProtocol.HTTP, current)
                text.contains(":") && !text.contains("://") -> parseHostPortLine(text, current)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHostPortLine(text: String, current: ProxySlot): ProxySlot? {
        val parts = text.split(":")
        if (parts.size < 2) return null
        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull() ?: return null
        val user = parts.getOrNull(2)?.trim() ?: ""
        val pass = parts.getOrNull(3)?.trim() ?: ""
        return current.copy(host = host, port = port, username = user, password = pass)
    }

    private fun parseStandardUri(raw: String, protocol: ProxyProtocol, current: ProxySlot): ProxySlot? {
        val normalized = raw.replaceFirst(Regex("^socks://", RegexOption.IGNORE_CASE), "socks5://")
        val uri = URI(normalized)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else current.port
        val userInfo = uri.userInfo ?: ""
        val user = userInfo.substringBefore(":", "")
        val pass = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        return current.copy(
            protocol = protocol,
            host = host,
            port = port,
            username = decode(user),
            password = decode(pass)
        )
    }

    private fun parseShadowsocks(raw: String, current: ProxySlot): ProxySlot? {
        val withoutScheme = raw.substring(5).substringBefore("#")
        val mainPart = withoutScheme.substringBefore("?")
        return if (mainPart.contains("@")) {
            val userPart = mainPart.substringBefore("@")
            val hostPort = mainPart.substringAfter("@")
            val decoded = decodeBase64Flexible(userPart)
            val method = decoded.substringBefore(":")
            val pass = decoded.substringAfter(":", "")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":").toIntOrNull() ?: return null
            current.copy(
                protocol = ProxyProtocol.SHADOWSOCKS,
                host = host,
                port = port,
                methodOrFlow = method,
                password = pass
            )
        } else {
            val decoded = decodeBase64Flexible(mainPart)
            val method = decoded.substringBefore(":")
            val rest = decoded.substringAfter(":")
            val pass = rest.substringBeforeLast("@")
            val hostPort = rest.substringAfterLast("@")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":").toIntOrNull() ?: return null
            current.copy(
                protocol = ProxyProtocol.SHADOWSOCKS,
                host = host,
                port = port,
                methodOrFlow = method,
                password = pass
            )
        }
    }

    private fun parseVless(raw: String, current: ProxySlot): ProxySlot? {
        val uri = URI(raw)
        val uuid = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)
        val sec = q["security"] ?: "none"
        return current.copy(
            protocol = ProxyProtocol.VLESS,
            host = host,
            port = port,
            password = uuid,
            methodOrFlow = q["flow"] ?: "",
            security = sec,
            sni = q["sni"] ?: q["host"] ?: "",
            realityPublicKey = q["pbk"] ?: "",
            realityShortId = q["sid"] ?: "",
            transport = q["type"] ?: "tcp",
            wsPathOrService = q["path"] ?: q["serviceName"] ?: ""
        )
    }

    private fun parseTrojan(raw: String, current: ProxySlot): ProxySlot? {
        val uri = URI(raw)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)
        return current.copy(
            protocol = ProxyProtocol.TROJAN,
            host = host,
            port = port,
            password = decode(password),
            security = q["security"] ?: "tls",
            sni = q["sni"] ?: q["host"] ?: host,
            transport = q["type"] ?: "tcp",
            wsPathOrService = q["path"] ?: q["serviceName"] ?: ""
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
        }.toMap()
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun decodeBase64Flexible(input: String): String {
        val clean = input.replace('-', '+').replace('_', '/').trim()
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }
}
