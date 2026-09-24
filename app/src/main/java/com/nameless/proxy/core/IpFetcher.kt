package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
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

    suspend fun getPublicIpInfo(): GeoIpResult? = withContext(Dispatchers.IO) {
        // Multi-provider fallback array
        val providers = listOf(
            "https://ipwho.is/",
            "https://api.ip.sb/geoip",
            "https://ipapi.co/json/"
        )

        for (endpoint in providers) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "curl/7.88.1")
                }

                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(raw)
                    val ip = json.optString("ip", "")
                    val countryCode = json.optString("country_code", "")
                    val country = json.optString("country", "United States")

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

        // Lightweight Raw IP Fallback if geo-APIs rate-limit
        val rawEndpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
        )
        for (rawUrl in rawEndpoints) {
            try {
                val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    setRequestProperty("User-Agent", "curl/7.88.1")
                }
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
