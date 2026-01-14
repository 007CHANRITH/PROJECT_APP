package com.example.project_ez_talk.ui.channel;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.adapter.MessageAdapter;
import com.example.project_ez_talk.adapter.SwipeToDeleteCallback;
import com.example.project_ez_talk.model.Message;
import com.example.project_ez_talk.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelDetailActivity extends BaseActivity {

    private static final String TAG = "ChannelDetailActivity";

    // UI Views
    private MaterialToolbar toolbar;
    private LinearLayout layoutChannelHeader;
    private ShapeableImageView ivChannelAvatar;
    private TextView tvChannelName, tvSubscriberCount;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageView btnSend, btnEmoji, btnSubscribe;

    // Adapters and Data
    private MessageAdapter messageAdapter;
    private List<Message> messageList = new ArrayList<>();

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String channelId;
    private String channelName;
    private String currentUserId;
    private String currentUserName;
    private String currentUserAvatar;
    private boolean isAdmin = false;
    private boolean isSubscribed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_detail);

        // Get channel info from intent
        channelId = getIntent().getStringExtra("channelId");
        channelName = getIntent().getStringExtra("channelName");

        if (channelId == null) {
            Toast.makeText(this, "Invalid channel", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initFirebase();
        initViews();
        setupListeners();
        fetchCurrentUserInfo();
        loadChannelInfo();
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUserId = auth.getCurrentUser().getUid();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        layoutChannelHeader = findViewById(R.id.layout_channel_header);
        ivChannelAvatar = findViewById(R.id.iv_channel_avatar);
        tvChannelName = findViewById(R.id.tv_channel_name);
        tvSubscriberCount = findViewById(R.id.tv_subscriber_count);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnEmoji = findViewById(R.id.btn_emoji);

        // Add subscribe button to toolbar
        addSubscribeButton();

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        messageAdapter = new MessageAdapter(messageList, this);
        rvMessages.setAdapter(messageAdapter);

        // ✅ Setup swipe to delete
        messageAdapter.setCurrentChatId(channelId);
        messageAdapter.setChatType("channel");

        SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this, messageAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(rvMessages);

        messageAdapter.setDeleteListener(message -> {
            Log.d(TAG, "✅ Message deleted callback: " + message.getMessageId());
        });

        if (channelName != null) {
            tvChannelName.setText(channelName);
        }
    }

    private void setupListeners() {
        toolbar.setNavigationOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> sendMessage());

        btnEmoji.setOnClickListener(v -> showEmojiPicker());
    }

    /**
     * Add subscribe/unsubscribe button to toolbar
     */
    private void addSubscribeButton() {
        btnSubscribe = new ImageView(this);
        btnSubscribe.setImageResource(R.drawable.ic_add);
        btnSubscribe.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
        
        androidx.appcompat.widget.Toolbar.LayoutParams params = new androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
        params.setMarginEnd(16);
        btnSubscribe.setLayoutParams(params);
    btnSubscribe.setPadding(16, 16, 16, 16);
        
        btnSubscribe.setOnClickListener(v -> toggleSubscription());
        
        toolbar.addView(btnSubscribe);
    }

    /**
     * Toggle subscription to channel
     */
    private void toggleSubscription() {
        if (isSubscribed) {
            // Unsubscribe
            db.collection("channels")
                    .document(channelId)
                    .update("subscribers." + currentUserId, com.google.firebase.firestore.FieldValue.delete())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Unsubscribed from channel", Toast.LENGTH_SHORT).show();
                        isSubscribed = false;
                        updateSubscribeButton();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to unsubscribe", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to unsubscribe: " + e.getMessage());
                    });
        } else {
            // Subscribe
            Map<String, Object> update = new HashMap<>();
            update.put("subscribers." + currentUserId, true);
            
            db.collection("channels")
                    .document(channelId)
                    .update(update)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Subscribed to channel", Toast.LENGTH_SHORT).show();
                        isSubscribed = true;
                        updateSubscribeButton();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to subscribe", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to subscribe: " + e.getMessage());
                    });
        }
    }

    /**
     * Update subscribe button appearance
     */
    private void updateSubscribeButton() {
        if (isSubscribed) {
            btnSubscribe.setImageResource(R.drawable.ic_check);
            btnSubscribe.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.secondary_green));
        } else {
            btnSubscribe.setImageResource(R.drawable.ic_add);
            btnSubscribe.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void showEmojiPicker() {
        com.example.project_ez_talk.ui.dialog.EmojiPickerBottomSheet emojiPicker =
                com.example.project_ez_talk.ui.dialog.EmojiPickerBottomSheet.newInstance(emoji -> {
                    // Insert emoji at cursor position
                    int cursorPosition = etMessage.getSelectionStart();
                    String currentText = etMessage.getText().toString();
                    String newText = currentText.substring(0, cursorPosition) + emoji + currentText.substring(cursorPosition);
                    etMessage.setText(newText);
                    etMessage.setSelection(cursorPosition + emoji.length());
                });
        emojiPicker.show(getSupportFragmentManager(), "EmojiPicker");
    }

    private void fetchCurrentUserInfo() {
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserName = documentSnapshot.getString("name");
                        currentUserAvatar = documentSnapshot.getString("avatarUrl");
                        if (currentUserName == null) currentUserName = "Unknown User";
                        if (currentUserAvatar == null) currentUserAvatar = "";
                        Log.d(TAG, "✅ Current user loaded: " + currentUserName);
                    } else {
                        currentUserName = auth.getCurrentUser().getEmail();
                        currentUserAvatar = "";
                    }
                    loadMessages();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error fetching user: " + e.getMessage());
                    currentUserName = auth.getCurrentUser().getEmail();
                    currentUserAvatar = "";
                    loadMessages();
                });
    }

    private void loadChannelInfo() {
        db.collection("channels")
                .document(channelId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load channel info", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String avatarUrl = documentSnapshot.getString("avatarUrl");
                        Map<String, Boolean> subscribers = (Map<String, Boolean>) documentSnapshot.get("subscribers");
                        Map<String, Boolean> admins = (Map<String, Boolean>) documentSnapshot.get("admins");

                        if (name != null) {
                            tvChannelName.setText(name);
                            channelName = name;
                        }

                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(avatarUrl)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_channel)
                                    .into(ivChannelAvatar);
                        }

                        if (subscribers != null) {
                            int subscriberCount = subscribers.size();
                            tvSubscriberCount.setText(subscriberCount + (subscriberCount == 1 ? " subscriber" : " subscribers"));
                            
                            // Check if current user is subscribed
                            isSubscribed = subscribers.containsKey(currentUserId) && Boolean.TRUE.equals(subscribers.get(currentUserId));
                            updateSubscribeButton();
                        }

                        // ✅ Check if user is admin
                        if (admins != null) {
                            isAdmin = admins.containsKey(currentUserId) && Boolean.TRUE.equals(admins.get(currentUserId));
                        }

                        // ✅ Only admins can send messages
                        etMessage.setEnabled(isAdmin);
                        btnSend.setEnabled(isAdmin);

                        if (!isAdmin) {
                            etMessage.setHint("Only admins can send messages");
                        } else {
                            etMessage.setHint("Type a message...");
                        }

                        Log.d(TAG, "✅ User is admin: " + isAdmin);
                    }
                });
    }

    private void loadMessages() {
        // ✅ Set channel ID for swipe delete
        messageAdapter.setCurrentChatId(channelId);
        messageAdapter.setChatType("channel");
        Log.d(TAG, "Loading messages for channel: " + channelId);

        db.collection("channels")
                .document(channelId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to load messages: " + error.getMessage());
                        Toast.makeText(this, "Failed to load messages", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (querySnapshot != null) {
                        List<Message> newMessages = new ArrayList<>();
                        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                            try {
                                Message message = documentSnapshot.toObject(Message.class);
                                if (message != null) {
                                    message.setMessageId(documentSnapshot.getId());
                                    newMessages.add(message);
                                    Log.d(TAG, "Message loaded: " + documentSnapshot.getId());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing message: " + e.getMessage());
                            }
                        }

                        // ✅ Use setMessages() to prevent race conditions
                        messageAdapter.setMessages(newMessages);

                        if (!newMessages.isEmpty()) {
                            rvMessages.smoothScrollToPosition(newMessages.size() - 1);
                        }
                    }
                });
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(messageText)) {
            return;
        }

        if (!isAdmin) {
            Toast.makeText(this, "Only admins can send messages", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);

        // ✅ Create message using Message model
        Message message = new Message(
                currentUserId,
                channelId,
                messageText,
                Message.MessageType.TEXT
        );
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        db.collection("channels")
                .document(channelId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    etMessage.setText("");
                    btnSend.setEnabled(true);
                    Log.d(TAG, "✅ Message sent successfully");

                    // Update channel's last message
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", messageText);
                    updates.put("lastMessageTime", System.currentTimeMillis());

                    db.collection("channels")
                            .document(channelId)
                            .update(updates)
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Failed to update channel: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "❌ Failed to send message: " + e.getMessage());
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}