package com.example.project_ez_talk.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.project_ez_talk.R;
import com.example.project_ez_talk.ui.auth.login.LoginActivity;
import com.example.project_ez_talk.ui.profile.EditProfileActivity;
import com.example.project_ez_talk.ui.profile.SettingsActivity;
import com.example.project_ez_talk.utils.Preferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private MaterialCardView cvProfilePicture;
    private ImageView ivProfilePicture;
    private FloatingActionButton fabEditPhoto;
    private TextView tvUserName, tvUserEmail;
    private MaterialButton btnEditProfile;
    private LinearLayout llAccount, llPrivacy, llSettings;
    private MaterialButton btnLogout;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();

        initViews(view);
        loadUserProfile();
        setupClickListeners();
    }

    private void initViews(View view) {
        cvProfilePicture = view.findViewById(R.id.cvProfilePicture);
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture);
        fabEditPhoto = view.findViewById(R.id.fabEditPhoto);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        llAccount = view.findViewById(R.id.llAccount);
        llPrivacy = view.findViewById(R.id.llPrivacy);
        llSettings = view.findViewById(R.id.llSettings);
        btnLogout = view.findViewById(R.id.btnLogout);
    }

    private void loadUserProfile() {
        if (currentUser == null) return;

        // Load from Firestore
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;

                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String email = documentSnapshot.getString("email");
                        String profilePic = documentSnapshot.getString("profilePicture");

                        // Set user info
                        if (tvUserName != null) {
                            tvUserName.setText(name != null && !name.isEmpty() ? name : "User");
                        }

                        if (tvUserEmail != null) {
                            tvUserEmail.setText(email != null ? email : "No email");
                        }

                        // Load profile picture
                        if (profilePic != null && !profilePic.isEmpty() && ivProfilePicture != null) {
                            Log.d("ProfileFragment", "📸 Loading profile picture: " + profilePic);
                            Glide.with(this)
                                    .load(profilePic)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                            Log.e("ProfileFragment", "❌ Failed to load profile image", e);
                                            if (e != null) {
                                                Log.e("ProfileFragment", "Error: " + e.getMessage());
                                            }
                                            return false;
                                        }

                                        @Override
                                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                            Log.d("ProfileFragment", "✅ Profile image loaded successfully!");
                                            return false;
                                        }
                                    })
                                    .into(ivProfilePicture);
                        } else {
                            Log.w("ProfileFragment", "⚠️ No profile picture URL found");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback to Firebase Auth
                    if (currentUser != null) {
                        String displayName = currentUser.getDisplayName();
                        String email = currentUser.getEmail();

                        if (tvUserName != null) {
                            tvUserName.setText(displayName != null ? displayName : "User");
                        }
                        if (tvUserEmail != null) {
                            tvUserEmail.setText(email != null ? email : "No email");
                        }
                    }
                });
    }

    private void setupClickListeners() {
        // Edit photo - opens EditProfileActivity
        if (fabEditPhoto != null) {
            fabEditPhoto.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), EditProfileActivity.class);
                startActivity(intent);
            });
        }

        // Edit profile
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), EditProfileActivity.class);
                startActivity(intent);
            });
        }

        // Account settings
        if (llAccount != null) {
            llAccount.setOnClickListener(v -> {
                // TODO: Navigate to account settings
            });
        }

        // Privacy settings
        if (llPrivacy != null) {
            llPrivacy.setOnClickListener(v -> {
                // TODO: Navigate to privacy settings
            });
        }

        // General settings
        if (llSettings != null) {
            llSettings.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                startActivity(intent);
            });
        }

        // Logout
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> showLogoutDialog());
        }
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        // Sign out from Firebase
        mAuth.signOut();

        // Clear preferences
        Preferences.clearAll(requireContext());

        // Navigate to login
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload profile when returning to fragment
        loadUserProfile();
    }
}