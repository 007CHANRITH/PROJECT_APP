package com.example.project_ez_talk.webrtc;

import androidx.annotation.NonNull;

import com.example.project_ez_talk.utils.ErrorCallBack;
import com.example.project_ez_talk.utils.NewEventCallBack;
import com.example.project_ez_talk.utils.SuccessCallBack;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.util.Objects;

public class FirebaseClient {

    private final Gson gson = new Gson();
    private final DatabaseReference dbRef;
    private String currentUsername;
    private static final String LATEST_EVENT_FIELD_NAME = "latest_event";
    private static final String DATABASE_URL = "https://project-ez-talk-dccea-default-rtdb.europe-west1.firebasedatabase.app";

    public FirebaseClient() {
        dbRef = FirebaseDatabase.getInstance(DATABASE_URL).getReference();
    }

    public void login(String username, SuccessCallBack callBack){
        dbRef.child(username).setValue("").addOnCompleteListener(task -> {
            currentUsername = username;
            callBack.onSuccess();
        });
    }

    public void sendMessageToOtherUser(DataModel dataModel, ErrorCallBack errorCallBack){
        // ✅ Add null checks before using dataModel fields
        if (dataModel == null || dataModel.getTarget() == null || dataModel.getTarget().isEmpty()) {
            android.util.Log.e("FirebaseClient", "❌ Invalid dataModel: target is null or empty");
            errorCallBack.onError();
            return;
        }
        
        // ✅ Store target in a final variable to avoid null issues in callback
        final String targetUser = dataModel.getTarget();
        
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // ✅ Double-check target is still valid when callback executes
                if (targetUser == null || targetUser.isEmpty()) {
                    android.util.Log.e("FirebaseClient", "❌ Target became null in callback");
                    errorCallBack.onError();
                    return;
                }
                
                if (snapshot.child(targetUser).exists()){
                    //send the signal to other user
                    dbRef.child(targetUser).child(LATEST_EVENT_FIELD_NAME)
                            .setValue(gson.toJson(dataModel));

                }else {
                    android.util.Log.w("FirebaseClient", "⚠️ Target user does not exist: " + targetUser);
                    errorCallBack.onError();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("FirebaseClient", "❌ Firebase sendMessage cancelled: " + error.getMessage());
                errorCallBack.onError();
            }
        });
    }

    public void observeIncomingLatestEvent(NewEventCallBack callBack){
        dbRef.child(currentUsername).child(LATEST_EVENT_FIELD_NAME).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try{
                            // ✅ Add null check to prevent NullPointerException
                            Object value = snapshot.getValue();
                            if (value == null || value.toString().isEmpty()) {
                                android.util.Log.d("FirebaseClient", "Empty or null data received, ignoring");
                                return;
                            }
                            String data = value.toString();
                            DataModel dataModel = gson.fromJson(data,DataModel.class);
                            if (dataModel != null) {
                                callBack.onNewEventReceived(dataModel);
                            }
                        }catch (Exception e){
                            android.util.Log.e("FirebaseClient", "Error processing incoming event", e);
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("FirebaseClient", "Firebase listener cancelled: " + error.getMessage());
                    }
                }
        );
    }
}
