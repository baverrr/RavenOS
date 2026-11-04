package org.ravenos.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Alarms on the left, timers (presets, custom, running) on the right. */
public class AlarmsActivity extends Activity {
    private static final int[] PRESET_MINUTES = {1, 3, 5, 10, 15, 30};
    private static final long TICK_MS = 500L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private LinearLayout mAlarmList;
    private LinearLayout mTimerList;
    /** Countdown text views by timer id, updated every tick. */
    private final SparseArray<TextView> mTimerTexts = new SparseArray<>();
    private List<Timers.Timer> mTimers = new ArrayList<>();

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            updateCountdowns();
            mHandler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarms);
        GlowBackdrop.attach(this);
        mAlarmList = findViewById(R.id.alarm_list);
        mTimerList = findViewById(R.id.timer_list);
        findViewById(R.id.close).setOnClickListener(v -> finish());
        findViewById(R.id.add_alarm).setOnClickListener(v -> {
            Alarms.Alarm a = new Alarms.Alarm();
            a.hour = 7;
            editTime(a);
        });
        buildPresets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        mHandler.post(mTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTick);
    }

    private void refresh() {
        refreshAlarms();
        refreshTimers();
    }

    // Alarms

    private void refreshAlarms() {
        mAlarmList.removeAllViews();
        List<Alarms.Alarm> alarms = Alarms.load(this);
        if (alarms.isEmpty()) {
            mAlarmList.addView(text(R.style.Raven_Caption, getString(R.string.no_alarms)));
            return;
        }
        for (Alarms.Alarm a : alarms) {
            mAlarmList.addView(alarmRow(a));
        }
    }

    private View alarmRow(Alarms.Alarm a) {
        LinearLayout row = card();
        row.setOnClickListener(v -> editTime(a));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView time = text(R.style.Raven_Title, Alarms.formatTime(this, a.hour, a.minute));
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        time.setShadowLayer(0, 0, 0, 0);
        time.setTextColor(getColor(a.enabled ? R.color.raven_text : R.color.raven_text_muted));
        texts.addView(time);
        String caption = Alarms.describeDays(this, a.days);
        if (!TextUtils.isEmpty(a.label)) {
            caption = a.label + "  ·  " + caption;
        }
        texts.addView(text(R.style.Raven_Caption, caption));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch toggle = new Switch(this);
        toggle.setChecked(a.enabled);
        toggle.setScaleX(1.3f);
        toggle.setScaleY(1.3f);
        toggle.setOnCheckedChangeListener((b, checked) -> {
            a.enabled = checked;
            Alarms.put(this, a);
            refreshAlarms();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(72), dp(56)));

        ImageButton delete = iconButton(R.drawable.ic_delete, R.string.alarm_delete);
        delete.setOnClickListener(v -> {
            Alarms.delete(this, a.id);
            refreshAlarms();
        });
        row.addView(delete);
        return row;
    }

    private void editTime(Alarms.Alarm a) {
        new TimePickerDialog(this, R.style.RavenDialog, (view, hour, minute) -> {
            a.hour = hour;
            a.minute = minute;
            editDetails(a);
        }, a.hour, a.minute, DateFormat.is24HourFormat(this)).show();
    }

    private void editDetails(Alarms.Alarm a) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);

        box.addView(text(R.style.Raven_Caption, getString(R.string.alarm_days)));
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = Alarms.shortDayNames();
        CheckBox[] boxes = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            boxes[i] = new CheckBox(this);
            boxes[i].setText(names[i]);
            boxes[i].setChecked((a.days & (1 << i)) != 0);
            days.addView(boxes[i]);
        }
        box.addView(days);

        EditText label = new EditText(this);
        label.setHint(R.string.alarm_label_hint);
        label.setSingleLine(true);
        label.setText(a.label);
        box.addView(label);

        final String[] sound = {a.sound};
        TextView soundButton = text(R.style.Raven_Label, "");
        soundButton.setMinHeight(dp(56));
        soundButton.setGravity(Gravity.CENTER_VERTICAL);
        soundButton.setText(getString(R.string.alarm_sound) + ": " + soundTitle(sound[0]));
        soundButton.setOnClickListener(v -> pickSound(sound[0], picked -> {
            sound[0] = picked;
            soundButton.setText(getString(R.string.alarm_sound) + ": " + soundTitle(picked));
        }));
        box.addView(soundButton);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(Alarms.formatTime(this, a.hour, a.minute))
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) {
                        if (boxes[i].isChecked()) {
                            mask |= 1 << i;
                        }
                    }
                    a.days = mask;
                    a.label = label.getText().toString().trim();
                    a.sound = sound[0] == null ? "" : sound[0];
                    a.enabled = true;
                    Alarms.put(this, a);
                    refreshAlarms();
                });
        if (a.id != 0) {
            b.setNeutralButton(R.string.alarm_delete, (d, w) -> {
                Alarms.delete(this, a.id);
                refreshAlarms();
            });
        }
        b.show();
    }

    private interface SoundPicked {
        void onPicked(String uri);
    }

    private void pickSound(String current, SoundPicked callback) {
        RingtoneManager rm = new RingtoneManager(this);
        rm.setType(RingtoneManager.TYPE_ALARM);
        Cursor c = rm.getCursor();
        List<String> titles = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        titles.add(getString(R.string.alarm_sound_default));
        uris.add("");
        while (c != null && c.moveToNext()) {
            titles.add(c.getString(RingtoneManager.TITLE_COLUMN_INDEX));
            uris.add(rm.getRingtoneUri(c.getPosition()).toString());
        }
        int checked = Math.max(0, uris.indexOf(current == null ? "" : current));
        final Ringtone[] preview = new Ringtone[1];
        new AlertDialog.Builder(this)
                .setTitle(R.string.alarm_sound)
                .setSingleChoiceItems(titles.toArray(new String[0]), checked, (d, which) -> {
                    if (preview[0] != null) {
                        preview[0].stop();
                    }
                    String uri = uris.get(which);
                    preview[0] = RingtoneManager.getRingtone(this, uri.isEmpty()
                            ? RingtoneManager.getActualDefaultRingtoneUri(this,
                                    RingtoneManager.TYPE_ALARM)
                            : Uri.parse(uri));
                    if (preview[0] != null) {
                        preview[0].play();
                    }
                    callback.onPicked(uri);
                })
                .setPositiveButton(R.string.save, null)
                .setOnDismissListener(d -> {
                    if (preview[0] != null) {
                        preview[0].stop();
                    }
                })
                .show();
    }

    private String soundTitle(String uri) {
        if (TextUtils.isEmpty(uri)) {
            return getString(R.string.alarm_sound_default);
        }
        Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(uri));
        return r == null ? getString(R.string.alarm_sound_default) : r.getTitle(this);
    }

    // Timers

    private void buildPresets() {
        LinearLayout presets = findViewById(R.id.presets);
        presets.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i <= PRESET_MINUTES.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                // Keep chips level; baseline alignment shifts any chip whose text wraps.
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);
                presets.addView(row, lp);
            }
            TextView chip;
            if (i < PRESET_MINUTES.length) {
                final int minutes = PRESET_MINUTES[i];
                chip = chip(getString(R.string.minutes_short, minutes));
                chip.setOnClickListener(v -> {
                    Timers.start(this, minutes * 60_000L, "");
                    refreshTimers();
                });
            } else {
                chip = chip(getString(R.string.custom_timer));
                chip.setOnClickListener(v -> customTimer());
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1);
            lp.setMarginEnd(dp(10));
            row.addView(chip, lp);
        }
        // Keep the last row's chips the same width as the others.
        int fill = 4 - (PRESET_MINUTES.length + 1) % 4;
        for (int i = 0; fill < 4 && i < fill; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1);
            lp.setMarginEnd(dp(10));
            row.addView(new View(this), lp);
        }
    }

    private void customTimer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        LinearLayout pickers = new LinearLayout(this);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);
        NumberPicker minutes = picker(0, 180, 5);
        NumberPicker seconds = picker(0, 59, 0);
        pickers.addView(labelled(minutes, R.string.minutes));
        pickers.addView(labelled(seconds, R.string.seconds));
        box.addView(pickers);
        EditText label = new EditText(this);
        label.setHint(R.string.timer_label_hint);
        label.setSingleLine(true);
        label.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(label);
        new AlertDialog.Builder(this)
                .setTitle(R.string.custom_timer)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.start, (d, w) -> {
                    long ms = (minutes.getValue() * 60L + seconds.getValue()) * 1000L;
                    if (ms > 0) {
                        Timers.start(this, ms, label.getText().toString());
                        refreshTimers();
                    }
                })
                .show();
    }

    private void refreshTimers() {
        mTimerList.removeAllViews();
        mTimerTexts.clear();
        mTimers = Timers.load(this);
        if (mTimers.isEmpty()) {
            mTimerList.addView(text(R.style.Raven_Caption, getString(R.string.no_timers)));
            return;
        }
        for (Timers.Timer t : mTimers) {
            mTimerList.addView(timerRow(t));
        }
        updateCountdowns();
    }

    private View timerRow(Timers.Timer t) {
        LinearLayout row = card();

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView left = text(R.style.Raven_Title, "");
        left.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        left.setShadowLayer(0, 0, 0, 0);
        mTimerTexts.put(t.id, left);
        texts.addView(left);
        String name = TextUtils.isEmpty(t.label) ? getString(R.string.timer_default_label) : t.label;
        texts.addView(text(R.style.Raven_Caption, name + "  ·  " + Timers.format(t.durationMs)));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton pause = iconButton(t.isPaused() ? R.drawable.ic_play : R.drawable.ic_pause,
                R.string.np_play_pause);
        pause.setOnClickListener(v -> {
            Timers.togglePause(this, t.id);
            refreshTimers();
        });
        row.addView(pause);

        TextView plus = chip(getString(R.string.add_minute));
        plus.setOnClickListener(v -> {
            Timers.addMinute(this, t.id);
            refreshTimers();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        lp.setMarginStart(dp(8));
        lp.setMarginEnd(dp(8));
        row.addView(plus, lp);

        ImageButton cancel = iconButton(R.drawable.ic_close, R.string.cancel);
        cancel.setOnClickListener(v -> {
            Timers.remove(this, t.id);
            refreshTimers();
        });
        row.addView(cancel);
        return row;
    }

    private void updateCountdowns() {
        long now = System.currentTimeMillis();
        for (Timers.Timer t : mTimers) {
            TextView v = mTimerTexts.get(t.id);
            if (v == null) {
                continue;
            }
            long left = t.remaining(now);
            if (left <= 0 && !t.isPaused()) {
                v.setText(R.string.times_up);
                v.setTextColor(getColor(R.color.raven_accent));
            } else {
                v.setText(Timers.format(left));
                v.setTextColor(getColor(t.isPaused() ? R.color.raven_text_muted : R.color.raven_text));
            }
        }
    }

    // Small view helpers

    private LinearLayout card() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.surface_bg);
        row.setPadding(dp(24), dp(14), dp(12), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView text(int style, String value) {
        TextView v = new TextView(this, null, 0, style);
        v.setText(value);
        return v;
    }

    private TextView chip(String value) {
        TextView v = text(R.style.Raven_Label, value);
        v.setBackgroundResource(R.drawable.chip_bg);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setPadding(dp(8), 0, dp(8), 0);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        return v;
    }

    private ImageButton iconButton(int icon, int description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(getString(description));
        b.setBackgroundResource(R.drawable.round_button_bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        return b;
    }

    private NumberPicker picker(int min, int max, int value) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setValue(value);
        return p;
    }

    private View labelled(View view, int label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(16), 0, dp(16), 0);
        col.addView(view);
        col.addView(text(R.style.Raven_Caption, getString(label)));
        return col;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
