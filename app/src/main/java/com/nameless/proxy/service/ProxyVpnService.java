package com.nameless.proxy.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.nameless.proxy.MainActivity;
import com.nameless.proxy.core.RootBridge;

public class ProxyVpnService extends Service {
    public static final String ACTION_START = "com.nameless.proxy.ACTION_START";
    public static final String ACTION_STOP = "com.nameless.proxy.ACTION_STOP";
    private static final String CHANNEL_ID = "nameless_proxy_channel";
    private static final int NOTIF_ID = 3601;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            new Thread(() -> {
                RootBridge.stopTunnel(getApplicationContext());
                try {
                    stopForeground(true);
                } catch (Throwable ignored) {
                }
                stopSelf();
            }).start();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTIF_ID, buildNotification("NamelessProxy Active", "Zero-Leak DNS & WebRTC Shield Running"));
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "NamelessProxy Tunnel Status",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Monitors root transparent proxy & WebRTC/DNS shield");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(String title, String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent piOpen = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, ProxyVpnService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(piOpen)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "Disconnect", piStop).build())
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
