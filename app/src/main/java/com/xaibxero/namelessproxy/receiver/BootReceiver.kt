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
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val u = ProfileContext.userId
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cmd = """
                    if [ -f '${ProfileContext.bootFlagPath}' ] && [ -f '${ProfileContext.daemonScriptPath}' ]; then
                        touch '${ProfileContext.enabledFlagPath}'
                        if ! pgrep -f 'live_daemon.sh.*user${u}/' >/dev/null 2>&1; then
                            setsid sh '${ProfileContext.daemonScriptPath}' >/dev/null 2>&1 < /dev/null &
                        fi
                    fi
                """.trimIndent()
                RootEngine.su(cmd)
            } finally {
                pending.finish()
            }
        }
    }
}
