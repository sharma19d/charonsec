package com.zalexdev.stryker.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.R;

/** Navigation clicks: a short vibration plus a soft mechanical sound (sound only in normal ringer mode). */
public final class Feedback {

    private static final long MIN_TICK_GAP_MS = 28;

    private static Feedback instance;

    public static Feedback get(@NonNull Context c) {
        if (instance == null) instance = new Feedback(c.getApplicationContext());
        return instance;
    }

    private final Vibrator vibrator;
    private final AudioManager audio;
    private final SoundPool pool;
    private int tickId, detentId;
    private volatile boolean tickReady, detentReady;
    private long lastTick;

    private Feedback(Context app) {
        vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        pool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status != 0) return;
            if (id == tickId) tickReady = true;
            else if (id == detentId) detentReady = true;
        });
        tickId = pool.load(app, R.raw.dial_tick, 1);
        detentId = pool.load(app, R.raw.dial_detent, 1);
    }

    /** Light click for every step the dial passes. Rate-limited for fast flings. */
    public void tick() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTick < MIN_TICK_GAP_MS) return;
        lastTick = now;
        vibrate(10, 110);
        play(tickReady, tickId, 0.55f);
    }

    /** Firmer click when the dial lands on a section or a tool is chosen. */
    public void confirm() {
        vibrate(20, 210);
        play(detentReady, detentId, 0.7f);
    }

    private void vibrate(long ms, int amplitude) {
        if (vibrator == null) return;
        try {
            if (!vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int amp = vibrator.hasAmplitudeControl() ? amplitude : VibrationEffect.DEFAULT_AMPLITUDE;
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void play(boolean ready, int id, float volume) {
        if (!ready || audio == null) return;
        if (audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
        pool.play(id, volume, volume, 1, 0, 1f);
    }
}
