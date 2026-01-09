package com.example.project_ez_talk.utils;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

/**
 * ✅ Audio Player Manager for Voice Messages
 * Plays audio messages with progress tracking
 */
public class AudioPlayerManager {

    private static final String TAG = "AudioPlayer";
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private String currentAudioUrl;

    public interface PlaybackCallback {
        void onPlaybackStarted();
        void onPlaybackProgress(int currentPosition, int duration);
        void onPlaybackCompleted();
        void onPlaybackError(String error);
    }

    /**
     * Play audio from URL or file path
     */
    public void playAudio(String audioUrl, PlaybackCallback callback) {
        if (isPlaying && audioUrl.equals(currentAudioUrl)) {
            // Same audio playing, pause it
            pauseAudio();
            return;
        }

        // Stop current playback if playing different audio
        if (isPlaying) {
            stopAudio();
        }

        try {
            currentAudioUrl = audioUrl;

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );

            mediaPlayer.setDataSource(audioUrl);

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlaying = true;
                Log.d(TAG, "✅ Playback started");
                if (callback != null) {
                    callback.onPlaybackStarted();
                    startProgressUpdates(callback);
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "✅ Playback completed");
                isPlaying = false;
                if (callback != null) {
                    callback.onPlaybackCompleted();
                }
                release();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "❌ Playback error: " + what + ", " + extra);
                if (callback != null) {
                    callback.onPlaybackError("Playback error");
                }
                release();
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to play audio", e);
            if (callback != null) {
                callback.onPlaybackError("Failed to play audio: " + e.getMessage());
            }
            release();
        }
    }

    /**
     * Pause audio playback
     */
    public void pauseAudio() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            Log.d(TAG, "⏸ Playback paused");
        }
    }

    /**
     * Resume audio playback
     */
    public void resumeAudio() {
        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            Log.d(TAG, "▶ Playback resumed");
        }
    }

    /**
     * Stop audio playback
     */
    public void stopAudio() {
        if (mediaPlayer != null) {
            if (isPlaying) {
                mediaPlayer.stop();
            }
            release();
            Log.d(TAG, "⏹ Playback stopped");
        }
    }

    /**
     * Seek to position (in milliseconds)
     */
    public void seekTo(int positionMs) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(positionMs);
        }
    }

    /**
     * Get current playback position
     */
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    /**
     * Get audio duration
     */
    public int getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    /**
     * Check if currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Start progress updates
     */
    private void startProgressUpdates(PlaybackCallback callback) {
        if (mediaPlayer == null || callback == null) return;

        new Thread(() -> {
            while (isPlaying && mediaPlayer != null) {
                try {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    callback.onPlaybackProgress(currentPosition, duration);
                    Thread.sleep(100); // Update every 100ms
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    /**
     * Release resources
     */
    private void release() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
        isPlaying = false;
        currentAudioUrl = null;
    }
}
