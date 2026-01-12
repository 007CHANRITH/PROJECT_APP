package com.example.project_ez_talk.ui.home;
import com.example.project_ez_talk.ui.profile.AddFriendDialog;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.project_ez_talk.ui.SearchActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.project_ez_talk.R;
import com.example.project_ez_talk.service.NotificationListenerService;
import com.example.project_ez_talk.ui.BaseActivity;
import com.example.project_ez_talk.ui.auth.welcome.WelcomeActivity;
import com.example.project_ez_talk.ui.profile.AddFriendDialog;
import com.example.project_ez_talk.utils.Preferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * HomeActivity - Main container for the app after login
 * Uses activity_main.xml layout with bottom navigation
 * Features draggable FAB that can be moved anywhere on screen
 */
public class HomeActivity extends BaseActivity {

    private NavController navController;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabCenter;
    private MaterialToolbar toolbar;
    private ImageView ivSearch, ivNotification;
    private TextView tvNotificationBadge;
    
    private ListenerRegistration unreadCountListener;

    // Variables for draggable FAB
    private float dX = 0f;
    private float dY = 0f;
    private float initialX = 0f;
    private float initialY = 0f;
    private boolean isDragging = false;
    private static final float DRAG_THRESHOLD = 10f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null || !Preferences.isLoggedIn(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupNavigation();
        setupToolbar();
        setupDraggableFab();
        setupUnreadCounter();
        
        // ✅ Start notification listener service
        startNotificationListener();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNav = findViewById(R.id.bottomNavigation);
        fabCenter = findViewById(R.id.fabCenter);
        ivSearch = findViewById(R.id.ivSearch);
        ivNotification = findViewById(R.id.ivNotification);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(bottomNav, navController);

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int destinationId = destination.getId();

                if (destinationId == R.id.navigation_home) {
                    toolbar.setTitle(R.string.home);
                } else if (destinationId == R.id.navigation_chats) {
                    toolbar.setTitle(R.string.chats);
                } else if (destinationId == R.id.navigation_calls) {
                    toolbar.setTitle(R.string.calls);
                } else if (destinationId == R.id.navigation_contacts) {
                    toolbar.setTitle(R.string.contacts);
                } else if (destinationId == R.id.navigation_profile) {
                    toolbar.setTitle(R.string.profile);
                }

                boolean showBottomNav = destinationId == R.id.navigation_home ||
                        destinationId == R.id.navigation_chats ||
                        destinationId == R.id.navigation_calls ||
                        destinationId == R.id.navigation_contacts ||
                        destinationId == R.id.navigation_profile;

                bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
                fabCenter.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        // Search icon click - Opens SearchActivity
        ivSearch.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        ivNotification.setOnClickListener(v -> {
            Toast.makeText(this, "Notifications clicked", Toast.LENGTH_SHORT).show();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableFab() {
        fabCenter.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        initialX = event.getRawX();
                        initialY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = Math.abs(event.getRawX() - initialX);
                        float deltaY = Math.abs(event.getRawY() - initialY);

                        if (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD) {
                            isDragging = true;

                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;

                            int screenWidth = getResources().getDisplayMetrics().widthPixels;
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;
                            int fabWidth = view.getWidth();
                            int fabHeight = view.getHeight();

                            if (newX < 0) newX = 0;
                            if (newX > screenWidth - fabWidth) newX = screenWidth - fabWidth;
                            if (newY < 0) newY = 0;
                            if (newY > screenHeight - fabHeight) newY = screenHeight - fabHeight;

                            view.animate()
                                    .x(newX)
                                    .y(newY)
                                    .setDuration(0)
                                    .start();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            view.performClick();
                            onFabClick();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    /**
     * Handle FAB click - Open AddFriendDialog
     */
    private void onFabClick() {
        try {
            AddFriendDialog dialog = new AddFriendDialog();
            dialog.setCancelable(true);
            dialog.show(getSupportFragmentManager(), "AddFriendDialog");
        } catch (Exception e) {
            Toast.makeText(this, "Error opening dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ✅ Start background service to listen for notifications
     */
    private void startNotificationListener() {
        Intent serviceIntent = new Intent(this, NotificationListenerService.class);
        startService(serviceIntent);
        android.util.Log.d("HomeActivity", "✅ NotificationListenerService started");
    }
    
    /**
     * Setup real-time unread message counter
     */
    private void setupUnreadCounter() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) return;
        
        unreadCountListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("chats")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        updateBadge(0);
                        return;
                    }
                    
                    int totalUnread = 0;
                    for (var doc : snapshots.getDocuments()) {
                        Long unreadCount = doc.getLong("unreadCount");
                        if (unreadCount != null) {
                            totalUnread += unreadCount;
                        }
                    }
                    
                    updateBadge(totalUnread);
                });
    }
    
    /**
     * Update notification badge display
     */
    private void updateBadge(int count) {
        if (tvNotificationBadge != null) {
            if (count > 0) {
                tvNotificationBadge.setVisibility(View.VISIBLE);
                tvNotificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                tvNotificationBadge.setVisibility(View.GONE);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadCountListener != null) {
            unreadCountListener.remove();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}