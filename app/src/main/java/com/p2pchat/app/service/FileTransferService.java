package com.p2pchat.app.service;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.p2pchat.app.P2PChatApplication;

/**
 * 文件传输前台服务（显示断点续传进度通知）
 */
public class FileTransferService extends Service {

    private static final String TAG = "FileTransferSvc";
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        startForeground(100, buildNotification("准备传输...", 0, 100));
    }

    private Notification buildNotification(String title, int progress, int max) {
        return new NotificationCompat.Builder(this, P2PChatApplication.CHANNEL_FILE)
                .setContentTitle("文件传输")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(max, progress, max == 0)
                .setOngoing(true)
                .build();
    }

    public void updateProgress(String fileName, int progress) {
        nm.notify(100, buildNotification("传输: " + fileName, progress, 100));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
}
