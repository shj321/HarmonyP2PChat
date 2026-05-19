package com.p2pchat.app.ui.activity;

import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.p2pchat.app.R;
import com.p2pchat.app.model.SignalMessage;
import com.p2pchat.app.p2p.CallManager;
import com.p2pchat.app.service.P2PService;
import com.p2pchat.app.util.PrefsUtil;
import org.webrtc.*;
import java.util.*;

/**
 * 语音/视频通话界面
 */
public class CallActivity extends AppCompatActivity {

    private final Gson gson = new Gson();
    private String fromId;
    private boolean isCaller;
    private boolean isVideo;
    private String  remoteSdp;
    private String  selfId;

    private CallManager callManager;
    private PeerConnectionFactory factory;

    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;
    private TextView tvStatus;
    private ImageButton btnMute, btnCam, btnEnd, btnSwitchCam;
    private boolean muted = false;
    private boolean camOff = false;

    private P2PService p2pService;
    private boolean bound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            p2pService = ((P2PService.LocalBinder) b).getService();
            bound = true;
            startCallFlow();
        }
        @Override public void onServiceDisconnected(ComponentName n) { bound = false; }
    };

    private final BroadcastReceiver callReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (P2PService.ACTION_CALL_ANSWER.equals(action)) {
                String from = intent.getStringExtra(P2PService.EXTRA_FROM_ID);
                if (fromId.equals(from)) {
                    String payload = intent.getStringExtra(P2PService.EXTRA_PAYLOAD);
                    P2PConnectionManager.CallAckPayload ack =
                        gson.fromJson(payload, P2PConnectionManager.CallAckPayload.class);
                    if (ack.accepted) {
                        callManager.setRemoteAnswer(ack.sdp);
                        tvStatus.setText("通话中...");
                    } else {
                        tvStatus.setText("对方已拒绝");
                        finish();
                    }
                }
            } else if (P2PService.ACTION_CALL_END.equals(action)) {
                callManager.endCall();
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        fromId    = getIntent().getStringExtra("fromId");
        isCaller  = getIntent().getBooleanExtra("isCaller", false);
        isVideo   = getIntent().getBooleanExtra("isVideo", false);
        remoteSdp = getIntent().getStringExtra("sdp");
        selfId    = PrefsUtil.getSelfId(this);

        localView   = findViewById(R.id.localView);
        remoteView  = findViewById(R.id.remoteView);
        tvStatus    = findViewById(R.id.tvCallStatus);
        btnMute     = findViewById(R.id.btnMute);
        btnCam      = findViewById(R.id.btnCamera);
        btnEnd      = findViewById(R.id.btnEndCall);
        btnSwitchCam= findViewById(R.id.btnSwitchCamera);

        if (!isVideo) {
            localView.setVisibility(View.GONE);
            remoteView.setVisibility(View.GONE);
            btnCam.setVisibility(View.GONE);
            btnSwitchCam.setVisibility(View.GONE);
        }

        tvStatus.setText(isCaller ? "呼叫中..." : "来电...");

        btnEnd.setOnClickListener(v -> endCallAndFinish());
        btnMute.setOnClickListener(v -> {
            muted = !muted;
            callManager.setMuted(muted);
            btnMute.setImageResource(muted ? android.R.drawable.ic_lock_silent_mode :
                android.R.drawable.ic_lock_silent_mode_off);
        });
        btnCam.setOnClickListener(v -> {
            camOff = !camOff;
            callManager.setCameraEnabled(!camOff);
        });
        btnSwitchCam.setOnClickListener(v -> callManager.switchCamera());

        initWebRTC();

        IntentFilter filter = new IntentFilter();
        filter.addAction(P2PService.ACTION_CALL_ANSWER);
        filter.addAction(P2PService.ACTION_CALL_END);
        registerReceiver(callReceiver, filter);

        bindService(new Intent(this, P2PService.class), serviceConn, BIND_AUTO_CREATE);
    }

    private void initWebRTC() {
        PeerConnectionFactory.InitializationOptions opts =
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(opts);
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        EglBase eglBase = EglBase.create();
        if (isVideo) {
            localView.init(eglBase.getEglBaseContext(), null);
            remoteView.init(eglBase.getEglBaseContext(), null);
        }

        callManager = new CallManager(this, factory);
        callManager.setListener(new CallManager.CallStateListener() {
            @Override public void onLocalVideo(VideoTrack track) {
                runOnUiThread(() -> { if (isVideo) track.addSink(localView); });
            }
            @Override public void onRemoteVideo(VideoTrack track) {
                runOnUiThread(() -> { if (isVideo) track.addSink(remoteView); });
            }
            @Override public void onCallConnected() {
                runOnUiThread(() -> tvStatus.setText("通话中"));
            }
            @Override public void onCallEnded() { finish(); }
            @Override public void onOfferCreated(String sdp) {
                // 发送 CALL_REQ 信令
                sendCallRequest(sdp);
            }
            @Override public void onAnswerCreated(String sdp) {
                // 发送 CALL_ACK 信令
                sendCallAck(true, sdp);
            }
            @Override public void onIceCandidate(String mid, int idx, String sdp) {
                sendIce(mid, idx, sdp);
            }
        });
    }

    private void startCallFlow() {
        List<PeerConnection.IceServer> iceServers = Arrays.asList(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        );
        if (isCaller) {
            callManager.startCall(fromId, isVideo, iceServers);
        } else {
            // 接听 - 先向对方发送 ACK
            callManager.acceptCall(fromId, isVideo, remoteSdp, iceServers);
        }
    }

    private void sendCallRequest(String sdp) {
        if (!bound) return;
        P2PConnectionManager.CallReqPayload payload = new P2PConnectionManager.CallReqPayload();
        payload.isVideo = isVideo;
        payload.sdp = sdp;
        SignalMessage msg = new SignalMessage(SignalMessage.TYPE_CALL_REQ, selfId, fromId, gson.toJson(payload));
        p2pService.getConnectionManager().sendSignal(msg, fromId);
    }

    private void sendCallAck(boolean accepted, String sdp) {
        if (!bound) return;
        P2PConnectionManager.CallAckPayload payload = new P2PConnectionManager.CallAckPayload();
        payload.accepted = accepted;
        payload.sdp = sdp;
        SignalMessage msg = new SignalMessage(SignalMessage.TYPE_CALL_ACK, selfId, fromId, gson.toJson(payload));
        p2pService.getConnectionManager().sendSignal(msg, fromId);
    }

    private void sendIce(String mid, int idx, String sdp) {
        if (!bound) return;
        P2PConnectionManager.IceCandidateData cd = new P2PConnectionManager.IceCandidateData();
        cd.sdpMid = mid;
        cd.sdpMLineIndex = idx;
        cd.sdp = sdp;
        SignalMessage msg = new SignalMessage(SignalMessage.TYPE_ICE, selfId, fromId, gson.toJson(cd));
        p2pService.getConnectionManager().sendSignal(msg, fromId);
    }

    private void endCallAndFinish() {
        if (bound) {
            SignalMessage bye = new SignalMessage(SignalMessage.TYPE_CALL_END, selfId, fromId, "");
            p2pService.getConnectionManager().sendSignal(bye, fromId);
        }
        callManager.endCall();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(callReceiver);
        if (bound) unbindService(serviceConn);
        if (callManager != null) callManager.endCall();
        if (localView  != null) localView.release();
        if (remoteView != null) remoteView.release();
        if (factory    != null) factory.dispose();
    }

    // 引用 P2PConnectionManager 内部类（简化引用）
    static class P2PConnectionManager {
        static class CallReqPayload { boolean isVideo; String sdp; }
        static class CallAckPayload { boolean accepted; String sdp; }
        static class IceCandidateData { String sdpMid; int sdpMLineIndex; String sdp; }
    }
}
