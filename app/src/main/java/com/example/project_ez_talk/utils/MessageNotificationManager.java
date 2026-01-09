package com.example.project_ez_talk.utils;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Send notifications via Railway backend server
 * When User A sends message to User B, User B gets notification!
 */
public class MessageNotificationManager {

    private static final String TAG = "MessageNotificationManager";

    // ⚠️ CHANGE THIS TO YOUR RAILWAY URL
    // Example: "https://ez-talk-notifications-prod.up.railway.app"
    private static final String BACKEND_URL = "https://your-project-name-production.up.railway.app";

    /**
     * Send notification when message is sent to a user
     *
     * Call this in ChatDetailActivity.sendTextMessage() after message is saved
     *
     * Example:
     * MessageNotificationManager.sendMessageNotification(
     *     receiverId,
     *     currentUserName,
     *     messageText,
     *     chatId,
     *     currentUser.getUid()
     * );
     */
    public static void sendMessageNotification(
            String receiverId,
            String senderName,
            String messageText,
            String chatId,
            String senderId) {

        // Don't notify self
        if (receiverId.equals(senderId)) {
            return;
        }

        Log.d(TAG, "📤 Sending notification");
        Log.d(TAG, "   To: " + receiverId);
        Log.d(TAG, "   From: " + senderName);

        // Get receiver's FCM token from Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .document(receiverId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String fcmToken = doc.getString("fcmToken");

                        if (fcmToken != null && !fcmToken.isEmpty()) {
                             // Call backend to send notification
                            callBackendNotification(
                                    fcmToken,
                                    senderName,
                                    messageText,
                                    chatId,
                                    senderId
                            );
                        } else {
                            Log.d(TAG, "⚠️ No FCM token for user: " + receiverId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting user: " + e.getMessage());
                });
    }

    /**
     * Send notification to all members of a group
     */
    public static void sendGroupNotification(
            String groupId,
            String senderName,
            String messageText,
            String senderId) {

        Log.d(TAG, "📤 Sending group notification");
        Log.d(TAG, "   Group: " + groupId);
        Log.d(TAG, "   From: " + senderName);

        // Get all group members and their tokens
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Get all members
                        // Implement similar logic to get all FCM tokens
                        // Then call callBackendGroupNotification()
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error: " + e.getMessage());
                });
    }

    /**
     * Call backend server to send notification
     */
    private static void callBackendNotification(
            String token,
            String title,
            String body,
            String chatId,
            String senderId) {

        // Run in background thread
        new Thread(() -> {
            try {
                String endpoint = BACKEND_URL + "/send-notification";

                Log.d(TAG, "🔗 Calling: " + endpoint);

                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // Truncate body if too long
                String truncatedBody = body.length() > 100
                        ? body.substring(0, 100) + "..."
                        : body;

                // Create JSON payload
                JSONObject payload = new JSONObject();
                payload.put("token", token);
                payload.put("title", title);
                payload.put("body", truncatedBody);
                payload.put("type", "private_chat");
                payload.put("chatId", chatId != null ? chatId : "");
                payload.put("senderId", senderId != null ? senderId : "");

                Log.d(TAG, "📤 Sending payload...");

                // Send request
                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.close();

                // Get response
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "📡 Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 200) {
                    Log.d(TAG, "✅ Notification sent successfully!");
                } else {
                    Log.e(TAG, "❌ Server error: " + responseCode);
                }

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}