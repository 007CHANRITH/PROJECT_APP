package com.example.project_ez_talk;

import android.app.Application;

import com.example.project_ez_talk.helper.SupabaseStorageManager;
import com.example.project_ez_talk.helper.ThemeHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import org.webrtc.PeerConnectionFactory;

@SuppressWarnings("ALL")
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // ✅ Apply saved theme on app start
        ThemeHelper.applyTheme(this);

        // ✅ Initialize Firebase ONCE
        FirebaseApp.initializeApp(this);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.setPersistenceEnabled(true);

        // ❌ REMOVE useEmulator() for production
        // database.useEmulator(...)

        // ✅ Initialize WebRTC ONCE per process
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
        );

        // ✅ Supabase init
        SupabaseStorageManager.init(this);
    }
}
