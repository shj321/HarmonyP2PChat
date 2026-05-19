package com.p2pchat.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.multidex.MultiDex;

/**
 * 应用入口，初始化全局资源
 */
public class P2PChatApplication extends Application {

    public static final String CHANNEL_P2P    = "p2p_service";
    public static final String CHANNEL_MSG    = "message";
    public static final String CHANNEL_CALL   = "incoming_call";
    public static final String CHANNEL_FILE   = "file_transfer";

    private static P2PChatApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static P2PChatApplication getInstance() { return instance; }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel p2pCh = new NotificationChannel(
                    CHANNEL_P2P, "P2P 连接服务", NotificationManager.IMPORTANCE_LOW);
            p2pCh.setDescription("保持 P2P 节点在线");

            NotificationChannel msgCh = new NotificationChannel(
                    CHANNEL_MSG, "新消息通知", NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel callCh = new NotificationChannel(
                    CHANNEL_CALL, "来电通知", NotificationManager.IMPORTANCE_HIGH);
            callCh.setDescription("语音/视频来电");
            callCh.enableVibration(true);

            NotificationChannel fileCh = new NotificationChannel(
                    CHANNEL_FILE, "文件传输", NotificationManager.IMPORTANCE_LOW);
            fileCh.setDescription("文件断点续传进度");

            nm.createNotificationChannel(p2pCh);
            nm.createNotificationChannel(msgCh);
            nm.createNotificationChannel(callCh);
            nm.createNotificationChannel(fileCh);
        }
    }
}
