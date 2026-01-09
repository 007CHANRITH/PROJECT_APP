package com.example.project_ez_talk.ui.chat.detail;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.SeekBar;

import java.io.File;
import java.io.IOException;

public class AudioPlayerManager {
    private static final String TAG = "AudioPlayerManager";
    private MediaPlayer mediaPlayer;
    private Handler handler;
    private Runnable updateSeekBar;
    private boolean isPlaying = false;
    private String currentPlayingPath = null;
    private SeekBar currentSeekBar = null;

    public AudioPlayerManager() {
        handler = new Handler(Looper.getMainLooper());
    }

    public void playPauseAudio(Context context, String audioPath, SeekBar seekBar, PlaybackCallback callback) {
        if (audioPath == null || audioPath.isEmpty()) {
            Log.e(TAG, "Audio path is null or empty");
            if (callback != null) callback.onError("Invalid audio path");
            return;
        }

        // If same audio is playing, pause it
        if (isPlaying && audioPath.equals(currentPlayingPath)) {
            pauseAudio();
            if (callback != null) callback.onPaused();
            return;
        }

        // If different audio is playing, stop it first
        if (isPlaying && !audioPath.equals(currentPlayingPath)) {
            stopAudio();
        }

        // Start playing the audio
        playAudio(context, audioPath, seekBar, callback);
    }

    private void playAudio(Context context, String audioPath, SeekBar seekBar, PlaybackCallback callback) {
        try {
            // Release previous player if exists
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Create new media player
            mediaPlayer = new MediaPlayer();
            
            // Check if file exists
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file does not exist: " + audioPath);
                if (callback != null) callback.onError("Audio file not found");
                return;
            }

            // Set data source
            mediaPlayer.setDataSource(context, Uri.fromFile(audioFile));
            mediaPlayer.prepare();

            // Set up seek bar
            currentSeekBar = seekBar;
            if (seekBar != null) {
                seekBar.setMax(mediaPlayer.getDuration());
                seekBar.setProgress(0);
                
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && mediaPlayer != null) {
                            mediaPlayer.seekTo(progress);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
            }

            // Start playback
            mediaPlayer.start();
            isPlaying = true;
            currentPlayingPath = audioPath;
            
            if (callback != null) callback.onPlaying();

            // Update seek bar progress
            updateSeekBar = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && isPlaying) {
                        try {
                            int currentPosition = mediaPlayer.getCurrentPosition();
                            if (currentSeekBar != null) {
                                currentSeekBar.setProgress(currentPosition);
                            }
                            handler.postDelayed(this, 100);
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Error updating seek bar: " + e.getMessage());
                        }
                    }
                }
            };
            handler.post(updateSeekBar);

            // Set completion listener
            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                currentPlayingPath = null;
                handler.removeCallbacks(updateSeekBar);
                if (currentSeekBar != null) {
                    currentSeekBar.setProgress(0);
                }
                if (callback != null) callback.onCompleted();
            });

            Log.d(TAG, "Audio started playing: " + audioPath);

        } catch (IOException e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage(), e);
            if (callback != null) callback.onError("Error playing audio: " + e.getMessage());
            isPlaying = false;
            currentPlayingPath = null;
        }
    }

    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            handler.removeCallbacks(updateSeekBar);
            Log.d(TAG, "Audio paused");
        }
    }

    public void stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
        currentPlayingPath = null;
        handler.removeCallbacks(updateSeekBar);
        if (currentSeekBar != null) {
            currentSeekBar.setProgress(0);
            currentSeekBar = null;
        }
        Log.d(TAG, "Audio stopped");
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getCurrentPlayingPath() {
        return currentPlayingPath;
    }

    public void release() {
        stopAudio();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "AudioPlayerManager released");
    }

    public interface PlaybackCallback {
        void onPlaying();
        void onPaused();
        void onCompleted();
        void onError(String error);
    }
}
