package com.example.project_ez_talk.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtcCertificatePem;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ✅ Complete WebRTC Engine for AUDIO CALLS
 * Handles:
 * - PeerConnection setup
 * - Microphone audio capture
 * - Audio transmission
 * - Audio reception
 * - NAT traversal (TURN servers)
 * - Echo cancellation
 */
public class WebRtcAudioEngine {

    private static final String TAG = "WebRtcAudioEngine";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String AUDIO_SOURCE_ID = "ARDAMSa0";

    // WebRTC Factory & Peer Connection
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private SurfaceTextureHelper surfaceTextureHelper;

    // Callbacks
    private WebRtcListener listener;
    private MyPeerConnectionObserver peerConnectionObserver;

    // STUN/TURN servers for NAT traversal
    private static final List<PeerConnection.IceServer> ICE_SERVERS = new ArrayList<>();

    static {
        // Public STUN servers (free, no auth needed)
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer());

        // Public TURN server (coturn)
        ICE_SERVERS.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
        );
        ICE_SERVERS.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
        );
        ICE_SERVERS.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
        );
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isAudioEnabled = true;

    public interface WebRtcListener {
        void onLocalSdpGenerated(SessionDescription sdp);
        void onIceCandidate(IceCandidate candidate);
        void onRemoteStreamAdded(MediaStream mediaStream);
        void onConnectionStateChanged(String state);
        void onError(String error);
    }

    /**
     * ✅ Initialize WebRTC Engine
     */
    public void initialize(Context context, WebRtcListener listener) {
        Log.d(TAG, "🎤 Initializing WebRTC Audio Engine...");
        this.listener = listener;

        try {
            // Initialize PeerConnectionFactory
            PeerConnectionFactory.InitializationOptions initializationOptions =
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .setEnableInternalTracer(true)
                            .setFieldTrials("WebRTC-Audio-Processing/Enabled/")
                            .createInitializationOptions();

            PeerConnectionFactory.initialize(initializationOptions);

            // Create encoder/decoder factories
            DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                    EglBase.create().getEglBaseContext(),
                    true,
                    true
            );

            DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                    EglBase.create().getEglBaseContext()
            );

            // Create PeerConnectionFactory
            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory();

            Log.d(TAG, "✅ PeerConnectionFactory created");

            // Create audio source with echo cancellation
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("echoCancellation", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("noiseSuppression", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("autoGainControl", "true"));

            audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            Log.d(TAG, "✅ Audio Source created (with echo cancellation)");

            // Create local audio track
            localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
            localAudioTrack.setEnabled(true);
            Log.d(TAG, "✅ Local Audio Track created");

        } catch (Exception e) {
            Log.e(TAG, "❌ Initialization error: " + e.getMessage(), e);
            if (listener != null) {
                listener.onError("Failed to initialize WebRTC: " + e.getMessage());
            }
        }
    }

    /**
     * ✅ Create PeerConnection for audio/video calls
     */
    public void createPeerConnection(String peerId) {
        Log.d(TAG, "🔗 Creating PeerConnection for: " + peerId);

        try {
            // Peer connection observer with callbacks
            peerConnectionObserver = new MyPeerConnectionObserver(
                    new MyPeerConnectionObserver.OnPeerConnectionCallback() {
                        @Override
                        public void onIceCandidate(IceCandidate candidate) {
                            Log.d(TAG, "🎯 ICE Candidate generated");
                            if (listener != null) {
                                listener.onIceCandidate(candidate);
                            }
                        }

                        @Override
                        public void onMediaStreamAdded(MediaStream mediaStream) {
                            Log.d(TAG, "🔊 Remote media stream received!");
                            if (listener != null) {
                                listener.onRemoteStreamAdded(mediaStream);
                            }
                        }

                        @Override
                        public void onConnectionStateChange(PeerConnection.PeerConnectionState newState) {
                            Log.d(TAG, "🔌 Peer connection state: " + newState);
                            if (listener != null) {
                                listener.onConnectionStateChanged(newState.toString());
                            }
                        }

                        @Override
                        public void onTrackAdded(RtpReceiver receiver) {
                            Log.d(TAG, "🎵 Track added via RtpReceiver");
                            if (receiver != null && receiver.track() != null) {
                                Log.d(TAG, "   Track kind: " + receiver.track().kind());
                            }
                        }
                    }
            );

            // Create peer connection with ICE servers
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(ICE_SERVERS);
            rtcConfig.enableDscp = true;
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver);

            if (peerConnection == null) {
                throw new Exception("Failed to create PeerConnection");
            }

            Log.d(TAG, "✅ PeerConnection created");

            // Add local audio track to connection
            MediaStream audioStream = peerConnectionFactory.createLocalMediaStream(AUDIO_SOURCE_ID);
            audioStream.addTrack(localAudioTrack);
            peerConnection.addTrack(localAudioTrack);

            Log.d(TAG, "✅ Local audio track added to PeerConnection");

        } catch (Exception e) {
            Log.e(TAG, "❌ PeerConnection creation error: " + e.getMessage(), e);
            if (listener != null) {
                listener.onError("Failed to create peer connection: " + e.getMessage());
            }
        }
    }

    /**
     * ✅ Create SDP Offer (for initiating call)
     */
    public void createOffer() {
        Log.d(TAG, "📤 Creating SDP Offer...");

        if (peerConnection == null) {
            Log.e(TAG, "❌ PeerConnection is null!");
            return;
        }

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        executor.execute(() -> {
            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    Log.d(TAG, "✅ Offer created successfully");
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {}

                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "✅ Local description set");
                            if (listener != null) {
                                listener.onLocalSdpGenerated(sessionDescription);
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
                    if (listener != null) {
                        listener.onError("Failed to create offer: " + error);
                    }
                }

                @Override
                public void onSetFailure(String error) {}
            }, constraints);
        });
    }

    /**
     * ✅ Create SDP Answer (for accepting call)
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
                    Log.d(TAG, "✅ Answer created successfully");
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {}

                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "✅ Local description set");
                            if (listener != null) {
                                listener.onLocalSdpGenerated(sessionDescription);
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
                    if (listener != null) {
                        listener.onError("Failed to create answer: " + error);
                    }
                }

                @Override
                public void onSetFailure(String error) {}
            }, constraints);
        });
    }

    /**
     * ✅ Add remote SDP (Offer or Answer from peer)
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
     * ✅ Add ICE Candidate
     */
    public void addIceCandidate(IceCandidate candidate) {
        Log.d(TAG, "🎯 Adding ICE candidate");

        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    /**
     * ✅ Toggle audio (mute/unmute)
     */
    public void toggleAudio(boolean enabled) {
        Log.d(TAG, "🎤 Audio: " + (enabled ? "ENABLED" : "MUTED"));
        isAudioEnabled = enabled;

        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
        }
    }

    /**
     * ✅ Close connection and cleanup
     */
    public void close() {
        Log.d(TAG, "🔌 Closing WebRTC connection...");

        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.stopAecDump();
        }

        executor.shutdown();
        Log.d(TAG, "✅ WebRTC connection closed");
    }

    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }
}