package com.nameless.proxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

enum class RootState {
    CHECKING,
    GRANTED,
    DENIED
}

object RootChecker {
    suspend fun verifyRoot(): Pair<RootState, String> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()

            if (output.contains("uid=0")) {
                Pair(RootState.GRANTED, "Root: Granted (UID 0)")
            } else {
                Pair(RootState.DENIED, "Root: Denied or Non-Root")
            }
        } catch (e: Exception) {
            Pair(RootState.DENIED, "Root: Not Available")
        }
    }
}
