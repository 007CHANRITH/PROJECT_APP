package com.example.project_ez_talk.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.ui.MainActivity;
import com.example.project_ez_talk.ui.chat.detail.ChatDetailActivity;
import com.example.project_ez_talk.ui.chat.group.GroupChatActivity;
import com.example.project_ez_talk.ui.channel.ChannelDetailActivity;
import com.example.project_ez_talk.helper.NotificationHelper;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    
    // Notification Channels
    private static final String CHANNEL_MESSAGES = "messages_channel";
    private static final String CHANNEL_CALLS = "calls_channel";
    private static final String CHANNEL_GROUPS = "groups_channel";
    
    // Notification Group
    private static final String GROUP_MESSAGES = "com.example.project_ez_talk.MESSAGES";

    /**
     * Called when a message is received
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "🔔 Message received from: " + remoteMessage.getFrom());

        // Get notification data
        Map<String, String> data = remoteMessage.getData();
        
        if (data.isEmpty()) {
            Log.w(TAG, "⚠️ No data in notification");
            return;
        }

        // Extract data
        String type = data.get("type");
        String title = data.get("title");
        String body = data.get("body");
        String senderId = data.get("senderId");
        String chatId = data.get("chatId");
        String senderAvatar = data.get("senderAvatar");
        String messageType = data.get("messageType"); // TEXT, IMAGE, AUDIO, etc.

        Log.d(TAG, "📊 Type: " + type);
        Log.d(TAG, "📢 Title: " + title);
        Log.d(TAG, "📝 Body: " + body);

        // Handle different notification types
        if ("private_chat".equals(type) || "message".equals(type)) {
            showMessageNotification(title, body, senderId, chatId, senderAvatar, messageType);
        } else if ("group_chat".equals(type)) {
            showGroupNotification(title, body, senderId, chatId, senderAvatar);
        } else if ("call".equals(type)) {
            showCallNotification(title, body, data);
        } else {
            // Default notification
            showDefaultNotification(title, body, data);
        }
    }

    /**
     * Called when new FCM token is generated
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "🔑 New FCM Token: " + token);

        // Save new token to Firestore
        NotificationHelper.saveFCMTokenToFirestore(this, token);
    }

    /**
     * Show professional message notification with MessagingStyle
     */
    private void showMessageNotification(String senderName, String messageText, 
                                        String senderId, String chatId, 
                                        String senderAvatar, String messageType) {
        
        createNotificationChannels();

        // Format message based on type
        String displayMessage = formatMessageByType(messageText, messageType);

        // Create Person object for sender
        Person sender = createPersonFromData(senderName, senderAvatar);
        
        // Create MessagingStyle notification
        NotificationCompat.MessagingStyle messagingStyle = 
            new NotificationCompat.MessagingStyle(sender)
                .setConversationTitle(senderName)
                .addMessage(displayMessage, System.currentTimeMillis(), sender);

        // Create intent to open chat
        Intent intent = new Intent(this, ChatDetailActivity.class);
        intent.putExtra("user_id", senderId);
        intent.putExtra("user_name", senderName);
        intent.putExtra("user_avatar", senderAvatar);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                senderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Load sender avatar
        Bitmap largeIcon = loadAvatarBitmap(senderAvatar);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setStyle(messagingStyle)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(new long[]{0, 400, 200, 400})
                .setGroup(GROUP_MESSAGES)
                .setColor(getResources().getColor(R.color.primary_purple, null));

        // Show notification
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(senderId.hashCode(), builder.build());

        // Show summary notification for grouped messages
        showSummaryNotification();
    }

    /**
     * Show group chat notification
     */
    private void showGroupNotification(String groupName, String message, 
                                      String senderId, String groupId, 
                                      String senderAvatar) {
        
        createNotificationChannels();

        Intent intent = new Intent(this, GroupChatActivity.class);
        intent.putExtra("group_id", groupId);
        intent.putExtra("group_name", groupName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                groupId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_GROUPS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(groupName)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(new long[]{0, 400, 200, 400})
                .setGroup(GROUP_MESSAGES)
                .setColor(getResources().getColor(R.color.primary_purple, null));

        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(groupId.hashCode(), builder.build());

        showSummaryNotification();
    }

    /**
    /**
     * Show call notification
     */
    private void showCallNotification(String callerName, String message, Map<String, String> data) {
        createNotificationChannels();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Call notification with special ringtone
        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_video_call)
                .setContentTitle(callerName)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setSound(ringtoneUri)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setColor(getResources().getColor(R.color.primary_purple, null));

        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(999, builder.build());
    }

    /**
     * Show default notification
     */
    private void showDefaultNotification(String title, String body, Map<String, String> data) {
        createNotificationChannels();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(new long[]{0, 400, 200, 400});

        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((title + body).hashCode(), builder.build());
    }

    /**
     * Show summary notification for grouped messages
     */
    private void showSummaryNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("EZ Talk")
                .setContentText("New messages")
                .setContentIntent(pendingIntent)
                .setGroup(GROUP_MESSAGES)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, summaryBuilder.build());
    }

    /**
     * Format message text based on message type
     */
    private String formatMessageByType(String messageText, String messageType) {
        if (messageType == null) {
            return messageText;
        }

        switch (messageType.toUpperCase()) {
            case "IMAGE":
                return "📷 Photo";
            case "VIDEO":
                return "🎥 Video";
            case "AUDIO":
                return "🎤 Voice message";
            case "FILE":
                return "📄 " + messageText;
            case "LOCATION":
                return "📍 Location";
            case "CONTACT":
                return "👤 Contact";
            default:
                return messageText;
        }
    }

    /**
     * Create Person object from sender data
     */
    private Person createPersonFromData(String name, String avatarUrl) {
        Person.Builder personBuilder = new Person.Builder()
                .setName(name);

        // Load avatar if available
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Bitmap avatar = loadAvatarBitmap(avatarUrl);
            if (avatar != null) {
                personBuilder.setIcon(IconCompat.createWithBitmap(avatar));
            }
        }

        return personBuilder.build();
    }

    /**
     * Load avatar bitmap from URL
     */
    private Bitmap loadAvatarBitmap(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return BitmapFactory.decodeResource(getResources(), R.drawable.ic_profile);
        }

        try {
            return Glide.with(this)
                    .asBitmap()
                    .load(avatarUrl)
                    .circleCrop()
                    .submit(128, 128)
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error loading avatar: " + e.getMessage());
            return BitmapFactory.decodeResource(getResources(), R.drawable.ic_profile);
        }
    }

    /**
     * Create notification channels for Android 8.0+
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Messages Channel
            NotificationChannel messagesChannel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messagesChannel.setDescription("Notifications for new messages");
            messagesChannel.enableLights(true);
            messagesChannel.setLightColor(getResources().getColor(R.color.primary_purple, null));
            messagesChannel.enableVibration(true);
            messagesChannel.setVibrationPattern(new long[]{0, 400, 200, 400});
            messagesChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(messagesChannel);

            // Groups Channel
            NotificationChannel groupsChannel = new NotificationChannel(
                    CHANNEL_GROUPS,
                    "Group Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            groupsChannel.setDescription("Notifications for group messages");
            groupsChannel.enableLights(true);
            groupsChannel.setLightColor(getResources().getColor(R.color.primary_purple, null));
            groupsChannel.enableVibration(true);
            groupsChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(groupsChannel);

            // Calls Channel
            NotificationChannel callsChannel = new NotificationChannel(
                    CHANNEL_CALLS,
                    "Calls",
                    NotificationManager.IMPORTANCE_MAX
            );
            callsChannel.setDescription("Notifications for incoming calls");
            callsChannel.enableLights(true);
            callsChannel.enableVibration(true);
            callsChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            callsChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(callsChannel);

            Log.d(TAG, "✅ Notification channels created");
        }
    }
}