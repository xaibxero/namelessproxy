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
        } else null

        val jsonProviders = listOf(
            "http://ip-api.com/json/?fields=status,country,countryCode,regionName,city,isp,org,query",
            "https://ipwho.is/",
            "https://ipinfo.io/json",
            "https://freeipapi.com/api/json",
            "https://api.ip.sb/geoip"
        )

        for (endpoint in jsonProviders) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.instanceFollowRedirects = true
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                conn.setRequestProperty("Accept", "application/json")

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
                            isp = isp
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }

        // Fallback: get raw IP, then resolve its country & flag via ip-api.com
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
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", BROWSER_UA)

                if (conn.responseCode == 200) {
                    val ip = conn.inputStream.bufferedReader().use { it.readText().trim() }
                    if (ip.isNotEmpty()) {
                        try {
                            val geoUrl = URL("http://ip-api.com/json/$ip?fields=status,country,countryCode,city")
                            val geoConn = (if (proxy != null) geoUrl.openConnection(proxy) else geoUrl.openConnection()) as HttpURLConnection
                            geoConn.connectTimeout = 2500
                            geoConn.readTimeout = 2500
                            if (geoConn.responseCode == 200) {
                                val gj = JSONObject(geoConn.inputStream.bufferedReader().use { it.readText() })
                                val cc = gj.optString("countryCode", "")
                                val cn = gj.optString("country", "")
                                val ct = gj.optString("city", "")
                                if (cc.length == 2) {
                                    val loc = if (ct.isNotEmpty()) "$cn • $ct" else cn
                                    return@withContext GeoIpResult(
                                        ip = ip,
                                        country = loc,
                                        flagEmoji = countryCodeToFlag(cc)
                                    )
                                }
                            }
                        } catch (_: Exception) {}

                        return@withContext GeoIpResult(
                            ip = ip,
                            country = "United States",
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
