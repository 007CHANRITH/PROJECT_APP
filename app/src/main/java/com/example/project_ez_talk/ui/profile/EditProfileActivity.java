package com.example.project_ez_talk.ui.profile;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.helper.SupabaseStorageManager;
import com.example.project_ez_talk.ui.BaseActivity;
import com.example.project_ez_talk.utils.Preferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ALL")
public class EditProfileActivity extends BaseActivity {

    private static final String TAG = "EditProfileActivity";

    private MaterialToolbar toolbar;
    private CardView cvProfilePicture;
    private ImageView ivProfilePicture;
    private FloatingActionButton fabChangePhoto;
    private EditText etFullName, etUsername, etEmail, etPhone, etStatus;
    private MaterialButton btnSave;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private Uri selectedImageUri;
    private String currentProfileImageUrl;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        
        // Initialize SupabaseStorageManager
        SupabaseStorageManager.init(this);

        initViews();
        setupImagePicker();
        setupListeners();
        loadUserData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        cvProfilePicture = findViewById(R.id.cvProfilePicture);
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        fabChangePhoto = findViewById(R.id.fabChangePhoto);
        etFullName = findViewById(R.id.etFullName);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etStatus = findViewById(R.id.etStatus);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);

        setSupportActionBar(toolbar);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        ivProfilePicture.setImageURI(uri);
                    }
                }
        );
    }

    private void setupListeners() {
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Click FAB to change photo
        fabChangePhoto.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        // Click profile picture to change photo
        cvProfilePicture.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        // Click image to change photo
        ivProfilePicture.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        btnSave.setOnClickListener(v -> saveProfile());
    }

    @SuppressLint("SetTextI18n")
    private void loadUserData() {
        if (currentUser == null) return;

        // Load from Firestore
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String email = documentSnapshot.getString("email");
                        String phone = documentSnapshot.getString("phone");
                        String status = documentSnapshot.getString("status");
                        String profilePic = documentSnapshot.getString("profilePicture");

                        // Set text fields
                        if (name != null && !name.isEmpty()) {
                            etFullName.setText(name);
                        }
                        if (email != null && !email.isEmpty()) {
                            etEmail.setText(email);
                        }
                        if (phone != null && !phone.isEmpty()) {
                            etPhone.setText(phone);
                        }
                        if (status != null && !status.isEmpty()) {
                            etStatus.setText(status);
                        } else {
                            etStatus.setText("Hey there! I'm using EZ Talk");
                        }

                        // Load profile picture
                        if (profilePic != null && !profilePic.isEmpty()) {
                            currentProfileImageUrl = profilePic;
                            Glide.with(this)
                                    .load(profilePic)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_profile)
                                    .into(ivProfilePicture);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user data", e);
                    // Fallback to Firebase Auth
                    if (currentUser != null) {
                        String displayName = currentUser.getDisplayName();
                        String email = currentUser.getEmail();

                        if (displayName != null && !displayName.isEmpty()) {
                            etFullName.setText(displayName);
                        }
                        if (email != null && !email.isEmpty()) {
                            etEmail.setText(email);
                        }
                    }
                });
    }

    private void saveProfile() {
        // Get input values
        String fullName = etFullName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String status = etStatus.getText().toString().trim();

        // Validate
        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email format");
            etEmail.requestFocus();
            return;
        }

        showLoading(true);

        // Check if image was selected
        if (selectedImageUri != null) {
            // Upload image to Supabase first
            uploadImageAndSaveProfile(fullName, username, email, phone, status);
        } else {
            // No image selected, just update profile
            updateProfileWithoutImage(fullName, username, email, phone, status);
        }
    }

    /**
     * Upload image to Supabase Storage, then save profile
     */
    private void uploadImageAndSaveProfile(String fullName, String username,
                                           String email, String phone, String status) {
        if (currentUser == null) {
            showLoading(false);
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();

        Log.d(TAG, "📤 Uploading image to Supabase Storage...");

        SupabaseStorageManager.uploadProfileImage(
                selectedImageUri,
                userId,
                new SupabaseStorageManager.UploadCallback() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        Log.d(TAG, "✅ Image uploaded successfully!");
                        Log.d(TAG, "📥 Public URL: " + publicUrl);

                        // Save profile with image URL
                        saveProfileData(fullName, username, email, phone, status, publicUrl);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Image upload failed: " + error);
                        showLoading(false);
                        Toast.makeText(EditProfileActivity.this,
                                "Failed to upload image: " + error,
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Save all profile data to Firestore
     */
    private void saveProfileData(String fullName, String username,
                                 String email, String phone, String status, String imageUrl) {
        if (currentUser == null) {
            showLoading(false);
            return;
        }

        String userId = currentUser.getUid();

        // Prepare data for Firestore
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", fullName);
        userData.put("username", username);  // ✅ ADDED: Save username field
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("status", status);
        if (imageUrl != null) {
            userData.put("profilePicture", imageUrl);
        }
        userData.put("updatedAt", System.currentTimeMillis());

        Log.d(TAG, "💾 Saving profile to Firestore...");
        Log.d(TAG, "📝 Username: " + username);  // ✅ ADDED: Debug log

        // Save to Firestore
        db.collection("users")
                .document(userId)
                .update(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Profile saved to Firestore!");

                    // Also update Firebase Auth display name
                    UserProfileChangeRequest.Builder profileBuilder = new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName);
                    
                    if (imageUrl != null) {
                        profileBuilder.setPhotoUri(Uri.parse(imageUrl));
                    }

                    currentUser.updateProfile(profileBuilder.build())
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Log.d(TAG, "✅ Firebase Auth profile updated!");
                                }
                            });

                    // Save to SharedPreferences
                    Preferences.setUsername(this, fullName);
                    Preferences.setUserEmail(this, email);
                    Preferences.setUserPhone(this, phone);
                    if (imageUrl != null) {
                        Preferences.setUserPhotoUrl(this, imageUrl);
                    }

                    showLoading(false);
                    Toast.makeText(this, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save profile", e);
                    showLoading(false);
                    Toast.makeText(this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Update profile without image (use existing image if available)
     */
    private void updateProfileWithoutImage(String fullName, String username,
                                           String email, String phone, String status) {
        // Use existing profile image URL if available
        saveProfileData(fullName, username, email, phone, status, currentProfileImageUrl);
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setText(loading ? "" : getString(R.string.save));
        btnSave.setEnabled(!loading);

        etFullName.setEnabled(!loading);
        etUsername.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPhone.setEnabled(!loading);
        etStatus.setEnabled(!loading);
        fabChangePhoto.setEnabled(!loading);
    }

    public CardView getCvProfilePicture() {
        return cvProfilePicture;
    }

    public void setCvProfilePicture(CardView cvProfilePicture) {
        this.cvProfilePicture = cvProfilePicture;
    }
}