/*
 * SPDX-FileCopyrightText: 2026 RavenOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ravenos.home;

import android.animation.TimeAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * The RavenOS glass backdrop with a glow that follows the finger: while the screen is touched,
 * a soft glow in the theme's settings_touch_glow color eases toward the touch point, and it fades
 * out after the finger lifts. It draws behind all content and never consumes touches.
 *
 * <p>Use {@link #attach(Activity)} after setContentView(). A copy of the class in Android Settings
 * (com.android.settings.widget.GlowBackdrop) with only the package, R import and resource
 * names changed; keep the copies in step.
 */
public class GlowBackdrop extends Drawable implements TimeAnimator.TimeListener {

    /** How quickly the glow catches up with the finger. */
    private static final float FOLLOW_MILLIS = 40f;
    private static final float FADE_IN_MILLIS = 80f;
    private static final float FADE_OUT_MILLIS = 600f;
    /** Glow radius as a fraction of the shorter screen side. */
    private static final float RADIUS_FRACTION = 0.45f;

    private final Drawable mBase;
    private final int mColor;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final TimeAnimator mAnimator = new TimeAnimator();

    private float mX = Float.NaN;
    private float mY;
    private float mTargetX;
    private float mTargetY;
    private float mStrength;
    private boolean mTouching;

    public GlowBackdrop(Drawable base, int color) {
        mBase = base;
        mColor = color;
        mAnimator.setTimeListener(this);
    }

    /**
     * Gives the activity's glass backdrop a glow that follows touches. Does nothing when the
     * window doesn't use the glass backdrop (e.g. setup wizard pages) or the variant has turned
     * the glow off.
     */
    public static void attach(Activity activity) {
        final TypedValue background = new TypedValue();
        if (!activity.getTheme().resolveAttribute(android.R.attr.windowBackground, background,
                true) || background.resourceId != R.drawable.settings_backdrop) {
            return;
        }
        final int color = activity.getColor(R.color.settings_touch_glow);
        if ((color >>> 24) == 0) {
            return;
        }
        final Window window = activity.getWindow();
        final GlowBackdrop glow = new GlowBackdrop(activity.getDrawable(R.drawable.settings_backdrop),
                color);
        window.setBackgroundDrawable(glow);
        window.setCallback(new TouchFollower(window.getCallback(), glow));
    }

    /** Feeds a touch event, in the drawable's coordinates, to the glow. */
    public void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mTargetX = event.getX();
                mTargetY = event.getY();
                if (Float.isNaN(mX) || mStrength == 0f) {
                    // Appear under the finger instead of sliding in from the last spot.
                    mX = mTargetX;
                    mY = mTargetY;
                }
                mTouching = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouching = false;
                break;
            default:
                return;
        }
        if (!mAnimator.isStarted()) {
            mAnimator.start();
        }
    }

    @Override
    public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
        final float dt = Math.min(deltaTime, 50);
        final float follow = 1f - (float) Math.exp(-dt / FOLLOW_MILLIS);
        mX += (mTargetX - mX) * follow;
        mY += (mTargetY - mY) * follow;
        if (mTouching) {
            mStrength = Math.min(1f, mStrength + dt / FADE_IN_MILLIS);
        } else {
            mStrength = Math.max(0f, mStrength - dt / FADE_OUT_MILLIS);
            if (mStrength == 0f) {
                mAnimator.end();
            }
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        mBase.draw(canvas);
        if (mStrength <= 0f || Float.isNaN(mX)) {
            return;
        }
        final Rect bounds = getBounds();
        final float radius = RADIUS_FRACTION * Math.min(bounds.width(), bounds.height());
        final int alpha = Math.round((mColor >>> 24) * mStrength);
        final int center = (alpha << 24) | (mColor & 0xFFFFFF);
        mPaint.setShader(new RadialGradient(mX, mY, radius,
                new int[] {center, center & 0xFFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(mX, mY, radius, mPaint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mBase.setBounds(bounds);
    }

    @Override
    public void setAlpha(int alpha) {
        mBase.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBase.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    /** Passes every window event through, showing touches to the glow on the way. */
    private static class TouchFollower implements Window.Callback {
        private final Window.Callback mWrapped;
        private final GlowBackdrop mGlow;

        TouchFollower(Window.Callback wrapped, GlowBackdrop glow) {
            mWrapped = wrapped;
            mGlow = glow;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            mGlow.onTouchEvent(event);
            return mWrapped.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return mWrapped.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return mWrapped.dispatchKeyShortcutEvent(event);
        }

        @Override
        public boolean dispatchTrackballEvent(MotionEvent event) {
            return mWrapped.dispatchTrackballEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return mWrapped.dispatchGenericMotionEvent(event);
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return mWrapped.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public View onCreatePanelView(int featureId) {
            return mWrapped.onCreatePanelView(featureId);
        }

        @Override
        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            return mWrapped.onCreatePanelMenu(featureId, menu);
        }

        @Override
        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            return mWrapped.onPreparePanel(featureId, view, menu);
        }

        @Override
        public boolean onMenuOpened(int featureId, Menu menu) {
            return mWrapped.onMenuOpened(featureId, menu);
        }

        @Override
        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return mWrapped.onMenuItemSelected(featureId, item);
        }

        @Override
        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
            mWrapped.onWindowAttributesChanged(attrs);
        }

        @Override
        public void onContentChanged() {
            mWrapped.onContentChanged();
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            mWrapped.onWindowFocusChanged(hasFocus);
        }

        @Override
        public void onAttachedToWindow() {
            mWrapped.onAttachedToWindow();
        }

        @Override
        public void onDetachedFromWindow() {
            mWrapped.onDetachedFromWindow();
        }

        @Override
        public void onPanelClosed(int featureId, Menu menu) {
            mWrapped.onPanelClosed(featureId, menu);
        }

        @Override
        public boolean onSearchRequested() {
            return mWrapped.onSearchRequested();
        }

        @Override
        public boolean onSearchRequested(SearchEvent searchEvent) {
            return mWrapped.onSearchRequested(searchEvent);
        }

        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
            return mWrapped.onWindowStartingActionMode(callback);
        }

        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
            return mWrapped.onWindowStartingActionMode(callback, type);
        }

        @Override
        public void onActionModeStarted(ActionMode mode) {
            mWrapped.onActionModeStarted(mode);
        }

        @Override
        public void onActionModeFinished(ActionMode mode) {
            mWrapped.onActionModeFinished(mode);
        }

        @Override
        public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu,
                int deviceId) {
            mWrapped.onProvideKeyboardShortcuts(data, menu, deviceId);
        }

        @Override
        public void onPointerCaptureChanged(boolean hasCapture) {
            mWrapped.onPointerCaptureChanged(hasCapture);
        }
    }
}
