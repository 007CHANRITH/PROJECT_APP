package com.example.project_ez_talk.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ✅ FIXED WebRTCClient - Now properly sends SDP offers/answers to Firebase
 */
public class WebRTCClient {

    private static final String TAG = "WebRTCClient";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String MEDIA_STREAM_ID = "stream0";

    // WebRTC Components
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack videoTrack;

    // Observer for callbacks
    private MyPeerConnectionObserver peerConnectionObserver;

    // ✅ NEW: Listener for SDP and ICE callbacks
    private WebRtcListener webRtcListener;

    // ICE Servers
    private static final List<PeerConnection.IceServer> ICE_SERVERS = new ArrayList<>();

    static {
        // ✅ Primary STUN servers (Google - Very reliable)
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer());

        // ✅ TURN servers (Multiple ports for maximum reliability)
        // Port 80 - Works in most corporate networks
        ICE_SERVERS.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
        );

        // Port 443 - Works even in restrictive networks (HTTPS port)
        ICE_SERVERS.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
        );

        // TCP on port 443 - Ultra-restrictive network fallback
        ICE_SERVERS.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
        );
    }
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isAudioEnabled = true;

    public void onRemoteSessionReceived(SessionDescription sessionDescription) {
        Log.d(TAG, "📨 Remote SDP received: " + sessionDescription.type);

        if (peerConnection == null) {
            Log.e(TAG, "❌ PeerConnection is null! Cannot set remote SDP");
            return;
        }

        addRemoteSdp(sessionDescription);
    }

    public void answer(String sender) {
        Log.d(TAG, "📤 Creating ANSWER for sender: " + sender);

        if (peerConnection == null) {
            Log.e(TAG, "❌ PeerConnection is null! Cannot answer");
            return;
        }

        createAnswer();
    }


    /**
     * ✅ NEW: Listener interface for SDP and ICE callbacks
     */
    public interface WebRtcListener {
        void onLocalSdpGenerated(SessionDescription sdp);
        void onIceCandidateGenerated(IceCandidate candidate);
    }

    /**
     * Constructor
     */
    public WebRTCClient(Context context, MyPeerConnectionObserver observer) {
        Log.d(TAG, "🎤 Initializing WebRTCClient...");
        this.peerConnectionObserver = observer;
        initializePeerConnectionFactory(context);
    }

    /**
     * ✅ NEW: Set listener for SDP/ICE callbacks
     */
    public void setWebRtcListener(WebRtcListener listener) {
        this.webRtcListener = listener;
        Log.d(TAG, "✅ WebRtcListener set");
    }

    /**
     * Initialize PeerConnectionFactory
     */
    private void initializePeerConnectionFactory(Context context) {
        try {
            PeerConnectionFactory.InitializationOptions initializationOptions =
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .setEnableInternalTracer(true)
                            .setFieldTrials("WebRTC-Audio-Processing/Enabled/")
                            .createInitializationOptions();

            PeerConnectionFactory.initialize(initializationOptions);

            EglBase eglBase = EglBase.create();
            DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                    eglBase.getEglBaseContext(), true, true
            );

            DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                    eglBase.getEglBaseContext()
            );

            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory();

            Log.d(TAG, "✅ PeerConnectionFactory initialized");

        } catch (Exception e) {
            Log.e(TAG, "❌ Factory initialization error: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Create PeerConnection
     */
    public void createPeerConnection() {
        Log.d(TAG, "🔗 Creating PeerConnection...");

        try {
            // Create audio source with echo cancellation
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("echoCancellation", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("noiseSuppression", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("autoGainControl", "true"));

            audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
            audioTrack.setEnabled(true);

            Log.d(TAG, "✅ Audio source and track created");

            // Create PeerConnection with ICE servers
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(ICE_SERVERS);
            rtcConfig.enableDscp = true;
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver);

            if (peerConnection == null) {
                throw new Exception("Failed to create PeerConnection");
            }

            Log.d(TAG, "✅ PeerConnection created");

            // Add audio track to connection
            peerConnection.addTrack(audioTrack);
            Log.d(TAG, "✅ Audio track added to PeerConnection");

        } catch (Exception e) {
            Log.e(TAG, "❌ PeerConnection creation error: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ FIXED: Create SDP Offer - Now sends to Firebase via listener
     */
    public void createOffer() {
        Log.d(TAG, "📤 Creating SDP Offer...");


        if (peerConnection == null) {
            Log.e(TAG, "❌ PeerConnection is null!");
            return;
        }

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        );
        constraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")
        );

        executor.execute(() -> {

            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    Log.d(TAG, "✅ Offer created");

                    // Set local description first
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {}

                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "✅ Local description set");

                            // ✅ NOW SEND TO FIREBASE via listener
                            if (webRtcListener != null) {
                                webRtcListener.onLocalSdpGenerated(sessionDescription);
                                Log.d(TAG, "📤 Offer sent to Firebase via listener");
                            } else {
                                Log.e(TAG, "❌ WebRtcListener is null! Cannot send offer");
                            }
                        }

                        @Override
                        public void onCreateFailure(String error) {
                            Log.e(TAG, "❌ Set local description failed: " + error);
                        }

                        @Override
                        public void onSetFailure(String error) {
                            Log.e(TAG, "❌ Set local description failed: " + error);
                        }
                    }, sessionDescription);
                }

                @Override
                public void onSetSuccess() {}

                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "❌ Create offer failed: " + error);
                }

                @Override
                public void onSetFailure(String error) {}
            }, constraints);
        });
    }

    /**
     * ✅ FIXED: Create SDP Answer - Now sends to Firebase via listener
     */
    public void createAnswer() {
        Log.d(TAG, "📥 Creating SDP Answer...");

        if (peerConnection == null) {
            Log.e(TAG, "❌ PeerConnection is null!");
            return;
        }

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        executor.execute(() -> {
            peerConnection.createAnswer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    Log.d(TAG, "✅ Answer created");

                    // Set local description first
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {}

                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "✅ Local description set");

                            // ✅ NOW SEND TO FIREBASE via listener
                            if (webRtcListener != null) {
                                webRtcListener.onLocalSdpGenerated(sessionDescription);
                                Log.d(TAG, "📤 Answer sent to Firebase via listener");
                            } else {
                                Log.e(TAG, "❌ WebRtcListener is null! Cannot send answer");
                            }
                        }

                        @Override
                        public void onCreateFailure(String error) {
                            Log.e(TAG, "❌ Set local description failed: " + error);
                        }

                        @Override
                        public void onSetFailure(String error) {
                            Log.e(TAG, "❌ Set local description failed: " + error);
                        }
                    }, sessionDescription);
                }

                @Override
                public void onSetSuccess() {}

                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "❌ Create answer failed: " + error);
                }

                @Override
                public void onSetFailure(String error) {}
            }, constraints);
        });
    }

    /**
     * Add remote SDP
     */
    public void addRemoteSdp(SessionDescription sdp) {
        Log.d(TAG, "📨 Adding remote SDP: " + sdp.type);

        if (peerConnection == null) {
            Log.e(TAG, "❌ PeerConnection is null!");
            return;
        }

        executor.execute(() -> {
            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {}

                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "✅ Remote SDP set successfully");
                }

                @Override
                public void onCreateFailure(String error) {}

                @Override
                public void onSetFailure(String error) {
                    Log.e(TAG, "❌ Set remote description failed: " + error);
                }
            }, sdp);
        });
    }

    /**
     * Add ICE Candidate
     */
    public void addIceCandidate(IceCandidate candidate) {
        Log.d(TAG, "🎯 Adding ICE candidate");
        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    /**
     * ✅ Send ICE candidate via listener
     */
    public void sendIceCandidate(IceCandidate candidate, String target) {
        Log.d(TAG, "📤 Sending ICE candidate");

        // Add to local peer connection
        addIceCandidate(candidate);

        // Send to remote peer via listener
        if (webRtcListener != null) {
            webRtcListener.onIceCandidateGenerated(candidate);
        }
    }

    /**
     * Toggle audio
     */
    public void toggleAudio(boolean enabled) {
        Log.d(TAG, "🎤 Audio: " + (enabled ? "ENABLED" : "MUTED"));
        isAudioEnabled = enabled;

        if (audioTrack != null) {
            audioTrack.setEnabled(enabled);
        }
    }

    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }

    /**
     * Close connection
     */
    public void close() {
        Log.d(TAG, "🔌 Closing WebRTC connection...");

        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (videoTrack != null) {
            videoTrack.dispose();
            videoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        Log.d(TAG, "✅ WebRTC connection closed");
    }

    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    public PeerConnectionFactory getPeerConnectionFactory() {
        return peerConnectionFactory;
    }

    /**
     * ✅ Initialize local video view for rendering
     */
    public void initLocalView(Object localView) {
        Log.d(TAG, "📱 Initializing local view...");
        // SurfaceViewRenderer binding handled by MainRepository
    }

    /**
     * ✅ Initialize remote video view for rendering
     */
    public void initRemoteView(Object remoteView) {
        Log.d(TAG, "📱 Initializing remote view...");
        // SurfaceViewRenderer binding handled by MainRepository
    }

    /**
     * ✅ Switch camera (front/back) - Currently not implemented
     */
    public void switchCamera() {
        Log.d(TAG, "📷 Camera switch requested (not yet implemented)");
    }

    /**
     * ✅ Toggle video on/off
     */
    public void toggleVideo(Boolean shouldBeOn) {
        if (videoTrack != null) {
            videoTrack.setEnabled(shouldBeOn);
            Log.d(TAG, "📹 Video toggled: " + (shouldBeOn ? "ON" : "OFF"));
        } else {
            Log.w(TAG, "⚠️ Video track not available");
        }
    }

    public interface Listener {}
}