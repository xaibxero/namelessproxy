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
        val endpoints = listOf(
            "https://ipwho.is/",
            "https://api.ip.sb/geoip"
        )

        for (endpoint in endpoints) {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.setRequestProperty("User-Agent", "curl/7.88.1")
                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(raw)
                    val ip = json.optString("ip", "")
                    val countryCode = json.optString("country_code", "")
                    val country = json.optString("country", "Unknown")

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
        null
    }
}
