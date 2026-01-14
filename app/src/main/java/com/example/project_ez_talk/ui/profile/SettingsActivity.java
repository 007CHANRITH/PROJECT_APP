package com.example.project_ez_talk.ui.profile;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.example.project_ez_talk.R;
import com.example.project_ez_talk.ui.BaseActivity;
import com.example.project_ez_talk.helper.LocaleHelper;
import com.example.project_ez_talk.utils.Preferences;

public class SettingsActivity extends BaseActivity {

    private SwitchCompat switchDarkMode, switchMessageNotif, switchCallNotif;
    private LinearLayout llLanguage;
    private TextView tvCurrentLanguage;
    private Toolbar toolbar;

    private static final String[] LANGUAGES = {"English", "ខ្មែរ"};
    private static final String[] LANGUAGE_CODES = {"en", "km"};
    private int selectedLanguageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Smooth fade animation when activity is created
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        switchDarkMode = findViewById(R.id.switchDarkMode);
        switchMessageNotif = findViewById(R.id.switchMessageNotif);
        switchCallNotif = findViewById(R.id.switchCallNotif);
        llLanguage = findViewById(R.id.llLanguage);
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadSettings() {
        // Get current language
        String languageCode = LocaleHelper.getLanguage(this);
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(languageCode)) {
                selectedLanguageIndex = i;
                break;
            }
        }
        tvCurrentLanguage.setText(LANGUAGES[selectedLanguageIndex]);
        
        // Load dark mode preference
        boolean isDarkMode = Preferences.isDarkMode(this);
        switchDarkMode.setChecked(isDarkMode);
    }

    private void setupListeners() {
        // Dark mode toggle
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                // Save preference
                Preferences.setDarkMode(this, isChecked);
                
                // Show message
                String message = isChecked ?
                        getString(R.string.dark_mode_enabled) :
                        getString(R.string.light_mode_enabled);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                
                // Recreate activity to apply theme with smooth animation
                buttonView.postDelayed(() -> {
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    recreate();
                }, 100);
            }
        });

        // Message notifications
        switchMessageNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                String message = isChecked ?
                        getString(R.string.message_notif_enabled) :
                        getString(R.string.message_notif_disabled);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        // Call notifications
        switchCallNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                String message = isChecked ?
                        getString(R.string.call_notif_enabled) :
                        getString(R.string.call_notif_disabled);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        // Language selection
        llLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    private void showLanguageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.select_language);

        builder.setSingleChoiceItems(LANGUAGES, selectedLanguageIndex, (dialog, which) -> {
            selectedLanguageIndex = which;
        });

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String selectedCode = LANGUAGE_CODES[selectedLanguageIndex];
            String currentCode = LocaleHelper.getLanguage(this);

            // Only proceed if language actually changed
            if (!selectedCode.equals(currentCode)) {
                // Save and apply locale
                LocaleHelper.setLocale(SettingsActivity.this, selectedCode);

                // Update the current language text immediately
                tvCurrentLanguage.setText(LANGUAGES[selectedLanguageIndex]);

                // Show toast before recreate
                Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show();

                // Use post to ensure toast is shown before recreating
                tvCurrentLanguage.postDelayed(() -> {
                    // Smooth fade animation
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    recreate();
                }, 100);
            }
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}