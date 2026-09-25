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
    val flagEmoji: String
)

object IpFetcher {

    private fun countryCodeToFlag(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
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
            try {
                val url = URL(endpoint)
                val conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "curl/7.88.1")

                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(raw)
                    val ip = json.optString("ip", "")
                    val countryCode = json.optString("country_code", "")
                    val country = json.optString("country", "Proxy Exit Node")

                    if (ip.isNotEmpty()) {
                        return@withContext GeoIpResult(
                            ip = ip,
                            country = country,
                            flagEmoji = countryCodeToFlag(countryCode)
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        val rawEndpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
        )
        for (rawUrl in rawEndpoints) {
            try {
                val url = URL(rawUrl)
                val conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "curl/7.88.1")

                if (conn.responseCode == 200) {
                    val ip = conn.inputStream.bufferedReader().readText().trim()
                    if (ip.isNotEmpty()) {
                        return@withContext GeoIpResult(
                            ip = ip,
                            country = "Active Proxy Tunnel",
                            flagEmoji = "🌐"
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        null
    }
}
