package com.example.project_ez_talk.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.ui.chat.detail.ChatDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Background service that listens for Firestore notification triggers
 * Shows notifications when other users send messages
 */
public class NotificationListenerService extends Service {

    private static final String TAG = "NotificationListener";
    private static final String CHANNEL_MESSAGES = "messages_channel";
    private static final String GROUP_MESSAGES = "com.example.project_ez_talk.MESSAGES";
    
    private ListenerRegistration notificationListener;
    private FirebaseFirestore db;
    private String currentUserId;

    @Override
    public void onCreate() {
        super.onCreate();
        
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            startListening();
        } else {
            Log.w(TAG, "⚠️ No user logged in, stopping service");
            stopSelf();
        }
    }

    private void startListening() {
        Log.d(TAG, "👂 Starting to listen for notifications...");
        
        // Listen to: users/{userId}/notifications
        notificationListener = db.collection("users")
                .document(currentUserId)
                .collection("notifications")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "❌ Listen failed: " + error.getMessage());
                        return;
                    }

                    if (snapshots != null) {
                        for (DocumentChange doc : snapshots.getDocumentChanges()) {
                            if (doc.getType() == DocumentChange.Type.ADDED) {
                                // New notification arrived!
                                Map<String, Object> data = doc.getDocument().getData();
                                String docId = doc.getDocument().getId();
                                
                                Log.d(TAG, "🔔 New notification received!");
                                
                                // Show notification
                                showNotificationFromData(data);
                                
                                // Delete notification document after showing
                                db.collection("users")
                                        .document(currentUserId)
                                        .collection("notifications")
                                        .document(docId)
                                        .delete()
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "🗑️ Notification document deleted"))
                                        .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to delete: " + e.getMessage()));
                            }
                        }
                    }
                });
    }

    private void showNotificationFromData(Map<String, Object> data) {
        try {
            String senderId = (String) data.get("senderId");
            String senderName = (String) data.get("senderName");
            String messageText = (String) data.get("messageText");
            String messageType = (String) data.get("messageType");
            String chatId = (String) data.get("chatId");
            String senderAvatar = (String) data.get("senderAvatar");
            
            // Default values
            if (senderId == null) senderId = "";
            if (senderName == null) senderName = "Unknown";
            if (messageText == null) messageText = "";
            if (messageType == null) messageType = "TEXT";
            if (chatId == null) chatId = "";
            
            Log.d(TAG, "📢 From: " + senderName);
            Log.d(TAG, "📝 Message: " + messageText);
            Log.d(TAG, "📊 Type: " + messageType);
            
            // Create notification channel
            createNotificationChannels();
            
            // Format message text based on type
            String formattedMessage = formatMessageByType(messageText, messageType);
            
            // Create Person for sender
            Person sender = createPersonFromData(senderName, senderId, senderAvatar);
            
            // Create Person for "me" (current user)
            Person me = new Person.Builder()
                    .setName("Me")
                    .setKey(currentUserId)
                    .build();
            
            // Create intent to open chat (using keys that ChatDetailActivity expects)
            Intent intent = new Intent(this, ChatDetailActivity.class);
            intent.putExtra("chatId", chatId);
            intent.putExtra("user_id", senderId);        // ChatDetailActivity expects "user_id"
            intent.putExtra("user_name", senderName);    // ChatDetailActivity expects "user_name"
            intent.putExtra("user_avatar", senderAvatar); // Also pass avatar
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            Log.d(TAG, "🔗 Intent data: chatId=" + chatId + ", user_id=" + senderId + ", user_name=" + senderName);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    chatId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build notification with MessagingStyle
            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(me)
                    .setConversationTitle(senderName)
                    .addMessage(formattedMessage, System.currentTimeMillis(), sender);
            
            // Default sound
            Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(messagingStyle)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setSound(defaultSound)
                    .setVibrate(new long[]{100, 200, 100, 200})
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(GROUP_MESSAGES);
            
            // Load avatar if available
            if (senderAvatar != null && !senderAvatar.isEmpty()) {
                Bitmap avatarBitmap = loadAvatarBitmap(senderAvatar);
                if (avatarBitmap != null) {
                    builder.setLargeIcon(avatarBitmap);
                }
            }
            
            // Show notification
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(chatId.hashCode(), builder.build());
                Log.d(TAG, "✅ Notification shown!");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatMessageByType(String text, String type) {
        switch (type) {
            case "IMAGE":
                return "📷 Photo";
            case "AUDIO":
                return "🎤 Voice message";
            case "VIDEO":
                return "🎥 Video";
            case "FILE":
                return "📄 File";
            case "LOCATION":
                return "📍 Location";
            default:
                return text;
        }
    }

    private Person createPersonFromData(String name, String id, String avatarUrl) {
        Person.Builder builder = new Person.Builder()
                .setName(name)
                .setKey(id);
        
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Bitmap avatar = loadAvatarBitmap(avatarUrl);
            if (avatar != null) {
                IconCompat icon = IconCompat.createWithBitmap(avatar);
                builder.setIcon(icon);
            }
        }
        
        return builder.build();
    }

    private Bitmap loadAvatarBitmap(String avatarUrl) {
        try {
            return Glide.with(this)
                    .asBitmap()
                    .load(avatarUrl)
                    .circleCrop()
                    .submit(96, 96)
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "❌ Failed to load avatar: " + e.getMessage());
            return null;
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("New message notifications");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 100, 200});
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) {
            notificationListener.remove();
            Log.d(TAG, "👂 Listener removed");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Restart if killed
    }
}
