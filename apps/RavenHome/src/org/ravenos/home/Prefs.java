package org.ravenos.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Calendar;

/** Typed access to the settings the user picks in RavenOS settings. */
final class Prefs {
    static final String WEATHER_QUERY = "weather_query";
    static final String WEATHER_NAME = "weather_name";
    static final String WEATHER_LAT = "weather_lat";
    static final String WEATHER_LON = "weather_lon";
    static final String TEMP_UNIT = "temp_unit";
    static final String BACKGROUND = "background";
    static final String REVEAL_SECONDS = "reveal_seconds";
    static final String PHOTO_INTERVAL_SECONDS = "photo_interval_seconds";
    static final String NIGHT_ENABLED = "night_enabled";
    static final String NIGHT_START = "night_start";
    static final String NIGHT_END = "night_end";
    static final String NIGHT_LEVEL = "night_level";

    private final SharedPreferences mPrefs;

    Prefs(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.raven_prefs, false);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    SharedPreferences raw() {
        return mPrefs;
    }

    boolean hasWeatherPlace() {
        return mPrefs.contains(WEATHER_LAT) && mPrefs.contains(WEATHER_LON);
    }

    double lat() {
        return Double.longBitsToDouble(mPrefs.getLong(WEATHER_LAT, 0));
    }

    double lon() {
        return Double.longBitsToDouble(mPrefs.getLong(WEATHER_LON, 0));
    }

    String placeName() {
        return mPrefs.getString(WEATHER_NAME, "");
    }

    void setPlace(String name, double lat, double lon) {
        mPrefs.edit()
                .putString(WEATHER_NAME, name)
                .putLong(WEATHER_LAT, Double.doubleToRawLongBits(lat))
                .putLong(WEATHER_LON, Double.doubleToRawLongBits(lon))
                .apply();
    }

    boolean fahrenheit() {
        return "fahrenheit".equals(mPrefs.getString(TEMP_UNIT, "celsius"));
    }

    /** One of "auto", "video", "photos" or "none". */
    String background() {
        return mPrefs.getString(BACKGROUND, "auto");
    }

    int revealSeconds() {
        return Math.max(5, intPref(REVEAL_SECONDS, 15));
    }

    int photoIntervalSeconds() {
        return Math.max(5, intPref(PHOTO_INTERVAL_SECONDS, 15));
    }

    boolean isNightNow() {
        if (!mPrefs.getBoolean(NIGHT_ENABLED, true)) {
            return false;
        }
        int start = intPref(NIGHT_START, 22);
        int end = intPref(NIGHT_END, 7);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (start == end) {
            return false;
        }
        // The night window can wrap past midnight (22 -> 7) or not (1 -> 6).
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }

    /** Night brightness as a window brightness value (0..1). */
    float nightBrightness() {
        return Math.max(1, intPref(NIGHT_LEVEL, 5)) / 100f;
    }

    private int intPref(String key, int def) {
        try {
            return Integer.parseInt(mPrefs.getString(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
