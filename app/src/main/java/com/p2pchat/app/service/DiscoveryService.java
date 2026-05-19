package com.p2pchat.app.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * NSD 节点发现服务（局域网 mDNS 发现，作为 SignalingServer UDP广播的补充）
 */
public class DiscoveryService extends Service {
    private static final String TAG = "DiscoveryService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "发现服务已启动（UDP广播模式）");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
