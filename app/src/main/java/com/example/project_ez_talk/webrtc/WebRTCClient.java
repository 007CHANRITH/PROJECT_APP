package com.example.project_ez_talk.webrtc;

import android.content.Context;
import android.util.Log;


import com.google.gson.Gson;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class WebRTCClient {

    private final Gson gson = new Gson();
    private final Context context;
    private final String username;
    private EglBase.Context eglBaseContext= EglBase.create().getEglBaseContext();
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private List<PeerConnection.IceServer> iceServer = new ArrayList<>();
    private CameraVideoCapturer videoCapturer;
    private VideoSource localVideoSource;
    private AudioSource localAudioSource;
    private String localTrackId = "local_track";
    private String localStreamId = "local_stream";
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private MediaStream localStream;
    private MediaConstraints mediaConstraints = new MediaConstraints();
    private boolean isDisposed = false; // ✅ Track disposal state

    public Listener listener;

    public WebRTCClient(Context context, PeerConnection.Observer observer, String username) {
        this.context = context;
        this.username = username;
        
        Log.d("WebRTCClient", "🔧 Initializing WebRTCClient for user: " + username);
        
        initPeerConnectionFactory();
        peerConnectionFactory = createPeerConnectionFactory();
        
        // ✅ Add STUN servers for NAT discovery
        iceServer.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServer.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        
        // ✅ Add TURN servers for emulator-to-emulator calls (relay traffic)
        // Free TURN server from Metered.ca
        iceServer.add(PeerConnection.IceServer.builder("turn:a.relay.metered.ca:80")
                .setUsername("85d4fa7c0d83e2130e1cd9e0")
                .setPassword("4pxET4c0oa/730YK")
                .createIceServer());
        
        iceServer.add(PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443")
                .setUsername("85d4fa7c0d83e2130e1cd9e0")
                .setPassword("4pxET4c0oa/730YK")
                .createIceServer());
        
        // Backup TURN server
        iceServer.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer());
        
        Log.d("WebRTCClient", "✅ ICE servers configured: " + iceServer.size() + " servers (2 STUN + 3 TURN)");
        
        // ✅ Create PeerConnection with RTCConfiguration optimized for emulators
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServer);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        
        // ✅ ICE transport policy: USE ALL (allows both STUN and TURN)
        // For emulators, you can force "relay" to use only TURN servers
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        
        // ✅ Continuous gathering for better connectivity
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        
        Log.d("WebRTCClient", "🔧 Creating PeerConnection...");
        Log.d("WebRTCClient", "   Config: " + rtcConfig.iceServers.size() + " servers, SDP: " + rtcConfig.sdpSemantics);
        Log.d("WebRTCClient", "   ICE Transport: " + rtcConfig.iceTransportsType);
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer);
        
        if (peerConnection == null) {
            Log.e("WebRTCClient", "❌ CRITICAL: PeerConnection creation failed!");
            Log.e("WebRTCClient", "   PeerConnectionFactory: " + (peerConnectionFactory != null ? "OK" : "NULL"));
            Log.e("WebRTCClient", "   ICE Servers: " + iceServer.size());
            Log.e("WebRTCClient", "   Observer: " + (observer != null ? "OK" : "NULL"));
            
            // Try with even simpler config
            Log.e("WebRTCClient", "🔄 Trying with PLAN_B semantics...");
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer);
            
            if (peerConnection == null) {
                Log.e("WebRTCClient", "❌ PLAN_B also failed!");
                throw new RuntimeException("Failed to create PeerConnection with both UNIFIED_PLAN and PLAN_B");
            } else {
                Log.w("WebRTCClient", "⚠️ PeerConnection created with PLAN_B (fallback)");
            }
        } else {
            Log.d("WebRTCClient", "✅ PeerConnection created successfully with UNIFIED_PLAN");
        }
        
        localVideoSource = peerConnectionFactory.createVideoSource(false);
        localAudioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio","true")); // ✅ Add audio receiving
        
        Log.d("WebRTCClient", "✅ WebRTCClient initialization complete");
    }

    //initializing peer connection section
    private void initPeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .setEnableInternalTracer(true)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
    }

    private PeerConnectionFactory createPeerConnectionFactory() {
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;
        
        // ✅ Build the factory with audio and video support
        PeerConnectionFactory factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBaseContext,true,true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBaseContext))
                .setOptions(options)
                .createPeerConnectionFactory();
        
        Log.d("WebRTCClient", "PeerConnectionFactory created: " + (factory != null ? "SUCCESS" : "FAILED"));
        return factory;
    }

    private PeerConnection createPeerConnection(PeerConnection.Observer observer){
        // ✅ Create with RTCConfiguration for better control
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServer);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        
        PeerConnection pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer);
        if (pc == null) {
            Log.e("WebRTCClient", "❌ Failed to create PeerConnection!");
        } else {
            Log.d("WebRTCClient", "✅ PeerConnection created successfully");
        }
        return pc;
    }

    //initilizing ui like surface view renderers

    public void initSurfaceViewRendere(SurfaceViewRenderer viewRenderer){
        viewRenderer.setEnableHardwareScaler(true);
        viewRenderer.setMirror(true);
        viewRenderer.init(eglBaseContext,null);
    }

    public void initLocalSurfaceView(SurfaceViewRenderer view){
        initSurfaceViewRendere(view);
        startLocalVideoStreaming(view);
    }

    private void startLocalVideoStreaming(SurfaceViewRenderer view) {
        // ✅ Check if peerConnection is null before proceeding
        if (peerConnection == null) {
            Log.e("WebRTCClient", "❌ Cannot start video streaming - PeerConnection is null!");
            return;
        }
        
        SurfaceTextureHelper helper= SurfaceTextureHelper.create(
                Thread.currentThread().getName(), eglBaseContext
        );

        videoCapturer = getVideoCapturer();
        videoCapturer.initialize(helper,context,localVideoSource.getCapturerObserver());
        videoCapturer.startCapture(480,360,15);
        localVideoTrack = peerConnectionFactory.createVideoTrack(
                localTrackId+"_video",localVideoSource
        );
        localVideoTrack.addSink(view);

        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId+"_audio",localAudioSource);
        localStream = peerConnectionFactory.createLocalMediaStream(localStreamId);
        localStream.addTrack(localVideoTrack);
        localStream.addTrack(localAudioTrack);
        peerConnection.addTrack(localVideoTrack, java.util.Collections.singletonList(localStreamId));
        peerConnection.addTrack(localAudioTrack, java.util.Collections.singletonList(localStreamId));
        Log.d("WebRTCClient", "✅ Local tracks added to peer connection");
    }

    private CameraVideoCapturer getVideoCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);

        String[] deviceNames = enumerator.getDeviceNames();

        for (String device: deviceNames){
            if (enumerator.isFrontFacing(device)){
                return enumerator.createCapturer(device,null);
            }
        }
        throw new IllegalStateException("front facing camera not found");
    }

    public void initRemoteSurfaceView(SurfaceViewRenderer view){
        initSurfaceViewRendere(view);
    }

    //negotiation section like call and answer
    public void call(String target){
        try{
            peerConnection.createOffer(new MySdpObserver(){
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    peerConnection.setLocalDescription(new MySdpObserver(){
                        @Override
                        public void onSetSuccess() {
                            super.onSetSuccess();
                            //its time to transfer this sdp to other peer
                            if (listener!=null){
                                listener.onTransferDataToOtherPeer(new DataModel(
                                        target,username,sessionDescription.description, DataModelType.Offer
                                ));
                            }
                        }
                    },sessionDescription);
                }
            },mediaConstraints);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void answer(String target){
        try{
            peerConnection.createAnswer(new MySdpObserver(){
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    peerConnection.setLocalDescription(new MySdpObserver(){
                        @Override
                        public void onSetSuccess() {
                            super.onSetSuccess();
                            //its time to transfer this sdp to other peer
                            if (listener!=null){
                                listener.onTransferDataToOtherPeer(new DataModel(
                                        target,username,sessionDescription.description, DataModelType.Answer
                                ));
                            }
                        }
                    },sessionDescription);
                }
            },mediaConstraints);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void onRemoteSessionReceived(SessionDescription sessionDescription){
        peerConnection.setRemoteDescription(new MySdpObserver(),sessionDescription);
    }

    public void addIceCandidate(IceCandidate iceCandidate){
        peerConnection.addIceCandidate(iceCandidate);
    }

    public void sendIceCandidate(IceCandidate iceCandidate, String target){
        addIceCandidate(iceCandidate);
        if (listener!=null){
            listener.onTransferDataToOtherPeer(new DataModel(
                    target,username,gson.toJson(iceCandidate),DataModelType.IceCandidate
            ));
        }
    }

    public void switchCamera() {
        videoCapturer.switchCamera(null);
    }

    public void toggleVideo(Boolean shouldBeMuted){
        localVideoTrack.setEnabled(shouldBeMuted);
    }

    public void toggleAudio(Boolean shouldBeMuted){
        localAudioTrack.setEnabled(shouldBeMuted);
    }

    public void closeConnection(){
        if (isDisposed) {
            Log.d("WebRTCClient", "Already disposed, skipping cleanup");
            return;
        }
        
        try{
            Log.d("WebRTCClient", "🧹 Starting cleanup...");
            
            // ✅ IMPORTANT: Remove tracks from stream BEFORE disposing them
            if (localStream != null) {
                if (localVideoTrack != null) {
                    localStream.removeTrack(localVideoTrack);
                }
                if (localAudioTrack != null) {
                    localStream.removeTrack(localAudioTrack);
                }
            }
            
            // ✅ Dispose video track with null check
            if (localVideoTrack != null) {
                localVideoTrack.setEnabled(false);
                localVideoTrack.dispose();
                localVideoTrack = null;
                Log.d("WebRTCClient", "✅ Video track disposed");
            }
            
            // ✅ Dispose audio track with null check
            if (localAudioTrack != null) {
                localAudioTrack.setEnabled(false);
                localAudioTrack.dispose();
                localAudioTrack = null;
                Log.d("WebRTCClient", "✅ Audio track disposed");
            }
            
            // ✅ Stop and dispose video capturer
            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture();
                    videoCapturer.dispose();
                    videoCapturer = null;
                    Log.d("WebRTCClient", "✅ Video capturer stopped and disposed");
                } catch (InterruptedException e) {
                    Log.e("WebRTCClient", "Error stopping capturer", e);
                    e.printStackTrace();
                }
            }
            
            // ✅ NOW dispose local stream (after tracks are removed)
            if (localStream != null) {
                localStream.dispose();
                localStream = null;
                Log.d("WebRTCClient", "✅ Local stream disposed");
            }
            
            // ✅ Close peer connection
            if (peerConnection != null) {
                peerConnection.close();
                peerConnection.dispose();
                peerConnection = null;
                Log.d("WebRTCClient", "✅ Peer connection closed");
            }
            
            // ✅ Dispose sources
            if (localVideoSource != null) {
                localVideoSource.dispose();
                localVideoSource = null;
            }
            
            if (localAudioSource != null) {
                localAudioSource.dispose();
                localAudioSource = null;
            }
            
            isDisposed = true;
            Log.d("WebRTCClient", "✅ Cleanup complete");
            
        }catch (Exception e){
            Log.e("WebRTCClient", "❌ Error during cleanup", e);
            e.printStackTrace();
        }
    }

    public interface Listener {
        void onTransferDataToOtherPeer(DataModel model);
    }
}