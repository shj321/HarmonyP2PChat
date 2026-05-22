package com.p2pchat.app.service;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.gson.Gson;
import com.p2pchat.app.P2PChatApplication;
import com.p2pchat.app.R;
import com.p2pchat.app.db.ChatDatabase;
import com.p2pchat.app.model.*;
import com.p2pchat.app.p2p.*;
import com.p2pchat.app.util.PrefsUtil;
import java.util.*;

/**
 * P2P 核心前台服务
 * 负责：节点发现、信令、消息收发、事件广播
 */
public class P2PService extends Service {

    private static final String TAG = "P2PService";
    public  static final String ACTION_MSG_RECEIVED    = "com.p2pchat.MSG_RECEIVED";
    public  static final String ACTION_PEER_DISCOVERED = "com.p2pchat.PEER_DISCOVERED";
    public  static final String ACTION_PEER_CONNECTED  = "com.p2pchat.PEER_CONNECTED";
    public  static final String ACTION_PEER_OFFLINE    = "com.p2pchat.PEER_OFFLINE";
    public  static final String ACTION_CALL_REQUEST    = "com.p2pchat.CALL_REQUEST";
    public  static final String ACTION_CALL_ANSWER     = "com.p2pchat.CALL_ANSWER";
    public  static final String ACTION_CALL_END        = "com.p2pchat.CALL_END";
    public  static final String ACTION_FILE_META       = "com.p2pchat.FILE_META";
    public  static final String ACTION_FILE_ACK        = "com.p2pchat.FILE_ACK";
    public  static final String ACTION_GROUP_MSG       = "com.p2pchat.GROUP_MSG";

    public  static final String EXTRA_PEER_ID          = "peerId";
    public  static final String EXTRA_PAYLOAD          = "payload";
    public  static final String EXTRA_FROM_ID          = "fromId";
    public  static final String EXTRA_CALL_IS_VIDEO    = "isVideo";
    public  static final String EXTRA_GROUP_ID         = "groupId";

    private final IBinder binder = new LocalBinder();
    private final Gson gson = new Gson();

    private SignalingServer signalingServer;
    private P2PConnectionManager connectionManager;
    private FileTransferManager  fileTransferManager;
    private ChatDatabase db;

    // 定时广播 HELLO（发现新节点）
    private final Handler helloHandler = new Handler(Looper.getMainLooper());
    private final Runnable helloRunnable = new Runnable() {
        @Override public void run() {
            connectionManager.broadcastHello();
            helloHandler.postDelayed(this, 10_000); // 每10秒广播一次
        }
    };

    public class LocalBinder extends Binder {
        public P2PService getService() { return P2PService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = ChatDatabase.getInstance(this);
        initP2P();
        startForeground(1, buildForegroundNotification());
        Log.i(TAG, "P2P 服务启动");
    }

    private void initP2P() {
        signalingServer = new SignalingServer(this);
        signalingServer.start();

        connectionManager = new P2PConnectionManager(this, signalingServer);
        connectionManager.setEventListener(new P2PConnectionManager.P2PEventListener() {
            @Override public void onPeerDiscovered(PeerInfo peer) {
                Intent i = new Intent(ACTION_PEER_DISCOVERED);
                i.putExtra(EXTRA_PEER_ID, peer.peerId);
                i.putExtra(EXTRA_PAYLOAD, gson.toJson(peer));
                sendBroadcast(i);
            }
            @Override public void onPeerConnected(String peerId) {
                Intent i = new Intent(ACTION_PEER_CONNECTED);
                i.putExtra(EXTRA_PEER_ID, peerId);
                sendBroadcast(i);
            }
            @Override public void onPeerDisconnected(String peerId) {
                Intent i = new Intent(ACTION_PEER_OFFLINE);
                i.putExtra(EXTRA_PEER_ID, peerId);
                sendBroadcast(i);
            }
            @Override public void onMessageReceived(String fromId, String payload) {
                // 持久化消息
                try {
                    ChatMsgPayload mp = gson.fromJson(payload, ChatMsgPayload.class);
                    if (mp != null && mp.msgId != null) {
                        com.p2pchat.app.model.Message msg = new com.p2pchat.app.model.Message();
                        msg.msgId = mp.msgId;
                        msg.fromPeerId = fromId;
                        msg.toPeerId = PrefsUtil.getSelfId(P2PService.this);
                        msg.content = mp.text;
                        msg.msgType = com.p2pchat.app.model.Message.TYPE_TEXT;
                        msg.sessionId = fromId.compareTo(msg.toPeerId) < 0 ?
                                fromId + "_" + msg.toPeerId : msg.toPeerId + "_" + fromId;
                        msg.state = com.p2pchat.app.model.Message.STATE_RECEIVED;
                        db.messageDao().insert(msg);
                    }
                } catch (Exception ignored) {}

                Intent i = new Intent(ACTION_MSG_RECEIVED);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_PAYLOAD, payload);
                sendBroadcast(i);
                showMessageNotification(fromId, payload);
            }
            @Override public void onCallRequest(String fromId, boolean isVideo, String sdp) {
                Intent i = new Intent(ACTION_CALL_REQUEST);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_CALL_IS_VIDEO, isVideo);
                i.putExtra(EXTRA_PAYLOAD, sdp);
                sendBroadcast(i);
            }
            @Override public void onCallAnswer(String fromId, boolean accepted, String sdp) {
                Intent i = new Intent(ACTION_CALL_ANSWER);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_PAYLOAD, gson.toJson(new CallAckPayload(accepted, sdp)));
                sendBroadcast(i);
            }
            @Override public void onCallEnded(String fromId) {
                Intent i = new Intent(ACTION_CALL_END);
                i.putExtra(EXTRA_FROM_ID, fromId);
                sendBroadcast(i);
            }
            @Override public void onIceCandidate(String fromId, String json) { /* 已在 manager 内处理 */ }
            @Override public void onFileMeta(String fromId, String metaJson) {
                Intent i = new Intent(ACTION_FILE_META);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_PAYLOAD, metaJson);
                sendBroadcast(i);
            }
            @Override public void onFileAck(String fromId, String ackJson) {
                Intent i = new Intent(ACTION_FILE_ACK);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_PAYLOAD, ackJson);
                sendBroadcast(i);
            }
            @Override public void onGroupMessage(String fromId, String groupId, String payload) {
                Intent i = new Intent(ACTION_GROUP_MSG);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_GROUP_ID, groupId);
                i.putExtra(EXTRA_PAYLOAD, payload);
                sendBroadcast(i);
            }
            @Override public void onGroupInfo(String fromId, String groupJson) {
                Intent i = new Intent(ACTION_GROUP_MSG);
                i.putExtra(EXTRA_FROM_ID, fromId);
                i.putExtra(EXTRA_PAYLOAD, groupJson);
                sendBroadcast(i);
            }
        });

        fileTransferManager = new FileTransferManager(this, signalingServer);
        fileTransferManager.startServer();

        // 启动后立即广播 HELLO
        helloHandler.post(helloRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        helloHandler.removeCallbacks(helloRunnable);
        if (connectionManager != null) {
            // 发送 BYE
            String selfId = PrefsUtil.getSelfId(this);
            for (String peerId : connectionManager.getPeerMap().keySet()) {
                SignalMessage bye = new SignalMessage(SignalMessage.TYPE_BYE, selfId, peerId, "");
                connectionManager.sendSignal(bye, peerId);
            }
        }
        if (signalingServer != null) signalingServer.stop();
        if (fileTransferManager != null) fileTransferManager.stopServer();
        super.onDestroy();
    }

    // ---- 公开 API ----

    public void sendTextMessage(String toPeerId, String text, boolean isGroup, String groupId) {
        ChatMsgPayload payload = new ChatMsgPayload();
        payload.msgId = java.util.UUID.randomUUID().toString();
        payload.text = text;
        payload.isGroup = isGroup;
        payload.groupId = groupId;

        String selfId = PrefsUtil.getSelfId(this);
        SignalMessage msg = new SignalMessage(
            isGroup ? SignalMessage.TYPE_GROUP_MSG : SignalMessage.TYPE_CHAT,
            selfId, toPeerId, gson.toJson(payload));
        connectionManager.sendSignal(msg, toPeerId);

        // 本地持久化
        com.p2pchat.app.model.Message m = com.p2pchat.app.model.Message.text(selfId, toPeerId, text, isGroup);
        m.msgId = payload.msgId;
        m.state = com.p2pchat.app.model.Message.STATE_SENT;
        db.messageDao().insert(m);
    }

    public P2PConnectionManager getConnectionManager() { return connectionManager; }
    public FileTransferManager getFileTransferManager() { return fileTransferManager; }
    public SignalingServer getSignalingServer() { return signalingServer; }

    // ---- 通知 ----

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, P2PChatApplication.CHANNEL_P2P)
                .setContentTitle("星际通运行中")
                .setContentText("P2P 网络已就绪")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
                .build();
    }

    private void showMessageNotification(String fromId, String payload) {
        try {
            ChatMsgPayload mp = gson.fromJson(payload, ChatMsgPayload.class);
            PeerInfo peer = connectionManager.getPeer(fromId);
            String nick = peer != null ? peer.nickname : fromId.substring(0, 8);
            NotificationCompat.Builder nb = new NotificationCompat.Builder(this, P2PChatApplication.CHANNEL_MSG)
                    .setContentTitle(nick)
                    .setContentText(mp != null ? mp.text : "新消息")
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setAutoCancel(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(fromId.hashCode(), nb.build());
        } catch (Exception ignored) {}
    }

    // 内部 payload 类
    public static class ChatMsgPayload {
        public String msgId;
        public String text;
        public boolean isGroup;
        public String groupId;
    }

    private static class CallAckPayload {
        boolean accepted;
        String sdp;
        CallAckPayload(boolean a, String s) { accepted = a; sdp = s; }
    }
}
