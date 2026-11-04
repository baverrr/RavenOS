package org.ravenos.home;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Open-Meteo client (no API key needed). All calls block; run them off the main thread. */
final class Weather {
    static final class Place {
        final String name;
        final double lat;
        final double lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static final class Report {
        final int temp;
        final int high;
        final int low;
        final String condition;

        Report(int temp, int high, int low, String condition) {
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.condition = condition;
        }
    }

    private Weather() {
    }

    /** Looks up a town or city by name; returns null when nothing matches. */
    static Place geocode(String query) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name="
                + URLEncoder.encode(query.trim(), "UTF-8")
                + "&language=" + Locale.getDefault().getLanguage();
        JSONObject json = new JSONObject(get(url));
        JSONArray results = json.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return null;
        }
        JSONObject r = results.getJSONObject(0);
        StringBuilder name = new StringBuilder(r.getString("name"));
        String region = r.optString("admin1", "");
        String country = r.optString("country", "");
        if (!region.isEmpty() && !region.equals(r.getString("name"))) {
            name.append(", ").append(region);
        } else if (!country.isEmpty()) {
            name.append(", ").append(country);
        }
        return new Place(name.toString(), r.getDouble("latitude"), r.getDouble("longitude"));
    }

    static Report fetch(double lat, double lon, boolean fahrenheit) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current_weather=true&daily=temperature_2m_max,temperature_2m_min"
                        + "&forecast_days=1&timezone=auto&temperature_unit=%s",
                lat, lon, fahrenheit ? "fahrenheit" : "celsius");
        JSONObject json = new JSONObject(get(url));
        JSONObject now = json.getJSONObject("current_weather");
        JSONObject daily = json.getJSONObject("daily");
        return new Report(
                (int) Math.round(now.getDouble("temperature")),
                (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                describe(now.getInt("weathercode")));
    }

    /** WMO weather interpretation codes, as used by Open-Meteo. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: return "Snow";
            case 77: return "Snow grains";
            case 80: case 81: case 82: return "Rain showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "";
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("User-Agent", "RavenOS");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }
}
