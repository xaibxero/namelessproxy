package com.xaibxero.namelessproxy.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object SettingsStore {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(context: Context): ProfileSettings = withContext(Dispatchers.IO) {
        val localFile = File(context.filesDir, "settings_user${ProfileContext.userId}.json")

        val rootRead = RootEngine.su("cat '${ProfileContext.settingsPath}' 2>/dev/null")
        val raw = if (rootRead.ok && rootRead.out.startsWith("{")) {
            localFile.writeText(rootRead.out)
            rootRead.out
        } else if (localFile.exists()) {
            localFile.readText()
        } else {
            ""
        }

        if (raw.isBlank()) return@withContext ProfileSettings()

        return@withContext try {
            val parsed = json.decodeFromString<ProfileSettings>(raw)
            if (parsed.slots.size == 5) parsed else ProfileSettings()
        } catch (_: Exception) {
            ProfileSettings()
        }
    }

    suspend fun save(context: Context, settings: ProfileSettings) = withContext(Dispatchers.IO) {
        val serialized = json.encodeToString(settings)
        val localFile = File(context.filesDir, "settings_user${ProfileContext.userId}.json")
        localFile.writeText(serialized)

        val bootCmd = if (settings.bootOnStartup) {
            "touch '${ProfileContext.bootFlagPath}'"
        } else {
            "rm -f '${ProfileContext.bootFlagPath}'"
        }

        RootEngine.su(
            """
            mkdir -p '${ProfileContext.userDir}'
            chmod 700 '${ProfileContext.BASE_DIR}' '${ProfileContext.userDir}'
            cp -f '${localFile.absolutePath}' '${ProfileContext.settingsPath}'
            chmod 600 '${ProfileContext.settingsPath}'
            $bootCmd
            """.trimIndent()
        )
    }
}
