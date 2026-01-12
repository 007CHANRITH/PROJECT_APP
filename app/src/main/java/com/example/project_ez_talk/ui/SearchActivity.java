package com.example.project_ez_talk.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.project_ez_talk.R;
import com.example.project_ez_talk.adapter.SearchResultAdapter;
import com.example.project_ez_talk.model.SearchResult;
import com.example.project_ez_talk.ui.channel.ChannelDetailActivity;
import com.example.project_ez_talk.ui.chat.detail.ChatDetailActivity;
import com.example.project_ez_talk.ui.chat.group.GroupChatActivity;
import com.example.project_ez_talk.ui.dialog.UserProfileDialog;  // ✅ ADDED
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchActivity extends BaseActivity {

    private static final String TAG = "SearchActivity";

    private MaterialToolbar toolbar;
    private EditText etSearch;
    private ImageView btnClear;
    private ChipGroup chipGroupFilter;
    private RecyclerView rvSearchResults;
    private View layoutEmpty;

    private SearchResultAdapter adapter;
    private List<SearchResult> searchResults = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUserId;

    // Filter state
    private String currentFilter = "all"; // "all", "users", "groups", "channels"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        initViews();
        setupRecyclerView();
        setupFilters();
        setupListeners();

        etSearch.requestFocus();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        etSearch = findViewById(R.id.et_search);
        btnClear = findViewById(R.id.btn_clear);
        chipGroupFilter = findViewById(R.id.chip_group_filter);
        rvSearchResults = findViewById(R.id.rv_search_results);
        layoutEmpty = findViewById(R.id.layout_empty);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupRecyclerView() {
        adapter = new SearchResultAdapter(searchResults, this);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(adapter);

        // ✅ UPDATED: Show UserProfileDialog for users
        adapter.setOnItemClickListener(result -> {
            switch (result.getType()) {
                case "user":
                    showUserProfileDialog(result);  // ✅ CHANGED
                    break;
                case "group":
                    openGroup(result);
                    break;
                case "channel":
                    openChannel(result);
                    break;
            }
        });
    }

    private void setupFilters() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentFilter = "all";
            } else {
                int checkedId = checkedIds.get(0);
                Chip selectedChip = findViewById(checkedId);
                if (selectedChip != null) {
                    String chipText = selectedChip.getText().toString().toLowerCase();
                    if (chipText.contains("user")) {
                        currentFilter = "users";
                    } else if (chipText.contains("group")) {
                        currentFilter = "groups";
                    } else if (chipText.contains("channel")) {
                        currentFilter = "channels";
                    } else {
                        currentFilter = "all";
                    }
                }
            }

            // Re-run search with new filter
            String query = etSearch.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch(query);
            }
        });
    }

    private void setupListeners() {
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() > 0) {
                    btnClear.setVisibility(View.VISIBLE);
                    performSearch(query);
                } else {
                    btnClear.setVisibility(View.GONE);
                    searchResults.clear();
                    adapter.notifyDataSetChanged();
                    showEmptyState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.requestFocus();
        });
    }

    private void performSearch(String query) {
        String lowerQuery = query.toLowerCase();
        searchResults.clear();
        
        // Track how many search types are pending
        final int[] pendingSearches = {0};
        
        // Count how many searches we need to perform
        if ("all".equals(currentFilter)) {
            pendingSearches[0] = 3; // users, groups, channels
        } else if ("users".equals(currentFilter)) {
            pendingSearches[0] = 1;
        } else if ("groups".equals(currentFilter)) {
            pendingSearches[0] = 1;
        } else if ("channels".equals(currentFilter)) {
            pendingSearches[0] = 1;
        }
        
        // Create callback to update UI after all searches complete
        Runnable onSearchComplete = () -> {
            pendingSearches[0]--;
            if (pendingSearches[0] == 0) {
                updateResultsUI();
            }
        };

        // Search based on filter
        if ("all".equals(currentFilter) || "users".equals(currentFilter)) {
            searchUsers(lowerQuery, onSearchComplete);
        }
        if ("all".equals(currentFilter) || "groups".equals(currentFilter)) {
            searchGroups(lowerQuery, onSearchComplete);
        }
        if ("all".equals(currentFilter) || "channels".equals(currentFilter)) {
            searchChannels(lowerQuery, onSearchComplete);
        }
    }

    // ==================== SEARCH USERS ====================
    private void searchUsers(String query, Runnable onComplete) {
        db.collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot.getDocuments()) {
                        String userId = doc.getId();

                        // Don't show current user
                        if (userId.equals(currentUserId)) continue;

                        String name = doc.getString("name");
                        String email = doc.getString("email");
                        String profilePic = doc.getString("profilePicture");
                        String bio = doc.getString("bio");  // ✅ ADDED
                        Boolean isOnline = doc.getBoolean("isOnline");  // ✅ ADDED

                        // Search by name OR email
                        boolean matchesName = name != null && name.toLowerCase().contains(query);
                        boolean matchesEmail = email != null && email.toLowerCase().contains(query);

                        if (matchesName || matchesEmail) {
                            SearchResult result = new SearchResult();
                            result.setUserId(userId);
                            result.setTitle(name != null ? name : "Unknown");
                            result.setSubtitle(email != null ? email : "");
                            result.setAvatarUrl(profilePic);
                            result.setType("user");
                            result.setTime("");
                            result.setBio(bio != null ? bio : "Hey there! I'm using EZ Talk");  // ✅ ADDED
                            result.setOnline(isOnline != null && isOnline);  // ✅ ADDED
                            searchResults.add(result);
                        }
                    }
                    Log.d(TAG, "✅ Found " + searchResults.size() + " users");
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ User search failed: " + e.getMessage());
                    onComplete.run();
                });
    }

    // ==================== SEARCH GROUPS ====================
    private void searchGroups(String query, Runnable onComplete) {
        db.collection("groups")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot.getDocuments()) {
                        String groupId = doc.getId();
                        String name = doc.getString("name");
                        String icon = doc.getString("icon");
                        String description = doc.getString("description");

                        List<String> memberIds = (List<String>) doc.get("memberIds");
                        int memberCount = memberIds != null ? memberIds.size() : 0;

                        // Search by group name or description
                        boolean matchesName = name != null && name.toLowerCase().contains(query);
                        boolean matchesDesc = description != null && description.toLowerCase().contains(query);

                        if (matchesName || matchesDesc) {
                            SearchResult result = new SearchResult();
                            result.setUserId(groupId);
                            result.setTitle(name != null ? name : "Group");
                            result.setSubtitle(memberCount + " members");
                            result.setAvatarUrl(icon);
                            result.setType("group");
                            result.setTime("");
                            searchResults.add(result);
                        }
                    }
                    Log.d(TAG, "✅ Found " + searchResults.size() + " results (with groups)");
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Group search failed: " + e.getMessage());
                    onComplete.run();
                });
    }

    // ==================== SEARCH CHANNELS ====================
    private void searchChannels(String query, Runnable onComplete) {
        db.collection("channels")
                .whereEqualTo("isPublic", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot.getDocuments()) {
                        String channelId = doc.getId();
                        String name = doc.getString("name");
                        String description = doc.getString("description");
                        String avatarUrl = doc.getString("avatarUrl");
                        Long subscriberCount = doc.getLong("subscriberCount");

                        // Search by channel name or description
                        boolean matchesName = name != null && name.toLowerCase().contains(query);
                        boolean matchesDesc = description != null && description.toLowerCase().contains(query);

                        if (matchesName || matchesDesc) {
                            SearchResult result = new SearchResult();
                            result.setUserId(channelId);
                            result.setTitle(name != null ? name : "Channel");
                            result.setSubtitle((subscriberCount != null ? subscriberCount : 0) + " subscribers");
                            result.setAvatarUrl(avatarUrl);
                            result.setType("channel");
                            result.setTime("");
                            searchResults.add(result);
                        }
                    }
                    Log.d(TAG, "✅ Found " + searchResults.size() + " total results");
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Channel search failed: " + e.getMessage());
                    onComplete.run();
                });
    }

    // ==================== RESULT HANDLERS ====================

    /**
     * ✅ NEW: Show beautiful user profile dialog
     */
    private void showUserProfileDialog(SearchResult user) {
        // Extract username from email
        String username = user.getSubtitle();
        if (username != null && username.contains("@")) {
            username = username.split("@")[0];
        }

        UserProfileDialog dialog = UserProfileDialog.newInstance(
                user.getUserId(),
                user.getTitle(),
                username,
                user.getAvatarUrl(),
                user.getBio(),
                user.isOnline()
        );

        dialog.show(getSupportFragmentManager(), "UserProfileDialog");
        Log.d(TAG, "✅ Showing profile dialog for: " + user.getTitle());
    }

    /**
     * KEPT: Old options dialog (not used anymore but kept for reference)
     */
    private void showUserOptionsDialog(SearchResult user) {
        String[] options = {"Send Message", "View Profile", "Add Friend"};

        new MaterialAlertDialogBuilder(this)
                .setTitle(user.getTitle())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Send Message
                            openChat(user);
                            break;
                        case 1: // View Profile
                            Toast.makeText(this, "View profile - Coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 2: // Add Friend
                            addFriend(user);
                            break;
                    }
                })
                .show();
    }

    private void openChat(SearchResult user) {
        Intent intent = new Intent(this, ChatDetailActivity.class);
        intent.putExtra("user_id", user.getUserId());
        intent.putExtra("user_name", user.getTitle());
        intent.putExtra("user_avatar", user.getAvatarUrl());
        startActivity(intent);
    }

    private void openGroup(SearchResult group) {
        Intent intent = new Intent(this, GroupChatActivity.class);
        intent.putExtra("groupId", group.getUserId());
        intent.putExtra("groupName", group.getTitle());
        startActivity(intent);
        Log.d(TAG, "✅ Opening group: " + group.getTitle());
    }

    private void openChannel(SearchResult channel) {
        Intent intent = new Intent(this, ChannelDetailActivity.class);
        intent.putExtra("channelId", channel.getUserId());
        intent.putExtra("channelName", channel.getTitle());
        startActivity(intent);
        Log.d(TAG, "✅ Opening channel: " + channel.getTitle());
    }

    private void addFriend(SearchResult user) {
        // Add to current user's friends collection
        db.collection("users")
                .document(currentUserId)
                .collection("friends")
                .document(user.getUserId())
                .set(new HashMap<String, Object>() {{
                    put("userId", user.getUserId());
                    put("name", user.getTitle());
                    put("email", user.getSubtitle());
                    put("avatarUrl", user.getAvatarUrl());
                    put("addedAt", System.currentTimeMillis());
                }})
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, user.getTitle() + " added as friend!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add friend", Toast.LENGTH_SHORT).show();
                });

        // Also add current user to their friends list (mutual friendship)
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    String myName = doc.getString("name");
                    String myEmail = doc.getString("email");
                    String myAvatar = doc.getString("profilePicture");

                    db.collection("users")
                            .document(user.getUserId())
                            .collection("friends")
                            .document(currentUserId)
                            .set(new HashMap<String, Object>() {{
                                put("userId", currentUserId);
                                put("name", myName);
                                put("email", myEmail);
                                put("avatarUrl", myAvatar);
                                put("addedAt", System.currentTimeMillis());
                            }});
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateResultsUI() {
        adapter.notifyDataSetChanged();
        if (searchResults.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
        }
    }

    private void showEmptyState() {
        layoutEmpty.setVisibility(View.VISIBLE);
        rvSearchResults.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        layoutEmpty.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.VISIBLE);
    }
}