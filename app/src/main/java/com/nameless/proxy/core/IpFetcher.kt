package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class GeoIpResult(
    val ip: String,
    val country: String,
    val flagEmoji: String,
    val isp: String = ""
)

object IpFetcher {

    private fun countryCodeToFlag(countryCode: String): String {
        val code = countryCode.trim().uppercase()
        if (code.length != 2 || !code[0].isLetter() || !code[1].isLetter()) return "🌐"
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    suspend fun getPublicIpInfo(localSocksPort: Int? = null): GeoIpResult? = withContext(Dispatchers.IO) {
        val proxy = if (localSocksPort != null) {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localSocksPort))
        } else null

        val providers = listOf(
            "https://ipwho.is/",
            "https://api.ip.sb/geoip",
            "https://ipapi.co/json/"
        )

        for (endpoint in providers) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "curl/8.5.0")

                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(raw)
                    if (!json.optBoolean("success", true)) continue

                    val ip = json.optString("ip", "").trim()
                    val countryCode = json.optString("country_code", "").ifEmpty {
                        val c = json.optString("country", "")
                        if (c.length == 2) c else ""
                    }
                    val countryName = json.optString("country_name", "").ifEmpty {
                        json.optString("country", "Exit Node")
                    }
                    val city = json.optString("city", "")
                    val isp = json.optJSONObject("connection")?.optString("isp", "")
                        ?.ifEmpty { json.optString("isp", json.optString("org", "")) }
                        ?: json.optString("isp", json.optString("org", ""))

                    if (ip.isNotEmpty()) {
                        val locationLabel = if (city.isNotEmpty() && !city.equals(countryName, ignoreCase = true)) {
                            "$countryName • $city"
                        } else {
                            countryName
                        }
                        return@withContext GeoIpResult(
                            ip = ip,
                            country = locationLabel,
                            flagEmoji = countryCodeToFlag(countryCode),
                            isp = isp
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }

        val rawEndpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
        )
        for (rawUrl in rawEndpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(rawUrl)
                conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "curl/8.5.0")

                if (conn.responseCode == 200) {
                    val ip = conn.inputStream.bufferedReader().use { it.readText().trim() }
                    if (ip.isNotEmpty()) {
                        return@withContext GeoIpResult(
                            ip = ip,
                            country = "Encrypted Exit Tunnel",
                            flagEmoji = "🌐"
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }

        null
    }
}
