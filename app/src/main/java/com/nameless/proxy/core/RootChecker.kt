package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RootState {
    CHECKING,
    GRANTED,
    DENIED
}

object RootChecker {
    suspend fun verifyRoot(): Pair<RootState, String> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor()

            if (output.contains("uid=0")) {
                Pair(RootState.GRANTED, "ROOT UID 0")
            } else {
                Pair(RootState.DENIED, "NO ROOT")
            }
        } catch (e: Exception) {
            Pair(RootState.DENIED, "NO ROOT")
        }
    }
}
