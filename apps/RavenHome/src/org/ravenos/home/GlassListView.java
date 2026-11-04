package org.ravenos.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Preference list for the RavenOS page in the glass style of Android Settings: each run of
 * preferences between category headers is one rounded glass card with hairline separators
 * inset to the title, in a centred column no wider than MAX_WIDTH_DP. The same look as
 * GlassGroupDecoration in Settings, for the framework (ListView) preference list.
 */
public class GlassListView extends ListView {
    private static final int MAX_WIDTH_DP = 760;
    private static final int MIN_SIDE_DP = 24;

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSeparatorPaint = new Paint();
    private final RectF mRun = new RectF();
    private final float mRadius;
    private final float mStrokeWidth;

    public GlassListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = context.getResources();
        mRadius = res.getDimension(R.dimen.settings_card_radius);
        mStrokeWidth = res.getDisplayMetrics().density;
        mFillPaint.setColor(context.getColor(R.color.settings_card));
        mStrokePaint.setColor(context.getColor(R.color.settings_card_stroke));
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(mStrokeWidth);
        mSeparatorPaint.setColor(context.getColor(R.color.settings_separator));

        setDivider(null);
        setDividerHeight(0);
        setCacheColorHint(0);
        setBackground(null);
        // Row touch feedback stays inside the row; the card is drawn underneath.
        setSelector(new RippleDrawable(
                ColorStateList.valueOf(context.getColor(R.color.settings_ripple)),
                null, new ColorDrawable(0xFFFFFFFF)));
        setDrawSelectorOnTop(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        int side = Math.max((int) (MIN_SIDE_DP * density),
                (w - (int) (MAX_WIDTH_DP * density)) / 2);
        if (side != getPaddingLeft()) {
            post(() -> setPadding(side, getPaddingTop(), side, getPaddingBottom()));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawCards(canvas);
        super.dispatchDraw(canvas);
    }

    private void drawCards(Canvas canvas) {
        boolean inRun = false;
        int first = getFirstVisiblePosition();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int position = first + i;
            if (!isCard(position)) {
                if (inRun) {
                    // The rest of this group is not laid out yet; leave its bottom open.
                    drawRun(canvas, false);
                    inRun = false;
                }
                continue;
            }
            float top = child.getTop();
            float bottom = child.getBottom();
            boolean startsGroup = !isCard(position - 1);
            if (!inRun) {
                // A group whose first rows have scrolled away keeps its top edge off-screen.
                mRun.set(child.getLeft(), startsGroup ? top : top - 2 * mRadius,
                        child.getRight(), bottom);
                inRun = true;
            } else {
                mRun.bottom = bottom;
                drawSeparator(canvas, child, top);
            }
            if (!isCard(position + 1)) {
                drawRun(canvas, true);
                inRun = false;
            }
        }
        if (inRun) {
            drawRun(canvas, false);
        }
    }

    private void drawRun(Canvas canvas, boolean closed) {
        if (!closed) {
            mRun.bottom += 2 * mRadius;
        }
        canvas.drawRoundRect(mRun, mRadius, mRadius, mFillPaint);
        float half = mStrokeWidth / 2;
        canvas.drawRoundRect(mRun.left + half, mRun.top + half, mRun.right - half,
                mRun.bottom - half, mRadius, mRadius, mStrokePaint);
    }

    private void drawSeparator(Canvas canvas, View child, float y) {
        float start = child.getLeft();
        View title = child.findViewById(android.R.id.title);
        if (title != null && title.getVisibility() == View.VISIBLE) {
            int left = 0;
            for (View v = title; v != child && v != null; v = (View) v.getParent()) {
                left += v.getLeft();
            }
            start += left;
        } else {
            start += mRadius;
        }
        canvas.drawRect(start, y, child.getRight(), y + mStrokeWidth, mSeparatorPaint);
    }

    private boolean isCard(int position) {
        ListAdapter adapter = getAdapter();
        if (adapter == null || position < 0 || position >= adapter.getCount()) {
            return false;
        }
        Object item = adapter.getItem(position);
        return item instanceof Preference && !(item instanceof PreferenceCategory);
    }
}
