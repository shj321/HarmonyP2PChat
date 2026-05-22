package com.p2pchat.app.p2p;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.p2pchat.app.model.PeerInfo;
import com.p2pchat.app.model.SignalMessage;
import com.p2pchat.app.util.PrefsUtil;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UDP 信令服务器（无中心服务器，纯本地广播+单播）
 * 端口：37891
 */
public class SignalingServer {

    private static final String TAG = "SignalingServer";
    public  static final int    PORT = 37891;
    private static final int    BROADCAST_PORT = 37892;
    private static final String BROADCAST_ADDR = "255.255.255.255";
    private static final int    BUFFER_SIZE = 65507;

    private final Gson gson = new Gson();
    private DatagramSocket unicastSocket;
    private DatagramSocket broadcastSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    private final Context context;
    private SignalListener listener;

    private WebSocketSignalClient wsClient;

    public void setWsClient(WebSocketSignalClient client) { this.wsClient = client; }

    /** 向指定目标发送（双模式路由） */
    public void sendToTarget(SignalMessage msg, String targetIdentifier) {
        if (wsClient != null && wsClient.getState() == WebSocketSignalClient.State.CONNECTED) {
            msg.toId = targetIdentifier;
            wsClient.sendToPeer(msg);
        } else {
            sendUnicast(msg, targetIdentifier, SignalingServer.PORT);
        }
    }

    public WebSocketSignalClient getWsClient() { return wsClient; }


    public interface SignalListener {
        void onSignalReceived(SignalMessage msg, String senderIp);
    }

    public SignalingServer(Context ctx) {
        this.context = ctx;
    }

    public void setListener(SignalListener l) { this.listener = l; }

    public void start() {
        running = true;
        executor.execute(this::runUnicast);
        executor.execute(this::runBroadcastReceiver);
        Log.i(TAG, "信令服务启动，端口 " + PORT);
    }

    public void stop() {
        running = false;
        try { if (unicastSocket  != null) unicastSocket.close(); } catch (Exception ignored) {}
        try { if (broadcastSocket != null) broadcastSocket.close(); } catch (Exception ignored) {}
        executor.shutdownNow();
    }

    /** 单播监听（处理定向信令） */
    private void runUnicast() {
        try {
            unicastSocket = new DatagramSocket(PORT);
            byte[] buf = new byte[BUFFER_SIZE];
            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                unicastSocket.receive(pkt);
                String json = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8");
                String ip = pkt.getAddress().getHostAddress();
                handlePacket(json, ip);
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "单播异常: " + e.getMessage());
        }
    }

    /** 广播监听（处理 HELLO 发现） */
    private void runBroadcastReceiver() {
        try {
            broadcastSocket = new DatagramSocket(BROADCAST_PORT);
            broadcastSocket.setBroadcast(true);
            byte[] buf = new byte[BUFFER_SIZE];
            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                broadcastSocket.receive(pkt);
                String json = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8");
                String ip = pkt.getAddress().getHostAddress();
                handlePacket(json, ip);
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "广播接收异常: " + e.getMessage());
        }
    }

    private void handlePacket(String json, String senderIp) {
        try {
            SignalMessage msg = gson.fromJson(json, SignalMessage.class);
            String selfId = PrefsUtil.getSelfId(context);
            if (msg != null && !msg.fromId.equals(selfId) && listener != null) {
                listener.onSignalReceived(msg, senderIp);
            }
        } catch (Exception e) {
            Log.w(TAG, "解析信令异常: " + e.getMessage());
        }
    }

    /** 发送单播信令 */
    public void sendUnicast(SignalMessage msg, String ip, int port) {
        executor.execute(() -> {
            try {
                byte[] data = gson.toJson(msg).getBytes("UTF-8");
                DatagramSocket sock = new DatagramSocket();
                InetAddress addr = InetAddress.getByName(ip);
                DatagramPacket pkt = new DatagramPacket(data, data.length, addr, port);
                sock.send(pkt);
                sock.close();
            } catch (Exception e) {
                Log.e(TAG, "单播发送失败: " + e.getMessage());
            }
        });
    }

    /** 发送局域网广播（HELLO 发现） */
    public void sendBroadcast(SignalMessage msg) {
        executor.execute(() -> {
            try {
                byte[] data = gson.toJson(msg).getBytes("UTF-8");
                DatagramSocket sock = new DatagramSocket();
                sock.setBroadcast(true);
                InetAddress addr = InetAddress.getByName(BROADCAST_ADDR);
                DatagramPacket pkt = new DatagramPacket(data, data.length, addr, BROADCAST_PORT);
                sock.send(pkt);
                sock.close();
                Log.d(TAG, "广播发送: " + msg.type);
            } catch (Exception e) {
                Log.e(TAG, "广播发送失败: " + e.getMessage());
            }
        });
    }
}
