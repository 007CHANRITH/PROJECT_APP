package com.example.project_ez_talk.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * ✅ Audio Recorder Manager for Voice Messages
 * Records audio in AAC format with proper quality settings
 */
public class AudioRecorderManager {

    private static final String TAG = "AudioRecorder";
    private MediaRecorder mediaRecorder;
    private String currentFilePath;
    private boolean isRecording = false;
    private long recordingStartTime;

    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingProgress(long durationMs);
        void onRecordingCompleted(String filePath, long durationMs);
        void onRecordingError(String error);
    }

    /**
     * Start recording audio
     */
    public void startRecording(Context context, RecordingCallback callback) {
        if (isRecording) {
            Log.w(TAG, "Already recording!");
            return;
        }

        try {
            // Create output file
            File audioDir = new File(context.getCacheDir(), "voice_messages");
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }

            String fileName = "voice_" + System.currentTimeMillis() + ".m4a";
            File audioFile = new File(audioDir, fileName);
            currentFilePath = audioFile.getAbsolutePath();

            // Setup MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(currentFilePath);

            // Prepare and start
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordingStartTime = System.currentTimeMillis();

            Log.d(TAG, "✅ Recording started: " + currentFilePath);
            if (callback != null) {
                callback.onRecordingStarted();
            }

        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to start recording", e);
            if (callback != null) {
                callback.onRecordingError("Failed to start recording: " + e.getMessage());
            }
            release();
        }
    }

    /**
     * Stop recording and save file
     */
    public void stopRecording(RecordingCallback callback) {
        if (!isRecording) {
            Log.w(TAG, "Not recording!");
            return;
        }

        try {
            long duration = System.currentTimeMillis() - recordingStartTime;

            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;

            isRecording = false;

            Log.d(TAG, "✅ Recording stopped. Duration: " + duration + "ms");
            if (callback != null) {
                callback.onRecordingCompleted(currentFilePath, duration);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to stop recording", e);
            if (callback != null) {
                callback.onRecordingError("Failed to stop recording: " + e.getMessage());
            }
            release();
        }
    }

    /**
     * Cancel recording and delete file
     */
    public void cancelRecording() {
        if (isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;

                // Delete the file
                if (currentFilePath != null) {
                    File file = new File(currentFilePath);
                    if (file.exists()) {
                        file.delete();
                    }
                }

                isRecording = false;
                Log.d(TAG, "🚫 Recording cancelled");

            } catch (Exception e) {
                Log.e(TAG, "Error cancelling recording", e);
                release();
            }
        }
    }

    /**
     * Get current recording duration
     */
    public long getCurrentDuration() {
        if (isRecording) {
            return System.currentTimeMillis() - recordingStartTime;
        }
        return 0;
    }

    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Release resources
     */
    private void release() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
            mediaRecorder = null;
        }
        isRecording = false;
    }
}
