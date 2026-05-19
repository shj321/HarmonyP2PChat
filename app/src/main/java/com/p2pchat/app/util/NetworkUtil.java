package com.p2pchat.app.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * 网络工具类
 */
public class NetworkUtil {

    /** 获取本机局域网 IP */
    public static String getLocalIp(Context ctx) {
        try {
            // 优先从 Wi-Fi 获取
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return ((ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." +
                            ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF));
                }
            }
            // 回退到枚举网络接口
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
