package com.example.project_ez_talk.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.project_ez_talk.helper.LocaleHelper;
import com.example.project_ez_talk.utils.Preferences;

/**
 * ✅ Base Activity class
 * Provides common functionality for all activities
 * Handles locale switching for multi-language support
 * Handles dark/light theme switching
 */
public class BaseActivity extends AppCompatActivity {

    protected static final String TAG = "BaseActivity";

    @Override
    protected void attachBaseContext(Context newBase) {
        // Apply saved locale to this activity
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme BEFORE calling super.onCreate()
        applyTheme();
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: " + this.getClass().getSimpleName());
    }

    /**
     * Apply saved theme preference (dark/light mode)
     */
    private void applyTheme() {
        boolean isDarkMode = Preferences.isDarkMode(this);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        Log.d(TAG, "🎨 Theme applied: " + (isDarkMode ? "DARK" : "LIGHT"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: " + this.getClass().getSimpleName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: " + this.getClass().getSimpleName());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: " + this.getClass().getSimpleName());
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: " + this.getClass().getSimpleName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: " + this.getClass().getSimpleName());
    }
}