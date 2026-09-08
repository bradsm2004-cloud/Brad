package com.bsmith.customstopwatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 501;
    public static final String EXTRA_CONFIRM_RESET = "confirmReset";
    private static final int MENU_SHARE = 1;
    private static final int MENU_COPY = 2;
    private static final int MENU_KEEP_AWAKE = 3;
    private static final int MENU_LAP_VIBRATION = 4;

    private TextView timeMain;
    private TextView timeCentis;
    private TextView currentLapTime;
    private TextView setStartButton;
    private TextView optionsButton;
    private TextView emptyLaps;
    private Button startButton;
    private Button resetButton;
    private Button lapButton;
    private ListView lapList;
    private LapAdapter lapAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private StopwatchService.State state;
    private boolean receiverRegistered;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (state != null && state.running) {
                renderTimes();
                handler.postDelayed(this, 16L);
            }
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshFromService();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timeMain = findViewById(R.id.timeMain);
        timeCentis = findViewById(R.id.timeCentis);
        currentLapTime = findViewById(R.id.currentLapTime);
        setStartButton = findViewById(R.id.setStartButton);
        optionsButton = findViewById(R.id.optionsButton);
        emptyLaps = findViewById(R.id.emptyLaps);
        startButton = findViewById(R.id.startButton);
        resetButton = findViewById(R.id.resetButton);
        lapButton = findViewById(R.id.lapButton);
        lapList = findViewById(R.id.lapList);

        lapAdapter = new LapAdapter();
        lapList.setAdapter(lapAdapter);

        startButton.setOnClickListener(view -> toggleRunning());
        resetButton.setOnClickListener(view -> confirmReset());
        lapButton.setOnClickListener(view -> sendServiceAction(StopwatchService.ACTION_LAP));
        setStartButton.setOnClickListener(view -> showStartTimeDialog());
        optionsButton.setOnClickListener(this::showOptionsMenu);

        applySystemBarInsets();

        refreshFromService();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshFromService();
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(StopwatchService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
        refreshFromService();
        if (state.active) {
            StopwatchService.sendAction(this, StopwatchService.ACTION_REFRESH, state.initialMs);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver);
            receiverRegistered = false;
        }
        super.onPause();
    }

    private void toggleRunning() {
        if (state.running) {
            sendServiceAction(StopwatchService.ACTION_PAUSE);
        } else {
            requestNotificationAndStart();
        }
    }

    private void requestNotificationAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        startStopwatchService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startStopwatchService();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Notifications are needed")
                    .setMessage("Allow notifications so the stopwatch can remain visible on the lock screen and in the notification panel while it runs.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void startStopwatchService() {
        StopwatchService.sendAction(this, StopwatchService.ACTION_START, state.initialMs);
    }

    private void sendServiceAction(String action) {
        if (!state.active && !StopwatchService.ACTION_START.equals(action)) return;
        StopwatchService.sendAction(this, action, state.initialMs);
    }

    private void refreshFromService() {
        state = StopwatchService.readState(this);
        renderTimes();
        lapAdapter.notifyDataSetChanged();
        refreshControls();
        refreshLapVisibility();
        handler.removeCallbacks(ticker);
        if (state.running && StopwatchService.isKeepScreenAwakeEnabled(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            handler.post(ticker);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (state.running) handler.post(ticker);
        }
    }

    private void refreshControls() {
        startButton.setText(state.running ? R.string.pause : (state.active ? R.string.resume : R.string.start));
        startButton.setBackgroundResource(state.running ? R.drawable.button_paused : R.drawable.button_primary);
        startButton.setTextColor(state.running ? getColor(R.color.accent) : Color.WHITE);
        resetButton.setEnabled(state.active);
        lapButton.setEnabled(state.running);
        setStartButton.setEnabled(!state.active);
        setStartButton.setAlpha(state.active ? 0.35f : 1f);
    }

    private void refreshLapVisibility() {
        boolean hasLaps = !state.laps.isEmpty();
        emptyLaps.setVisibility(hasLaps ? View.GONE : View.VISIBLE);
        lapList.setVisibility(hasLaps ? View.VISIBLE : View.GONE);
    }

    private void renderTime(long milliseconds) {
        long safe = Math.max(0L, milliseconds);
        long hours = safe / 3_600_000L;
        long minutes = (safe / 60_000L) % 60L;
        long seconds = (safe / 1_000L) % 60L;
        long centis = (safe % 1_000L) / 10L;
        timeMain.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
        timeCentis.setText(String.format(Locale.US, ".%02d", centis));
        timeMain.setContentDescription(hours + " hours, " + minutes + " minutes, " + seconds + " seconds");
    }

    private void renderTimes() {
        if (state == null) return;
        renderTime(state.currentElapsed());
        long currentLap = state.currentLapElapsed();
        currentLapTime.setText(StopwatchService.formatLapTime(currentLap));
        currentLapTime.setContentDescription("Current lap " + StopwatchService.formatTime(currentLap));
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.rootLayout);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop() + dp(8);
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPaddingRelative(dp(24), top, dp(24), bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_CONFIRM_RESET, false)) {
            intent.removeExtra(EXTRA_CONFIRM_RESET);
            confirmReset();
        }
    }

    private void confirmReset() {
        if (state == null || !state.active) return;
        new AlertDialog.Builder(this)
                .setTitle("Reset stopwatch?")
                .setMessage("This will erase the current time and all recorded laps.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> sendServiceAction(StopwatchService.ACTION_RESET))
                .show();
    }

    private void showOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, MENU_SHARE, Menu.NONE, "Share lap times")
                .setEnabled(state != null && !state.laps.isEmpty());
        menu.add(Menu.NONE, MENU_COPY, Menu.NONE, "Copy lap times")
                .setEnabled(state != null && !state.laps.isEmpty());
        menu.add(Menu.NONE, MENU_KEEP_AWAKE, Menu.NONE, "Keep screen awake")
                .setCheckable(true)
                .setChecked(StopwatchService.isKeepScreenAwakeEnabled(this));
        menu.add(Menu.NONE, MENU_LAP_VIBRATION, Menu.NONE, "Vibrate when lap is recorded")
                .setCheckable(true)
                .setChecked(StopwatchService.isLapVibrationEnabled(this));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SHARE) {
                shareLaps();
                return true;
            }
            if (item.getItemId() == MENU_COPY) {
                copyAllLaps();
                return true;
            }
            if (item.getItemId() == MENU_KEEP_AWAKE) {
                StopwatchService.setKeepScreenAwakeEnabled(this, !item.isChecked());
                refreshFromService();
                return true;
            }
            if (item.getItemId() == MENU_LAP_VIBRATION) {
                StopwatchService.setLapVibrationEnabled(this, !item.isChecked());
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showStartTimeDialog() {
        if (state.active) return;
        long safe = Math.max(0L, state.initialMs);
        int hours = (int) Math.min(99L, safe / 3_600_000L);
        int minutes = (int) ((safe / 60_000L) % 60L);
        int seconds = (int) ((safe / 1_000L) % 60L);
        int centis = (int) ((safe % 1_000L) / 10L);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), 0, dp(24), 0);
        TextView explanation = new TextView(this);
        explanation.setText("The stopwatch will count up from this time.");
        explanation.setTextColor(getColor(R.color.text_muted));
        explanation.setTextSize(15f);
        explanation.setPadding(0, 0, 0, dp(18));
        content.addView(explanation);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        EditText hoursInput = addTimeField(fields, "Hours", hours);
        EditText minutesInput = addTimeField(fields, "Minutes", minutes);
        EditText secondsInput = addTimeField(fields, "Seconds", seconds);
        EditText centisInput = addTimeField(fields, "Hundredths", centis);
        content.addView(fields, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.set_start_time)
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Set time", (ignored, which) -> {
                    int h = boundedValue(hoursInput, 0, 99);
                    int m = boundedValue(minutesInput, 0, 59);
                    int s = boundedValue(secondsInput, 0, 59);
                    int c = boundedValue(centisInput, 0, 99);
                    long initial = ((h * 3600L + m * 60L + s) * 1000L) + c * 10L;
                    StopwatchService.saveIdleInitial(this, initial);
                    refreshFromService();
                })
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.accent));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getColor(R.color.text_muted));
            hoursInput.requestFocus();
            hoursInput.selectAll();
        });
        dialog.show();
    }

    private EditText addTimeField(LinearLayout parent, String label, int value) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(getColor(R.color.text_muted));
        caption.setTextSize(label.length() > 8 ? 10f : 12f);
        caption.setGravity(Gravity.CENTER);
        column.addView(caption, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        EditText input = new EditText(this);
        input.setText(String.valueOf(value));
        input.setSelectAllOnFocus(true);
        input.setGravity(Gravity.CENTER);
        input.setTextColor(getColor(R.color.text_primary));
        input.setTextSize(20f);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        input.setBackgroundResource(R.drawable.input_background);
        input.setPadding(0, 0, 0, 0);
        column.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(column, params);
        return input;
    }

    private int boundedValue(EditText input, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return min;
        }
    }

    private void shareLaps() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Stopwatch laps");
        share.putExtra(Intent.EXTRA_TEXT, buildLapText());
        startActivity(Intent.createChooser(share, "Share lap times"));
    }

    private void copyAllLaps() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Stopwatch laps", buildLapText()));
        Toast.makeText(this, "Lap times copied", Toast.LENGTH_SHORT).show();
    }

    private String buildLapText() {
        StringBuilder text = new StringBuilder("Stopwatch total: ").append(StopwatchService.formatTime(state.currentElapsed()));
        for (int i = 0; i < state.laps.size(); i++) {
            StopwatchService.Lap lap = state.laps.get(i);
            text.append("\nLap ").append(i + 1)
                    .append("  Split ").append(StopwatchService.formatTime(lap.split))
                    .append("  Total ").append(StopwatchService.formatTime(lap.total));
        }
        return text.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class LapAdapter extends BaseAdapter {
        @Override public int getCount() { return state == null ? 0 : state.laps.size(); }
        @Override public StopwatchService.Lap getItem(int position) { return state.laps.get(state.laps.size() - 1 - position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = getLayoutInflater().inflate(R.layout.item_lap, parent, false);
            TextView number = row.findViewById(R.id.lapNumber);
            TextView split = row.findViewById(R.id.lapSplit);
            TextView total = row.findViewById(R.id.lapTotal);
            int originalIndex = state.laps.size() - 1 - position;
            StopwatchService.Lap lap = getItem(position);

            number.setText("Lap " + (originalIndex + 1));
            split.setText(StopwatchService.formatTime(lap.split));
            total.setText(StopwatchService.formatTime(lap.total));
            int color = getColor(R.color.text_primary);
            int numberColor = getColor(R.color.text_muted);
            if (state.laps.size() > 1) {
                long fastest = Long.MAX_VALUE;
                long slowest = Long.MIN_VALUE;
                for (StopwatchService.Lap item : state.laps) {
                    fastest = Math.min(fastest, item.split);
                    slowest = Math.max(slowest, item.split);
                }
                if (fastest != slowest && lap.split == fastest) color = numberColor = getColor(R.color.fastest);
                if (fastest != slowest && lap.split == slowest) color = numberColor = getColor(R.color.slowest);
            }
            number.setTextColor(numberColor);
            split.setTextColor(color);
            total.setTextColor(color);
            row.setOnLongClickListener(view -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                String line = "Lap " + (originalIndex + 1) + ": " + StopwatchService.formatTime(lap.split)
                        + " (total " + StopwatchService.formatTime(lap.total) + ")";
                clipboard.setPrimaryClip(ClipData.newPlainText("Stopwatch lap", line));
                Toast.makeText(MainActivity.this, "Lap copied", Toast.LENGTH_SHORT).show();
                return true;
            });
            return row;
        }
    }
}
