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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.adapter.GroupMemberAdapter;
import com.example.project_ez_talk.model.GroupMember;
import com.example.project_ez_talk.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.Query;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
public class GroupDetailsActivity extends BaseActivity {

    private static final String TAG = "GroupDetailsActivity";

    private Toolbar toolbar;
    private ImageView btnEditGroup;
    private ShapeableImageView ivGroupIcon;
    private TextView tvGroupName, tvMemberCount, tvGroupDescription;
    private LinearLayout btnVoiceCall, btnVideoCall, btnSearch, btnMedia;
    private LinearLayout btnMuteNotifications, btnReportGroup, btnExitGroup;
    private SwitchMaterial switchMute;
    private RecyclerView rvMembers;

    private NestedScrollView nestedScrollView;
    private LinearLayout settingsSection;
    private ImageView btnScrollToSettings;

    private String groupId;
    private String currentUserId;
    private boolean isAdmin = false;

    private GroupMemberAdapter memberAdapter;
    private List<GroupMember> memberList = new ArrayList<>();

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // ==================== ACTIVITY RESULT LAUNCHERS ====================
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> documentLauncher;
    private ActivityResultLauncher<Intent> audioLauncher;
    private ActivityResultLauncher<Intent> contactLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> galleryPermissionLauncher;

    private Uri imageUri;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, "Invalid group", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ==================== SETUP LAUNCHERS ====================
        setupActivityResultLaunchers();

        initFirebase();
        initViews();
        setupToolbar();
        setupListeners();
        loadGroupDetails();
        loadMembers();
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
                            Log.d(TAG, "Gallery image selected: " + imageUri);
                            Toast.makeText(this, "Image selected (upload coming soon)", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Camera Launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && imageUri != null) {
                        Log.d(TAG, "Camera photo captured: " + imageUri);
                        Toast.makeText(this, "Photo captured (upload coming soon)", Toast.LENGTH_SHORT).show();
                    }
                });

        // Document Launcher
        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            Log.d(TAG, "Document selected: " + fileUri);
                            Toast.makeText(this, "Document selected (upload coming soon)", Toast.LENGTH_SHORT).show();
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
                            Log.d(TAG, "Audio selected: " + audioUri);
                            Toast.makeText(this, "Audio selected (upload coming soon)", Toast.LENGTH_SHORT).show();
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
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        try {
            toolbar = findViewById(R.id.toolbar);
            ivGroupIcon = findViewById(R.id.iv_group_icon);
            tvGroupName = findViewById(R.id.tv_group_name);
            tvMemberCount = findViewById(R.id.tv_member_count);
            tvGroupDescription = findViewById(R.id.tv_group_description);

            btnVoiceCall = findViewById(R.id.btn_voice_call);
            btnVideoCall = findViewById(R.id.btn_video_call);
            btnSearch = findViewById(R.id.btn_search);
            btnMedia = findViewById(R.id.btn_media);

            rvMembers = findViewById(R.id.rv_members);

            btnMuteNotifications = findViewById(R.id.btn_mute_notifications);
            switchMute = findViewById(R.id.switch_mute);
            btnReportGroup = findViewById(R.id.btn_report_group);
            btnExitGroup = findViewById(R.id.btn_exit_group);

            nestedScrollView = findViewById(R.id.nested_scroll_view);
            settingsSection = findViewById(R.id.settings_section);
            btnScrollToSettings = findViewById(R.id.btn_scroll_to_settings);

            // Setup RecyclerView
            if (rvMembers != null) {
                rvMembers.setLayoutManager(new LinearLayoutManager(this));
                memberAdapter = new GroupMemberAdapter(memberList);
                rvMembers.setAdapter(memberAdapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupToolbar() {
        if (toolbar == null) {
            Toast.makeText(this, "Toolbar not found", Toast.LENGTH_SHORT).show();
            return;
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        btnEditGroup = new ImageView(this);
        btnEditGroup.setImageResource(R.drawable.ic_edit);
        btnEditGroup.setContentDescription("Edit Group");

        Toolbar.LayoutParams params = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
        btnEditGroup.setLayoutParams(params);
        btnEditGroup.setPadding(16, 16, 16, 16);
        btnEditGroup.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        btnEditGroup.setOnClickListener(v -> openEditGroupBottomSheet());

        toolbar.addView(btnEditGroup);
        btnEditGroup.setVisibility(View.GONE);
    }

    private void setupListeners() {
        try {
            if (btnScrollToSettings != null) {
                btnScrollToSettings.setOnClickListener(v -> scrollToSettings());
            }

            if (btnVoiceCall != null) {
                btnVoiceCall.setOnClickListener(v -> initiateGroupVoiceCall());
            }
            if (btnVideoCall != null) {
                btnVideoCall.setOnClickListener(v -> initiateGroupVideoCall());
            }
            if (btnSearch != null) {
                btnSearch.setOnClickListener(v -> openSearchMessages());
            }
            if (btnMedia != null) {
                btnMedia.setOnClickListener(v -> openMediaGallery());
            }

            if (switchMute != null) {
                switchMute.setOnCheckedChangeListener((buttonView, isChecked) -> updateMuteStatus(isChecked));
            }
            if (btnMuteNotifications != null) {
                btnMuteNotifications.setOnClickListener(v -> {
                    if (switchMute != null) {
                        switchMute.toggle();
                    }
                });
            }

            if (btnReportGroup != null) {
                btnReportGroup.setOnClickListener(v -> showReportDialog());
            }
            if (btnExitGroup != null) {
                btnExitGroup.setOnClickListener(v -> showExitGroupDialog());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners: " + e.getMessage());
        }
    }

    private void scrollToSettings() {
        if (nestedScrollView != null && settingsSection != null) {
            nestedScrollView.post(() -> nestedScrollView.smoothScrollTo(0, settingsSection.getTop()));
        }
    }

    @SuppressLint("SetTextI18n")
    private void loadGroupDetails() {
        db.collection("groups")
                .document(groupId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading group: " + error.getMessage());
                        return;
                    }

                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    try {
                        String name = documentSnapshot.getString("name");
                        String description = documentSnapshot.getString("description");
                        String iconUrl = documentSnapshot.getString("icon");
                        Map<String, Boolean> members = (Map<String, Boolean>) documentSnapshot.get("members");
                        Map<String, String> memberRoles = (Map<String, String>) documentSnapshot.get("memberRoles");

                        if (name != null && tvGroupName != null) {
                            tvGroupName.setText(name);
                        }

                        if (description != null && !description.isEmpty() && tvGroupDescription != null) {
                            tvGroupDescription.setText(description);
                            tvGroupDescription.setVisibility(View.VISIBLE);
                        } else if (tvGroupDescription != null) {
                            tvGroupDescription.setVisibility(View.GONE);
                        }

                        if (iconUrl != null && !iconUrl.isEmpty() && ivGroupIcon != null) {
                            Glide.with(this)
                                    .load(iconUrl)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_group)
                                    .into(ivGroupIcon);
                        }

                        if (members != null && tvMemberCount != null) {
                            int count = members.size();
                            tvMemberCount.setText(count + (count == 1 ? " member" : " members"));
                        }

                        if (memberRoles != null && currentUserId != null) {
                            String role = memberRoles.get(currentUserId);
                            isAdmin = "admin".equals(role);

                            if (btnEditGroup != null) {
                                btnEditGroup.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                                Log.d(TAG, "User is admin: " + isAdmin);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing group data: " + e.getMessage());
                    }
                });
    }

    private void loadMembers() {
        db.collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        return;
                    }

                    try {
                        Map<String, Boolean> members = (Map<String, Boolean>) documentSnapshot.get("members");
                        Map<String, String> memberRoles = (Map<String, String>) documentSnapshot.get("memberRoles");

                        if (members == null) {
                            return;
                        }

                        memberList.clear();
                        int total = members.size();
                        final int[] loaded = {0};

                        for (String userId : members.keySet()) {
                            db.collection("users")
                                    .document(userId)
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        try {
                                            if (userDoc.exists()) {
                                                String name = userDoc.getString("name");
                                                String avatar = userDoc.getString("profilePicture");
                                                boolean admin = memberRoles != null && "admin".equals(memberRoles.get(userId));

                                                memberList.add(new GroupMember(
                                                        userId,
                                                        name != null ? name : "Unknown User",
                                                        avatar != null ? avatar : "",
                                                        admin
                                                ));
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error parsing user: " + e.getMessage());
                                        }
                                        loaded[0]++;
                                        if (loaded[0] == total && memberAdapter != null) {
                                            memberAdapter.notifyDataSetChanged();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        loaded[0]++;
                                        if (loaded[0] == total && memberAdapter != null) {
                                            memberAdapter.notifyDataSetChanged();
                                        }
                                    });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading members: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get group: " + e.getMessage());
                });
    }

    private void openEditGroupBottomSheet() {
        if (!isAdmin) {
            Toast.makeText(this, "Only admins can edit group", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            GroupSettingsBottomSheet bottomSheet = GroupSettingsBottomSheet.newInstance(groupId);
            if (bottomSheet != null && getSupportFragmentManager() != null) {
                bottomSheet.show(getSupportFragmentManager(), "GroupSettings");
                Log.d(TAG, "Bottom sheet shown successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing bottom sheet: " + e.getMessage());
            Toast.makeText(this, "Error opening settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ATTACHMENT METHODS ====================

    private void openGallery() {
        // For Android 13+ (API 33+), use READ_MEDIA_IMAGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                launchGalleryPicker();
            } else {
                galleryPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // For Android 12 and below
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

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        audioLauncher.launch(intent);
    }

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
                Log.d(TAG, "Location: " + latitude + ", " + longitude);
                Toast.makeText(this, "Location: " + latitude + ", " + longitude, Toast.LENGTH_SHORT).show();
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

                Log.d(TAG, "Contact: " + contactName + ", Phone: " + phoneNumber);
                Toast.makeText(this, "Contact: " + contactName, Toast.LENGTH_SHORT).show();
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading contact: " + e.getMessage());
            Toast.makeText(this, "Error reading contact", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ATTACHMENT BOTTOM SHEET ====================

    public void showAttachmentBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
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

    // ==================== SETTINGS METHODS ====================

    private void updateMuteStatus(boolean muted) {
        if (currentUserId == null || groupId == null) {
            return;
        }

        try {
            db.collection("users")
                    .document(currentUserId)
                    .collection("groupSettings")
                    .document(groupId)
                    .set(new java.util.HashMap<String, Object>() {{ put("muted", muted); }},
                            com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this, muted ? "Notifications muted" : "Notifications unmuted",
                                    Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update settings", Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error updating mute status: " + e.getMessage());
        }
    }

    private void showReportDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Report Group")
                .setMessage("Are you sure you want to report this group?")
                .setPositiveButton("Report", (dialog, which) ->
                        Toast.makeText(this, "Group reported", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExitGroupDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit Group")
                .setMessage("Are you sure you want to exit this group?")
                .setPositiveButton("Exit", (dialog, which) -> exitGroup())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exitGroup() {
        if (groupId == null || currentUserId == null) {
            Toast.makeText(this, "Invalid group or user", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        Map<String, Boolean> members = (Map<String, Boolean>) documentSnapshot.get("members");
                        Map<String, String> memberRoles = (Map<String, String>) documentSnapshot.get("memberRoles");

                        if (members != null) {
                            members.remove(currentUserId);
                        }
                        if (memberRoles != null) {
                            memberRoles.remove(currentUserId);
                        }

                        db.collection("groups")
                                .document(groupId)
                                .update("members", members, "memberRoles", memberRoles)
                                .addOnSuccessListener(unused -> {
                                    db.collection("users")
                                            .document(currentUserId)
                                            .collection("chats")
                                            .document(groupId)
                                            .delete()
                                            .addOnSuccessListener(u -> {
                                                Toast.makeText(this, "You left the group", Toast.LENGTH_SHORT).show();
                                                finish();
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to exit group", Toast.LENGTH_SHORT).show();
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "Error exiting group: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to exit group", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Initiate group voice call (placeholder)
     */
    private void initiateGroupVoiceCall() {
        Toast.makeText(this, "Starting voice call with " + memberList.size() + " members...", Toast.LENGTH_SHORT).show();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Group Voice Call")
                .setMessage("Group voice calls support up to " + memberList.size() + " participants. This feature will be available in the next update!")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Initiate group video call (placeholder)
     */
    private void initiateGroupVideoCall() {
        Toast.makeText(this, "Starting video call with " + memberList.size() + " members...", Toast.LENGTH_SHORT).show();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Group Video Call")
                .setMessage("Group video calls support up to " + memberList.size() + " participants. This feature will be available in the next update!")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Open search messages dialog
     */
    private void openSearchMessages() {
        android.widget.EditText searchInput = new android.widget.EditText(this);
        searchInput.setHint("Search messages...");
        searchInput.setPadding(50, 30, 50, 30);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Search Messages")
                .setView(searchInput)
                .setPositiveButton("Search", (dialog, which) -> {
                    String query = searchInput.getText().toString().trim();
                    if (!query.isEmpty()) {
                        searchMessagesInGroup(query);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Search messages in group
     */
    private void searchMessagesInGroup(String query) {
        db.collection("groups")
                .document(groupId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> results = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String messageText = doc.getString("messageText");
                        String senderName = doc.getString("senderName");
                        if (messageText != null && messageText.toLowerCase().contains(query.toLowerCase())) {
                            results.add(senderName + ": " + messageText);
                        }
                    }

                    if (results.isEmpty()) {
                        Toast.makeText(this, "No messages found", Toast.LENGTH_SHORT).show();
                    } else {
                        showSearchResults(results);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Search error: " + e.getMessage());
                });
    }

    /**
     * Show search results
     */
    private void showSearchResults(List<String> results) {
        String[] resultsArray = results.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Search Results (" + results.size() + ")")
                .setItems(resultsArray, null)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Open media gallery for group
     */
    private void openMediaGallery() {
        db.collection("groups")
                .document(groupId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> mediaUrls = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String messageType = doc.getString("messageType");
                        if ("image".equals(messageType) || "video".equals(messageType)) {
                            String mediaUrl = doc.getString("mediaUrl");
                            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                                mediaUrls.add(mediaUrl);
                            }
                        }
                    }

                    if (mediaUrls.isEmpty()) {
                        Toast.makeText(this, "No media found in this group", Toast.LENGTH_SHORT).show();
                    } else {
                        showMediaGallery(mediaUrls);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load media", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Media load error: " + e.getMessage());
                });
    }

    /**
     * Show media gallery
     */
    private void showMediaGallery(List<String> mediaUrls) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Media Gallery")
                .setMessage("Found " + mediaUrls.size() + " photos and videos in this group")
                .setPositiveButton("View", (dialog, which) -> {
                    // TODO: Open media viewer activity
                    Toast.makeText(this, "Media viewer opening...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}