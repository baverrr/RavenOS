package org.ravenos.home;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Carries out the actions picked in Settings › Connected devices › Remote buttons. It sees key
 * presses before apps do; presses from the Portal TV remote on a changed button are replaced by
 * their action, and everything else passes through untouched.
 */
public class RemoteKeyService extends AccessibilityService {
    private static final String TAG = "RavenHome";

    private SharedPreferences mPrefs;

    @Override
    protected void onServiceConnected() {
        mPrefs = new Prefs(this).raw();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (mPrefs == null || !RemoteButtons.isProgrammable(event.getKeyCode())
                || !RemoteButtons.isRemote(event)) {
            return false;
        }
        String action = RemoteButtons.action(mPrefs, event.getKeyCode());
        if (RemoteButtons.DEFAULT.equals(action)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getRepeatCount() == 0 || repeats(action))) {
            perform(action);
        }
        return true;
    }

    /** Held buttons repeat for these, the way the D-pad and volume do. */
    private static boolean repeats(String action) {
        switch (action) {
            case "up":
            case "down":
            case "left":
            case "right":
            case "volume_up":
            case "volume_down":
                return true;
            default:
                return false;
        }
    }

    private void perform(String action) {
        if (action.startsWith(RemoteButtons.APP_PREFIX)) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(
                    action.substring(RemoteButtons.APP_PREFIX.length()));
            if (launch != null) {
                start(launch);
            }
            return;
        }
        AudioManager audio = getSystemService(AudioManager.class);
        switch (action) {
            case "home":
                performGlobalAction(GLOBAL_ACTION_HOME);
                break;
            case "back":
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            case "notifications":
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case "quick_settings":
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            case "sleep":
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                break;
            case "up":
                inject(KeyEvent.KEYCODE_DPAD_UP);
                break;
            case "down":
                inject(KeyEvent.KEYCODE_DPAD_DOWN);
                break;
            case "left":
                inject(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case "right":
                inject(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            case "ok":
                inject(KeyEvent.KEYCODE_DPAD_CENTER);
                break;
            case "play_pause":
                media(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case "next":
                media(audio, KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case "previous":
                media(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case "volume_up":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI);
                break;
            case "volume_down":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI);
                break;
            case "mute":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "alarms":
                start(new Intent(this, AlarmsActivity.class));
                break;
            case "raven_settings":
                start(new Intent(this, SettingsActivity.class));
                break;
            case "assistant":
                if (!start(new Intent(Intent.ACTION_VOICE_COMMAND))) {
                    start(new Intent(Intent.ACTION_ASSIST));
                }
                break;
            case "none":
            default:
                break;
        }
    }

    private boolean start(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Remote button: nothing handles " + intent);
            return false;
        }
    }

    private static void media(AudioManager audio, int keyCode) {
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    /** Presses a key as if from a keyboard; it isn't from the remote, so it passes through. */
    private static void inject(int keyCode) {
        long now = SystemClock.uptimeMillis();
        InputManager input = InputManager.getInstance();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent key = new KeyEvent(now, now, action, keyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD);
            input.injectInputEvent(key, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
