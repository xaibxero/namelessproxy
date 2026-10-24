package com.nameless.proxy.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.nameless.proxy.core.RootBridge;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        new Thread(() -> RootBridge.ensureBinariesExtracted(context.getApplicationContext())).start();
    }
}
