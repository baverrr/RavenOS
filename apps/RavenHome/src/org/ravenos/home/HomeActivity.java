package org.ravenos.home;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The RavenOS home screen (layout S): the background fills the screen with a small clock in
 * the corner. A tap reveals the big clock, weather, alarms, timers, now playing and actions;
 * they hide again after a few seconds without a touch.
 */
public class HomeActivity extends Activity {
    private static final String TAG = "RavenHome";

    private static final long TICK_MS = 1_000L;
    private static final long WEATHER_REFRESH_MS = 30 * 60_000L;
    private static final long WEATHER_RETRY_MS = 5 * 60_000L;
    private static final long SHOW_MS = 250L;
    private static final long HIDE_MS = 200L;
    private static final long HINT_MS = 10_000L;
    private static final long DISCOVERABLE_MS = 5 * 60_000L;
    private static final long NOTICE_MS = 6_000L;
    private static final int REQUEST_STORAGE = 1;
    /** Boot count of the last restart for unreadable storage, so it happens once per boot. */
    private static final String KEY_STORAGE_RESTART_BOOT = "storage_restart_boot";

    /** The tap hint shows once per boot. */
    private static boolean sHintShown;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Prefs mPrefs;
    private Background mBackground;

    private View mScrim;
    private View mAmbient;
    private TextView mAmbientClock;
    private TextView mAmbientAlarm;
    private View mHint;
    private View mDetails;
    private TextView mWeatherNow;
    private TextView mWeatherDetail;
    private LinearLayout mChips;
    private LinearLayout mActions;
    private View mNowPlaying;
    private ImageView mArt;
    /** Cover art URI last asked for, and its bitmap once loaded. */
    private String mArtUri;
    private Bitmap mArtBitmap;
    private TextView mTitle;
    private TextView mArtist;
    private ImageButton mPlay;
    private TextView mSpeakerLabel;
    private TextView mNotice;
    private boolean mWeatherFailed;
    // Weather fails if it's fetched before Wi-Fi is up (right after boot); fetch again as
    // soon as a network is available instead of waiting for the retry delay.
    private final ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mHandler.post(() -> {
                        if (mWeatherFailed) {
                            refreshWeather(true);
                        }
                    });
                }
            };

    private boolean mResumed;
    private boolean mRevealed;
    private boolean mNight;
    private boolean mNightApplied;
    private boolean mAskedStorage;
    private boolean mPendingDiscoverable;
    private long mLastWeather;

    private MediaSessionManager mSessionManager;
    private MediaController mController;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            applyNight();
            refreshWeather(false);
            updateAlarmsAndTimers();
            mHandler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable mAutoHide = this::hideDetails;

    private final Runnable mHideHint = () -> mHint.animate().alpha(0f).setDuration(HIDE_MS)
            .withEndAction(() -> mHint.setVisibility(View.GONE)).start();

    private final Runnable mResetSpeaker = () -> mSpeakerLabel.setText(R.string.bt_short);

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener =
            (prefs, key) -> {
                if (Prefs.WEATHER_LAT.equals(key) || Prefs.WEATHER_LON.equals(key)
                        || Prefs.TEMP_UNIT.equals(key)) {
                    refreshWeather(true);
                } else if (key != null && key.startsWith("night_")) {
                    mNightApplied = false;
                    applyNight();
                } else if (Prefs.BACKGROUND.equals(key)) {
                    restartBackground();
                }
            };

    private final MediaSessionManager.OnActiveSessionsChangedListener mSessionsListener =
            this::updateSessions;

    private final MediaController.Callback mControllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updateNowPlaying();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            updateNowPlaying();
        }

        @Override
        public void onSessionDestroyed() {
            setController(null);
        }
    };

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (mPendingDiscoverable && state == BluetoothAdapter.STATE_ON) {
                mPendingDiscoverable = false;
                makeDiscoverable(BluetoothAdapter.getDefaultAdapter());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        markProvisioned();

        mPrefs = new Prefs(this);
        mPrefs.raw().registerOnSharedPreferenceChangeListener(mPrefsListener);
        mSessionManager = getSystemService(MediaSessionManager.class);

        mScrim = findViewById(R.id.scrim);
        mNotice = findViewById(R.id.notice);
        mAmbient = findViewById(R.id.ambient);
        mAmbientClock = findViewById(R.id.ambient_clock);
        mAmbientAlarm = findViewById(R.id.ambient_alarm);
        mHint = findViewById(R.id.hint);
        mDetails = findViewById(R.id.details);
        mWeatherNow = findViewById(R.id.weather_now);
        mWeatherDetail = findViewById(R.id.weather_detail);
        mChips = findViewById(R.id.chips);
        mNowPlaying = findViewById(R.id.now_playing);
        mArt = findViewById(R.id.np_art);
        mTitle = findViewById(R.id.np_title);
        mArtist = findViewById(R.id.np_artist);
        mPlay = findViewById(R.id.np_play);

        mBackground = new Background(mHandler, mExecutor, mPrefs,
                (TextureView) findViewById(R.id.video),
                (ImageView) findViewById(R.id.photo_a), (ImageView) findViewById(R.id.photo_b));
        mBackground.setOnStorageBlind(this::restartForStorage);

        // A tap anywhere toggles the details; buttons inside handle their own taps.
        View root = findViewById(R.id.root);
        root.setOnClickListener(v -> toggleDetails());
        mDetails.setOnClickListener(v -> hideDetails());
        // Being clickable would make them focusable; the remote's D-pad should only land on
        // the buttons, which show a focus ring.
        root.setFocusable(false);
        mDetails.setFocusable(false);

        findViewById(R.id.np_prev).setOnClickListener(v -> {
            if (mController != null) {
                mController.getTransportControls().skipToPrevious();
            }
        });
        findViewById(R.id.np_next).setOnClickListener(v -> {
            if (mController != null) {
                mController.getTransportControls().skipToNext();
            }
        });
        mPlay.setOnClickListener(v -> {
            if (mController == null) {
                return;
            }
            if (isPlaying(mController)) {
                mController.getTransportControls().pause();
            } else {
                mController.getTransportControls().play();
            }
        });

        mActions = findViewById(R.id.actions);
        addAction(mActions, R.drawable.ic_alarm, R.string.alarms_short,
                v -> startActivity(new Intent(this, AlarmsActivity.class)));
        mSpeakerLabel = addAction(mActions, R.drawable.ic_bluetooth, R.string.bt_short,
                v -> startPairing());
        addAction(mActions, R.drawable.ic_apps, R.string.apps,
                v -> startActivity(new Intent(this, AppsActivity.class)));
        addAction(mActions, R.drawable.ic_settings, R.string.settings,
                v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        registerReceiver(mBluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(mNetworkCallback);
        // Alarms survive app updates and first boots without waiting for BOOT_COMPLETED.
        Alarms.schedule(this);
        Timers.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        hideSystemBars();
        mNightApplied = false;
        applyNight();
        refreshWeather(false);
        updateAlarmsAndTimers();
        try {
            mSessionManager.addOnActiveSessionsChangedListener(mSessionsListener, null, mHandler);
            updateSessions(mSessionManager.getActiveSessions(null));
        } catch (SecurityException e) {
            Log.w(TAG, "No access to media sessions", e);
        }
        if (hasStoragePermission()) {
            Background.VIDEO_DIR.mkdirs();
            Background.PHOTO_DIR.mkdirs();
            if (!mNight) {
                mBackground.start();
            }
        } else if (!mAskedStorage) {
            mAskedStorage = true;
            requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE);
        }
        if (!sHintShown) {
            sHintShown = true;
            mHint.setAlpha(1f);
            mHint.setVisibility(View.VISIBLE);
            mHandler.postDelayed(mHideHint, HINT_MS);
        }
        mHandler.removeCallbacks(mTick);
        mHandler.post(mTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        mHandler.removeCallbacks(mTick);
        mBackground.stop();
        hideDetailsNow();
        try {
            mSessionManager.removeOnActiveSessionsChangedListener(mSessionsListener);
        } catch (SecurityException e) {
            // Never registered.
        }
        setController(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPrefs.raw().unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        unregisterReceiver(mBluetoothReceiver);
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(mNetworkCallback);
        mHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_STORAGE && hasStoragePermission() && mResumed) {
            restartBackground();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home.
        hideDetails();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (mRevealed) {
            scheduleAutoHide();
        }
    }

    @Override
    public void onBackPressed() {
        hideDetails();
    }

    /**
     * A remote: while only the clock shows, the D-pad or OK reveals the details with focus on
     * the first action, the way a tap does. After that the D-pad moves between the buttons.
     * Media keys aren't touched; Android sends them to the playing app.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!mRevealed && isNavigationKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                showDetails();
                mActions.getChildAt(0).requestFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isNavigationKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    /**
     * RavenOS ships without a setup wizard (or AOSP's Provision, which the LineageOS wizard
     * overrides), so the home screen marks the device as set up. Until this is set, the
     * notification shade and the Home button stay disabled.
     */
    private void markProvisioned() {
        try {
            Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);
        } catch (SecurityException e) {
            Log.w(TAG, "Can't mark the device as set up", e);
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private TextView addAction(LinearLayout parent, int icon, int label, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setBackgroundResource(R.drawable.round_button_bg);
        button.setContentDescription(getString(label));
        button.setOnClickListener(l);
        col.addView(button, new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView text = new TextView(this, null, 0, R.style.Raven_Caption);
        text.setText(label);
        text.setShadowLayer(10, 0, 0, getColor(R.color.raven_shadow));
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setPadding(0, dp(6), 0, 0);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        col.addView(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(104),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        parent.addView(col, lp);
        return text;
    }

    // Details reveal

    private void toggleDetails() {
        if (mRevealed) {
            hideDetails();
        } else {
            showDetails();
        }
    }

    private void showDetails() {
        mRevealed = true;
        mHandler.removeCallbacks(mHideHint);
        mHint.setVisibility(View.GONE);
        updateAlarmsAndTimers();
        mDetails.animate().cancel();
        mDetails.setVisibility(View.VISIBLE);
        mDetails.animate().alpha(1f).setDuration(SHOW_MS).start();
        mScrim.animate().alpha(1f).setDuration(SHOW_MS).start();
        mAmbient.animate().alpha(0f).setDuration(SHOW_MS).start();
        scheduleAutoHide();
    }

    private void hideDetails() {
        if (!mRevealed) {
            return;
        }
        mRevealed = false;
        mHandler.removeCallbacks(mAutoHide);
        mDetails.animate().alpha(0f).setDuration(HIDE_MS)
                .withEndAction(() -> mDetails.setVisibility(View.GONE)).start();
        mScrim.animate().alpha(0f).setDuration(HIDE_MS).start();
        mAmbient.animate().alpha(1f).setDuration(HIDE_MS).start();
    }

    private void hideDetailsNow() {
        mRevealed = false;
        mHandler.removeCallbacks(mAutoHide);
        mDetails.animate().cancel();
        mDetails.setAlpha(0f);
        mDetails.setVisibility(View.GONE);
        mScrim.setAlpha(0f);
        mAmbient.setAlpha(1f);
    }

    private void scheduleAutoHide() {
        mHandler.removeCallbacks(mAutoHide);
        mHandler.postDelayed(mAutoHide, mPrefs.revealSeconds() * 1000L);
    }

    // Night

    private void applyNight() {
        boolean night = mPrefs.isNightNow();
        if (mNightApplied && night == mNight) {
            return;
        }
        mNight = night;
        mNightApplied = true;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = night
                ? mPrefs.nightBrightness()
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        // A warm, dim bedside clock at night; the background rests.
        mAmbientClock.setTextColor(getColor(night ? R.color.raven_night : R.color.raven_text));
        mAmbientAlarm.setTextColor(getColor(night ? R.color.raven_night : R.color.raven_text_dim));
        if (mResumed && hasStoragePermission()) {
            if (night) {
                mBackground.stop();
            } else {
                mBackground.start();
            }
        }
    }

    private void restartBackground() {
        mBackground.stop();
        if (mResumed && !mNight && hasStoragePermission()) {
            mBackground.start();
        }
    }

    // Weather

    private void refreshWeather(boolean force) {
        if (!mPrefs.hasWeatherPlace()) {
            mWeatherNow.setText("");
            mWeatherDetail.setText(R.string.weather_unset);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && mLastWeather != 0 && now - mLastWeather < WEATHER_REFRESH_MS) {
            return;
        }
        mLastWeather = now;
        final double lat = mPrefs.lat();
        final double lon = mPrefs.lon();
        final boolean fahrenheit = mPrefs.fahrenheit();
        final String place = mPrefs.placeName();
        mExecutor.execute(() -> {
            try {
                Weather.Report report = Weather.fetch(lat, lon, fahrenheit);
                mHandler.post(() -> {
                    mWeatherFailed = false;
                    showWeather(report, place);
                });
            } catch (Exception e) {
                Log.w(TAG, "Weather update failed", e);
                mHandler.post(() -> {
                    mWeatherFailed = true;
                    if (TextUtils.isEmpty(mWeatherNow.getText())) {
                        mWeatherDetail.setText(R.string.weather_error);
                    }
                    // Try again sooner than the normal refresh.
                    mLastWeather = System.currentTimeMillis() - WEATHER_REFRESH_MS + WEATHER_RETRY_MS;
                });
            }
        });
    }

    private void showWeather(Weather.Report report, String place) {
        mWeatherNow.setText(String.format(Locale.getDefault(), "%d°  %s", report.temp,
                report.condition));
        mWeatherDetail.setText(String.format(Locale.getDefault(), "H %d°  ·  L %d°  ·  %s",
                report.high, report.low, place));
    }

    // Alarms and timers

    private void updateAlarmsAndTimers() {
        long nextAlarm = Alarms.nextRing(this, null);
        String alarmText = nextAlarm == 0 ? null : Alarms.describeNext(this, nextAlarm);
        boolean soon = nextAlarm != 0 && nextAlarm - System.currentTimeMillis() < 24 * 3600_000L;
        if (soon) {
            setTextAndIcon(mAmbientAlarm, alarmText, R.drawable.ic_alarm);
            mAmbientAlarm.setVisibility(View.VISIBLE);
        } else {
            mAmbientAlarm.setVisibility(View.GONE);
        }
        if (!mRevealed) {
            return;
        }
        List<String> chips = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        if (alarmText != null) {
            chips.add(alarmText);
            icons.add(R.drawable.ic_alarm);
        }
        long now = System.currentTimeMillis();
        for (Timers.Timer t : Timers.load(this)) {
            String name = TextUtils.isEmpty(t.label) ? getString(R.string.timer_default_label) : t.label;
            long left = t.remaining(now);
            chips.add(name + "  " + (left > 0 || t.isPaused()
                    ? Timers.format(left) : getString(R.string.times_up)));
            icons.add(R.drawable.ic_hourglass);
        }
        // Reuse chip views so the countdown ticks without rebuilding the row.
        while (mChips.getChildCount() > chips.size()) {
            mChips.removeViewAt(mChips.getChildCount() - 1);
        }
        for (int i = 0; i < chips.size(); i++) {
            TextView chip;
            if (i < mChips.getChildCount()) {
                chip = (TextView) mChips.getChildAt(i);
            } else {
                chip = new TextView(this, null, 0, R.style.Raven_Label);
                chip.setBackgroundResource(R.drawable.chip_bg);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(dp(20), 0, dp(24), 0);
                chip.setCompoundDrawablePadding(dp(10));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                chip.setOnClickListener(v -> startActivity(new Intent(this, AlarmsActivity.class)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
                lp.setMarginEnd(dp(12));
                mChips.addView(chip, lp);
            }
            setTextAndIcon(chip, chips.get(i), icons.get(i));
        }
    }

    /**
     * Sets a label's text and leading icon only when they change. This runs every second, and
     * each set would otherwise lay out the screen again and allocate a new tinted icon.
     */
    private void setTextAndIcon(TextView view, CharSequence text, int icon) {
        if (!TextUtils.equals(view.getText(), text)) {
            view.setText(text);
        }
        if (!Integer.valueOf(icon).equals(view.getTag(R.id.raven_icon_res))) {
            view.setTag(R.id.raven_icon_res, icon);
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(tintedIcon(icon), null, null, null);
        }
    }

    private android.graphics.drawable.Drawable tintedIcon(int res) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        d.setTint(getColor(R.color.raven_accent));
        int size = dp(22);
        d.setBounds(0, 0, size, size);
        return d;
    }

    // Now playing

    private void updateSessions(List<MediaController> controllers) {
        MediaController pick = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                if (isPlaying(c)) {
                    pick = c;
                    break;
                }
                if (pick == null) {
                    pick = c;
                }
            }
        }
        setController(pick);
    }

    private void setController(MediaController controller) {
        if (mController != null) {
            mController.unregisterCallback(mControllerCallback);
        }
        mController = controller;
        if (controller != null) {
            controller.registerCallback(mControllerCallback, mHandler);
        }
        updateNowPlaying();
    }

    private static boolean isPlaying(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING
                || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void updateNowPlaying() {
        MediaMetadata md = mController == null ? null : mController.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(title) && md != null) {
            title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        if (TextUtils.isEmpty(title)) {
            mNowPlaying.setVisibility(View.INVISIBLE);
            return;
        }
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(artist)) {
            artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        mTitle.setText(title);
        mArtist.setText(artist == null ? "" : artist);
        if (art != null) {
            mArtUri = null;
            mArt.setImageBitmap(art);
        } else {
            showArtFromUri(artUri(md));
        }
        mPlay.setImageResource(isPlaying(mController) ? R.drawable.ic_pause : R.drawable.ic_play);
        mNowPlaying.setVisibility(View.VISIBLE);
    }

    /**
     * The cover's URI when the player sends one instead of a bitmap. A phone playing to the Echo
     * over Bluetooth does this: Android 11's AVRCP controller only gives a content:// URI from
     * its cover art provider.
     */
    private static String artUri(MediaMetadata md) {
        for (String key : new String[] {MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String uri = md.getString(key);
            if (!TextUtils.isEmpty(uri)) {
                return uri;
            }
        }
        return null;
    }

    /** Loads the cover off the main thread; the raven stays when there is none or it fails. */
    private void showArtFromUri(String uri) {
        if (uri == null) {
            mArtUri = null;
            mArt.setImageResource(R.drawable.ic_raven);
            return;
        }
        if (uri.equals(mArtUri)) {
            // Same track: keep what is shown, or what is still loading.
            if (mArtBitmap != null) {
                mArt.setImageBitmap(mArtBitmap);
            }
            return;
        }
        mArtUri = uri;
        mArtBitmap = null;
        mArt.setImageResource(R.drawable.ic_raven);
        final int size = Math.max(mArt.getWidth(), dp(96));
        mExecutor.execute(() -> {
            Bitmap bitmap = decodeArt(Uri.parse(uri), size);
            mHandler.post(() -> {
                // A newer track may have started while this one loaded.
                if (bitmap != null && uri.equals(mArtUri)) {
                    mArtBitmap = bitmap;
                    mArt.setImageBitmap(bitmap);
                }
            });
        });
    }

    private Bitmap decodeArt(Uri uri, int size) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / (options.inSampleSize * 2) >= size
                    && bounds.outHeight / (options.inSampleSize * 2) >= size) {
                options.inSampleSize *= 2;
            }
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "No cover art from " + uri, e);
            return null;
        }
    }

    // Bluetooth speaker pairing

    private void startPairing() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            showNotice(R.string.bt_failed);
            return;
        }
        if (adapter.isEnabled()) {
            makeDiscoverable(adapter);
        } else {
            // Finishes in mBluetoothReceiver once Bluetooth is on.
            mPendingDiscoverable = true;
            if (!adapter.enable()) {
                mPendingDiscoverable = false;
                showNotice(R.string.bt_failed);
            }
        }
    }

    private void makeDiscoverable(BluetoothAdapter adapter) {
        boolean ok = adapter != null && adapter.setScanMode(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DISCOVERABLE_MS);
        showNotice(ok ? R.string.bt_discoverable : R.string.bt_failed);
        if (ok) {
            mSpeakerLabel.setText(R.string.bt_pair);
            mHandler.removeCallbacks(mResetSpeaker);
            mHandler.postDelayed(mResetSpeaker, DISCOVERABLE_MS);
        }
    }

    private final Runnable mHideNotice = () -> mNotice.animate().alpha(0f).setDuration(250)
            .withEndAction(() -> mNotice.setVisibility(View.GONE));

    /** Shows a themed notice at the bottom of the home screen for a few seconds. */
    private void showNotice(int textRes) {
        mHandler.removeCallbacks(mHideNotice);
        mNotice.animate().cancel();
        mNotice.setText(textRes);
        if (mNotice.getVisibility() != View.VISIBLE) {
            mNotice.setAlpha(0f);
            mNotice.setVisibility(View.VISIBLE);
        }
        mNotice.animate().alpha(1f).setDuration(200);
        mHandler.postDelayed(mHideNotice, NOTICE_MS);
    }

    /**
     * Restarts RavenHome, at most once per boot, when storage access is granted but shared
     * storage reads as empty. A process keeps the storage view it started with, so a grant that
     * lands after RavenHome started (the default grant is re-applied during the first boot after
     * a flash) only shows up in a new process. The system relaunches the home screen right away.
     */
    private void restartForStorage() {
        if (!hasStoragePermission()) {
            return;
        }
        final int boot = Settings.Global.getInt(getContentResolver(),
                Settings.Global.BOOT_COUNT, -1);
        final SharedPreferences prefs = mPrefs.raw();
        if (prefs.getInt(KEY_STORAGE_RESTART_BOOT, -2) == boot) {
            return;
        }
        prefs.edit().putInt(KEY_STORAGE_RESTART_BOOT, boot).commit();
        Log.i(TAG, "Storage access granted but storage unreadable; restarting");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private boolean hasStoragePermission() {
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
