package com.bsmith.customstopwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String KEY_INITIAL = "initial";
    private static final String KEY_ELAPSED = "elapsed";
    private static final String KEY_STARTED_AT = "startedAt";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_HAS_STARTED = "hasStarted";
    private static final String KEY_LAP_TOTALS = "lapTotals";
    private static final String KEY_LAP_SPLITS = "lapSplits";

    private TextView timeMain;
    private TextView timeCentis;
    private TextView setStartButton;
    private TextView emptyLaps;
    private Button startButton;
    private Button resetButton;
    private Button lapButton;
    private ListView lapList;
    private LapAdapter lapAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Lap> laps = new ArrayList<>();
    private long initialMs = 0L;
    private long elapsedMs = 0L;
    private long startedAt = 0L;
    private boolean running = false;
    private boolean hasStarted = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            renderTime(currentTime());
            handler.postDelayed(this, 16L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timeMain = findViewById(R.id.timeMain);
        timeCentis = findViewById(R.id.timeCentis);
        setStartButton = findViewById(R.id.setStartButton);
        emptyLaps = findViewById(R.id.emptyLaps);
        startButton = findViewById(R.id.startButton);
        resetButton = findViewById(R.id.resetButton);
        lapButton = findViewById(R.id.lapButton);
        lapList = findViewById(R.id.lapList);

        lapAdapter = new LapAdapter(this);
        lapList.setAdapter(lapAdapter);

        if (savedInstanceState != null) restoreState(savedInstanceState);

        startButton.setOnClickListener(view -> toggleRunning());
        resetButton.setOnClickListener(view -> reset());
        lapButton.setOnClickListener(view -> recordLap());
        setStartButton.setOnClickListener(view -> showStartTimeDialog());

        renderTime(currentTime());
        refreshControls();
        refreshLapVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (running) startTicker();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_INITIAL, initialMs);
        outState.putLong(KEY_ELAPSED, elapsedMs);
        outState.putLong(KEY_STARTED_AT, startedAt);
        outState.putBoolean(KEY_RUNNING, running);
        outState.putBoolean(KEY_HAS_STARTED, hasStarted);

        long[] totals = new long[laps.size()];
        long[] splits = new long[laps.size()];
        for (int i = 0; i < laps.size(); i++) {
            totals[i] = laps.get(i).total;
            splits[i] = laps.get(i).split;
        }
        outState.putLongArray(KEY_LAP_TOTALS, totals);
        outState.putLongArray(KEY_LAP_SPLITS, splits);
    }

    private void restoreState(Bundle state) {
        initialMs = state.getLong(KEY_INITIAL, 0L);
        elapsedMs = state.getLong(KEY_ELAPSED, initialMs);
        startedAt = state.getLong(KEY_STARTED_AT, 0L);
        running = state.getBoolean(KEY_RUNNING, false);
        hasStarted = state.getBoolean(KEY_HAS_STARTED, false);

        long[] totals = state.getLongArray(KEY_LAP_TOTALS);
        long[] splits = state.getLongArray(KEY_LAP_SPLITS);
        if (totals != null && splits != null) {
            int count = Math.min(totals.length, splits.length);
            for (int i = 0; i < count; i++) laps.add(new Lap(totals[i], splits[i]));
        }
    }

    private long currentTime() {
        if (!running) return elapsedMs;
        return elapsedMs + (SystemClock.elapsedRealtime() - startedAt);
    }

    private void toggleRunning() {
        if (running) {
            elapsedMs = currentTime();
            running = false;
            handler.removeCallbacks(ticker);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            renderTime(elapsedMs);
        } else {
            startedAt = SystemClock.elapsedRealtime();
            running = true;
            hasStarted = true;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            startTicker();
        }
        refreshControls();
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.post(ticker);
    }

    private void reset() {
        running = false;
        hasStarted = false;
        elapsedMs = initialMs;
        laps.clear();
        handler.removeCallbacks(ticker);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderTime(elapsedMs);
        lapAdapter.notifyDataSetChanged();
        refreshLapVisibility();
        refreshControls();
    }

    private void recordLap() {
        if (!running) return;
        long total = currentTime();
        long previousTotal = laps.isEmpty() ? initialMs : laps.get(laps.size() - 1).total;
        laps.add(new Lap(total, total - previousTotal));
        lapAdapter.notifyDataSetChanged();
        refreshLapVisibility();
        lapList.setSelection(0);
    }

    private void refreshControls() {
        startButton.setText(running ? R.string.pause : (hasStarted ? R.string.resume : R.string.start));
        startButton.setBackgroundResource(running ? R.drawable.button_paused : R.drawable.button_primary);
        startButton.setTextColor(running ? getColor(R.color.accent) : Color.WHITE);
        resetButton.setEnabled(hasStarted || elapsedMs != initialMs);
        lapButton.setEnabled(running);
        setStartButton.setEnabled(!hasStarted);
        setStartButton.setAlpha(hasStarted ? 0.35f : 1f);
    }

    private void refreshLapVisibility() {
        boolean hasLaps = !laps.isEmpty();
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
        String description = hours + " hours, " + minutes + " minutes, " + seconds + " seconds";
        timeMain.setContentDescription(description);
    }

    private String formatLap(long milliseconds) {
        long safe = Math.max(0L, milliseconds);
        long hours = safe / 3_600_000L;
        long minutes = (safe / 60_000L) % 60L;
        long seconds = (safe / 1_000L) % 60L;
        long centis = (safe % 1_000L) / 10L;
        return String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis);
    }

    private void showStartTimeDialog() {
        if (hasStarted) return;

        long safe = Math.max(0L, initialMs);
        int hours = (int) Math.min(99L, safe / 3_600_000L);
        int minutes = (int) ((safe / 60_000L) % 60L);
        int seconds = (int) ((safe / 1_000L) % 60L);
        int centis = (int) ((safe % 1_000L) / 10L);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView explanation = new TextView(this);
        explanation.setText("The stopwatch will count up from this time.");
        explanation.setTextColor(getColor(R.color.text_muted));
        explanation.setTextSize(15f);
        explanation.setPadding(0, 0, 0, dp(18));
        content.addView(explanation);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        EditText hoursInput = addTimeField(fields, "Hours", hours, 99);
        EditText minutesInput = addTimeField(fields, "Minutes", minutes, 59);
        EditText secondsInput = addTimeField(fields, "Seconds", seconds, 59);
        EditText centisInput = addTimeField(fields, "Hundredths", centis, 99);
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
                    initialMs = ((h * 3600L + m * 60L + s) * 1000L) + c * 10L;
                    elapsedMs = initialMs;
                    renderTime(elapsedMs);
                    refreshControls();
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

    private EditText addTimeField(LinearLayout parent, String label, int value, int max) {
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
        input.setTag(max);
        column.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(column, columnParams);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Lap {
        final long total;
        final long split;

        Lap(long total, long split) {
            this.total = total;
            this.split = split;
        }
    }

    private class LapAdapter extends BaseAdapter {
        private final Context context;

        LapAdapter(Context context) {
            this.context = context;
        }

        @Override public int getCount() { return laps.size(); }
        @Override public Lap getItem(int position) { return laps.get(laps.size() - 1 - position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = getLayoutInflater().inflate(R.layout.item_lap, parent, false);

            TextView number = row.findViewById(R.id.lapNumber);
            TextView split = row.findViewById(R.id.lapSplit);
            TextView total = row.findViewById(R.id.lapTotal);
            int originalIndex = laps.size() - 1 - position;
            Lap lap = getItem(position);

            number.setText("Lap " + (originalIndex + 1));
            split.setText(formatLap(lap.split));
            total.setText(formatLap(lap.total));

            int color = getColor(R.color.text_primary);
            int numberColor = getColor(R.color.text_muted);
            if (laps.size() > 1) {
                long fastest = Long.MAX_VALUE;
                long slowest = Long.MIN_VALUE;
                for (Lap item : laps) {
                    fastest = Math.min(fastest, item.split);
                    slowest = Math.max(slowest, item.split);
                }
                if (fastest != slowest && lap.split == fastest) color = numberColor = getColor(R.color.fastest);
                if (fastest != slowest && lap.split == slowest) color = numberColor = getColor(R.color.slowest);
            }
            number.setTextColor(numberColor);
            split.setTextColor(color);
            total.setTextColor(color);
            return row;
        }
    }
}
