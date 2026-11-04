package org.ravenos.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Countdown timers. Several can run at once; the soonest is registered with AlarmManager. */
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

        boolean isPaused() {
            return pausedLeft >= 0;
        }

        long remaining(long now) {
            return isPaused() ? pausedLeft : Math.max(0, endAt - now);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("label", label)
                    .put("duration", durationMs).put("end", endAt).put("paused", pausedLeft);
        }

        static Timer fromJson(JSONObject o) {
            Timer t = new Timer();
            t.id = o.optInt("id");
            t.label = o.optString("label", "");
            t.durationMs = o.optLong("duration");
            t.endAt = o.optLong("end");
            t.pausedLeft = o.optLong("paused", -1);
            return t;
        }
    }

    private Timers() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<Timer> load(Context context) {
        List<Timer> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Timer.fromJson(arr.getJSONObject(i)));
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
        t.endAt = System.currentTimeMillis() + durationMs;
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
                t.endAt = Math.max(t.endAt, now) + 60_000L;
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
                t.endAt = now + t.pausedLeft;
                t.pausedLeft = -1;
            } else if (t.endAt > now) {
                t.pausedLeft = t.endAt - now;
                t.endAt = 0;
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

    static void schedule(Context context) {
        long now = System.currentTimeMillis();
        Timer soonest = null;
        for (Timer t : load(context)) {
            if (!t.isPaused() && t.endAt > now && (soonest == null || t.endAt < soonest.endAt)) {
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
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, soonest.endAt, operation);
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
