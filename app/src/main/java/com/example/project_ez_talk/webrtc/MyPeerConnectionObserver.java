package com.example.project_ez_talk.webrtc;

import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

public class MyPeerConnectionObserver implements PeerConnection.Observer {

    private OnPeerConnectionCallback callback;

    public MyPeerConnectionObserver() {
    }

    public MyPeerConnectionObserver(OnPeerConnectionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        if (callback != null) {
            callback.onIceCandidate(iceCandidate);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d("MyPeerObserver", "🎥 onAddStream called! Video tracks: " + mediaStream.videoTracks.size() + ", Audio tracks: " + mediaStream.audioTracks.size());
        if (callback != null) {
            callback.onMediaStreamAdded(mediaStream);
        }
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {

    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d("MyPeerObserver", "🔥 onAddTrack called! MediaStreams: " + mediaStreams.length);
        Log.d("MyPeerObserver", "   RtpReceiver track: " + (rtpReceiver != null && rtpReceiver.track() != null ? rtpReceiver.track().kind() : "null"));
        if (callback != null) {
            // With addTrack API, we need to create a MediaStream and add the track
            if (rtpReceiver != null && rtpReceiver.track() != null) {
                // Pass the receiver to callback for proper handling
                callback.onTrackAdded(rtpReceiver);
            } else if (mediaStreams.length > 0) {
                callback.onMediaStreamAdded(mediaStreams[0]);
            }
        }
    }

    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        if (callback != null) {
            callback.onConnectionStateChange(newState);
        }
    }

    public interface OnPeerConnectionCallback {
        void onIceCandidate(IceCandidate candidate);
        void onMediaStreamAdded(MediaStream mediaStream);
        void onConnectionStateChange(PeerConnection.PeerConnectionState newState);
        void onTrackAdded(RtpReceiver receiver);
    }
}