package com.p2pchat.app.p2p;

import android.content.Context;
import android.util.Log;
import org.webrtc.*;
import java.util.*;

/**
 * WebRTC 音视频通话管理器
 * 支持：语音通话、视频通话
 */
public class CallManager {

    private static final String TAG = "CallManager";

    private final Context context;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private AudioTrack   localAudioTrack;
    private VideoTrack   localVideoTrack;
    private VideoTrack   remoteVideoTrack;
    private VideoSource  videoSource;
    private AudioSource  audioSource;
    private CameraVideoCapturer videoCapturer;

    private CallStateListener listener;
    private boolean isCaller;
    private boolean isVideo;
    private String  remotePeerId;

    public interface CallStateListener {
        void onLocalVideo(VideoTrack track);
        void onRemoteVideo(VideoTrack track);
        void onCallConnected();
        void onCallEnded();
        void onOfferCreated(String sdp);
        void onAnswerCreated(String sdp);
        void onIceCandidate(String sdpMid, int sdpMLineIndex, String sdp);
    }

    public CallManager(Context ctx, PeerConnectionFactory factory) {
        this.context = ctx;
        this.factory = factory;
    }

    public void setListener(CallStateListener l) { this.listener = l; }

    /** 发起通话 */
    public void startCall(String remotePeerId, boolean isVideo, List<PeerConnection.IceServer> iceServers) {
        this.isCaller = true;
        this.isVideo = isVideo;
        this.remotePeerId = remotePeerId;
        setupPeerConnection(iceServers);
        setupMediaTracks();
        createOffer();
    }

    /** 接受通话（收到 Offer 后调用） */
    public void acceptCall(String remotePeerId, boolean isVideo, String remoteSdp,
                           List<PeerConnection.IceServer> iceServers) {
        this.isCaller = false;
        this.isVideo = isVideo;
        this.remotePeerId = remotePeerId;
        setupPeerConnection(iceServers);
        setupMediaTracks();

        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, remoteSdp);
        peerConnection.setRemoteDescription(new SimpleSdpObserver(), offer);
        createAnswer();
    }

    /** 收到 Answer 后设置远端 SDP */
    public void setRemoteAnswer(String sdp) {
        if (peerConnection != null) {
            SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            peerConnection.setRemoteDescription(new SimpleSdpObserver(), answer);
        }
    }

    /** 添加 ICE candidate */
    public void addIceCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, sdp));
        }
    }

    /** 挂断 */
    public void endCall() {
        try {
            if (videoCapturer != null) { videoCapturer.stopCapture(); videoCapturer.dispose(); videoCapturer = null; }
            if (localAudioTrack != null) { localAudioTrack.dispose(); localAudioTrack = null; }
            if (localVideoTrack != null) { localVideoTrack.dispose(); localVideoTrack = null; }
            if (videoSource != null) { videoSource.dispose(); videoSource = null; }
            if (audioSource != null) { audioSource.dispose(); audioSource = null; }
            if (peerConnection != null) { peerConnection.close(); peerConnection = null; }
        } catch (Exception e) { Log.e(TAG, "endCall error", e); }
        if (listener != null) listener.onCallEnded();
    }

    /** 切换摄像头 */
    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
        }
    }

    /** 静音/取消静音 */
    public void setMuted(boolean muted) {
        if (localAudioTrack != null) localAudioTrack.setEnabled(!muted);
    }

    /** 开关摄像头 */
    public void setCameraEnabled(boolean enabled) {
        if (localVideoTrack != null) localVideoTrack.setEnabled(enabled);
    }

    private void setupPeerConnection(List<PeerConnection.IceServer> iceServers) {
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = factory.createPeerConnection(config, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate candidate) {
                if (listener != null)
                    listener.onIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
            }
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                MediaStreamTrack track = receiver.track();
                if (track instanceof VideoTrack) {
                    remoteVideoTrack = (VideoTrack) track;
                    if (listener != null) listener.onRemoteVideo(remoteVideoTrack);
                }
            }
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                    if (listener != null) listener.onCallConnected();
                } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                           state == PeerConnection.PeerConnectionState.FAILED) {
                    endCall();
                }
            }
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {}
            @Override public void onIceConnectionReceivingChange(boolean r) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] cs) {}
            @Override public void onAddStream(MediaStream s) {}
            @Override public void onRemoveStream(MediaStream s) {}
            @Override public void onDataChannel(DataChannel dc) {}
            @Override public void onRenegotiationNeeded() {}
        });
    }

    private void setupMediaTracks() {
        // 音频
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("local_audio_track", audioSource);
        localAudioTrack.setEnabled(true);
        peerConnection.addTrack(localAudioTrack);

        // 视频
        if (isVideo) {
            videoCapturer = createCameraCapturer();
            if (videoCapturer != null) {
                SurfaceTextureHelper surfaceHelper = SurfaceTextureHelper.create("CaptureThread", null);
                videoSource = factory.createVideoSource(videoCapturer.isScreencast());
                videoCapturer.initialize(surfaceHelper, context, videoSource.getCapturerObserver());
                videoCapturer.startCapture(640, 480, 30);
                localVideoTrack = factory.createVideoTrack("local_video_track", videoSource);
                localVideoTrack.setEnabled(true);
                peerConnection.addTrack(localVideoTrack);
                if (listener != null) listener.onLocalVideo(localVideoTrack);
            }
        }
    }

    private CameraVideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);
        String[] names = enumerator.getDeviceNames();
        // 优先前置摄像头
        for (String name : names) {
            if (enumerator.isFrontFacing(name)) return enumerator.createCapturer(name, null);
        }
        for (String name : names) {
            if (!enumerator.isFrontFacing(name)) return enumerator.createCapturer(name, null);
        }
        return null;
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo ? "true" : "false"));

        peerConnection.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                if (listener != null) listener.onOfferCreated(sdp.description);
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String e) { Log.e(TAG,"createOffer fail:"+e); }
            @Override public void onSetFailure(String e) { Log.e(TAG,"setOffer fail:"+e); }
        }, constraints);
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo ? "true" : "false"));

        peerConnection.createAnswer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                if (listener != null) listener.onAnswerCreated(sdp.description);
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String e) { Log.e(TAG,"createAnswer fail:"+e); }
            @Override public void onSetFailure(String e) { Log.e(TAG,"setAnswer fail:"+e); }
        }, constraints);
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription s) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String e) { Log.e("SdpObs","create:"+e); }
        @Override public void onSetFailure(String e) { Log.e("SdpObs","set:"+e); }
    }
}
