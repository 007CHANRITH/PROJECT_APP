package com.example.project_ez_talk.ui.chat.group;

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
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("ALL")
public class GroupChatActivity extends BaseActivity {

    private static final String TAG = "GroupChatActivity";

    // Supabase Configuration
    private static final String SUPABASE_URL = "https://ijcfvpodwmshmdecmxmk.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlqY2Z2cG9kd21zaG1kZWNteG1rIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjYwNTM5NzQsImV4cCI6MjA4MTYyOTk3NH0.35y9_9TMIMEltfYRFs06oOPJwpIGEUHZQasXYkch3IQ";
    private static final String BUCKET_IMAGES = "chat-images";
    private static final String BUCKET_DOCUMENTS = "chat-documents";
    private static final String BUCKET_AUDIO = "chat-audio";

    // UI Views
    private MaterialToolbar toolbar;
    private LinearLayout layoutGroupHeader;
    private ShapeableImageView ivGroupIcon;
    private TextView tvGroupName;
    private TextView tvMemberCount;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageView btnSend, btnAttach, btnEmoji;

    // Adapters and Data
    private MessageAdapter messageAdapter;
    private List<Message> messageList = new ArrayList<>();

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String groupId;
    private String groupName;
    private String groupIcon;
    private String currentUserId;
    private String currentUserName;

    // Media handling
    private Uri imageUri;
    private LocationManager locationManager;

    // ==================== ACTIVITY RESULT LAUNCHERS ====================
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> documentLauncher;
    private ActivityResultLauncher<Intent> audioLauncher;
    private ActivityResultLauncher<Intent> contactLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> galleryPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        // Get group info from intent
        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        groupIcon = getIntent().getStringExtra("groupIcon");

        if (groupId == null) {
            Toast.makeText(this, "Invalid group", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ==================== SETUP LAUNCHERS ====================
        setupActivityResultLaunchers();

        initFirebase();
        initViews();
        setupListeners();
        loadGroupInfo();
        loadMessages();
    }

    // ==================== SETUP ALL ACTIVITY RESULT LAUNCHERS ====================
    private void setupActivityResultLaunchers() {
        // Gallery Launcher
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

        // Camera Launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && imageUri != null) {
                        uploadImageToSupabase(imageUri);
                    }
                });

        // Document Launcher
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

        // Audio Launcher
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

        // Contact Launcher
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

        // Permission Launcher (Camera)
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

        // Location Permission Launcher
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

        // Gallery Permission Launcher (For Android 13+)
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

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        currentUserId = auth.getCurrentUser().getUid();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        layoutGroupHeader = findViewById(R.id.layout_group_header);
        ivGroupIcon = findViewById(R.id.iv_group_icon);
        tvGroupName = findViewById(R.id.tv_group_name);
        tvMemberCount = findViewById(R.id.tv_member_count);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnAttach = findViewById(R.id.btn_attach);
        btnEmoji = findViewById(R.id.btn_emoji);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        messageAdapter = new MessageAdapter(messageList, this);
        rvMessages.setAdapter(messageAdapter);

        // ==================== FIXED: SET GROUP ID FIRST ====================
        messageAdapter.setCurrentChatId(groupId);
        Log.d(TAG, "Chat ID set to: " + groupId);

        // ==================== SETUP SWIPE TO DELETE ====================
        SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this, messageAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(rvMessages);

        messageAdapter.setDeleteListener(message -> {
            Log.d(TAG, "✅ Message deleted callback: " + message.getMessageId());
        });

        if (groupName != null) {
            tvGroupName.setText(groupName);
        }
    }

    private void setupListeners() {
        // Back button
        toolbar.setNavigationOnClickListener(v -> finish());

        // Group header touch handler
        layoutGroupHeader.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (event.getX() < dpToPx(80)) {
                    return false;
                }
                openGroupDetails();
                return true;
            }
            return false;
        });

        layoutGroupHeader.setOnClickListener(v -> openGroupDetails());

        // Send message
        btnSend.setOnClickListener(v -> sendMessage());

        // Attach file
        btnAttach.setOnClickListener(v -> showAttachmentBottomSheet());

        // Emoji picker
        btnEmoji.setOnClickListener(v -> showEmojiPicker());
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

    private void openGroupDetails() {
        Intent intent = new Intent(this, GroupDetailsActivity.class);
        intent.putExtra("groupId", groupId);
        startActivity(intent);
    }

    private void loadGroupInfo() {
        db.collection("groups")
                .document(groupId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(GroupChatActivity.this, "Failed to load group info", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String iconUrl = documentSnapshot.getString("icon");
                        Map<String, Boolean> members = (Map<String, Boolean>) documentSnapshot.get("members");

                        if (name != null) {
                            tvGroupName.setText(name);
                            groupName = name;
                        }
                        if (iconUrl != null && !iconUrl.isEmpty()) {
                            groupIcon = iconUrl;
                            Glide.with(GroupChatActivity.this)
                                    .load(iconUrl)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_group)
                                    .into(ivGroupIcon);
                        }
                        if (members != null) {
                            int memberCount = members.size();
                            tvMemberCount.setText(memberCount + (memberCount == 1 ? " member" : " members"));
                        }
                    }
                });
    }

    // ✅ FIXED GROUP CHAT ACTIVITY - Key change in loadMessages()

    private void loadMessages() {
        // ==================== SET CHAT TYPE AND ID FOR SWIPE DELETE ====================
        messageAdapter.setCurrentChatId(groupId);
        messageAdapter.setChatType("group");  // ✅ Important: Set chat type to "group"
        Log.d(TAG, "Loading messages for group: " + groupId);

        db.collection("groups")
                .document(groupId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to load messages: " + error.getMessage());
                        Toast.makeText(GroupChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (querySnapshot != null) {
                        List<Message> newMessages = new ArrayList<>();
                        for (var documentSnapshot : querySnapshot.getDocuments()) {
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

                        // ✅ CRITICAL FIX: Use setMessages() instead of clearing and notifying
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

        btnSend.setEnabled(false);

        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentUserName = documentSnapshot.exists()
                            ? documentSnapshot.getString("name")
                            : "Unknown User";
                    String currentUserAvatar = documentSnapshot.exists()
                            ? documentSnapshot.getString("avatarUrl")
                            : "";

                    // Create message with Message model
                    Message message = new Message(
                            currentUserId,
                            groupId,
                            messageText,
                            Message.MessageType.TEXT
                    );
                    message.setSenderName(currentUserName);
                    message.setSenderAvatarUrl(currentUserAvatar);
                    message.setTimestamp(System.currentTimeMillis());

                    db.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(unused -> {
                                etMessage.setText("");
                                btnSend.setEnabled(true);

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("lastMessage", messageText);
                                updates.put("lastMessageTime", System.currentTimeMillis());

                                db.collection("groups")
                                        .document(groupId)
                                        .update(updates)
                                        .addOnFailureListener(e ->
                                                Log.e(TAG, "Failed to update group: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> {
                                btnSend.setEnabled(true);
                                Toast.makeText(GroupChatActivity.this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(GroupChatActivity.this, "Failed to get user info", Toast.LENGTH_SHORT).show();
                });
    }

    // ==================== NETWORK CHECK ====================

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

    // ==================== ATTACHMENT BOTTOM SHEET ====================

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

    // ==================== IMAGE HANDLING ====================

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
                if (inputStream == null) return;

                byte[] imageData = readBytes(inputStream);
                String fileName = "group_" + groupId + "_" + System.currentTimeMillis() + ".jpg";
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
                    runOnUiThread(() -> Toast.makeText(GroupChatActivity.this, "Upload failed", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(GroupChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendImageMessage(String imageUrl) {
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String senderName = documentSnapshot.exists()
                            ? documentSnapshot.getString("name")
                            : "Unknown User";
                    String senderAvatar = documentSnapshot.exists()
                            ? documentSnapshot.getString("avatarUrl")
                            : "";

                    Message message = new Message(
                            currentUserId,
                            groupId,
                            "",
                            Message.MessageType.IMAGE
                    );
                    message.setFileUrl(imageUrl);
                    message.setSenderName(senderName);
                    message.setSenderAvatarUrl(senderAvatar);
                    message.setTimestamp(System.currentTimeMillis());

                    db.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> {
                                Log.d(TAG, "✅ Image sent");
                                Toast.makeText(this, "Image sent", Toast.LENGTH_SHORT).show();
                            });
                });
    }

    // ==================== DOCUMENT HANDLING ====================

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        documentLauncher.launch(intent);
    }

    private void uploadDocumentToSupabase(Uri documentUri) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading document...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(documentUri);
                if (inputStream == null) return;

                byte[] fileData = readBytes(inputStream);
                String fileName = getFileName(documentUri);
                String filePath = "documents/" + groupId + "_" + System.currentTimeMillis() + "_" + fileName;
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_DOCUMENTS + "/" + filePath;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(fileData, MediaType.parse("application/octet-stream"));

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
                    runOnUiThread(() -> sendDocumentMessage(documentUrl, fileName));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }).start();
    }

    private void sendDocumentMessage(String documentUrl, String fileName) {
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String senderName = documentSnapshot.exists()
                            ? documentSnapshot.getString("name")
                            : "Unknown User";
                    String senderAvatar = documentSnapshot.exists()
                            ? documentSnapshot.getString("avatarUrl")
                            : "";

                    Message message = new Message(
                            currentUserId,
                            groupId,
                            fileName,
                            Message.MessageType.FILE
                    );
                    message.setFileUrl(documentUrl);
                    message.setSenderName(senderName);
                    message.setSenderAvatarUrl(senderAvatar);
                    message.setTimestamp(System.currentTimeMillis());

                    db.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> Log.d(TAG, "✅ Document sent"));
                });
    }

    // ==================== AUDIO HANDLING ====================

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        audioLauncher.launch(intent);
    }

    private void uploadAudioToSupabase(Uri audioUri) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading audio...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(audioUri);
                if (inputStream == null) return;

                byte[] audioData = readBytes(inputStream);
                String fileName = "audio_" + groupId + "_" + System.currentTimeMillis() + ".m4a";
                String filePath = "audio/" + fileName;
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_AUDIO + "/" + filePath;

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(audioData, MediaType.parse("audio/mp4"));

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader("Content-Type", "audio/mp4")
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("apikey", SUPABASE_KEY)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String audioUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_AUDIO + "/" + filePath;
                    runOnUiThread(() -> sendAudioMessage(audioUrl));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }).start();
    }

    private void sendAudioMessage(String audioUrl) {
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String senderName = documentSnapshot.exists()
                            ? documentSnapshot.getString("name")
                            : "Unknown User";
                    String senderAvatar = documentSnapshot.exists()
                            ? documentSnapshot.getString("avatarUrl")
                            : "";

                    Message message = new Message(
                            currentUserId,
                            groupId,
                            "",
                            Message.MessageType.AUDIO
                    );
                    message.setFileUrl(audioUrl);
                    message.setSenderName(senderName);
                    message.setSenderAvatarUrl(senderAvatar);
                    message.setTimestamp(System.currentTimeMillis());

                    db.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> Log.d(TAG, "✅ Audio sent"));
                });
    }

    // ==================== LOCATION HANDLING ====================

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
                final double latitude = location.getLatitude();
                final double longitude = location.getLongitude();

                db.collection("users")
                        .document(currentUserId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String senderName = documentSnapshot.exists()
                                    ? documentSnapshot.getString("name")
                                    : "Unknown User";
                            String senderAvatar = documentSnapshot.exists()
                                    ? documentSnapshot.getString("avatarUrl")
                                    : "";

                            Message message = new Message(
                                    currentUserId,
                                    groupId,
                                    latitude + "," + longitude,
                                    Message.MessageType.LOCATION
                            );
                            message.setSenderName(senderName);
                            message.setSenderAvatarUrl(senderAvatar);
                            message.setTimestamp(System.currentTimeMillis());

                            db.collection("groups")
                                    .document(groupId)
                                    .collection("messages")
                                    .add(message)
                                    .addOnSuccessListener(ref -> Log.d(TAG, "✅ Location sent"));
                        });
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Location error: " + e.getMessage());
            Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== CONTACT HANDLING ====================

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
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String senderName = documentSnapshot.exists()
                            ? documentSnapshot.getString("name")
                            : "Unknown User";
                    String senderAvatar = documentSnapshot.exists()
                            ? documentSnapshot.getString("avatarUrl")
                            : "";

                    Message message = new Message(
                            currentUserId,
                            groupId,
                            contactName + "|" + phoneNumber,
                            Message.MessageType.FILE
                    );
                    message.setSenderName(senderName);
                    message.setSenderAvatarUrl(senderAvatar);
                    message.setTimestamp(System.currentTimeMillis());

                    db.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> Log.d(TAG, "✅ Contact sent"));
                });
    }

    // ==================== UTILITY METHODS ====================

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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}