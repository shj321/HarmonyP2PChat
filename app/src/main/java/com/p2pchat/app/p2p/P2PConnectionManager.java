package com.p2pchat.app.p2p;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.p2pchat.app.model.PeerInfo;
import com.p2pchat.app.model.SignalMessage;
import com.p2pchat.app.util.PrefsUtil;
import org.webrtc.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P2P 连接管理器（WebRTC DataChannel 文字/数据通道）
 * 每个 PeerConnection 对应一个远端节点
 */
public class P2PConnectionManager implements PeerConnectionObserverBase {

    private static final String TAG = "P2PConnMgr";

    private final Context context;
    private final Gson    gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PeerConnectionFactory factory;
    private final Map<String, PeerConnection>    connections  = new ConcurrentHashMap<>();
    private final Map<String, DataChannel>       dataChannels = new ConcurrentHashMap<>();
    private final Map<String, PeerInfo>          peerMap      = new ConcurrentHashMap<>();

    private SignalingServer signalingServer;
    private P2PEventListener eventListener;

    private List<PeerConnection.IceServer> iceServers;

    public interface P2PEventListener {
        void onPeerDiscovered(PeerInfo peer);
        void onPeerConnected(String peerId);
        void onPeerDisconnected(String peerId);
        void onMessageReceived(String fromId, String payload);
        void onCallRequest(String fromId, boolean isVideo, String sdpOffer);
        void onCallAnswer(String fromId, boolean accepted, String sdpAnswer);
        void onCallEnded(String fromId);
        void onIceCandidate(String fromId, String candidateJson);
        void onFileMeta(String fromId, String metaJson);
        void onFileAck(String fromId, String ackJson);
        void onGroupMessage(String fromId, String groupId, String payload);
        void onGroupInfo(String fromId, String groupJson);
    }

    public P2PConnectionManager(Context ctx, SignalingServer server) {
        this.context = ctx;
        this.signalingServer = server;

        // 使用公共 STUN（穿透内网，局域网内直连不需要）
        iceServers = Arrays.asList(
            // STUN
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            // TURN (Open Relay 免费公共 TURN)
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        );

        initWebRTC();

        server.setListener((msg, senderIp) -> handleSignal(msg, senderIp));
    }

    private void initWebRTC() {
        PeerConnectionFactory.InitializationOptions initOpts =
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("")
                .setEnableInternalTracer(false)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initOpts);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();
        Log.i(TAG, "WebRTC PeerConnectionFactory 初始化完成");
    }

    public void setEventListener(P2PEventListener l) { this.eventListener = l; }

    /** 向局域网广播自身存在（HELLO） */
    public void broadcastHello() {
        PeerInfo self = PrefsUtil.getSelfInfo(context);
        SignalMessage msg = new SignalMessage(
            SignalMessage.TYPE_HELLO,
            self.peerId,
            "*",
            gson.toJson(self)
        );
        signalingServer.sendBroadcast(msg);
    }

    /** 处理接收到的信令消息 */
    public void handleSignal(SignalMessage msg, String senderIp) {
        switch (msg.type) {
            case SignalMessage.TYPE_HELLO:
                handleHello(msg, senderIp);
                break;
            case SignalMessage.TYPE_HELLO_ACK:
                handleHelloAck(msg, senderIp);
                break;
            case SignalMessage.TYPE_OFFER:
                handleOffer(msg);
                break;
            case SignalMessage.TYPE_ANSWER:
                handleAnswer(msg);
                break;
            case SignalMessage.TYPE_ICE:
                handleIce(msg);
                break;
            case SignalMessage.TYPE_BYE:
                handleBye(msg);
                break;
            case SignalMessage.TYPE_CHAT:
            case SignalMessage.TYPE_FILE_META:
            case SignalMessage.TYPE_FILE_ACK:
            case SignalMessage.TYPE_GROUP_MSG:
            case SignalMessage.TYPE_GROUP_INFO:
            case SignalMessage.TYPE_CALL_REQ:
            case SignalMessage.TYPE_CALL_ACK:
            case SignalMessage.TYPE_CALL_END:
                dispatchAppSignal(msg);
                break;
            case SignalMessage.TYPE_PING:
                replyPong(msg, senderIp);
                break;
        }
    }

    private void handleHello(SignalMessage msg, String senderIp) {
        try {
            PeerInfo remote = gson.fromJson(msg.payload, PeerInfo.class);
            remote.address = senderIp;
            peerMap.put(remote.peerId, remote);
            mainHandler.post(() -> { if (eventListener != null) eventListener.onPeerDiscovered(remote); });

            // 回复 ACK
            PeerInfo self = PrefsUtil.getSelfInfo(context);
            SignalMessage ack = new SignalMessage(
                SignalMessage.TYPE_HELLO_ACK, self.peerId, remote.peerId, gson.toJson(self));
            signalingServer.sendUnicast(ack, senderIp, SignalingServer.PORT);

        } catch (Exception e) { Log.e(TAG, "handleHello error", e); }
    }

    private void handleHelloAck(SignalMessage msg, String senderIp) {
        try {
            PeerInfo remote = gson.fromJson(msg.payload, PeerInfo.class);
            remote.address = senderIp;
            peerMap.put(remote.peerId, remote);
            mainHandler.post(() -> { if (eventListener != null) eventListener.onPeerDiscovered(remote); });
        } catch (Exception e) { Log.e(TAG, "handleHelloAck error", e); }
    }

    private void handleOffer(SignalMessage msg) {
        try {
            String remotePeerId = msg.fromId;
            PeerInfo remote = peerMap.get(remotePeerId);
            if (remote == null) return;

            createPeerConnection(remotePeerId, false, null);
            PeerConnection pc = connections.get(remotePeerId);
            if (pc == null) return;

            SessionDescription offer = new SessionDescription(
                SessionDescription.Type.OFFER, msg.payload);
            pc.setRemoteDescription(new SimpleSdpObserver(), offer);
            pc.createAnswer(new SdpObserver() {
                @Override public void onCreateSuccess(SessionDescription sdp) {
                    pc.setLocalDescription(new SimpleSdpObserver(), sdp);
                    SignalMessage ans = new SignalMessage(
                        SignalMessage.TYPE_ANSWER,
                        PrefsUtil.getSelfId(context),
                        remotePeerId,
                        sdp.description
                    );
                    signalingServer.sendUnicast(ans, remote.address, SignalingServer.PORT);
                }
                @Override public void onSetSuccess() {}
                @Override public void onCreateFailure(String e) { Log.e(TAG,"createAnswer fail:"+e); }
                @Override public void onSetFailure(String e) { Log.e(TAG,"setAnswer fail:"+e); }
            }, new MediaConstraints());

        } catch (Exception e) { Log.e(TAG, "handleOffer error", e); }
    }

    private void handleAnswer(SignalMessage msg) {
        PeerConnection pc = connections.get(msg.fromId);
        if (pc != null) {
            SessionDescription answer = new SessionDescription(
                SessionDescription.Type.ANSWER, msg.payload);
            pc.setRemoteDescription(new SimpleSdpObserver(), answer);
        }
    }

    private void handleIce(SignalMessage msg) {
        try {
            PeerConnection pc = connections.get(msg.fromId);
            if (pc != null) {
                IceCandidateData cd = gson.fromJson(msg.payload, IceCandidateData.class);
                IceCandidate ic = new IceCandidate(cd.sdpMid, cd.sdpMLineIndex, cd.sdp);
                pc.addIceCandidate(ic);
            }
        } catch (Exception e) { Log.e(TAG, "handleIce error", e); }
    }

    private void handleBye(SignalMessage msg) {
        closePeerConnection(msg.fromId);
        mainHandler.post(() -> { if (eventListener != null) eventListener.onPeerDisconnected(msg.fromId); });
    }

    private void dispatchAppSignal(SignalMessage msg) {
        mainHandler.post(() -> {
            if (eventListener == null) return;
            switch (msg.type) {
                case SignalMessage.TYPE_CHAT:
                    eventListener.onMessageReceived(msg.fromId, msg.payload);
                    break;
                case SignalMessage.TYPE_CALL_REQ:
                    CallReqPayload cr = gson.fromJson(msg.payload, CallReqPayload.class);
                    eventListener.onCallRequest(msg.fromId, cr.isVideo, cr.sdp);
                    break;
                case SignalMessage.TYPE_CALL_ACK:
                    CallAckPayload ca = gson.fromJson(msg.payload, CallAckPayload.class);
                    eventListener.onCallAnswer(msg.fromId, ca.accepted, ca.sdp);
                    break;
                case SignalMessage.TYPE_CALL_END:
                    eventListener.onCallEnded(msg.fromId);
                    break;
                case SignalMessage.TYPE_FILE_META:
                    eventListener.onFileMeta(msg.fromId, msg.payload);
                    break;
                case SignalMessage.TYPE_FILE_ACK:
                    eventListener.onFileAck(msg.fromId, msg.payload);
                    break;
                case SignalMessage.TYPE_GROUP_MSG:
                    GroupMsgPayload gm = gson.fromJson(msg.payload, GroupMsgPayload.class);
                    eventListener.onGroupMessage(msg.fromId, gm.groupId, msg.payload);
                    break;
                case SignalMessage.TYPE_GROUP_INFO:
                    eventListener.onGroupInfo(msg.fromId, msg.payload);
                    break;
            }
        });
    }

    private void replyPong(SignalMessage msg, String ip) {
        PeerInfo self = PrefsUtil.getSelfInfo(context);
        SignalMessage pong = new SignalMessage(SignalMessage.TYPE_PONG, self.peerId, msg.fromId, "");
        signalingServer.sendUnicast(pong, ip, SignalingServer.PORT);
    }

    /** 主动发起 WebRTC 连接 */
    public void connectToPeer(String remotePeerId) {
        PeerInfo remote = peerMap.get(remotePeerId);
        if (remote == null) { Log.w(TAG, "未知节点: " + remotePeerId); return; }
        if (connections.containsKey(remotePeerId)) return;

        createPeerConnection(remotePeerId, true, remote);
    }

    /**
     * 通过 IP 地址主动发送 HELLO 并连接（手动添加联系人时使用）
     * 向指定 IP:端口 发送 HELLO 信令，对方收到后自动回复 HELLO_ACK
     */
    public void connectToPeerByIp(String ip, int port) {
        PeerInfo self = PrefsUtil.getSelfInfo(context);
        SignalMessage hello = new SignalMessage(
            SignalMessage.TYPE_HELLO,
            self.peerId,
            "*",
            gson.toJson(self)
        );
        // 向目标 IP 发送单播 HELLO（而非广播）
        signalingServer.sendUnicast(hello, ip, port);
        Log.i(TAG, "已向 " + ip + ":" + port + " 发送定向 HELLO");
    }

    /**
     * 手动添加一个已知 PeerInfo（不依赖发现）
     * 通常在收到对方的 HELLO_ACK 后由 P2PService 调用
     */
    public void addKnownPeer(PeerInfo peer) {
        if (!peerMap.containsKey(peer.peerId)) {
            peerMap.put(peer.peerId, peer);
            mainHandler.post(() -> {
                if (eventListener != null) eventListener.onPeerDiscovered(peer);
            });
        }
    }

    private void createPeerConnection(String remotePeerId, boolean createOffer, PeerInfo remote) {
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        PeerConnection pc = factory.createPeerConnection(config, new PeerConnectionObserver(remotePeerId, remote));
        if (pc == null) { Log.e(TAG, "创建 PeerConnection 失败"); return; }
        connections.put(remotePeerId, pc);

        if (createOffer) {
            // 创建 DataChannel
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            DataChannel dc = pc.createDataChannel("chat", init);
            dataChannels.put(remotePeerId, dc);
            dc.registerObserver(new DataChannelObserver(remotePeerId));

            pc.createOffer(new SdpObserver() {
                @Override public void onCreateSuccess(SessionDescription sdp) {
                    pc.setLocalDescription(new SimpleSdpObserver(), sdp);
                    SignalMessage offer = new SignalMessage(
                        SignalMessage.TYPE_OFFER,
                        PrefsUtil.getSelfId(context),
                        remotePeerId,
                        sdp.description
                    );
                    if (remote != null)
                        signalingServer.sendUnicast(offer, remote.address, SignalingServer.PORT);
                }
                @Override public void onSetSuccess() {}
                @Override public void onCreateFailure(String e) { Log.e(TAG,"createOffer fail:"+e); }
                @Override public void onSetFailure(String e) { Log.e(TAG,"setOffer fail:"+e); }
            }, new MediaConstraints());
        }
    }

    /** 通过信令直接发送（不依赖 DataChannel）*/
    public void sendSignal(SignalMessage msg, String targetPeerId) {
        PeerInfo remote = peerMap.get(targetPeerId);
        if (remote != null && remote.address != null) {
            signalingServer.sendUnicast(msg, remote.address, SignalingServer.PORT);
        }
    }

    /** 关闭指定连接 */
    public void closePeerConnection(String peerId) {
        DataChannel dc = dataChannels.remove(peerId);
        if (dc != null) dc.close();
        PeerConnection pc = connections.remove(peerId);
        if (pc != null) pc.close();
    }

    public Map<String, PeerInfo> getPeerMap() { return peerMap; }

    public PeerInfo getPeer(String id) { return peerMap.get(id); }

    // ---- 内部辅助类 ----

    private class PeerConnectionObserver implements PeerConnection.Observer {
        private final String remotePeerId;
        private final PeerInfo remote;

        PeerConnectionObserver(String id, PeerInfo r) { remotePeerId = id; remote = r; }

        @Override public void onIceCandidate(IceCandidate candidate) {
            if (remote == null || remote.address == null) return;
            IceCandidateData cd = new IceCandidateData();
            cd.sdpMid = candidate.sdpMid;
            cd.sdpMLineIndex = candidate.sdpMLineIndex;
            cd.sdp = candidate.sdp;
            SignalMessage msg = new SignalMessage(
                SignalMessage.TYPE_ICE,
                PrefsUtil.getSelfId(context),
                remotePeerId,
                gson.toJson(cd)
            );
            signalingServer.sendUnicast(msg, remote.address, SignalingServer.PORT);
        }

        @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
            Log.i(TAG, remotePeerId + " 状态: " + state);
            if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                mainHandler.post(() -> { if (eventListener != null) eventListener.onPeerConnected(remotePeerId); });
            } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                       state == PeerConnection.PeerConnectionState.FAILED) {
                mainHandler.post(() -> { if (eventListener != null) eventListener.onPeerDisconnected(remotePeerId); });
            }
        }

        @Override public void onDataChannel(DataChannel dc) {
            dataChannels.put(remotePeerId, dc);
            dc.registerObserver(new DataChannelObserver(remotePeerId));
        }

        @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {}
        @Override public void onIceConnectionReceivingChange(boolean r) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
        @Override public void onIceCandidatesRemoved(IceCandidate[] cs) {}
        @Override public void onAddStream(MediaStream s) {}
        @Override public void onRemoveStream(MediaStream s) {}
        @Override public void onRenegotiationNeeded() {}
        @Override public void onAddTrack(RtpReceiver r, MediaStream[] s) {}
    }

    private class DataChannelObserver implements DataChannel.Observer {
        private final String peerId;
        DataChannelObserver(String id) { this.peerId = id; }

        @Override public void onMessage(DataChannel.Buffer buf) {
            byte[] bytes = new byte[buf.data.remaining()];
            buf.data.get(bytes);
            String text = new String(bytes);
            mainHandler.post(() -> { if (eventListener != null) eventListener.onMessageReceived(peerId, text); });
        }

        @Override public void onBufferedAmountChange(long l) {}
        @Override public void onStateChange() {}
    }

    // JSON 辅助实体
    public static class IceCandidateData {
        public String sdpMid;
        public int    sdpMLineIndex;
        public String sdp;
    }

    public static class CallReqPayload {
        public boolean isVideo;
        public String  sdp;
    }

    public static class CallAckPayload {
        public boolean accepted;
        public String  sdp;
    }

    public static class GroupMsgPayload {
        public String groupId;
        public String content;
        public String msgType;
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription s) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String e) { Log.e("SdpObserver","create fail:"+e); }
        @Override public void onSetFailure(String e) { Log.e("SdpObserver","set fail:"+e); }
    }
}
