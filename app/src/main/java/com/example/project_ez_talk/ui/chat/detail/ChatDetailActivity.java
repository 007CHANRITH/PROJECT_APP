package com.example.project_ez_talk.ui.chat.detail;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.adapter.MessageAdapter;
import com.example.project_ez_talk.adapter.SwipeToDeleteCallback;
import com.example.project_ez_talk.model.CallData;
import com.example.project_ez_talk.model.Message;
import com.example.project_ez_talk.ui.BaseActivity;
import com.example.project_ez_talk.ui.call.incoming.IntegratedIncomingCallActivity;
import com.example.project_ez_talk.ui.call.video.IntegratedVideoCallActivity;
import com.example.project_ez_talk.ui.call.voice.VoiceCallActivity;
import com.example.project_ez_talk.utils.AudioRecorderManager;
import com.example.project_ez_talk.utils.MessageNotificationManager;
import com.example.project_ez_talk.webrtc.FirebaseSignaling;
import com.example.project_ez_talk.webrtc.MainRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ✅ COMPLETE ChatDetailActivity with VIDEO + AUDIO UPLOAD
 */
@SuppressWarnings("ALL")
public class ChatDetailActivity extends BaseActivity {

    private static final String TAG = "ChatDetailActivity";

    // ✅ Firebase Realtime Database URL
    private static final String DATABASE_URL = "https://project-ez-talk-dccea-default-rtdb.europe-west1.firebasedatabase.app";

    // Supabase Configuration
    private static final String SUPABASE_URL = "https://ijcfvpodwmshmdecmxmk.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlqY2Z2cG9kd21zaG1kZWNteG1rIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjYwNTM5NzQsImV4cCI6MjA4MTYyOTk3NH0.35y9_9TMIMEltfYRFs06oOPJwpIGEUHZQasXYkch3IQ";
    private static final String BUCKET_IMAGES = "chat-images";
    private static final String BUCKET_DOCUMENTS = "chat-documents";
    private static final String BUCKET_AUDIO = "chat-audio";
    private static final String BUCKET_VIDEO = "chat-video";

    // UI Views
    private Toolbar toolbar;
    private ImageView ivUserAvatar;
    private TextView tvUserName;
    private TextView tvUserStatus;
    private ImageView btnBack;
    private ImageView btnVoiceCall;
    private ImageView btnVideoCall;
    private ImageView btnMore;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private FloatingActionButton fabSend;
    private FloatingActionButton fabVoice;
    private ImageView btnEmoji;
    private ImageView btnAttach;

    // Voice Recording Views
    private View voiceRecordingOverlay;
    private TextView tvRecordingTime;

    // Voice Recording
    private AudioRecorderManager audioRecorder;
    private Handler recordingHandler;
    private long recordingStartTime;
    private boolean isRecording = false;
    private AudioRecorderManager.RecordingCallback currentRecordingCallback;

    // Adapters and Data
    private MessageAdapter messageAdapter;

    // Firebase
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private FirebaseDatabase rtdb;
    private String chatId;
    private CollectionReference messagesRef;
    private ListenerRegistration listener;

    // Firebase Signaling for calls
    private FirebaseSignaling firebaseSignaling;

    // Other user info
    private String receiverId;
    private String receiverName;
    private String receiverAvatar;

    // Current user info
    private String currentUserName = "";
    private String currentUserAvatar = "";
    private String currentUserId = "";

    // Media handling
    private Uri imageUri;

    // Location
    private LocationManager locationManager;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> documentLauncher;
    private ActivityResultLauncher<Intent> audioLauncher;
    private ActivityResultLauncher<Intent> videoLauncher;
    private ActivityResultLauncher<Intent> contactLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> galleryPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_detail);

        setupActivityResultLaunchers();
        initializeFirebase();

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get receiver info from intent
        receiverId = getIntent().getStringExtra("user_id");
        receiverName = getIntent().getStringExtra("user_name");
        receiverAvatar = getIntent().getStringExtra("user_avatar");

        if (receiverId == null || receiverId.isEmpty()) {
            Toast.makeText(this, "Invalid user ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        initViews();
        setupToolbar();
        setupRecyclerView();
        generateChatId();

        // Fetch current user info and start loading messages
        fetchCurrentUserInfo();
        setupMessageInput();
        setupClickListeners();

        // Initialize Firebase Signaling for incoming calls
        initializeFirebaseSignaling();
    }

    private void initializeFirebase() {
        Log.d(TAG, "🔄 Initializing Firebase...");

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        db = FirebaseFirestore.getInstance();
        rtdb = FirebaseDatabase.getInstance(DATABASE_URL);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Log.d(TAG, "✅ Firebase fully initialized");
    }

    private void initializeFirebaseSignaling() {
        Log.d(TAG, "🔔 Initializing Firebase Signaling...");

        if (currentUser == null) {
            Log.e(TAG, "❌ Current user is null");
            return;
        }

        firebaseSignaling = FirebaseSignaling.getInstance();

        if (firebaseSignaling.isInitialized() &&
                firebaseSignaling.getCurrentUserId().equals(currentUser.getUid())) {
            Log.d(TAG, "✅ Firebase Signaling already initialized");
            setupIncomingCallListener();
            return;
        }

        firebaseSignaling.init(currentUser.getUid(), new FirebaseSignaling.OnSuccessListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✅ Firebase Signaling initialized");
                setupIncomingCallListener();
            }

            @Override
            public void onError() {
                Log.e(TAG, "❌ Failed to initialize Firebase Signaling");
            }
        });
    }

    private void setupIncomingCallListener() {
        Log.d(TAG, "👂 Setting up incoming call listener...");

        firebaseSignaling.observeIncomingCalls(new FirebaseSignaling.OnCallDataListener() {
            @Override
            public void onCallDataReceived(CallData callData) {
                if (callData.getType() == CallData.Type.OFFER) {
                    Log.d(TAG, "📞 INCOMING CALL!");
                    showIncomingCallScreen(callData);
                }
            }

            @Override
            public void onOffer(CallData callData) {}

            @Override
            public void onAnswer(CallData callData) {}

            @Override
            public void onIceCandidate(CallData callData) {}

            @Override
            public void onAccept(CallData callData) {}

            @Override
            public void onReject(CallData callData) {}

            @Override
            public void onError() {
                Log.e(TAG, "❌ Error listening for incoming calls");
            }
        });
    }

    private void showIncomingCallScreen(CallData callData) {
        String callerId = callData.getSenderId();
        String callerName = callData.getData() != null ? callData.getData() : "Unknown";
        String callType = callData.getCallType() != null ? callData.getCallType() : "voice";

        Intent intent = new Intent(this, IntegratedIncomingCallActivity.class);
        intent.putExtra(IntegratedIncomingCallActivity.EXTRA_CALLER_ID, callerId);
        intent.putExtra(IntegratedIncomingCallActivity.EXTRA_CALLER_NAME, callerName);
        intent.putExtra(IntegratedIncomingCallActivity.EXTRA_CALLER_AVATAR, "");
        intent.putExtra(IntegratedIncomingCallActivity.EXTRA_CALL_TYPE, callType);
        intent.putExtra(IntegratedIncomingCallActivity.EXTRA_CURRENT_USER_ID, currentUser.getUid());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
    }

    // ============================================================
    // ACTIVITY RESULT LAUNCHERS
    // ============================================================

    private void setupActivityResultLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageToSupabase(imageUri);
                        }
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && imageUri != null) {
                        uploadImageToSupabase(imageUri);
                    }
                });

        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            uploadDocumentToSupabase(fileUri);
                        }
                    }
                });

        // ✅ AUDIO LAUNCHER
        audioLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri audioUri = result.getData().getData();
                        if (audioUri != null) {
                            uploadAudioToSupabase(audioUri);
                        }
                    }
                });

        // ✅ VIDEO LAUNCHER
        videoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri videoUri = result.getData().getData();
                        if (videoUri != null) {
                            uploadVideoToSupabase(videoUri);
                        }
                    }
                });

        contactLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri contactUri = result.getData().getData();
                        if (contactUri != null) {
                            handleContactSelection(contactUri);
                        }
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    Boolean cameraGranted = permissions.get(Manifest.permission.CAMERA);
                    Boolean storageGranted = permissions.get(Manifest.permission.READ_EXTERNAL_STORAGE);

                    if (Boolean.TRUE.equals(cameraGranted) && Boolean.TRUE.equals(storageGranted)) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                });

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    Boolean fineLocationGranted = permissions.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarseLocationGranted = permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION);

                    if (Boolean.TRUE.equals(fineLocationGranted) || Boolean.TRUE.equals(coarseLocationGranted)) {
                        sendLocationMessage();
                    } else {
                        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                });

        galleryPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchGalleryPicker();
                    } else {
                        Toast.makeText(this, "Gallery permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ============================================================
    // SETUP METHODS
    // ============================================================

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserStatus = findViewById(R.id.tvUserStatus);
        btnBack = findViewById(R.id.btnBack);
        btnVoiceCall = findViewById(R.id.btnVoiceCall);
        btnVideoCall = findViewById(R.id.btnVideoCall);
        btnMore = findViewById(R.id.btnMore);

        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        fabSend = findViewById(R.id.fabSend);
        fabVoice = findViewById(R.id.fabVoice);
        btnEmoji = findViewById(R.id.btnEmoji);
        btnAttach = findViewById(R.id.btnAttach);

        // Voice recording views
        voiceRecordingOverlay = findViewById(R.id.voiceRecordingOverlay);
        tvRecordingTime = findViewById(R.id.tvRecordingTime);

        // Initialize voice recorder
        audioRecorder = new AudioRecorderManager();
        recordingHandler = new Handler(Looper.getMainLooper());

        fabSend.setVisibility(View.GONE);
        fabVoice.setVisibility(View.VISIBLE);
    }

    private void fetchCurrentUserInfo() {
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String avatar = documentSnapshot.getString("avatarUrl");
                        currentUserName = name != null ? name : "User";
                        currentUserAvatar = avatar != null ? avatar : "";
                    } else {
                        currentUserName = currentUser.getEmail() != null ? currentUser.getEmail() : "User";
                        currentUserAvatar = "";
                    }
                    createInitialChatDocument();
                    loadRealTimeMessages();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error fetching user: " + e.getMessage());
                    currentUserName = currentUser.getEmail() != null ? currentUser.getEmail() : "User";
                    currentUserAvatar = "";
                    createInitialChatDocument();
                    loadRealTimeMessages();
                });
    }

    @SuppressLint("SetTextI18n")
    private void setupToolbar() {
        if (tvUserName != null) {
            tvUserName.setText(receiverName != null && !receiverName.isEmpty() ? receiverName : "User");
        }

        if (ivUserAvatar != null && receiverAvatar != null && !receiverAvatar.isEmpty()) {
            Glide.with(this)
                    .load(receiverAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(ivUserAvatar);
        } else if (ivUserAvatar != null) {
            ivUserAvatar.setImageResource(R.drawable.ic_profile);
        }

        if (tvUserStatus != null) {
            tvUserStatus.setText("Online");
        }
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(new ArrayList<>(), this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        ItemTouchHelper.Callback callback = new SwipeToDeleteCallback(this, messageAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(rvMessages);

        messageAdapter.setDeleteListener(message -> {
            Log.d(TAG, "Message deleted: " + message.getMessageId());
        });
    }

    private void generateChatId() {
        String id1 = currentUser.getUid();
        String id2 = receiverId;
        chatId = id1.compareTo(id2) < 0 ? id1 + "_" + id2 : id2 + "_" + id1;
        Log.d(TAG, "Generated Chat ID: " + chatId);
    }

    private void createInitialChatDocument() {
        long timestamp = System.currentTimeMillis();

        Map<String, Object> sharedChatData = new HashMap<>();
        sharedChatData.put("id", chatId);
        sharedChatData.put("participants", Arrays.asList(currentUser.getUid(), receiverId));
        sharedChatData.put("createdAt", timestamp);

        db.collection("chats")
                .document(chatId)
                .set(sharedChatData, SetOptions.merge());

        Map<String, Object> chatDataCurrent = new HashMap<>();
        chatDataCurrent.put("id", chatId);
        chatDataCurrent.put("name", receiverName != null ? receiverName : "Unknown");
        chatDataCurrent.put("avatarUrl", receiverAvatar != null ? receiverAvatar : "");
        chatDataCurrent.put("lastMessage", "");
        chatDataCurrent.put("lastMessageTimestamp", timestamp);
        chatDataCurrent.put("unreadCount", 0);

        Map<String, Object> chatDataReceiver = new HashMap<>();
        chatDataReceiver.put("id", chatId);
        chatDataReceiver.put("name", currentUserName);
        chatDataReceiver.put("avatarUrl", currentUserAvatar);
        chatDataReceiver.put("lastMessage", "");
        chatDataReceiver.put("lastMessageTimestamp", timestamp);
        chatDataReceiver.put("unreadCount", 0);

        db.collection("users")
                .document(currentUser.getUid())
                .collection("chats")
                .document(chatId)
                .set(chatDataCurrent, SetOptions.merge());

        db.collection("users")
                .document(receiverId)
                .collection("chats")
                .document(chatId)
                .set(chatDataReceiver, SetOptions.merge());

        Log.d(TAG, "Chat document created");
    }

    private void loadRealTimeMessages() {
        Log.d(TAG, "Loading messages for chatId: " + chatId);

        messageAdapter.setCurrentChatId(chatId);
        messageAdapter.setChatType("private");

        messagesRef = db.collection("chats")
                .document(chatId)
                .collection("messages");

        listener = messagesRef
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "❌ Firestore Error: " + e.getMessage());
                        return;
                    }

                    if (snapshots == null) {
                        Log.d(TAG, "No messages yet");
                        return;
                    }

                    Log.d(TAG, "✅ Messages loaded: " + snapshots.size());

                    List<Message> newMessages = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        try {
                            Message message = doc.toObject(Message.class);
                            if (message != null) {
                                message.setMessageId(doc.getId());
                                newMessages.add(message);
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error parsing message: " + ex.getMessage());
                        }
                    }

                    messageAdapter.setMessages(newMessages);

                    if (!newMessages.isEmpty()) {
                        rvMessages.scrollToPosition(newMessages.size() - 1);
                    }
                });
    }

    private void setupMessageInput() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s.length() > 0;
                fabSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                fabVoice.setVisibility(hasText ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        fabSend.setOnClickListener(v -> sendTextMessage());

        // Voice recording with press and hold
        fabVoice.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startVoiceRecording();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopVoiceRecording(event.getAction() == MotionEvent.ACTION_CANCEL);
                    return true;
            }
            return false;
        });
        btnAttach.setOnClickListener(v -> showAttachmentBottomSheet());
        btnEmoji.setOnClickListener(v -> Toast.makeText(this, "Emoji picker coming soon", Toast.LENGTH_SHORT).show());

        btnVoiceCall.setOnClickListener(v -> initiateVoiceCall());
        btnVideoCall.setOnClickListener(v -> initiateVideoCall());
        btnMore.setOnClickListener(v -> Toast.makeText(this, "More options coming soon", Toast.LENGTH_SHORT).show());
    }

    private void initiateVoiceCall() {
        if (receiverId == null || receiverId.isEmpty()) {
            Toast.makeText(this, "Cannot start call: Invalid user", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "📞 INITIATING VOICE CALL to: " + receiverName);

        if (firebaseSignaling != null) {
            CallData callData = new CallData();
            callData.setTargetId(receiverId);
            callData.setSenderId(currentUserId);
            callData.setType(CallData.Type.OFFER);
            callData.setCallType("voice");
            callData.setData(currentUserName);

            firebaseSignaling.sendCallData(callData, () -> {
                Log.e(TAG, "❌ Failed to send call via FirebaseSignaling");
                Toast.makeText(ChatDetailActivity.this, "Failed to send call", Toast.LENGTH_SHORT).show();
            });
        }

        MainRepository repository = MainRepository.getInstance();
        repository.sendCallRequest(receiverId, () -> {
            Log.e(TAG, "❌ Failed to send call via MainRepository");
            runOnUiThread(() -> {
                Toast.makeText(ChatDetailActivity.this, "User not available", Toast.LENGTH_SHORT).show();
            });
        });

        Intent intent = new Intent(this, VoiceCallActivity.class);
        intent.putExtra(VoiceCallActivity.EXTRA_USER_ID, receiverId);
        intent.putExtra(VoiceCallActivity.EXTRA_USER_NAME, receiverName);
        intent.putExtra(VoiceCallActivity.EXTRA_USER_AVATAR, receiverAvatar);
        intent.putExtra(VoiceCallActivity.EXTRA_CURRENT_USER_ID, currentUserId);
        intent.putExtra(VoiceCallActivity.EXTRA_IS_INCOMING, false);

        startActivity(intent);
    }

    private void initiateVideoCall() {
        if (receiverId == null || receiverId.isEmpty()) {
            Toast.makeText(this, "Cannot start call: Invalid user", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "📹 INITIATING VIDEO CALL to: " + receiverName);

        if (firebaseSignaling != null) {
            CallData callData = new CallData();
            callData.setTargetId(receiverId);
            callData.setSenderId(currentUserId);
            callData.setType(CallData.Type.OFFER);
            callData.setCallType("video");
            callData.setData(currentUserName);

            firebaseSignaling.sendCallData(callData, () -> {
                Log.e(TAG, "❌ Failed to send call via FirebaseSignaling");
                Toast.makeText(ChatDetailActivity.this, "Failed to send call", Toast.LENGTH_SHORT).show();
            });
        }

        MainRepository repository = MainRepository.getInstance();
        repository.sendCallRequest(receiverId, () -> {
            Log.e(TAG, "❌ Failed to send call via MainRepository");
            runOnUiThread(() -> {
                Toast.makeText(ChatDetailActivity.this, "User not available", Toast.LENGTH_SHORT).show();
            });
        });

        Intent intent = new Intent(this, IntegratedVideoCallActivity.class);
        intent.putExtra(IntegratedVideoCallActivity.EXTRA_USER_ID, receiverId);
        intent.putExtra(IntegratedVideoCallActivity.EXTRA_USER_NAME, receiverName);
        intent.putExtra(IntegratedVideoCallActivity.EXTRA_USER_AVATAR, receiverAvatar);
        intent.putExtra(IntegratedVideoCallActivity.EXTRA_CURRENT_USER_ID, currentUserId);
        intent.putExtra(IntegratedVideoCallActivity.EXTRA_IS_INCOMING, false);

        startActivity(intent);
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Network check error: " + e.getMessage());
            return true;
        }
    }

    private void sendTextMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }

        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                messageText,
                Message.MessageType.TEXT
        );

        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ Message saved");
                    etMessage.setText("");
                    updateChatListBothUsers(messageText);

                    MessageNotificationManager.sendMessageNotification(
                            receiverId,
                            currentUserName,
                            messageText,
                            chatId,
                            currentUser.getUid()
                    );
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed: " + e.getMessage());
                });
    }

    private void updateChatListBothUsers(String messagePreview) {
        long timestamp = System.currentTimeMillis();

        Map<String, Object> chatDataCurrent = new HashMap<>();
        chatDataCurrent.put("lastMessage", messagePreview);
        chatDataCurrent.put("lastMessageTimestamp", timestamp);
        chatDataCurrent.put("unreadCount", 0);

        db.collection("users")
                .document(currentUser.getUid())
                .collection("chats")
                .document(chatId)
                .update(chatDataCurrent);

        Map<String, Object> chatDataReceiver = new HashMap<>();
        chatDataReceiver.put("lastMessage", messagePreview);
        chatDataReceiver.put("lastMessageTimestamp", timestamp);
        chatDataReceiver.put("unreadCount", com.google.firebase.firestore.FieldValue.increment(1));

        db.collection("users")
                .document(receiverId)
                .collection("chats")
                .document(chatId)
                .update(chatDataReceiver);
    }

    // ============================================================
    // Voice Recording Methods
    // ============================================================

    private void startVoiceRecording() {
        if (isRecording) return;

        // Check permission
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        try {
            currentRecordingCallback = new AudioRecorderManager.RecordingCallback() {
                @Override
                public void onRecordingStarted() {
                    runOnUiThread(() -> {
                        isRecording = true;
                        recordingStartTime = System.currentTimeMillis();
                        voiceRecordingOverlay.setVisibility(View.VISIBLE);
                        startRecordingTimer();
                    });
                }

                @Override
                public void onRecordingProgress(long durationMs) {
                    // Progress handled by timer
                }

                @Override
                public void onRecordingCompleted(String filePath, long durationMs) {
                    Log.d(TAG, "🎤 onRecordingCompleted called");
                    Log.d(TAG, "🎤 File path: " + filePath);
                    Log.d(TAG, "🎤 Duration: " + durationMs + "ms");
                    runOnUiThread(() -> {
                        isRecording = false;
                        voiceRecordingOverlay.setVisibility(View.GONE);
                        recordingHandler.removeCallbacksAndMessages(null);
                        Log.d(TAG, "🎤 Calling uploadVoiceMessage...");
                        uploadVoiceMessage(filePath, durationMs);
                    });
                }

                @Override
                public void onRecordingError(String error) {
                    runOnUiThread(() -> {
                        isRecording = false;
                        voiceRecordingOverlay.setVisibility(View.GONE);
                        recordingHandler.removeCallbacksAndMessages(null);
                        Toast.makeText(ChatDetailActivity.this, "Recording error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            };
            audioRecorder.startRecording(this, currentRecordingCallback);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVoiceRecording(boolean cancel) {
        Log.d(TAG, "🎤 stopVoiceRecording called, cancel=" + cancel);
        Log.d(TAG, "🎤 isRecording=" + isRecording);
        Log.d(TAG, "🎤 currentRecordingCallback=" + (currentRecordingCallback != null ? "NOT NULL" : "NULL"));
        
        if (!isRecording) {
            Log.w(TAG, "❌ Not recording, returning");
            return;
        }

        if (cancel) {
            Log.d(TAG, "🚫 Cancelling recording...");
            audioRecorder.cancelRecording();
            isRecording = false;
            voiceRecordingOverlay.setVisibility(View.GONE);
            recordingHandler.removeCallbacksAndMessages(null);
            Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "✅ Stopping recording normally...");
            audioRecorder.stopRecording(currentRecordingCallback);
        }
    }

    private void startRecordingTimer() {
        recordingHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsed = System.currentTimeMillis() - recordingStartTime;
                    int seconds = (int) (elapsed / 1000) % 60;
                    int minutes = (int) (elapsed / 1000) / 60;
                    tvRecordingTime.setText(String.format("%02d:%02d", minutes, seconds));
                    recordingHandler.postDelayed(this, 100);
                }
            }
        });
    }

    private void uploadVoiceMessage(String filePath, long durationMs) {
        Log.d(TAG, "📤 uploadVoiceMessage called");
        Log.d(TAG, "📤 File path: " + filePath);
        Log.d(TAG, "📤 Duration: " + durationMs + "ms");
        
        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            Log.e(TAG, "❌ Audio file not found: " + filePath);
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "✅ Audio file exists, size: " + audioFile.length() + " bytes");

        // Show progress
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Uploading voice message...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        Log.d(TAG, "📤 Starting upload thread...");

        new Thread(() -> {
            try {
                Log.d(TAG, "📤 Creating OkHttpClient...");
                OkHttpClient client = new OkHttpClient();

                Log.d(TAG, "📤 Reading file bytes...");
                byte[] fileBytes = new byte[(int) audioFile.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(audioFile);
                fis.read(fileBytes);
                fis.close();
                Log.d(TAG, "✅ File read: " + fileBytes.length + " bytes");

                String fileName = "voice_" + System.currentTimeMillis() + ".m4a";
                Log.d(TAG, "📤 Uploading as: " + fileName);
                
                RequestBody requestBody = RequestBody.create(
                        MediaType.parse("audio/mp4"),
                        fileBytes
                );

                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_AUDIO + "/" + fileName;
                Log.d(TAG, "📤 Upload URL: " + uploadUrl);
                
                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("Content-Type", "audio/mp4")
                        .post(requestBody)
                        .build();

                Log.d(TAG, "📤 Executing upload request...");
                Response response = client.newCall(request).execute();
                Log.d(TAG, "📤 Response code: " + response.code());

                if (response.isSuccessful()) {
                    String fileUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_AUDIO + "/" + fileName;
                    Log.d(TAG, "✅ Upload successful! URL: " + fileUrl);

                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        sendAudioMessage(fileUrl, durationMs);
                        audioFile.delete();
                    });
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e(TAG, "❌ Upload failed: " + response.code() + " - " + errorBody);
                    throw new Exception("Upload failed: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Upload exception: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(ChatDetailActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    audioFile.delete();
                });
            }
        }).start();
    }

    private void sendAudioMessage(String audioUrl, long durationMs) {
        Log.d(TAG, "📤 sendAudioMessage called");
        Log.d(TAG, "📤 Audio URL: " + audioUrl);
        Log.d(TAG, "📤 Duration: " + durationMs + "ms");
        
        // 🔍 DEBUG: Log user IDs
        Log.d(TAG, "🔍 SENDER DEBUG:");
        Log.d(TAG, "   Current User ID (sender): " + currentUser.getUid());
        Log.d(TAG, "   Receiver ID: " + receiverId);
        Log.d(TAG, "   Current User Name: " + currentUserName);
        
        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                "🎤 Voice message",  // Content/text
                Message.MessageType.AUDIO
        );

        message.setFileUrl(audioUrl);  // ✅ THIS WAS MISSING!
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());
        message.setDuration(durationMs);

        Log.d(TAG, "💾 Saving voice message to Firestore with URL: " + audioUrl);
        Log.d(TAG, "💾 Message senderId: " + message.getSenderId());
        Log.d(TAG, "💾 Message groupId (receiverId): " + message.getGroupId());

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ Voice message sent successfully!");
                    updateChatListBothUsers("🎤 Voice message");

                    MessageNotificationManager.sendMessageNotification(
                            receiverId,
                            currentUserName,
                            "🎤 Voice message",
                            chatId,
                            currentUser.getUid()
                    );
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to send voice message: " + e.getMessage());
                    Toast.makeText(this, "Failed to send voice message", Toast.LENGTH_SHORT).show();
                });
    }

    // ============================================================

    private void showAttachmentBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_attachment);

        View llCamera = dialog.findViewById(R.id.llCamera);
        View llGallery = dialog.findViewById(R.id.llGallery);
        View llDocument = dialog.findViewById(R.id.llDocument);
        View llAudio = dialog.findViewById(R.id.llAudio);
        View llLocation = dialog.findViewById(R.id.llLocation);
        View llContact = dialog.findViewById(R.id.llContact);

        if (llCamera != null) llCamera.setOnClickListener(v -> { openCameraWithPermission(); dialog.dismiss(); });
        if (llGallery != null) llGallery.setOnClickListener(v -> { openGallery(); dialog.dismiss(); });
        if (llDocument != null) llDocument.setOnClickListener(v -> { openDocumentPicker(); dialog.dismiss(); });
        if (llAudio != null) llAudio.setOnClickListener(v -> { openAudioPicker(); dialog.dismiss(); });
        if (llLocation != null) llLocation.setOnClickListener(v -> { requestLocationPermission(); dialog.dismiss(); });
        if (llContact != null) llContact.setOnClickListener(v -> { openContactPicker(); dialog.dismiss(); });

        dialog.show();
    }

    // ============================================================
    // IMAGE HANDLING
    // ============================================================

    private void openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                launchGalleryPicker();
            } else {
                galleryPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                launchGalleryPicker();
            } else {
                galleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void launchGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void openCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            imageUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new android.content.ContentValues()
            );

            if (imageUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                cameraLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera error: " + e.getMessage());
            Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImageToSupabase(Uri imageUri) {
        if (imageUri == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show());
                    return;
                }

                byte[] imageData = readBytes(inputStream);
                String fileName = "chat_" + chatId + "_" + System.currentTimeMillis() + ".jpg";
                String filePath = "chat_images/" + fileName;
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_IMAGES + "/" + filePath;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(imageData, MediaType.parse("image/jpeg"));

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader("Content-Type", "image/jpeg")
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("apikey", SUPABASE_KEY)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String imageUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_IMAGES + "/" + filePath;
                    runOnUiThread(() -> sendImageMessage(imageUrl));
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendImageMessage(String imageUrl) {
        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                "",
                Message.MessageType.IMAGE
        );
        message.setFileUrl(imageUrl);
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    updateChatListBothUsers("[Image]");
                    Toast.makeText(this, "Image sent", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show());
    }

    // ============================================================
    // DOCUMENT HANDLING
    // ============================================================

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        documentLauncher.launch(intent);
    }

    private void uploadDocumentToSupabase(Uri documentUri) {
        if (documentUri == null) {
            Toast.makeText(this, "No document selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading document...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(documentUri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to read document", Toast.LENGTH_SHORT).show());
                    return;
                }

                byte[] documentData = readBytes(inputStream);
                String fileName = getFileName(documentUri);
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "document_" + System.currentTimeMillis();
                }

                String filePathWithoutExtension = "chat_" + chatId + "_" + System.currentTimeMillis();
                String filePath = "chat_documents/" + filePathWithoutExtension + "_" + fileName;
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_DOCUMENTS + "/" + filePath;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(documentData, MediaType.parse("application/octet-stream"));

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/octet-stream")
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("apikey", SUPABASE_KEY)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String documentUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_DOCUMENTS + "/" + filePath;
                    String finalFileName = fileName;
                    runOnUiThread(() -> sendDocumentMessage(documentUrl, finalFileName));
                    Log.d(TAG, "✅ Document uploaded successfully: " + documentUrl);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "❌ Upload failed: " + response.code() + " - " + errorBody);
                    runOnUiThread(() -> Toast.makeText(this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show());
                }

                response.close();

            } catch (Exception e) {
                Log.e(TAG, "❌ Document upload error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendDocumentMessage(String documentUrl, String fileName) {
        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                fileName,
                Message.MessageType.FILE
        );

        message.setFileUrl(documentUrl);
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ Document message saved");
                    updateChatListBothUsers("[Document: " + fileName + "]");
                    Toast.makeText(this, "Document sent successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save document message: " + e.getMessage());
                    Toast.makeText(this, "Failed to send document", Toast.LENGTH_SHORT).show();
                });
    }

    // ============================================================
    // ✅ AUDIO HANDLING
    // ============================================================

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        audioLauncher.launch(intent);
    }

    /**
     * ✅ COMPLETE: Upload Audio to Supabase
     */
    private void uploadAudioToSupabase(Uri audioUri) {
        if (audioUri == null) {
            Toast.makeText(this, "No audio selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading audio...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // ✅ STEP 1: Read the audio file
                InputStream inputStream = getContentResolver().openInputStream(audioUri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to read audio", Toast.LENGTH_SHORT).show());
                    return;
                }

                byte[] audioData = readBytes(inputStream);

                // ✅ STEP 2: Get the file name
                String fileName = getFileName(audioUri);
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "audio_" + System.currentTimeMillis() + ".mp3";
                }

                Log.d(TAG, "🎵 Audio name: " + fileName);

                // ✅ STEP 3: Create file path in Supabase
                String filePath = "chat_audio/" + "chat_" + chatId + "_" + System.currentTimeMillis() + "_" + fileName;

                // ✅ STEP 4: Upload to Supabase
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_AUDIO + "/" + filePath;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(audioData, MediaType.parse("application/octet-stream"));

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/octet-stream")
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("apikey", SUPABASE_KEY)
                        .build();

                Log.d(TAG, "📤 Uploading audio...");

                Response response = client.newCall(request).execute();

                // ✅ STEP 5: Handle response
                if (response.isSuccessful()) {
                    String audioUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_AUDIO + "/" + filePath;
                    Log.d(TAG, "✅ Audio uploaded successfully: " + audioUrl);
                    String finalFileName = fileName;
                    runOnUiThread(() -> sendAudioFileMessage(audioUrl, finalFileName));
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "❌ Upload failed: " + response.code() + " - " + errorBody);
                    runOnUiThread(() -> Toast.makeText(this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show());
                }

                response.close();

            } catch (Exception e) {
                Log.e(TAG, "❌ Audio upload error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * ✅ NEW: Send Audio File Message to Firestore
     */
    private void sendAudioFileMessage(String audioUrl, String fileName) {
        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                fileName,
                Message.MessageType.AUDIO
        );

        message.setFileUrl(audioUrl);
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        Log.d(TAG, "💾 Saving audio message to Firestore...");

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ Audio message saved to Firebase");
                    updateChatListBothUsers("[Audio]");
                    Toast.makeText(this, "Audio sent successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save audio message: " + e.getMessage());
                    Toast.makeText(this, "Failed to send audio", Toast.LENGTH_SHORT).show();
                });
    }

    // ============================================================
    // ✅ VIDEO HANDLING
    // ============================================================

    /**
     * ✅ NEW: Open Video Picker
     */
    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        videoLauncher.launch(intent);
    }

    /**
     * ✅ COMPLETE: Upload Video to Supabase
     */
    private void uploadVideoToSupabase(Uri videoUri) {
        if (videoUri == null) {
            Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // ✅ STEP 1: Read the video file
                InputStream inputStream = getContentResolver().openInputStream(videoUri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to read video", Toast.LENGTH_SHORT).show());
                    return;
                }

                byte[] videoData = readBytes(inputStream);

                // ✅ STEP 2: Get the file name
                String fileName = getFileName(videoUri);
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "video_" + System.currentTimeMillis() + ".mp4";
                }

                Log.d(TAG, "🎬 Video name: " + fileName);

                // ✅ STEP 3: Create file path in Supabase
                String filePath = "chat_video/" + "chat_" + chatId + "_" + System.currentTimeMillis() + "_" + fileName;

                // ✅ STEP 4: Upload to Supabase
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_VIDEO + "/" + filePath;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(videoData, MediaType.parse("application/octet-stream"));

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/octet-stream")
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("apikey", SUPABASE_KEY)
                        .build();

                Log.d(TAG, "📤 Uploading video...");

                Response response = client.newCall(request).execute();

                // ✅ STEP 5: Handle response
                if (response.isSuccessful()) {
                    String videoUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_VIDEO + "/" + filePath;
                    Log.d(TAG, "✅ Video uploaded successfully: " + videoUrl);
                    String finalFileName = fileName;
                    runOnUiThread(() -> sendVideoMessage(videoUrl, finalFileName));
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "❌ Upload failed: " + response.code() + " - " + errorBody);
                    runOnUiThread(() -> Toast.makeText(this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show());
                }

                response.close();

            } catch (Exception e) {
                Log.e(TAG, "❌ Video upload error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * ✅ NEW: Send Video Message to Firestore
     */
    private void sendVideoMessage(String videoUrl, String fileName) {
        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                fileName,
                Message.MessageType.VIDEO
        );

        message.setFileUrl(videoUrl);
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        Log.d(TAG, "💾 Saving video message to Firestore...");

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ Video message saved to Firebase");
                    updateChatListBothUsers("[Video]");
                    Toast.makeText(this, "Video sent successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save video message: " + e.getMessage());
                    Toast.makeText(this, "Failed to send video", Toast.LENGTH_SHORT).show();
                });
    }

    // ============================================================
    // LOCATION & CONTACT HANDLING
    // ============================================================

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            sendLocationMessage();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void sendLocationMessage() {
        try {
            Location location = null;

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            if (location == null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                Message message = new Message(
                        currentUser.getUid(),
                        receiverId,
                        latitude + "," + longitude,
                        Message.MessageType.LOCATION
                );
                message.setSenderName(currentUserName);
                message.setSenderAvatarUrl(currentUserAvatar);
                message.setTimestamp(System.currentTimeMillis());

                messagesRef.add(message)
                        .addOnSuccessListener(ref -> {
                            Log.d(TAG, "✅ LOCATION message saved");
                            updateChatListBothUsers("[Location]");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Failed to save location message");
                            Toast.makeText(ChatDetailActivity.this, "Failed to send location", Toast.LENGTH_SHORT).show();
                        });
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Location error: " + e.getMessage());
            Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactLauncher.launch(intent);
    }

    @SuppressLint("Range")
    private void handleContactSelection(Uri contactUri) {
        try {
            Cursor cursor = getContentResolver().query(contactUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                Cursor phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId,
                        null,
                        null
                );

                String phoneNumber = "";
                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    phoneCursor.close();
                }

                sendContactMessage(contactName, phoneNumber);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading contact: " + e.getMessage());
            Toast.makeText(this, "Error reading contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendContactMessage(String contactName, String phoneNumber) {
        Message message = new Message(
                currentUser.getUid(),
                receiverId,
                contactName + "|" + phoneNumber,
                Message.MessageType.FILE
        );
        message.setSenderName(currentUserName);
        message.setSenderAvatarUrl(currentUserAvatar);
        message.setTimestamp(System.currentTimeMillis());

        messagesRef.add(message)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ CONTACT message saved");
                    updateChatListBothUsers("[Contact: " + contactName + "]");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save contact message");
                    Toast.makeText(ChatDetailActivity.this, "Failed to send contact", Toast.LENGTH_SHORT).show();
                });
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    private byte[] readBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        byte[] allBytes = new byte[0];

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byte[] newBytes = new byte[allBytes.length + bytesRead];
            System.arraycopy(allBytes, 0, newBytes, 0, allBytes.length);
            System.arraycopy(buffer, 0, newBytes, allBytes.length, bytesRead);
            allBytes = newBytes;
        }

        inputStream.close();
        return allBytes;
    }

    private String getFileName(Uri uri) {
        String fileName = "file";
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex("_display_name");
                if (columnIndex >= 0) {
                    fileName = cursor.getString(columnIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name: " + e.getMessage());
        }
        return fileName;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            listener.remove();
            Log.d(TAG, "Listener removed");
        }

        if (firebaseSignaling != null) {
            firebaseSignaling.removeListener();
            Log.d(TAG, "Firebase Signaling listener removed");
        }
        
        // Release audio player resources
        if (messageAdapter != null) {
            messageAdapter.release();
            Log.d(TAG, "MessageAdapter released");
        }
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public void setToolbar(Toolbar toolbar) {
        this.toolbar = toolbar;
    }
}