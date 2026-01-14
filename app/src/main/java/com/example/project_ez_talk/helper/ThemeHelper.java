package com.example.project_ez_talk.helper;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.project_ez_talk.utils.Preferences;

/**
 * ThemeHelper - Utility class for managing app theme (dark/light mode)
 * Provides methods to apply and switch between themes
 */
public class ThemeHelper {

    /**
     * Apply saved theme preference
     * Call this in Application class or BaseActivity
     */
    public static void applyTheme(Context context) {
        boolean isDarkMode = Preferences.isDarkMode(context);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    /**
     * Toggle theme between dark and light mode
     * Returns the new theme state (true = dark, false = light)
     */
    public static boolean toggleTheme(Context context) {
        boolean currentMode = Preferences.isDarkMode(context);
        boolean newMode = !currentMode;
        Preferences.setDarkMode(context, newMode);
        applyTheme(context);
        return newMode;
    }

    /**
     * Set theme to dark mode
     */
    public static void setDarkMode(Context context) {
        Preferences.setDarkMode(context, true);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    /**
     * Set theme to light mode
     */
    public static void setLightMode(Context context) {
        Preferences.setDarkMode(context, false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Check if dark mode is enabled
     */
    public static boolean isDarkMode(Context context) {
        return Preferences.isDarkMode(context);
    }

    /**
     * Follow system theme setting
     */
    public static void followSystemTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
