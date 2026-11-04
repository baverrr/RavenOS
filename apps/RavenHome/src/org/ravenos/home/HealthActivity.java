package org.ravenos.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Device health: the Echo's system, processor, memory, storage, temperatures, network,
 * Bluetooth, display and sound on glass cards in two columns, refreshed every 5 seconds while
 * the page is open. Meters turn amber and then red as a value gets worse. Opened from
 * Settings › About.
 */
public class HealthActivity extends Activity {
    private static final long REFRESH_MS = 5_000L;
    private static final int MAX_WIDTH_DP = 1100;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private DeviceStats mStats;
    private TextView mStatus;
    private LinearLayout mLeft;
    private LinearLayout mRight;
    private boolean mResumed;

    private final Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            mExecutor.execute(() -> {
                List<DeviceStats.Section> sections = mStats.read();
                mHandler.post(() -> show(sections));
            });
            mHandler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStats = new DeviceStats(this);

        TextView title = new TextView(this);
        title.setTextAppearance(R.style.Raven_Title);
        title.setShadowLayer(0, 0, 0, 0);
        title.setText(R.string.health_title);
        mStatus = new TextView(this);
        mStatus.setTextAppearance(R.style.Raven_Caption);
        mStatus.setText(R.string.health_updated);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(title);
        titles.addView(mStatus);

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

        mLeft = column();
        mRight = column();
        LinearLayout columns = new LinearLayout(this);
        columns.addView(mLeft, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rightParams.setMarginStart(dp(20));
        columns.addView(mRight, rightParams);

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
        content.addView(header);
        LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        columnsParams.topMargin = dp(20);
        content.addView(columns, columnsParams);

        ScrollView scroll = new ScrollView(this);
        // The backdrop runs behind the status and navigation bars, but the cards stop at
        // them and fade out where there is more to scroll to.
        scroll.setClipToPadding(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(48));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(40), insets.getSystemWindowInsetTop(), dp(40),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        content.setPadding(0, dp(32), 0, dp(12));
        // The remote's D-pad scrolls the page.
        scroll.setFocusable(true);
        LinearLayout centre = new LinearLayout(this);
        centre.setGravity(Gravity.CENTER_HORIZONTAL);
        centre.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroll.addView(centre);
        setContentView(scroll);
        GlowBackdrop.attach(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        mHandler.post(mRefresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        mHandler.removeCallbacks(mRefresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    private void show(List<DeviceStats.Section> sections) {
        if (!mResumed) {
            return;
        }
        mLeft.removeAllViews();
        mRight.removeAllViews();
        // Fill the shorter column next, so the two end up about the same height.
        int leftRows = 0;
        int rightRows = 0;
        String worst = null;
        DeviceStats.Level worstLevel = DeviceStats.Level.OK;
        for (DeviceStats.Section section : sections) {
            boolean left = leftRows <= rightRows;
            (left ? mLeft : mRight).addView(card(section));
            int weight = section.rows.size() + 2;
            if (left) {
                leftRows += weight;
            } else {
                rightRows += weight;
            }
            for (DeviceStats.Row row : section.rows) {
                if (row.level.compareTo(worstLevel) > 0) {
                    worstLevel = row.level;
                    worst = section.title.equals(row.label) ? row.label
                            : section.title + " " + row.label.toLowerCase();
                }
            }
        }
        String status = worstLevel == DeviceStats.Level.BAD
                ? getString(R.string.health_status_bad, worst)
                : worstLevel == DeviceStats.Level.WARN
                ? getString(R.string.health_status_warn, worst)
                : getString(R.string.health_status_ok);
        mStatus.setText(status + " · " + getString(R.string.health_updated));
        mStatus.setTextColor(color(worstLevel, R.color.raven_text_dim));
    }

    private View card(DeviceStats.Section section) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(18), dp(24), dp(14));
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(getColor(R.color.settings_card));
        glass.setStroke(Math.max(1, dp(1)), getColor(R.color.settings_card_stroke));
        glass.setCornerRadius(getResources().getDimension(R.dimen.settings_card_radius));
        card.setBackground(glass);

        TextView title = new TextView(this);
        title.setTextAppearance(R.style.Raven_Label);
        title.setTextColor(getColor(R.color.raven_text_muted));
        title.setText(section.title);
        title.setPadding(0, 0, 0, dp(6));
        card.addView(title);

        for (DeviceStats.Row row : section.rows) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(6), 0, dp(6));
            TextView label = new TextView(this);
            label.setTextAppearance(R.style.Raven_Caption);
            label.setText(row.label);
            TextView value = new TextView(this);
            value.setTextAppearance(R.style.Raven_Body);
            value.setTextSize(17);
            value.setGravity(Gravity.END);
            value.setText(row.value);
            if (row.level != DeviceStats.Level.OK) {
                value.setTextColor(color(row.level, R.color.raven_text));
            }
            line.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            valueParams.setMarginStart(dp(16));
            line.addView(value, valueParams);
            card.addView(line);
            if (row.meter >= 0) {
                LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
                meterParams.bottomMargin = dp(6);
                card.addView(new Meter(this, row.meter,
                        color(row.level, R.color.raven_text_dim)), meterParams);
            }
        }
        return card;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this) {
            @Override
            public void addView(View child) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(20);
                super.addView(child, params);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private int color(DeviceStats.Level level, int okColor) {
        switch (level) {
            case BAD:
                return getColor(R.color.raven_danger);
            case WARN:
                return getColor(R.color.raven_warn);
            default:
                return getColor(okColor);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    /** A thin rounded bar, filled to a fraction in the given color. */
    private static final class Meter extends View {
        private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final float mFraction;

        Meter(Context context, float fraction, int color) {
            super(context);
            mFraction = Math.max(0f, Math.min(1f, fraction));
            mTrack.setColor(context.getColor(R.color.settings_separator));
            mFill.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            mRect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(mRect, r, r, mTrack);
            if (mFraction > 0f) {
                mRect.right = Math.max(getHeight(), getWidth() * mFraction);
                canvas.drawRoundRect(mRect, r, r, mFill);
            }
        }
    }
}
