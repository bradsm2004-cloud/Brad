package com.bsmith.customstopwatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class StopwatchService extends Service {
    public static final String ACTION_STATE_CHANGED = "com.bsmith.customstopwatch.STATE_CHANGED";
    public static final String ACTION_START = "com.bsmith.customstopwatch.START";
    public static final String ACTION_PAUSE = "com.bsmith.customstopwatch.PAUSE";
    public static final String ACTION_LAP = "com.bsmith.customstopwatch.LAP";
    public static final String ACTION_RESET = "com.bsmith.customstopwatch.RESET";
    public static final String ACTION_REFRESH = "com.bsmith.customstopwatch.REFRESH";
    public static final String EXTRA_INITIAL_MS = "initialMs";

    private static final String PREFS = "stopwatch_state";
    private static final String KEY_INITIAL = "initial";
    private static final String KEY_BASE = "base";
    private static final String KEY_STARTED_WALL = "startedWall";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LAPS = "laps";
    private static final String SETTINGS_PREFS = "stopwatch_settings";
    private static final String KEY_KEEP_SCREEN_AWAKE = "keepScreenAwake";
    private static final String KEY_LAP_VIBRATION = "lapVibration";
    private static final String CHANNEL_ID = "stopwatch_running";
    private static final int NOTIFICATION_ID = 8201;

    private long initialMs;
    private long baseElapsedMs;
    private long startedRealtime;
    private long startedWall;
    private boolean running;
    private boolean active;
    private final ArrayList<Lap> laps = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        loadState();
        if (active && running) {
            baseElapsedMs = currentFromStoredWall();
            startedRealtime = SystemClock.elapsedRealtime();
            startedWall = System.currentTimeMillis();
            persistState();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_START.equals(action)) {
            if (!active) {
                initialMs = Math.max(0L, intent.getLongExtra(EXTRA_INITIAL_MS, initialMs));
                baseElapsedMs = initialMs;
                laps.clear();
                active = true;
            }
            if (!running) {
                startedRealtime = SystemClock.elapsedRealtime();
                startedWall = System.currentTimeMillis();
                running = true;
            }
        } else if (ACTION_PAUSE.equals(action) && active && running) {
            baseElapsedMs = currentElapsed();
            running = false;
        } else if (ACTION_LAP.equals(action) && active && running) {
            long total = currentElapsed();
            long previous = laps.isEmpty() ? initialMs : laps.get(laps.size() - 1).total;
            laps.add(new Lap(total, Math.max(0L, total - previous)));
            vibrateForLap();
        } else if (ACTION_RESET.equals(action)) {
            running = false;
            active = false;
            baseElapsedMs = initialMs;
            laps.clear();
            persistState();
            broadcastState();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        persistState();
        if (active) startForeground(NOTIFICATION_ID, buildNotification());
        broadcastState();
        return active ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (running) {
            baseElapsedMs = currentElapsed();
            startedWall = System.currentTimeMillis();
        }
        persistState();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private long currentElapsed() {
        if (!running) return baseElapsedMs;
        return baseElapsedMs + Math.max(0L, SystemClock.elapsedRealtime() - startedRealtime);
    }

    private long currentFromStoredWall() {
        if (!running) return baseElapsedMs;
        return baseElapsedMs + Math.max(0L, System.currentTimeMillis() - startedWall);
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Running stopwatch",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows stopwatch time and controls while it is active");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        long elapsed = currentElapsed();
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(
                this, 10, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp)
                .setContentTitle(running ? "Stopwatch running" : "Stopwatch paused")
                .setContentText(running ? "Tap to open" : formatTime(elapsed))
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setColor(getColor(R.color.accent))
                .setColorized(false);

        Bundle liveUpdateExtras = new Bundle();
        liveUpdateExtras.putBoolean("android.requestPromotedOngoing", running);
        builder.addExtras(liveUpdateExtras);

        if (running) {
            builder.setWhen(System.currentTimeMillis() - elapsed)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .addAction(android.R.drawable.ic_media_pause, "Pause", serviceIntent(ACTION_PAUSE, 11))
                    .addAction(android.R.drawable.ic_menu_add, "Lap", serviceIntent(ACTION_LAP, 12));
        } else {
            builder.setShowWhen(false)
                    .addAction(android.R.drawable.ic_media_play, "Resume", serviceIntent(ACTION_START, 13))
                    .addAction(android.R.drawable.ic_menu_revert, "Reset", resetConfirmationIntent());
        }
        return builder.build();
    }

    private PendingIntent resetConfirmationIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_CONFIRM_RESET, true);
        return PendingIntent.getActivity(
                this, 14, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void vibrateForLap() {
        if (!isLapVibrationEnabled(this)) return;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(35L);
        }
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, StopwatchService.class).setAction(action);
        return PendingIntent.getService(
                this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void broadcastState() {
        Intent update = new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName());
        sendBroadcast(update);
    }

    private void persistState() {
        JSONArray array = new JSONArray();
        for (Lap lap : laps) {
            JSONObject object = new JSONObject();
            try {
                object.put("total", lap.total);
                object.put("split", lap.split);
                array.put(object);
            } catch (JSONException ignored) { }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(KEY_INITIAL, initialMs)
                .putLong(KEY_BASE, baseElapsedMs)
                .putLong(KEY_STARTED_WALL, startedWall)
                .putBoolean(KEY_RUNNING, running)
                .putBoolean(KEY_ACTIVE, active)
                .putString(KEY_LAPS, array.toString())
                .apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        initialMs = prefs.getLong(KEY_INITIAL, 0L);
        baseElapsedMs = prefs.getLong(KEY_BASE, initialMs);
        startedWall = prefs.getLong(KEY_STARTED_WALL, System.currentTimeMillis());
        running = prefs.getBoolean(KEY_RUNNING, false);
        active = prefs.getBoolean(KEY_ACTIVE, false);
        laps.clear();
        readLaps(prefs.getString(KEY_LAPS, "[]"), laps);
    }

    public static void sendAction(Context context, String action, long initialMs) {
        Intent intent = new Intent(context, StopwatchService.class).setAction(action);
        if (ACTION_START.equals(action)) intent.putExtra(EXTRA_INITIAL_MS, initialMs);
        if ((ACTION_START.equals(action) || ACTION_REFRESH.equals(action))
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void saveIdleInitial(Context context, long initialMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            prefs.edit().putLong(KEY_INITIAL, initialMs).putLong(KEY_BASE, initialMs).apply();
        }
    }

    public static boolean isKeepScreenAwakeEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_KEEP_SCREEN_AWAKE, true);
    }

    public static void setKeepScreenAwakeEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled).apply();
    }

    public static boolean isLapVibrationEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_LAP_VIBRATION, true);
    }

    public static void setLapVibrationEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_LAP_VIBRATION, enabled).apply();
    }

    public static State readState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        State state = new State();
        state.initialMs = prefs.getLong(KEY_INITIAL, 0L);
        state.elapsedMs = prefs.getLong(KEY_BASE, state.initialMs);
        state.running = prefs.getBoolean(KEY_RUNNING, false);
        state.active = prefs.getBoolean(KEY_ACTIVE, false);
        if (state.running) {
            long wall = prefs.getLong(KEY_STARTED_WALL, System.currentTimeMillis());
            state.elapsedMs += Math.max(0L, System.currentTimeMillis() - wall);
        }
        state.snapshotRealtime = SystemClock.elapsedRealtime();
        readLaps(prefs.getString(KEY_LAPS, "[]"), state.laps);
        return state;
    }

    private static void readLaps(String json, ArrayList<Lap> destination) {
        try {
            JSONArray array = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                destination.add(new Lap(object.optLong("total"), object.optLong("split")));
            }
        } catch (JSONException ignored) { }
    }

    public static String formatTime(long milliseconds) {
        long safe = Math.max(0L, milliseconds);
        long hours = safe / 3_600_000L;
        long minutes = (safe / 60_000L) % 60L;
        long seconds = (safe / 1_000L) % 60L;
        long centis = (safe % 1_000L) / 10L;
        return String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis);
    }

    public static String formatLapTime(long milliseconds) {
        long safe = Math.max(0L, milliseconds);
        long hours = safe / 3_600_000L;
        long minutes = (safe / 60_000L) % 60L;
        long seconds = (safe / 1_000L) % 60L;
        long centis = (safe % 1_000L) / 10L;
        if (hours > 0L) {
            return String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis);
        }
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis);
    }

    public static class State {
        public long initialMs;
        public long elapsedMs;
        public long snapshotRealtime;
        public boolean running;
        public boolean active;
        public final ArrayList<Lap> laps = new ArrayList<>();

        public long currentElapsed() {
            if (!running) return elapsedMs;
            return elapsedMs + Math.max(0L, SystemClock.elapsedRealtime() - snapshotRealtime);
        }

        public long currentLapElapsed() {
            long previous = laps.isEmpty() ? initialMs : laps.get(laps.size() - 1).total;
            return Math.max(0L, currentElapsed() - previous);
        }
    }

    public static class Lap {
        public final long total;
        public final long split;

        public Lap(long total, long split) {
            this.total = total;
            this.split = split;
        }
    }
}
