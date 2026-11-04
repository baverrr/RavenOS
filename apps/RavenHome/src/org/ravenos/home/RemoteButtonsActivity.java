package org.ravenos.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings › Connected devices › Remote buttons: choose what each button on the Portal TV
 * remote does, right on the Echo. Changes work at once.
 */
public class RemoteButtonsActivity extends Activity {
    private static final int MAX_WIDTH_DP = 820;

    private SharedPreferences mPrefs;
    private final TextView[] mSummaries = new TextView[RemoteButtons.KEYS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = new Prefs(this).raw();

        TextView title = new TextView(this);
        title.setTextAppearance(R.style.Raven_Title);
        title.setShadowLayer(0, 0, 0, 0);
        title.setText(R.string.remote_title);
        TextView subtitle = new TextView(this);
        subtitle.setTextAppearance(R.style.Raven_Caption);
        subtitle.setText(R.string.remote_subtitle);
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

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(getColor(R.color.settings_card));
        glass.setStroke(Math.max(1, dp(1)), getColor(R.color.settings_card_stroke));
        glass.setCornerRadius(getResources().getDimension(R.dimen.settings_card_radius));
        card.setBackground(glass);
        for (int i = 0; i < RemoteButtons.KEYS.length; i++) {
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(getColor(R.color.settings_separator));
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
                lineParams.setMarginStart(dp(16));
                card.addView(line, lineParams);
            }
            card.addView(row(i));
        }

        Button reset = new Button(this);
        reset.setText(R.string.remote_reset);
        reset.setBackgroundResource(R.drawable.chip_bg);
        reset.setTextColor(getColor(R.color.raven_text));
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            RemoteButtons.resetAll(this);
            refresh();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        resetParams.topMargin = dp(16);
        resetParams.gravity = Gravity.END;

        TextView note = new TextView(this);
        note.setTextAppearance(R.style.Raven_Caption);
        note.setText(R.string.remote_note);
        note.setPadding(dp(4), dp(16), dp(4), 0);

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
        content.addView(note);
        content.addView(reset, resetParams);

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
        refresh();
    }

    private View row(int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(rowBackground());
        TextView label = new TextView(this);
        label.setTextAppearance(R.style.Raven_Body);
        label.setText(RemoteButtons.KEY_LABELS[index]);
        TextView summary = new TextView(this);
        summary.setTextAppearance(R.style.Raven_Caption);
        mSummaries[index] = summary;
        row.addView(label);
        row.addView(summary);
        row.setOnClickListener(v -> choose(index));
        return row;
    }

    private void refresh() {
        for (int i = 0; i < RemoteButtons.KEYS.length; i++) {
            String action = RemoteButtons.action(mPrefs, RemoteButtons.KEYS[i]);
            mSummaries[i].setText(RemoteButtons.DEFAULT.equals(action)
                    ? defaultLabel(i) : RemoteButtons.label(this, action));
            mSummaries[i].setTextColor(getColor(RemoteButtons.DEFAULT.equals(action)
                    ? R.color.raven_text_dim : R.color.raven_accent));
        }
    }

    private String defaultLabel(int index) {
        return getString(R.string.remote_default_is,
                getString(RemoteButtons.KEY_DEFAULTS[index]));
    }

    private void choose(int index) {
        int key = RemoteButtons.KEYS[index];
        String current = RemoteButtons.action(mPrefs, key);
        int count = RemoteButtons.ACTIONS.length + 2;
        String[] ids = new String[count];
        CharSequence[] labels = new CharSequence[count];
        ids[0] = RemoteButtons.DEFAULT;
        labels[0] = defaultLabel(index);
        for (int i = 0; i < RemoteButtons.ACTIONS.length; i++) {
            ids[i + 1] = RemoteButtons.ACTIONS[i];
            labels[i + 1] = getString(RemoteButtons.ACTION_LABELS[i]);
        }
        ids[count - 1] = RemoteButtons.APP_PREFIX;
        labels[count - 1] = current.startsWith(RemoteButtons.APP_PREFIX)
                ? RemoteButtons.label(this, current) : getString(R.string.remote_action_open_app);
        int checked = 0;
        for (int i = 0; i < count; i++) {
            if (ids[i].equals(current) || (i == count - 1
                    && current.startsWith(RemoteButtons.APP_PREFIX))) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(RemoteButtons.KEY_LABELS[index])
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == count - 1) {
                        chooseApp(key);
                    } else {
                        RemoteButtons.setAction(this, key, ids[which]);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseApp(int key) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(main, 0));
        apps.removeIf(info -> getPackageName().equals(info.activityInfo.packageName));
        List<String> names = new ArrayList<>();
        for (ResolveInfo info : apps) {
            names.add(info.loadLabel(pm).toString());
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            order.add(i);
        }
        Collections.sort(order, (a, b) -> names.get(a).compareToIgnoreCase(names.get(b)));
        CharSequence[] labels = new CharSequence[order.size()];
        String[] packages = new String[order.size()];
        for (int i = 0; i < order.size(); i++) {
            labels[i] = names.get(order.get(i));
            packages[i] = apps.get(order.get(i)).activityInfo.packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_action_open_app)
                .setItems(labels, (dialog, which) -> {
                    RemoteButtons.setAction(this, key, RemoteButtons.APP_PREFIX + packages[which]);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Accent outline and tint while the remote has the row focused, a light tint on press. */
    private StateListDrawable rowBackground() {
        int accent = getColor(R.color.raven_accent);
        float radius = dp(12);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(radius);
        focused.setColor((accent & 0xFFFFFF) | 0x29000000);
        focused.setStroke(dp(2), accent);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(radius);
        pressed.setColor(getColor(R.color.settings_ripple));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        return states;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
