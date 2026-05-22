package com.p2pchat.app.p2p;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.p2pchat.app.model.PeerInfo;
import com.p2pchat.app.model.SignalMessage;
import com.p2pchat.app.util.PrefsUtil;
import okhttp3.*;
import okio.ByteString;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp 的 WebSocket 信令客户端
 * 负责连接公网信令服务器、注册、心跳、消息收发
 */
public class WebSocketSignalClient {

    private static final String TAG = "WsSignalClient";

    public enum State { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    private static final int RECONNECT_BASE = 2000;
    private static final int RECONNECT_MAX   = 30000;
    private static final int HB_INTERVAL     = 30000;

    private final Context context;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private volatile State state = State.DISCONNECTED;
    private String serverUrl;
    private WsListener listener;
    private boolean manuallyDisconnected = false;

    private int reconnectDelay = RECONNECT_BASE;
    private Runnable heartbeatRunnable;

    public interface WsListener {
        void onConnected();
        void onDisconnected(int code, String reason);
        void onSignalReceived(SignalMessage msg);
        void onPeerListReceived(List<PeerInfo> peers);
        void onStateChange(State newState);
    }

    public WebSocketSignalClient(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public void setListener(WsListener l) { this.listener = l; }

    public State getState() { return state; }

    /** 连接服务器 */
    public void connect(String url) {
        this.serverUrl = url;
        this.manuallyDisconnected = false;
        doConnect();
    }

    private void doConnect() {
        if (manuallyDisconnected) return;

        setState(State.CONNECTING);

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build();

        Request request = new Request.Builder()
            .url(serverUrl)
            .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.i(TAG, "WebSocket 已连接");
                reconnectDelay = RECONNECT_BASE;
                // 连接成功后自动注册
                register();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    SignalMessage msg = gson.fromJson(text, SignalMessage.class);
                    handleServerMessage(msg);
                } catch (Exception e) {
                    Log.e(TAG, "解析消息失败: " + e.getMessage());
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.i(TAG, "WebSocket 已关闭: " + code + " " + reason);
                stopHeartbeat();
                setState(State.DISCONNECTED);
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.w(TAG, "WebSocket 连接失败: " + t.getMessage());
                setState(State.RECONNECTING);
                scheduleReconnect();
            }
        });
    }

    /** 注册到服务器 */
    private void register() {
        PeerInfo self = PrefsUtil.getSelfInfo(context);
        if (self == null) {
            Log.e(TAG, "无法注册: 未设置用户信息");
            return;
        }
        SignalMessage reg = new SignalMessage(
            SignalMessage.TYPE_REGISTER, self.peerId, "server",
            gson.toJson(self)
        );
        send(reg);
    }

    /** 处理服务端消息 */
    private void handleServerMessage(SignalMessage msg) {
        if (msg == null) return;

        switch (msg.type) {
            case "REGISTER_OK":
                Log.i(TAG, "注册成功");
                setState(State.CONNECTED);
                startHeartbeat();
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnected();
                });
                break;

            case "PONG":
                // 心跳响应，重置超时（OkHttp ping 已处理底层保活）
                break;

            case "PEER_LIST":
                try {
                    Type listType = new TypeToken<List<PeerInfo>>(){}.getType();
                    List<PeerInfo> peers = gson.fromJson(msg.payload, listType);
                    if (peers != null) {
                        mainHandler.post(() -> {
                            if (listener != null) listener.onPeerListReceived(peers);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析在线列表失败: " + e.getMessage());
                }
                break;

            case "PEER_OFFLINE":
                Log.d(TAG, "节点离线: " + msg.payload);
                break;

            case "ERROR":
                Log.w(TAG, "服务器错误: " + msg.payload);
                break;

            default:
                // 其他所有消息视为对等信令，透传给上层
                mainHandler.post(() -> {
                    if (listener != null) listener.onSignalReceived(msg);
                });
                break;
        }
    }

    /** 发送消息 */
    private void send(SignalMessage msg) {
        if (webSocket != null) {
            String json = gson.toJson(msg);
            webSocket.send(json);
        }
    }

    /** 向指定 peer 发送信令 */
    public void sendToPeer(SignalMessage msg) {
        if (state != State.CONNECTED) {
            Log.w(TAG, "未连接，无法发送");
            return;
        }
        send(msg);
    }

    /** 请求 TURN 凭据 */
    public void requestTurnCredentials() {
        String selfId = PrefsUtil.getSelfId(context);
        SignalMessage req = new SignalMessage(
            "TURN_CREDENTIALS", selfId, "server", ""
        );
        send(req);
    }

    /** 心跳 */
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatRunnable = () -> {
            if (state == State.CONNECTED) {
                String selfId = PrefsUtil.getSelfId(context);
                SignalMessage ping = new SignalMessage(
                    SignalMessage.TYPE_PING, selfId, "server", ""
                );
                send(ping);
                mainHandler.postDelayed(heartbeatRunnable, HB_INTERVAL);
            }
        };
        mainHandler.postDelayed(heartbeatRunnable, HB_INTERVAL);
    }

    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            mainHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
    }

    /** 指数退避重连 */
    private void scheduleReconnect() {
        if (manuallyDisconnected) return;
        final int delay = reconnectDelay;
        reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX);
        Log.i(TAG, delay / 1000 + " 秒后重连...");
        mainHandler.postDelayed(this::doConnect, delay);
    }

    /** 断开连接 */
    public void disconnect() {
        manuallyDisconnected = true;
        stopHeartbeat();
        if (webSocket != null) {
            webSocket.close(1000, "user disconnect");
            webSocket = null;
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient = null;
        }
        setState(State.DISCONNECTED);
    }

    private void setState(State newState) {
        State old = this.state;
        this.state = newState;
        if (old != newState) {
            mainHandler.post(() -> {
                if (listener != null) listener.onStateChange(newState);
            });
        }
    }
}
