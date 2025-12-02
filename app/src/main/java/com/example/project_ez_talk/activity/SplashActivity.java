package com.example.project_ez_talk.activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.project_ez_talk.R;

public class SplashActivity extends AppCompatActivity {

    // Duration of splash screen in milliseconds
    private static final int SPLASH_DURATION = 20000; // 20 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // your splash XML

        // Delay then start MainActivity
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // close splash so user can't return to it
        }, SPLASH_DURATION);
    }
}

