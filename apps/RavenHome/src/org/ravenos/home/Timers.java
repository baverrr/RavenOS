package org.ravenos.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Countdown timers. Several can run at once; the soonest that hasn't rung yet is registered with
 * AlarmManager. A running timer counts down on the elapsed-time clock, so changing the wall clock
 * doesn't make it ring early or late; the wall-clock end is kept for display and for after a
 * reboot, when the elapsed clock starts again.
 */
final class Timers {
    private static final String TAG = "RavenTimers";
    private static final String PREFS = "timers";
    private static final String KEY_LIST = "list";
    private static final String KEY_NEXT_ID = "next_id";

    static final class Timer {
        int id;
        String label = "";
        long durationMs;
        /** Wall-clock end time while running; 0 while paused. */
        long endAt;
        /** Time left while paused; -1 while running. */
        long pausedLeft = -1;
        /** SystemClock.elapsedRealtime() end while running, valid only during boot {@link #boot}. */
        long endElapsed;
        /** Settings.Global.BOOT_COUNT when endElapsed was set. */
        int boot = -1;
        /** Whether it has rung since it was last started, resumed or extended. */
        boolean rang;

        boolean isPaused() {
            return pausedLeft >= 0;
        }

        long remaining(long now) {
            return isPaused() ? pausedLeft : Math.max(0, endAt - now);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("label", label)
                    .put("duration", durationMs).put("end", endAt).put("paused", pausedLeft)
                    .put("endElapsed", endElapsed).put("boot", boot).put("rang", rang);
        }

        static Timer fromJson(JSONObject o) {
            Timer t = new Timer();
            t.id = o.optInt("id");
            t.label = o.optString("label", "");
            t.durationMs = o.optLong("duration");
            t.endAt = o.optLong("end");
            t.pausedLeft = o.optLong("paused", -1);
            t.endElapsed = o.optLong("endElapsed");
            t.boot = o.optInt("boot", -1);
            t.rang = o.optBoolean("rang");
            return t;
        }
    }

    private Timers() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int bootCount(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** Whether the timer's elapsed-clock end belongs to this boot. */
    private static boolean hasElapsedEnd(Timer t, int boot) {
        return !t.isPaused() && t.endElapsed > 0 && boot >= 0 && t.boot == boot;
    }

    /** Sets a running timer to end at {@code endAt} (wall clock) and lets it ring again. */
    private static void setEnd(Context context, Timer t, long endAt) {
        t.endAt = endAt;
        t.endElapsed = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis());
        t.boot = bootCount(context);
        t.pausedLeft = -1;
        t.rang = false;
    }

    static List<Timer> load(Context context) {
        List<Timer> out = new ArrayList<>();
        int boot = bootCount(context);
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                Timer t = Timer.fromJson(arr.getJSONObject(i));
                if (hasElapsedEnd(t, boot)) {
                    // Follow the elapsed clock, so a wall-clock change moves the end with it.
                    t.endAt = System.currentTimeMillis()
                            + (t.endElapsed - SystemClock.elapsedRealtime());
                }
                out.add(t);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Bad timer list", e);
        }
        long now = System.currentTimeMillis();
        out.sort((a, b) -> Long.compare(a.remaining(now), b.remaining(now)));
        return out;
    }

    private static void saveAll(Context context, List<Timer> timers) {
        JSONArray arr = new JSONArray();
        try {
            for (Timer t : timers) {
                arr.put(t.toJson());
            }
        } catch (JSONException e) {
            Log.w(TAG, "Can't save timers", e);
            return;
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    static Timer get(Context context, int id) {
        for (Timer t : load(context)) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }

    static void start(Context context, long durationMs, String label) {
        List<Timer> timers = load(context);
        Timer t = new Timer();
        int next = prefs(context).getInt(KEY_NEXT_ID, 1);
        prefs(context).edit().putInt(KEY_NEXT_ID, next + 1).apply();
        t.id = next;
        t.label = label == null ? "" : label.trim();
        t.durationMs = durationMs;
        setEnd(context, t, System.currentTimeMillis() + durationMs);
        timers.add(t);
        saveAll(context, timers);
        schedule(context);
    }

    static void remove(Context context, int id) {
        List<Timer> timers = load(context);
        timers.removeIf(t -> t.id == id);
        saveAll(context, timers);
        schedule(context);
    }

    /** Adds a minute; a finished timer starts again with one minute left. */
    static void addMinute(Context context, int id) {
        long now = System.currentTimeMillis();
        List<Timer> timers = load(context);
        for (Timer t : timers) {
            if (t.id != id) {
                continue;
            }
            if (t.isPaused()) {
                t.pausedLeft += 60_000L;
            } else {
                setEnd(context, t, Math.max(t.endAt, now) + 60_000L);
            }
        }
        saveAll(context, timers);
        schedule(context);
    }

    static void togglePause(Context context, int id) {
        long now = System.currentTimeMillis();
        List<Timer> timers = load(context);
        for (Timer t : timers) {
            if (t.id != id) {
                continue;
            }
            if (t.isPaused()) {
                setEnd(context, t, now + t.pausedLeft);
            } else if (t.endAt > now) {
                t.pausedLeft = t.endAt - now;
                t.endAt = 0;
                t.endElapsed = 0;
            }
        }
        saveAll(context, timers);
        schedule(context);
    }

    /** Drops timers that ended while the device was off (their moment has passed). */
    static void dropExpired(Context context) {
        long now = System.currentTimeMillis();
        List<Timer> timers = load(context);
        timers.removeIf(t -> !t.isPaused() && t.endAt <= now);
        saveAll(context, timers);
        schedule(context);
    }

    /**
     * Marks every running timer that has reached its end and hasn't rung as rung, registers the
     * next one, and returns those that are due, soonest first. Several can be due at once, such
     * as two timers ending moments apart or after the clock jumped forward.
     */
    static List<Timer> takeDue(Context context) {
        // A little slack, since the alarm may be delivered a moment before the end.
        long now = System.currentTimeMillis() + 1_000L;
        List<Timer> timers = load(context);
        List<Timer> due = new ArrayList<>();
        for (Timer t : timers) {
            if (!t.isPaused() && !t.rang && t.endAt <= now) {
                t.rang = true;
                due.add(t);
            }
        }
        if (!due.isEmpty()) {
            saveAll(context, timers);
        }
        schedule(context);
        return due;
    }

    /** Removes the timers that have rung and not been extended since. */
    static void removeRung(Context context) {
        List<Timer> timers = load(context);
        if (timers.removeIf(t -> !t.isPaused() && t.rang)) {
            saveAll(context, timers);
        }
        schedule(context);
    }

    static void schedule(Context context) {
        Timer soonest = null;
        for (Timer t : load(context)) {
            // A timer whose end already passed without ringing is registered too: it rings now.
            if (!t.isPaused() && !t.rang && (soonest == null || t.endAt < soonest.endAt)) {
                soonest = t;
            }
        }
        AlarmManager am = context.getSystemService(AlarmManager.class);
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_TIMER)
                .putExtra(AlarmReceiver.EXTRA_ID, soonest == null ? 0 : soonest.id);
        PendingIntent operation = PendingIntent.getBroadcast(context, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (soonest == null) {
            am.cancel(operation);
        } else {
            if (hasElapsedEnd(soonest, bootCount(context))) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        soonest.endElapsed, operation);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, soonest.endAt, operation);
            }
        }
    }

    /** 1:05:09, 4:12 or 0:07. */
    static String format(long ms) {
        long total = (ms + 999) / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
                : String.format(Locale.getDefault(), "%d:%02d", m, s);
    }
}
