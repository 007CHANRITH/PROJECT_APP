package com.example.project_ez_talk.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    private static final String PREF_NAME = "EZTalkPrefs";

    // Keys
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_PHOTO_URL = "user_photo_url";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_USER_PASSWORD = "user_password";
    private static final String KEY_TEMP_PASSWORD = "temp_password"; // For email verification flow
    private static final String KEY_DARK_MODE = "dark_mode"; // Theme preference

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Login Status
    public static boolean isLoggedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_LOGGED_IN, false);
    }

    public static void setLoggedIn(Context context, boolean loggedIn) {
        getPrefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply();
    }

    // User Email
    public static String getUserEmail(Context context) {
        return getPrefs(context).getString(KEY_USER_EMAIL, "");
    }

    public static void setUserEmail(Context context, String email) {
        getPrefs(context).edit().putString(KEY_USER_EMAIL, email).apply();
    }

    // Username
    public static String getUsername(Context context) {
        return getPrefs(context).getString(KEY_USERNAME, "");
    }

    public static void setUsername(Context context, String username) {
        getPrefs(context).edit().putString(KEY_USERNAME, username).apply();
    }

    // User Photo URL
    public static String getUserPhotoUrl(Context context) {
        return getPrefs(context).getString(KEY_USER_PHOTO_URL, "");
    }

    public static void setUserPhotoUrl(Context context, String photoUrl) {
        getPrefs(context).edit().putString(KEY_USER_PHOTO_URL, photoUrl).apply();
    }

    // User ID
    public static String getUserId(Context context) {
        return getPrefs(context).getString(KEY_USER_ID, "");
    }

    public static void setUserId(Context context, String userId) {
        getPrefs(context).edit().putString(KEY_USER_ID, userId).apply();
    }

    // User Phone
    public static String getUserPhone(Context context) {
        return getPrefs(context).getString(KEY_USER_PHONE, "");
    }

    public static void setUserPhone(Context context, String phone) {
        getPrefs(context).edit().putString(KEY_USER_PHONE, phone).apply();
    }

    // User Password
    public static String getUserPassword(Context context) {
        return getPrefs(context).getString(KEY_USER_PASSWORD, "");
    }

    public static void setUserPassword(Context context, String password) {
        getPrefs(context).edit().putString(KEY_USER_PASSWORD, password).apply();
    }

    // Temporary Password (for email verification flow)
    public static String getTempPassword(Context context) {
        return getPrefs(context).getString(KEY_TEMP_PASSWORD, "");
    }

    public static void setTempPassword(Context context, String password) {
        getPrefs(context).edit().putString(KEY_TEMP_PASSWORD, password).apply();
    }

    public static void clearTempPassword(Context context) {
        getPrefs(context).edit().remove(KEY_TEMP_PASSWORD).apply();
    }

    // User Name (alias for getUsername)
    public static String getUserName(Context context) {
        return getUsername(context);
    }

    // User Photo (alias for getUserPhotoUrl)
    public static String getUserPhoto(Context context) {
        return getUserPhotoUrl(context);
    }

    // Clear all preferences (for logout)
    public static void clearAll(Context context) {
        getPrefs(context).edit().clear().apply();
    }

    // Logout
    public static void logout(Context context) {
        clearAll(context);
    }

    // Dark Mode
    public static boolean isDarkMode(Context context) {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, true); // Default to dark mode
    }

    public static void setDarkMode(Context context, boolean isDarkMode) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, isDarkMode).apply();
    }
}