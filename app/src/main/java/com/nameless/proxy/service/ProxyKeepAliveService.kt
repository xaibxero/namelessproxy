package com.nameless.proxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.nameless.proxy.MainActivity
import com.nameless.proxy.core.ConfigGenerator
import com.nameless.proxy.core.IptablesManager
import com.nameless.proxy.core.ProfileManager
import com.nameless.proxy.core.ProxyController
import com.nameless.proxy.core.StunResponder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent Foreground Service that keeps the app process, StunResponder, and sing-box
 * completely exempt from Android 12–15's Cached App Freezer (cgroup v2 freezer) and Doze mode
 * when the user leaves MainActivity or locks the screen.
 */
class ProxyKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "nameless_tunnel_channel"
        private const val NOTIFICATION_ID = 10801
        const val ACTION_START = "com.nameless.proxy.action.START_KEEPALIVE"
        const val ACTION_STOP = "com.nameless.proxy.action.STOP_KEEPALIVE"

        fun start(context: Context) {
            try {
                val intent = Intent(context, ProxyKeepAliveService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ProxyKeepAliveService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseLocks()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }

        acquireLocks()
        startWatchdogLoop()
        return START_STICKY
    }

    private fun startWatchdogLoop() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            while (isActive) {
                try {
                    val user = ProfileManager.androidUserId
                    val rSlot = ProxyController.getRunningSlot(user)
                    if (rSlot != null) {
                        val port = 10800 + (user * 100) + (rSlot * 10)
                        val cfg = ProxyController.getLastActiveSettings(applicationContext, rSlot)
                        val cleanHost = ConfigGenerator.cleanHost(cfg.host)
                        StunResponder.ensureRunning(
                            stunPort = port + 3,
                            socksPort = port + 1,
                            fallbackHostIp = cleanHost.takeIf { IptablesManager.isValidIpv4(it) },
                            enableLocalStun = true,
                            enableHotspotStun = cfg.routeHotspot
                        )
                    }
                } catch (_: Exception) {
                }
                delay(4000)
            }
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NamelessProxy::TunnelWakeLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } else if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (_: Exception) {
        }

        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(lockMode, "NamelessProxy::TunnelWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } else if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nameless Kernel Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Nameless Proxy tunnel and STUN responder active in the background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val exitIp = StunResponder.getExitIp().ifEmpty { "Active" }
        return builder
            .setContentTitle("Nameless Proxy • Profile U#${ProfileManager.androidUserId}")
            .setContentText("Tunnel & Hotspot Shield Active ($exitIp)")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
