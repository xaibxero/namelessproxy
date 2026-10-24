package com.nameless.proxy.service;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.nameless.proxy.core.RootBridge;

public class NamelessTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        new Thread(() -> {
            if (RootBridge.isTunnelRunning()) {
                RootBridge.stopTunnel(getApplicationContext());
            }
            refreshTile();
        }).start();
    }

    private void refreshTile() {
        new Thread(() -> {
            boolean running = RootBridge.isTunnelRunning();
            Tile tile = getQsTile();
            if (tile != null) {
                tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                tile.setLabel(running ? "Tunnel Active" : "Nameless Proxy");
                tile.updateTile();
            }
        }).start();
    }
}
