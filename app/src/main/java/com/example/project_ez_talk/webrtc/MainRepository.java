package com.example.project_ez_talk.webrtc;

import android.content.Context;
import android.util.Log;


import com.google.gson.Gson;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

/**
 * ✅ COMPLETE & CORRECTED MainRepository
 * Central manager for WebRTC calls with proper error handling
 * Coordinates between Firebase signaling and WebRTC client
 * Uses singleton pattern for application-wide access
 */
public class MainRepository {
    private static final String TAG = "MainRepository";
    private static MainRepository instance;

    private WebRTCClient webRTCClient;
    private final FirebaseSignalingClient firebaseClient;
    private String currentUsername;
    private String currentUserId;
    private String target;
    private SurfaceViewRenderer remoteView;
    private Gson gson = new Gson();

    // ✅ NEW: Track initialization state and pending offer
    private boolean isInitialized = false;
    private DataModel pendingOffer = null;
    private Context context;  // ← ADD THIS
    public RepositoryListener repositoryListener;
    private Object callBack;

    public void loginForCall(String currentUserId, CallCallback callCallback) {

    }

    /**
     * Listener for repository events
     */
    public interface RepositoryListener {
        void onCallConnected();
        void onCallEnded();
        void onRemoteStreamAdded(MediaStream mediaStream);
    }

    /**
     * Private constructor for singleton pattern
     */
    private MainRepository() {
        this.firebaseClient = new FirebaseSignalingClient();
        Log.d(TAG, "✅ MainRepository singleton created");
    }

    /**
     * Get singleton instance
     */
    public static synchronized MainRepository getInstance() {
        if (instance == null) {
            instance = new MainRepository();
        }
        return instance;
    }

    /**
     * ✅ FIXED: Login with proper WebRTC initialization and detailed logging
     */
    public void login(String userId, String username, Context context, Runnable onSuccess) {
        this.currentUserId = userId;
        this.currentUsername = username;

        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "🔐 LOGGING IN USER");
        Log.d(TAG, "   Name: " + username);
        Log.d(TAG, "   ID: " + userId);
        Log.d(TAG, "═══════════════════════════════════════");

        firebaseClient.login(userId, () -> {
            Log.d(TAG, "✅ Firebase login successful");

            try {
                Log.d(TAG, "🎤 Creating WebRTCClient...");

                // Initialize WebRTC with proper observer
                webRTCClient = new WebRTCClient(context, new MyPeerConnectionObserver(
                        new MyPeerConnectionObserver.OnPeerConnectionCallback() {
                            @Override
                            public void onIceCandidate(IceCandidate iceCandidate) {
                                Log.d(TAG, "🎯 ICE Candidate generated");
                                Log.d(TAG, "   sdpMid: " + iceCandidate.sdpMid);
                                Log.d(TAG, "   sdpMLineIndex: " + iceCandidate.sdpMLineIndex);
                                sendIceCandidateToFirebase(iceCandidate);
                            }

                            @Override
                            public void onMediaStreamAdded(MediaStream mediaStream) {
                                Log.d(TAG, "═══════════════════════════════════════");
                                Log.d(TAG, "📊 REMOTE MEDIA STREAM ADDED!");
                                Log.d(TAG, "   Audio tracks: " + mediaStream.audioTracks.size());
                                Log.d(TAG, "   Video tracks: " + mediaStream.videoTracks.size());
                                Log.d(TAG, "═══════════════════════════════════════");

                                // Add video track to surface if available
                                if (mediaStream.videoTracks.size() > 0 && remoteView != null) {
                                    try {
                                        mediaStream.videoTracks.get(0).addSink(remoteView);
                                        Log.d(TAG, "✅ Video track added to surface");
                                    } catch (Exception e) {
                                        Log.e(TAG, "❌ Error adding video sink: " + e.getMessage(), e);
                                    }
                                }

                                // Notify listener
                                if (repositoryListener != null) {
                                    repositoryListener.onRemoteStreamAdded(mediaStream);
                                }
                            }

                            @Override
                            public void onConnectionStateChange(PeerConnection.PeerConnectionState newState) {
                                Log.d(TAG, "🔌 Peer Connection State: " + newState);

                                // Call connected
                                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                                    Log.d(TAG, "═══════════════════════════════════════");
                                    Log.d(TAG, "✅ CALL CONNECTED!");
                                    Log.d(TAG, "   Audio should now be flowing");
                                    Log.d(TAG, "═══════════════════════════════════════");

                                    if (repositoryListener != null) {
                                        repositoryListener.onCallConnected();
                                    }
                                }

                                // Call disconnected or closed
                                if (newState == PeerConnection.PeerConnectionState.CLOSED ||
                                        newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                                    Log.d(TAG, "📞 Call disconnected/closed");
                                    if (repositoryListener != null) {
                                        repositoryListener.onCallEnded();
                                    }
                                }

                                // Call failed
                                if (newState == PeerConnection.PeerConnectionState.FAILED) {
                                    Log.e(TAG, "❌ Call failed!");
                                    if (repositoryListener != null) {
                                        repositoryListener.onCallEnded();
                                    }
                                }
                            }

                            @Override
                            public void onIceConnectionStateChange(
                                    PeerConnection.IceConnectionState iceConnectionState) {
                                Log.d(TAG, "🌐 ICE Connection State: " + iceConnectionState);
                            }
                        }
                ));

                Log.d(TAG, "✅ WebRTCClient created");

                // ✅ CRITICAL: Set WebRTC listener to send SDP to Firebase
                Log.d(TAG, "🔗 Setting WebRTC listener...");
                webRTCClient.setWebRtcListener(new WebRTCClient.WebRtcListener() {
                    @Override
                    public void onLocalSdpGenerated(SessionDescription sdp) {
                        Log.d(TAG, "═══════════════════════════════════════");
                        Log.d(TAG, "📤 LOCAL SDP GENERATED!");
                        Log.d(TAG, "   Type: " + sdp.type);
                        Log.d(TAG, "   Description length: " + sdp.description.length());
                        Log.d(TAG, "═══════════════════════════════════════");
                        sendSdpToFirebase(sdp);
                    }

                    @Override
                    public void onIceCandidateGenerated(IceCandidate candidate) {
                        Log.d(TAG, "🎯 ICE candidate generated (from listener)");
                        sendIceCandidateToFirebase(candidate);
                    }
                });

                Log.d(TAG, "✅ WebRTC listener set");

                // ✅ CRITICAL: Create PeerConnection
                Log.d(TAG, "🔗 Creating PeerConnection...");
                webRTCClient.createPeerConnection();
                Log.d(TAG, "✅ PeerConnection created");

                // ✅ NEW: Mark as initialized
                isInitialized = true;
                Log.d(TAG, "✅ WebRTC marked as initialized");

                // ✅ NEW: Process pending offer if one arrived early
                if (pendingOffer != null) {
                    Log.d(TAG, "📥 Processing pending offer that arrived early");
                    handleOfferReceived(pendingOffer);
                    pendingOffer = null;
                }

                Log.d(TAG, "═══════════════════════════════════════");
                Log.d(TAG, "✅ LOGIN COMPLETE");
                Log.d(TAG, "   WebRTC ready for calls");
                Log.d(TAG, "═══════════════════════════════════════");

                // Call success callback
                onSuccess.run();

            } catch (Exception e) {
                Log.e(TAG, "❌ Error during WebRTC initialization: " + e.getMessage(), e);
            }
        });
    }

    /**
     * ✅ Send SDP (Offer/Answer) to Firebase
     */
    private void sendSdpToFirebase(SessionDescription sdp) {
        if (firebaseClient == null) {
            Log.e(TAG, "❌ Cannot send SDP - Firebase client is null");
            return;
        }

        if (target == null || target.isEmpty()) {
            Log.e(TAG, "❌ Cannot send SDP - Target not set");
            return;
        }

        DataModelType type = (sdp.type == SessionDescription.Type.OFFER)
                ? DataModelType.OFFER
                : DataModelType.ANSWER;

        DataModel dataModel = new DataModel(
                target,
                currentUserId,
                sdp.description,
                type
        );

        Log.d(TAG, "📤 Sending SDP to Firebase...");
        Log.d(TAG, "   Type: " + type);
        Log.d(TAG, "   Target: " + target);
        Log.d(TAG, "   Sender: " + currentUserId);

        firebaseClient.sendMessageToOtherUser(dataModel);

        Log.d(TAG, "✅ SDP sent to Firebase successfully");
    }

    /**
     * ✅ Send ICE candidate to Firebase
     */
    private void sendIceCandidateToFirebase(IceCandidate candidate) {
        if (firebaseClient == null) {
            Log.w(TAG, "⚠️ Cannot send ICE - Firebase client is null");
            return;
        }

        if (target == null || target.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot send ICE - Target not set");
            return;
        }

        try {
            // Wrap IceCandidate in serializable model
            IceCandidateModel model = new IceCandidateModel(candidate);
            String candidateJson = gson.toJson(model);

            DataModel dataModel = new DataModel(
                    target,
                    currentUserId,
                    candidateJson,
                    DataModelType.ICE_CANDIDATE
            );

            Log.d(TAG, "📤 Sending ICE candidate to Firebase...");
            Log.d(TAG, "   Target: " + target);

            firebaseClient.sendMessageToOtherUser(dataModel);

            Log.d(TAG, "✅ ICE candidate sent to Firebase");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending ICE candidate: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize local video view (for video calls)
     */
    public void initLocalView(SurfaceViewRenderer view) {
        if (webRTCClient != null) {
            // For video calls, initialize video surface
            // For audio-only, this does nothing
            Log.d(TAG, "Local view initialized");
        }
    }

    /**
     * Initialize remote video view
     */
    public void initRemoteView(SurfaceViewRenderer view) {
        this.remoteView = view;
        Log.d(TAG, "Remote video view initialized");
    }

    /**
     * ✅ Start outgoing call - creates SDP offer
     */
    public void startCall(String targetUserId) {
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📞 STARTING CALL");
        Log.d(TAG, "   Target: " + targetUserId);
        Log.d(TAG, "═══════════════════════════════════════");

        // ✅ Validation: Check if targetUserId is valid
        if (targetUserId == null || targetUserId.isEmpty()) {
            Log.e(TAG, "❌ Invalid target user ID");
            if (repositoryListener != null) {
                repositoryListener.onCallEnded();
            }
            return;
        }

        // ✅ Validation: Check if already in a call
        if (this.target != null && !this.target.isEmpty()) {
            Log.w(TAG, "⚠️ Already in a call with: " + this.target);
            return;
        }

        // ✅ Store target user ID
        this.target = targetUserId;
        Log.d(TAG, "✅ Target stored: " + this.target);

        // ✅ Check if WebRTC client exists
        if (webRTCClient != null) {
            Log.d(TAG, "📤 Creating SDP offer...");
            webRTCClient.createOffer();
        } else {
            Log.e(TAG, "❌ WebRTCClient is null! Cannot start call.");

            // Notify listener about error
            if (repositoryListener != null) {
                repositoryListener.onCallEnded();
            }

            // Clear target since call failed
            this.target = null;
        }
    }

    /**
     * Send initial call request notification
     */
    public void sendCallRequest(String targetUserId, Runnable onError) {

        this.target = targetUserId;

        Log.d(TAG, "📱 Sending call request to: " + targetUserId);

        if (firebaseClient != null) {
            firebaseClient.sendMessageToOtherUser(
                    new DataModel(targetUserId, currentUserId, null, DataModelType.START_CALL)
            );

            Log.d(TAG, "✅ Call request sent");
        } else {
            Log.e(TAG, "❌ Firebase client is null");
            if (onError != null) {
                onError.run();
            }
        }
    }

    /**
     * Switch between front and back camera
     */
    public void switchCamera() {
        if (webRTCClient != null) {
            try {
                webRTCClient.switchCamera();
                Log.d(TAG, "📷 Camera switched");
            } catch (Exception e) {
                Log.e(TAG, "switchCamera not supported: " + e.getMessage());
            }
        }
    }

    /**
     * Toggle audio on/off
     */
    public void toggleAudio(Boolean shouldBeOn) {
        if (webRTCClient != null) {
            webRTCClient.toggleAudio(shouldBeOn);
            Log.d(TAG, "🎤 Audio: " + (shouldBeOn ? "ON" : "OFF"));
        }
    }

    /**
     * Toggle video on/off
     */
    public void toggleVideo(Boolean shouldBeOn) {
        if (webRTCClient != null) {
            try {
                webRTCClient.toggleVideo(shouldBeOn);
                Log.d(TAG, "📹 Video: " + (shouldBeOn ? "ON" : "OFF"));
            } catch (Exception e) {
                Log.e(TAG, "toggleVideo not supported: " + e.getMessage());
            }
        }
    }

    /**
     * End call and cleanup
     */
    public void endCall() {
        Log.d(TAG, "📞 Ending call...");

        if (webRTCClient != null) {
            webRTCClient.close();
            Log.d(TAG, "✅ WebRTC connection closed");
        }
    }

    /**
     * ✅ Subscribe to Firebase signaling events (SDP & ICE)
     */
    public void subscribeForLatestEvent(NewEventCallBack callBack) {
        if (firebaseClient == null) {
            Log.e(TAG, "❌ Firebase client not initialized");
            return;
        }

        Log.d(TAG, "👂 Subscribing to signaling events...");

        firebaseClient.observeIncomingEvents(model -> {
            if (model == null) {
                Log.w(TAG, "⚠️ Received null signaling model");
                return;
            }

            DataModelType type = model.getType();
            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "📨 SIGNALING EVENT RECEIVED");
            Log.d(TAG, "   Type: " + type);
            Log.d(TAG, "   From: " + model.getSender());
            Log.d(TAG, "═══════════════════════════════════════");

            switch (model.getType()) {
                case OFFER:
                    handleOfferReceived(model);
                    break;

                case ANSWER:
                    handleAnswerReceived(model);
                    break;

                case ICE_CANDIDATE:
                    handleIceCandidateReceived(model);
                    break;


                case START_CALL:
                    // ✅ This is the important one!
                    this.target = model.getSender();
                    callBack.onNewEventReceived(model); // 📞 Notify the Activity!
                    break;
                case ACCEPT:
                    Log.d(TAG, "📞 Call accepted (waiting for ANSWER)");
                    // DO NOTHING ELSE
                    break;


                case END:
                    Log.d(TAG, "🔴 Call ended by remote peer");
                    if (repositoryListener != null) {
                        repositoryListener.onCallEnded();
                    }
                    break;

                case REJECT:
                    Log.d(TAG, "❌ Call rejected by remote peer");
                    if (repositoryListener != null) {
                        repositoryListener.onCallEnded();
                    }
                    break;
            }
        });

        Log.d(TAG, "✅ Subscribed to signaling events");
    }

    /**
     * ✅ Handle incoming SDP Offer (with safety check)
     */
    private void handleOfferReceived(DataModel model) {
        this.target = model.getSender();

        try {
            Log.d(TAG, "📥 Processing OFFER from: " + model.getSender());

            // ✅ SAFETY CHECK: If WebRTC not ready yet, queue the offer
            if (!isInitialized || webRTCClient == null) {
                Log.w(TAG, "⚠️ WebRTC not ready yet - queuing offer");
                pendingOffer = model;
                return;
            }

            SessionDescription offer = new SessionDescription(
                    SessionDescription.Type.OFFER,
                    model.getData()
            );

            webRTCClient.addRemoteSdp(offer);
            Log.d(TAG, "✅ Remote SDP (offer) set");

            Log.d(TAG, "📤 Creating answer...");
            webRTCClient.createAnswer();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling offer: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Handle incoming SDP Answer (with safety check)
     */
    private void handleAnswerReceived(DataModel model) {
        this.target = model.getSender();

        try {
            Log.d(TAG, "📥 Processing ANSWER from: " + model.getSender());

            // ✅ SAFETY CHECK
            if (!isInitialized || webRTCClient == null) {
                Log.e(TAG, "❌ WebRTCClient not initialized - cannot process answer");
                return;
            }

            SessionDescription answer = new SessionDescription(
                    SessionDescription.Type.ANSWER,
                    model.getData()
            );

            webRTCClient.addRemoteSdp(answer);
            Log.d(TAG, "✅ Remote SDP (answer) set");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling answer: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Handle incoming ICE Candidate (with safety check)
     */
    private void handleIceCandidateReceived(DataModel model) {
        try {
            Log.d(TAG, "📥 Processing ICE candidate from: " + model.getSender());

            // ✅ SAFETY CHECK
            if (!isInitialized || webRTCClient == null) {
                Log.w(TAG, "⚠️ WebRTC not ready - ignoring ICE candidate");
                return;
            }

            String candidateJson = model.getData();
            IceCandidateModel iceCandidateModel = gson.fromJson(
                    candidateJson,
                    IceCandidateModel.class
            );

            IceCandidate candidate = iceCandidateModel.toIceCandidate();
            webRTCClient.addIceCandidate(candidate);
            Log.d(TAG, "✅ ICE candidate added");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing ICE candidate: " + e.getMessage(), e);
        }
    }

    /**
     * Handle incoming call request
     */
    private void handleStartCallReceived(DataModel model, SignalingEventListener listener) {
        Log.d(TAG, "📱 Incoming call request from: " + model.getSender());

        if (listener != null) {
            listener.onCallRequest(model);
        }
    }

    /**
     * Listener for signaling events
     */
    public interface SignalingEventListener {
        void onCallRequest(DataModel model);
    }

    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Get current username
     */
    public String getCurrentUsername() {
        return currentUsername;
    }
    public interface NewEventCallBack {
        void onNewEventReceived(DataModel model);
    }
    /**
     * Check if WebRTC is initialized
     */
    public boolean isInitialized() {
        return isInitialized && webRTCClient != null;
    }

    public interface CallCallback {
        void onSuccess();

        void onError();
    }
}