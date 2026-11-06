package com.nameless.proxy.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.nameless.proxy.core.ProxyConfig;
import com.nameless.proxy.core.RootBridge;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        SharedPreferences prefs = context.getSharedPreferences("nameless_prefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("active_config", "");
        ProxyConfig cfg = ProxyConfig.fromJson(savedJson);
        if (cfg.autoStartOnBoot && !cfg.host.isEmpty()) {
            new Thread(() -> {
                if (RootBridge.startTunnel(context.getApplicationContext(), cfg)) {
                    Intent svc = new Intent(context, ProxyVpnService.class);
                    svc.setAction(ProxyVpnService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(svc);
                    } else {
                        context.startService(svc);
                    }
                }
            }).start();
        }
    }
}
