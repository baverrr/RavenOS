package org.ravenos.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Alarm storage and scheduling. Only the next alarm is ever registered with AlarmManager. */
final class Alarms {
    private static final String TAG = "RavenAlarms";
    private static final String PREFS = "alarms";
    private static final String KEY_LIST = "list";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_SNOOZE_ID = "snooze_id";
    private static final String KEY_SNOOZE_AT = "snooze_at";
    static final long SNOOZE_MS = 10 * 60_000L;

    /** Days are a bitmask: Monday is bit 0 ... Sunday is bit 6. 0 means ring once. */
    static final int WEEKDAYS = 0b0011111;
    static final int WEEKENDS = 0b1100000;
    static final int EVERY_DAY = 0b1111111;

    static final class Alarm {
        int id;
        int hour;
        int minute;
        int days;
        String label = "";
        boolean enabled = true;
        /** Ringtone URI, or empty for the default alarm sound. */
        String sound = "";

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id).put("hour", hour).put("minute", minute).put("days", days)
                    .put("label", label).put("enabled", enabled).put("sound", sound);
        }

        static Alarm fromJson(JSONObject o) {
            Alarm a = new Alarm();
            a.id = o.optInt("id");
            a.hour = o.optInt("hour");
            a.minute = o.optInt("minute");
            a.days = o.optInt("days");
            a.label = o.optString("label", "");
            a.enabled = o.optBoolean("enabled", true);
            a.sound = o.optString("sound", "");
            return a;
        }
    }

    private Alarms() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<Alarm> load(Context context) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Bad alarm list", e);
        }
        out.sort((a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        return out;
    }

    private static void saveAll(Context context, List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : alarms) {
                arr.put(a.toJson());
            }
        } catch (JSONException e) {
            Log.w(TAG, "Can't save alarms", e);
            return;
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    static Alarm get(Context context, int id) {
        for (Alarm a : load(context)) {
            if (a.id == id) {
                return a;
            }
        }
        return null;
    }

    /** Adds the alarm (id 0) or replaces the one with the same id, then reschedules. */
    static void put(Context context, Alarm alarm) {
        List<Alarm> alarms = load(context);
        if (alarm.id == 0) {
            int next = prefs(context).getInt(KEY_NEXT_ID, 1);
            alarm.id = next;
            prefs(context).edit().putInt(KEY_NEXT_ID, next + 1).apply();
        } else {
            alarms.removeIf(a -> a.id == alarm.id);
        }
        alarms.add(alarm);
        saveAll(context, alarms);
        clearSnoozeFor(context, alarm.id);
        schedule(context);
    }

    static void delete(Context context, int id) {
        List<Alarm> alarms = load(context);
        alarms.removeIf(a -> a.id == id);
        saveAll(context, alarms);
        clearSnoozeFor(context, id);
        schedule(context);
    }

    static void snooze(Context context, int id) {
        prefs(context).edit()
                .putInt(KEY_SNOOZE_ID, id)
                .putLong(KEY_SNOOZE_AT, System.currentTimeMillis() + SNOOZE_MS)
                .apply();
        schedule(context);
    }

    private static void clearSnoozeFor(Context context, int id) {
        if (prefs(context).getInt(KEY_SNOOZE_ID, 0) == id) {
            prefs(context).edit().remove(KEY_SNOOZE_ID).remove(KEY_SNOOZE_AT).apply();
        }
    }

    /** Called when an alarm rings: one-off alarms turn themselves off. */
    static void onFired(Context context, int id) {
        clearSnoozeFor(context, id);
        List<Alarm> alarms = load(context);
        for (Alarm a : alarms) {
            if (a.id == id && a.days == 0) {
                a.enabled = false;
            }
        }
        saveAll(context, alarms);
        schedule(context);
    }

    static int dayBit(Calendar c) {
        // Calendar: SUNDAY = 1 ... SATURDAY = 7. Ours: Monday = bit 0 ... Sunday = bit 6.
        return 1 << ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7);
    }

    static long nextTrigger(Alarm a, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        if (a.days != 0) {
            for (int i = 0; i < 7 && (a.days & dayBit(c)) == 0; i++) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return c.getTimeInMillis();
    }

    /** The next time anything rings (alarm or snooze), or 0; the id goes in idOut[0]. */
    static long nextRing(Context context, int[] idOut) {
        long now = System.currentTimeMillis();
        long best = 0;
        int bestId = 0;
        for (Alarm a : load(context)) {
            if (!a.enabled) {
                continue;
            }
            long t = nextTrigger(a, now);
            if (best == 0 || t < best) {
                best = t;
                bestId = a.id;
            }
        }
        long snoozeAt = prefs(context).getLong(KEY_SNOOZE_AT, 0);
        if (snoozeAt > now && (best == 0 || snoozeAt < best)) {
            best = snoozeAt;
            bestId = prefs(context).getInt(KEY_SNOOZE_ID, 0);
        }
        if (idOut != null) {
            idOut[0] = bestId;
        }
        return best;
    }

    static void schedule(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        int[] id = new int[1];
        long when = nextRing(context, id);
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_ALARM)
                .putExtra(AlarmReceiver.EXTRA_ID, id[0]);
        PendingIntent operation = PendingIntent.getBroadcast(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (when == 0) {
            am.cancel(operation);
            return;
        }
        PendingIntent show = PendingIntent.getActivity(context, 2,
                new Intent(context, AlarmsActivity.class), PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), operation);
    }

    static String formatTime(Context context, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return DateFormat.getTimeFormat(context).format(c.getTime());
    }

    /** "Today 6:30", "Tomorrow 6:30" or "Wed 6:30". */
    static String describeNext(Context context, long when) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        String time = DateFormat.getTimeFormat(context).format(c.getTime());
        int days = c.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)
                + 365 * (c.get(Calendar.YEAR) - now.get(Calendar.YEAR));
        if (days <= 0) {
            return context.getString(R.string.next_alarm_today, time);
        } else if (days == 1) {
            return context.getString(R.string.next_alarm_tomorrow, time);
        }
        String day = new DateFormatSymbols().getShortWeekdays()[c.get(Calendar.DAY_OF_WEEK)];
        return context.getString(R.string.next_alarm_day, day, time);
    }

    static String describeDays(Context context, int days) {
        if (days == 0) {
            return context.getString(R.string.alarm_once);
        } else if (days == EVERY_DAY) {
            return context.getString(R.string.alarm_every_day);
        } else if (days == WEEKDAYS) {
            return context.getString(R.string.alarm_weekdays);
        } else if (days == WEEKENDS) {
            return context.getString(R.string.alarm_weekends);
        }
        String[] names = shortDayNames();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(names[i]);
            }
        }
        return sb.toString();
    }

    /** Short day names, Monday first, matching the bitmask order. */
    static String[] shortDayNames() {
        String[] weekdays = new DateFormatSymbols().getShortWeekdays();
        return new String[] {
            weekdays[Calendar.MONDAY], weekdays[Calendar.TUESDAY], weekdays[Calendar.WEDNESDAY],
            weekdays[Calendar.THURSDAY], weekdays[Calendar.FRIDAY], weekdays[Calendar.SATURDAY],
            weekdays[Calendar.SUNDAY],
        };
    }
}
