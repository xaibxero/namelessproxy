package com.xaibxero.namelessproxy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xaibxero.namelessproxy.core.ProfileContext
import com.xaibxero.namelessproxy.core.RootEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cmd = """
                    if [ -f '${ProfileContext.bootFlagPath}' ] && [ -f '${ProfileContext.daemonScriptPath}' ]; then
                        touch '${ProfileContext.enabledFlagPath}'
                        pkill -f 'live_daemon.sh.*user${ProfileContext.userId}' 2>/dev/null || true
                        setsid sh '${ProfileContext.daemonScriptPath}' >/dev/null 2>&1 < /dev/null &
                    fi
                """.trimIndent()
                RootEngine.su(cmd)
            } finally {
                pending.finish()
            }
        }
    }
}
