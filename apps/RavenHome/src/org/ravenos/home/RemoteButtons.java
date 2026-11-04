package org.ravenos.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * What each button on the Portal TV remote does. Every button starts on its default; Settings ›
 * Connected devices › Remote buttons can give it another action, which {@link RemoteKeyService}
 * carries out. The service only runs while at least one button has been changed.
 */
final class RemoteButtons {
    /** The Portal TV remote's Bluetooth vendor and product IDs. */
    private static final int VENDOR_ID = 0x1915;
    private static final int PRODUCT_ID = 0xeeee;

    private static final String PREF_PREFIX = "remote_button_";

    static final String DEFAULT = "default";
    static final String APP_PREFIX = "app:";

    /** The buttons that can be changed, in the order the settings page lists them. */
    static final int[] KEYS = {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_BLUE};
    static final int[] KEY_LABELS = {
            R.string.remote_key_up, R.string.remote_key_down, R.string.remote_key_left,
            R.string.remote_key_right, R.string.remote_key_ok, R.string.remote_key_back,
            R.string.remote_key_home, R.string.remote_key_play, R.string.remote_key_volume_up,
            R.string.remote_key_volume_down, R.string.remote_key_mic, R.string.remote_key_red,
            R.string.remote_key_green, R.string.remote_key_blue};
    /** What each button does by default, shown next to "Default". */
    static final int[] KEY_DEFAULTS = {
            R.string.remote_action_up, R.string.remote_action_down, R.string.remote_action_left,
            R.string.remote_action_right, R.string.remote_action_ok, R.string.remote_action_back,
            R.string.remote_action_home, R.string.remote_action_play_pause,
            R.string.remote_action_volume_up, R.string.remote_action_volume_down,
            R.string.remote_action_mute, R.string.remote_action_none,
            R.string.remote_action_none, R.string.remote_action_none};

    /** The actions a button can be given, apart from Default and "Open an app". */
    static final String[] ACTIONS = {
            "none", "home", "back", "up", "down", "left", "right", "ok", "play_pause", "next",
            "previous", "volume_up", "volume_down", "mute", "notifications", "quick_settings",
            "sleep", "alarms", "assistant", "raven_settings"};
    static final int[] ACTION_LABELS = {
            R.string.remote_action_none, R.string.remote_action_home,
            R.string.remote_action_back, R.string.remote_action_up,
            R.string.remote_action_down, R.string.remote_action_left,
            R.string.remote_action_right, R.string.remote_action_ok,
            R.string.remote_action_play_pause, R.string.remote_action_next,
            R.string.remote_action_previous, R.string.remote_action_volume_up,
            R.string.remote_action_volume_down, R.string.remote_action_mute,
            R.string.remote_action_notifications, R.string.remote_action_quick_settings,
            R.string.remote_action_sleep, R.string.remote_action_alarms,
            R.string.remote_action_assistant, R.string.remote_action_raven_settings};

    private RemoteButtons() {
    }

    static boolean isRemote(KeyEvent event) {
        InputDevice device = event.getDevice();
        return device != null && device.getVendorId() == VENDOR_ID
                && device.getProductId() == PRODUCT_ID;
    }

    static boolean isProgrammable(int keyCode) {
        for (int key : KEYS) {
            if (key == keyCode) {
                return true;
            }
        }
        return false;
    }

    static String action(SharedPreferences prefs, int keyCode) {
        return prefs.getString(PREF_PREFIX + keyCode, DEFAULT);
    }

    static void setAction(Context context, int keyCode, String action) {
        SharedPreferences prefs = new Prefs(context).raw();
        SharedPreferences.Editor edit = prefs.edit();
        if (DEFAULT.equals(action)) {
            edit.remove(PREF_PREFIX + keyCode);
        } else {
            edit.putString(PREF_PREFIX + keyCode, action);
        }
        edit.commit();
        syncService(context);
    }

    static void resetAll(Context context) {
        SharedPreferences prefs = new Prefs(context).raw();
        SharedPreferences.Editor edit = prefs.edit();
        for (int key : KEYS) {
            edit.remove(PREF_PREFIX + key);
        }
        edit.commit();
        syncService(context);
    }

    static boolean anyChanged(SharedPreferences prefs) {
        for (int key : KEYS) {
            if (!DEFAULT.equals(action(prefs, key))) {
                return true;
            }
        }
        return false;
    }

    /** Label for an action id, for the settings page. */
    static String label(Context context, String action) {
        if (action.startsWith(APP_PREFIX)) {
            String pkg = action.substring(APP_PREFIX.length());
            try {
                CharSequence name = context.getPackageManager().getApplicationLabel(
                        context.getPackageManager().getApplicationInfo(pkg, 0));
                return context.getString(R.string.remote_action_open_app_named, name);
            } catch (Exception e) {
                return context.getString(R.string.remote_action_open_app_named, pkg);
            }
        }
        for (int i = 0; i < ACTIONS.length; i++) {
            if (ACTIONS[i].equals(action)) {
                return context.getString(ACTION_LABELS[i]);
            }
        }
        return context.getString(R.string.remote_action_default);
    }

    /**
     * Turns the key service on while any button is changed and off otherwise, so the remote
     * goes through no extra step when it works as it came.
     */
    static void syncService(Context context) {
        boolean want = anyChanged(new Prefs(context).raw());
        String me = new ComponentName(context, RemoteKeyService.class).flattenToString();
        android.content.ContentResolver resolver = context.getContentResolver();
        String enabled = Settings.Secure.getString(resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        StringBuilder out = new StringBuilder();
        boolean has = false;
        if (enabled != null) {
            for (String service : enabled.split(":")) {
                if (service.isEmpty()) {
                    continue;
                }
                if (service.equalsIgnoreCase(me)) {
                    has = true;
                    if (!want) {
                        continue;
                    }
                }
                if (out.length() > 0) {
                    out.append(':');
                }
                out.append(service);
            }
        }
        if (want == has) {
            return;
        }
        if (want) {
            if (out.length() > 0) {
                out.append(':');
            }
            out.append(me);
        }
        try {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    out.toString());
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED,
                    out.length() > 0 ? 1 : 0);
        } catch (SecurityException e) {
            android.util.Log.w("RavenHome", "Can't turn the remote button service on or off", e);
        }
    }
}
