package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object IpFetcher {
    suspend fun getPublicIp(): String? = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
        )
        for (targetUrl in endpoints) {
            try {
                val conn = URL(targetUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "curl/7.88.1")
                if (conn.responseCode == 200) {
                    val ip = conn.inputStream.bufferedReader().readText().trim()
                    if (ip.isNotEmpty()) return@withContext ip
                }
            } catch (_: Exception) {}
        }
        null
    }
}
