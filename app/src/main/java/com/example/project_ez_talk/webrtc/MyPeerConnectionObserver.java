package com.example.project_ez_talk.webrtc;

import android.util.Log;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

/**
 * ✅ FIXED Observer - NOW HANDLES onAddTrack PROPERLY
 * This is called when REMOTE audio/video tracks arrive
 */
public class MyPeerConnectionObserver implements PeerConnection.Observer {

    private static final String TAG = "PeerConnectionObserver";
    private OnPeerConnectionCallback callback;

    public interface OnPeerConnectionCallback {
        void onIceCandidate(IceCandidate candidate);
        void onMediaStreamAdded(MediaStream mediaStream);
        void onConnectionStateChange(PeerConnection.PeerConnectionState newState);
        void onIceConnectionStateChange(PeerConnection.IceConnectionState iceConnectionState);
    }

    public MyPeerConnectionObserver(OnPeerConnectionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(TAG, "🔄 Signaling State: " + signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(TAG, "🌐 ICE Connection State: " + iceConnectionState);

        if (callback != null) {
            callback.onIceConnectionStateChange(iceConnectionState);
        }

        switch (iceConnectionState) {
            case NEW:
                Log.d(TAG, "   ℹ️ ICE gathering starting...");
                break;
            case CHECKING:
                Log.d(TAG, "   🔍 ICE candidates being checked...");
                break;
            case CONNECTED:
                Log.d(TAG, "   ✅ ICE connection established!");
                break;
            case COMPLETED:
                Log.d(TAG, "   ✅ ICE connection completed!");
                break;
            case FAILED:
                Log.e(TAG, "   ❌ ICE connection failed!");
                break;
            case DISCONNECTED:
                Log.w(TAG, "   ⚠️ ICE disconnected");
                break;
            case CLOSED:
                Log.d(TAG, "   🔌 ICE connection closed");
                break;
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean isReceiving) {
        Log.d(TAG, "📡 ICE Receiving: " + (isReceiving ? "YES" : "NO"));
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "📍 ICE Gathering State: " + iceGatheringState);
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        Log.d(TAG, "🎯 New ICE Candidate: " + iceCandidate.sdpMLineIndex + " / " + iceCandidate.sdpMid);
        if (callback != null) {
            callback.onIceCandidate(iceCandidate);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        Log.d(TAG, "🗑️ ICE Candidates Removed: " + iceCandidates.length);
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(TAG, "🔊 Remote Stream Added (OLD API)!");
        Log.d(TAG, "   Audio Tracks: " + mediaStream.audioTracks.size());
        Log.d(TAG, "   Video Tracks: " + mediaStream.videoTracks.size());

        // ✅ ENABLE ALL AUDIO TRACKS
        if (mediaStream.audioTracks.size() > 0) {
            for (AudioTrack audioTrack : mediaStream.audioTracks) {
                audioTrack.setEnabled(true);
                Log.d(TAG, "✅✅✅ Audio track ENABLED from onAddStream!");
            }
        }

        if (callback != null) {
            callback.onMediaStreamAdded(mediaStream);
        }
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(TAG, "🔇 Remote Stream Removed");
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.d(TAG, "📊 Data Channel Created: " + dataChannel.label());
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(TAG, "🔄 Renegotiation Needed");
    }

    /**
     * ✅ THIS IS THE KEY FIX!
     * onAddTrack is called when REMOTE audio/video arrives (Unified Plan SDP)
     */
    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d(TAG, "🎵 RTP Track Added!");

        String trackKind = rtpReceiver.track().kind();
        Log.d(TAG, "   Track Type: " + trackKind);
        Log.d(TAG, "   Streams: " + mediaStreams.length);

        // ✅ ENABLE AUDIO TRACKS WHEN THEY ARRIVE
        if ("audio".equals(trackKind)) {
            Log.d(TAG, "🔊 REMOTE AUDIO TRACK RECEIVED!");
            try {
                Object trackObj = rtpReceiver.track();
                if (trackObj instanceof AudioTrack) {
                    AudioTrack audioTrack = (AudioTrack) trackObj;
                    audioTrack.setEnabled(true);
                    Log.d(TAG, "✅✅✅ AUDIO ENABLED - SOUND SHOULD PLAY NOW!");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error enabling audio: " + e.getMessage(), e);
            }
        }

        if ("video".equals(trackKind)) {
            Log.d(TAG, "📹 REMOTE VIDEO TRACK RECEIVED!");
        }

        if (mediaStreams.length > 0 && callback != null) {
            callback.onMediaStreamAdded(mediaStreams[0]);
        }
    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        Log.d(TAG, "🔌 Connection State: " + newState);

        if (callback != null) {
            callback.onConnectionStateChange(newState);
        }

        switch (newState) {
            case NEW:
                Log.d(TAG, "   ℹ️ Connection initializing...");
                break;
            case CONNECTING:
                Log.d(TAG, "   🔗 Connecting...");
                break;
            case CONNECTED:
                Log.d(TAG, "   ✅ Connected!");
                break;
            case DISCONNECTED:
                Log.w(TAG, "   ⚠️ Disconnected");
                break;
            case FAILED:
                Log.e(TAG, "   ❌ Connection Failed!");
                break;
            case CLOSED:
                Log.d(TAG, "   🔌 Connection Closed");
                break;
        }
    }
}