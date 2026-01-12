package com.example.project_ez_talk.utils;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Send notifications to other users via Cloud Functions
 * When User A sends message to User B, User B gets notification!
 * 
 * ⚠️ TEMPORARY: Currently using placeholder backend URL
 * TODO: Deploy Firebase Cloud Function or Railway backend
 */
public class MessageNotificationManager {

    private static final String TAG = "MessageNotificationManager";

    // ⚠️ TEMPORARY SOLUTION: Store notification data in Firestore
    // Other user's app will listen to this collection via MyFirebaseMessagingService
    private static final boolean USE_FIRESTORE_TRIGGER = true;
    
    // For future backend implementation:
    private static final String BACKEND_URL = "https://your-project-name-production.up.railway.app";

    /**
     * Send notification when message is sent to a user
     * 
     * ✅ WORKING WITHOUT BACKEND - Uses Firestore trigger
     *
     * Call this in ChatDetailActivity.sendTextMessage() after message is saved
     *
     * Example:
     * MessageNotificationManager.sendMessageNotification(
     *     receiverId,
     *     currentUserName,
     *     messageText,
     *     "TEXT",  // messageType
     *     chatId,
     *     currentUser.getUid(),
     *     currentUserAvatar
     * );
     */
    public static void sendMessageNotification(
            String receiverId,
            String senderName,
            String messageText,
            String messageType,
            String chatId,
            String senderId,
            String senderAvatar) {

        // Don't notify self
        if (receiverId.equals(senderId)) {
            return;
        }

        Log.d(TAG, "📤 Sending notification");
        Log.d(TAG, "   To: " + receiverId);
        Log.d(TAG, "   From: " + senderName);
        Log.d(TAG, "   Type: " + messageType);

        if (USE_FIRESTORE_TRIGGER) {
            // ✅ NEW METHOD: Write notification data to Firestore
            // MyFirebaseMessagingService on receiver's device will listen and show notification
            sendViaFirestoreTrigger(receiverId, senderName, messageText, messageType, chatId, senderId, senderAvatar);
        } else {
            // OLD METHOD: Call backend server (requires deployment)
            sendViaBackendServer(receiverId, senderName, messageText, messageType, chatId, senderId, senderAvatar);
        }
    }
    
    /**
     * Overload for backward compatibility
     */
    public static void sendMessageNotification(
            String receiverId,
            String senderName,
            String messageText,
            String chatId,
            String senderId) {
        sendMessageNotification(receiverId, senderName, messageText, "TEXT", chatId, senderId, null);
    }

    /**
     * ✅ NEW: Send notification via Firestore trigger
     * Writes notification data to Firestore, receiver's app listens and shows notification
     */
    private static void sendViaFirestoreTrigger(
            String receiverId,
            String senderName,
            String messageText,
            String messageType,
            String chatId,
            String senderId,
            String senderAvatar) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Truncate message if too long
        String truncatedMessage = messageText != null && messageText.length() > 100
                ? messageText.substring(0, 100) + "..."
                : messageText;

        // Create notification data as Map (easier than JSON)
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("senderId", senderId != null ? senderId : "");
        notificationData.put("senderName", senderName != null ? senderName : "Unknown");
        notificationData.put("messageText", truncatedMessage != null ? truncatedMessage : "");
        notificationData.put("messageType", messageType != null ? messageType : "TEXT");
        notificationData.put("chatId", chatId != null ? chatId : "");
        notificationData.put("senderAvatar", senderAvatar != null ? senderAvatar : "");
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("type", "private_chat");

        // Write to Firestore: users/{receiverId}/notifications/{autoId}
        db.collection("users")
                .document(receiverId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "✅ Notification data written to Firestore!");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to write notification: " + e.getMessage());
                });
    }

    /**
     * Send notification via backend server (requires deployment)
     */
    private static void sendViaBackendServer(
            String receiverId,
            String senderName,
            String messageText,
            String messageType,
            String chatId,
            String senderId,
            String senderAvatar) {

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
                                    messageType,
                                    chatId,
                                    senderId,
                                    senderAvatar
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
            String messageType,
            String chatId,
            String senderId,
            String senderAvatar) {

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

                // Create JSON payload with all required fields
                JSONObject payload = new JSONObject();
                payload.put("token", token);
                payload.put("title", title);
                payload.put("body", truncatedBody);
                payload.put("type", "private_chat");
                payload.put("messageType", messageType != null ? messageType : "TEXT");
                payload.put("chatId", chatId != null ? chatId : "");
                payload.put("senderId", senderId != null ? senderId : "");
                payload.put("senderAvatar", senderAvatar != null ? senderAvatar : "");

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