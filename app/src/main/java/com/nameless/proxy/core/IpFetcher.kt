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
    val isp: String = "",
    val timezone: String = ""
)

object IpFetcher {

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

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
        } else Proxy.NO_PROXY

        val jsonProviders = listOf(
            "http://ip-api.com/json/?fields=status,country,countryCode,regionName,city,isp,org,timezone,query",
            "https://ipinfo.io/json",
            "https://ipwho.is/",
            "https://freeipapi.com/api/json",
            "https://api.ip.sb/geoip"
        )

        for (endpoint in jsonProviders) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = url.openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = true
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Cache-Control", "no-cache, no-store")

                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(raw)

                    if (json.has("status") && json.optString("status") != "success") continue
                    if (json.has("success") && !json.optBoolean("success", true)) continue

                    val ip = json.optString("ip", "")
                        .ifEmpty { json.optString("query", "") }
                        .ifEmpty { json.optString("ipAddress", "") }
                        .trim()

                    val countryCode = json.optString("countryCode", "")
                        .ifEmpty { json.optString("country_code", "") }
                        .ifEmpty {
                            val c = json.optString("country", "")
                            if (c.length == 2) c else ""
                        }
                        .trim()

                    val countryName = json.optString("country_name", "")
                        .ifEmpty { json.optString("countryName", "") }
                        .ifEmpty {
                            val c = json.optString("country", "")
                            if (c.length > 2) c else ""
                        }
                        .ifEmpty { countryCode }
                        .trim()

                    val city = json.optString("city", "")
                        .ifEmpty { json.optString("cityName", "") }
                        .trim()

                    val isp = json.optJSONObject("connection")?.optString("isp", "")
                        ?.ifEmpty { json.optString("isp", json.optString("org", "")) }
                        ?: json.optString("isp", json.optString("org", ""))

                    val tz = json.optJSONObject("timezone")?.optString("id", "")
                        ?.ifEmpty { json.optString("timezone", "") }
                        ?: json.optString("timezone", "").ifEmpty {
                            json.optJSONArray("timeZones")?.optString(0, "") ?: ""
                        }

                    if (ip.isNotEmpty() && countryCode.length == 2) {
                        val locationLabel = if (city.isNotEmpty() && !city.equals(countryName, ignoreCase = true)) {
                            "$countryName • $city"
                        } else {
                            countryName.ifEmpty { countryCode }
                        }
                        return@withContext GeoIpResult(
                            ip = ip,
                            country = locationLabel,
                            flagEmoji = countryCodeToFlag(countryCode),
                            isp = isp,
                            timezone = tz.trim()
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }

        val rawEndpoints = listOf(
            "https://api4.ipify.org",
            "https://api.ipify.org",
            "https://icanhazip.com"
        )
        for (rawUrl in rawEndpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(rawUrl)
                conn = url.openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", BROWSER_UA)

                if (conn.responseCode == 200) {
                    val ip = conn.inputStream.bufferedReader().use { it.readText().trim() }
                    if (ip.isNotEmpty()) {
                        return@withContext GeoIpResult(
                            ip = ip,
                            country = "Tunnel Verified",
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
