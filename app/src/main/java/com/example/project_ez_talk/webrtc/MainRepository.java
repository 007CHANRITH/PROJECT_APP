package com.example.project_ez_talk.webrtc;

import android.content.Context;
import android.util.Log;

import com.example.project_ez_talk.utils.ErrorCallBack;
import com.example.project_ez_talk.utils.NewEventCallBack;
import com.example.project_ez_talk.utils.SuccessCallBack;
import com.google.gson.Gson;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

public class MainRepository implements WebRTCClient.Listener {

    public ConnectionListener listener;
    public RepositoryListener repositoryListener;
    private final Gson gson = new Gson();
    private final FirebaseClient firebaseClient;

    private WebRTCClient webRTCClient;

    private String currentUsername;

    private SurfaceViewRenderer remoteView;

    private String target;

    private void updateCurrentUsername(String username){
        this.currentUsername = username;
    }

    private MainRepository(){
        this.firebaseClient = new FirebaseClient();
    }

    private static MainRepository instance;

    public static MainRepository getInstance(){
        if (instance == null){
            instance = new MainRepository();
        }
        return instance;
    }

    public void loginForCall(String username, CallCallback callBack){
        firebaseClient.login(username,()->{
            updateCurrentUsername(username);
            callBack.onSuccess();
        });
    }

    public void login(String username, String displayName, Context context, SuccessCallBack callBack){
        firebaseClient.login(username,()->{
            updateCurrentUsername(username);
            this.webRTCClient = new WebRTCClient(context,new MyPeerConnectionObserver(){
                @Override
                public void onAddStream(MediaStream mediaStream) {
                    super.onAddStream(mediaStream);
                    Log.d("MainRepository", "📺 onAddStream - Adding remote video to view");
                    try{
                        if (mediaStream.videoTracks.size() > 0) {
                            VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                            remoteVideoTrack.setEnabled(true);
                            remoteVideoTrack.addSink(remoteView);
                            Log.d("MainRepository", "✅ Video track added to remoteView");
                        }
                        if (mediaStream.audioTracks.size() > 0) {
                            mediaStream.audioTracks.get(0).setEnabled(true);
                            Log.d("MainRepository", "✅ Audio track enabled");
                        }
                    }catch (Exception e){
                        Log.e("MainRepository", "❌ Error adding remote video", e);
                        e.printStackTrace();
                    }
                    if (repositoryListener!=null){
                        repositoryListener.onRemoteStreamAdded(mediaStream);
                    }
                }

                // ✅ Handle onAddTrack for modern WebRTC API
                @Override
                public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                    super.onAddTrack(rtpReceiver, mediaStreams);
                    Log.d("MainRepository", "🔥 onAddTrack called!");
                    
                    if (rtpReceiver == null || rtpReceiver.track() == null) {
                        Log.e("MainRepository", "❌ RtpReceiver or track is null");
                        return;
                    }
                    
                    try {
                        String trackKind = rtpReceiver.track().kind();
                        Log.d("MainRepository", "   Track kind: " + trackKind);
                        
                        if ("video".equals(trackKind)) {
                            VideoTrack remoteVideoTrack = (VideoTrack) rtpReceiver.track();
                            remoteVideoTrack.setEnabled(true);
                            
                            if (remoteView != null) {
                                remoteVideoTrack.addSink(remoteView);
                                Log.d("MainRepository", "✅ Remote video track added to view");
                            } else {
                                Log.e("MainRepository", "❌ remoteView is null!");
                            }
                        } else if ("audio".equals(trackKind)) {
                            rtpReceiver.track().setEnabled(true);
                            Log.d("MainRepository", "✅ Remote audio track enabled");
                        }
                        
                        // Also call repository listener if available
                        if (repositoryListener != null && mediaStreams.length > 0) {
                            repositoryListener.onRemoteStreamAdded(mediaStreams[0]);
                        }
                        
                    } catch (Exception e) {
                        Log.e("MainRepository", "❌ Error handling remote track", e);
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                    Log.d("TAG", "onConnectionChange: "+newState);
                    super.onConnectionChange(newState);
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED){
                        if (listener!=null){
                            listener.webrtcConnected();
                        }
                        if (repositoryListener!=null){
                            repositoryListener.onCallConnected();
                        }
                    }

                    if (newState == PeerConnection.PeerConnectionState.CLOSED ||
                            newState == PeerConnection.PeerConnectionState.DISCONNECTED ){
                        if (listener!=null){
                            listener.webrtcClosed();
                        }
                        if (repositoryListener!=null){
                            repositoryListener.onCallEnded();
                        }
                    }
                }

                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    super.onIceCandidate(iceCandidate);
                    webRTCClient.sendIceCandidate(iceCandidate,target);
                }
            },username);
            webRTCClient.listener = this;
            callBack.onSuccess();
        });
    }

    public void initLocalView(SurfaceViewRenderer view){
        webRTCClient.initLocalSurfaceView(view);
    }

    public void initRemoteView(SurfaceViewRenderer view){
        webRTCClient.initRemoteSurfaceView(view);
        this.remoteView = view;
    }

    public void startCall(String target){
        webRTCClient.call(target);
    }

    public void switchCamera() {
        webRTCClient.switchCamera();
    }

    public void toggleAudio(Boolean shouldBeMuted){
        webRTCClient.toggleAudio(shouldBeMuted);
    }

    public void toggleVideo(Boolean shouldBeMuted){
        webRTCClient.toggleVideo(shouldBeMuted);
    }

    public void sendCallRequest(String target, ErrorCallBack errorCallBack){
        firebaseClient.sendMessageToOtherUser(
                new DataModel(target,currentUsername,null, DataModelType.StartCall),errorCallBack
        );
    }

    public void endCall(){
        webRTCClient.closeConnection();
    }

    public void subscribeForLatestEvent(NewEventCallBack callBack){
        firebaseClient.observeIncomingLatestEvent(model -> {
            switch (model.getType()){

                case Offer:
                    this.target = model.getSender();
                    webRTCClient.onRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.OFFER,model.getData()
                    ));
                    webRTCClient.answer(model.getSender());
                    break;
                case Answer:
                    this.target = model.getSender();
                    webRTCClient.onRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.ANSWER,model.getData()
                    ));
                    break;
                case IceCandidate:
                    try{
                        IceCandidate candidate = gson.fromJson(model.getData(),IceCandidate.class);
                        webRTCClient.addIceCandidate(candidate);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                case StartCall:
                    this.target = model.getSender();
                    callBack.onNewEventReceived(model);
                    break;
            }

        });
    }

    @Override
    public void onTransferDataToOtherPeer(DataModel model) {
        firebaseClient.sendMessageToOtherUser(model,()->{});
    }

    public interface ConnectionListener{
        void webrtcConnected();
        void webrtcClosed();
    }

    public interface CallCallback{
        void onSuccess();
        void onError();
    }

    public interface RepositoryListener{
        void onCallConnected();
        void onCallEnded();
        void onRemoteStreamAdded(MediaStream mediaStream);
    }
}