package org.ravenos.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Settings › Sound › Speaker tuning: turns the built-in speaker's tune on or off and moves its
 * bass, mids and treble. Changes are heard at once.
 */
public class SpeakerTuningActivity extends Activity {
    private static final int MAX_WIDTH_DP = 820;

    private SharedPreferences mPrefs;
    private Switch mSwitch;
    private final SeekBar[] mSliders = new SeekBar[3];
    private final TextView[] mValues = new TextView[3];
    private static final String[] KEYS = {
            SpeakerTuning.PREF_BASS, SpeakerTuning.PREF_MIDS, SpeakerTuning.PREF_TREBLE};
    private static final int[] LABELS = {
            R.string.speaker_bass, R.string.speaker_mids, R.string.speaker_treble};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = new Prefs(this).raw();
        // Normally already running; this covers a page opened before anything else started it.
        SpeakerTuning.start(this);

        TextView title = new TextView(this);
        title.setTextAppearance(R.style.Raven_Title);
        title.setShadowLayer(0, 0, 0, 0);
        title.setText(R.string.speaker_title);
        TextView subtitle = new TextView(this);
        subtitle.setTextAppearance(R.style.Raven_Caption);
        subtitle.setText(R.string.speaker_subtitle);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(title);
        titles.addView(subtitle);

        ImageButton close = new ImageButton(this);
        close.setBackgroundResource(R.drawable.round_button_bg);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription(getString(R.string.cancel));
        close.setOnClickListener(v -> finish());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(close, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout card = card();
        mSwitch = new Switch(this);
        mSwitch.setText(R.string.speaker_switch);
        mSwitch.setTextAppearance(R.style.Raven_Body);
        mSwitch.setPadding(0, dp(10), 0, dp(10));
        tint(mSwitch);
        mSwitch.setOnCheckedChangeListener((v, on) -> {
            mPrefs.edit().putBoolean(SpeakerTuning.PREF_ENABLED, on).apply();
            updateEnabled();
            reload();
        });
        card.addView(mSwitch);
        TextView note = new TextView(this);
        note.setTextAppearance(R.style.Raven_Caption);
        note.setText(R.string.speaker_switch_summary);
        note.setPadding(0, 0, 0, dp(8));
        card.addView(note);

        for (int i = 0; i < KEYS.length; i++) {
            card.addView(slider(i));
        }

        Button reset = new Button(this);
        reset.setText(R.string.speaker_reset);
        reset.setBackgroundResource(R.drawable.chip_bg);
        reset.setTextColor(getColor(R.color.raven_text));
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            SharedPreferences.Editor edit = mPrefs.edit();
            for (String key : KEYS) {
                edit.putInt(key, 0);
            }
            edit.apply();
            load();
            reload();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        resetParams.topMargin = dp(12);
        resetParams.gravity = Gravity.END;
        card.addView(reset, resetParams);

        LinearLayout content = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int max = dp(MAX_WIDTH_DP);
                if (MeasureSpec.getSize(widthSpec) > max) {
                    widthSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(32), 0, dp(12));
        content.addView(header);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(20);
        content.addView(card, cardParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(48));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(40), insets.getSystemWindowInsetTop(), dp(40),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout centre = new LinearLayout(this);
        centre.setGravity(Gravity.CENTER_HORIZONTAL);
        centre.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroll.addView(centre);
        setContentView(scroll);
        GlowBackdrop.attach(this);
        load();
    }

    private View slider(int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));

        LinearLayout line = new LinearLayout(this);
        TextView label = new TextView(this);
        label.setTextAppearance(R.style.Raven_Body);
        label.setText(LABELS[index]);
        TextView value = new TextView(this);
        value.setTextAppearance(R.style.Raven_Caption);
        value.setGravity(Gravity.END);
        mValues[index] = value;
        line.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(value);
        row.addView(line);

        SeekBar bar = new SeekBar(this);
        bar.setMax(2 * SpeakerTuning.RANGE_DB);
        bar.setKeyProgressIncrement(1);
        tint(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int db = progress - SpeakerTuning.RANGE_DB;
                mValues[index].setText(format(db));
                if (fromUser) {
                    mPrefs.edit().putInt(KEYS[index], db).apply();
                    reload();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mSliders[index] = bar;
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        row.addView(bar, barParams);
        return row;
    }

    private void load() {
        mSwitch.setChecked(mPrefs.getBoolean(SpeakerTuning.PREF_ENABLED, true));
        for (int i = 0; i < KEYS.length; i++) {
            int db = mPrefs.getInt(KEYS[i], 0);
            mSliders[i].setProgress(db + SpeakerTuning.RANGE_DB);
            mValues[i].setText(format(db));
        }
        updateEnabled();
    }

    private void updateEnabled() {
        boolean on = mSwitch.isChecked();
        for (int i = 0; i < KEYS.length; i++) {
            mSliders[i].setEnabled(on);
            mSliders[i].setAlpha(on ? 1f : 0.4f);
            mValues[i].setAlpha(on ? 1f : 0.4f);
        }
    }

    private static void reload() {
        SpeakerTuning tuning = SpeakerTuning.get();
        if (tuning != null) {
            tuning.reload();
        }
    }

    private String format(int db) {
        if (db == 0) {
            return getString(R.string.speaker_value_default);
        }
        return getString(R.string.speaker_value_db, (db > 0 ? "+" : "−") + Math.abs(db));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(14), dp(24), dp(18));
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(getColor(R.color.settings_card));
        glass.setStroke(Math.max(1, dp(1)), getColor(R.color.settings_card_stroke));
        glass.setCornerRadius(getResources().getDimension(R.dimen.settings_card_radius));
        card.setBackground(glass);
        return card;
    }

    private void tint(SeekBar bar) {
        ColorStateList accent = ColorStateList.valueOf(getColor(R.color.raven_accent));
        bar.setProgressTintList(accent);
        bar.setThumbTintList(accent);
        bar.setProgressBackgroundTintList(
                ColorStateList.valueOf(getColor(R.color.settings_separator)));
    }

    private void tint(Switch view) {
        int accent = getColor(R.color.raven_accent);
        int off = getColor(R.color.raven_text_dim);
        int[][] states = {{android.R.attr.state_checked}, {}};
        view.setThumbTintList(new ColorStateList(states, new int[] {accent, off}));
        view.setTrackTintList(new ColorStateList(states,
                new int[] {(accent & 0xFFFFFF) | 0x80000000, getColor(R.color.settings_separator)}));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
