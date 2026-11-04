package org.ravenos.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** RavenOS settings: weather place, photo frame and night dimming. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GlowBackdrop.attach(this);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new RavenSettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    public static class RavenSettingsFragment extends PreferenceFragment {
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private Prefs mPrefs;
        private Preference mPlace;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mPrefs = new Prefs(getActivity());
            addPreferencesFromResource(R.xml.raven_prefs);
            mPlace = findPreference(Prefs.WEATHER_QUERY);
            updatePlaceSummary();
            mPlace.setOnPreferenceChangeListener((pref, value) -> {
                String query = String.valueOf(value).trim();
                if (!TextUtils.isEmpty(query)) {
                    lookUp(query);
                }
                return true;
            });
            findPreference("font_license").setOnPreferenceClickListener(pref -> {
                showFontLicense();
                return true;
            });
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.settings_list, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // The fragment applies the theme's list divider; the glass cards draw their own.
            ((GlassListView) view.findViewById(android.R.id.list)).setDivider(null);
        }

        private void showFontLicense() {
            String text;
            try (InputStream in = getResources().openRawResource(R.raw.ibm_plex_ofl)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                text = getString(R.string.pref_font_license_summary);
            }
            TextView body = new TextView(getActivity());
            body.setText(text);
            body.setTextIsSelectable(true);
            int pad = (int) (24 * getResources().getDisplayMetrics().density);
            body.setPadding(pad, pad / 2, pad, pad / 2);
            ScrollView scroll = new ScrollView(getActivity());
            scroll.addView(body);
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_font_license)
                    .setView(scroll)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        private void updatePlaceSummary() {
            String name = mPrefs.placeName();
            mPlace.setSummary(TextUtils.isEmpty(name)
                    ? getString(R.string.pref_weather_place_none) : name);
        }

        private void lookUp(String query) {
            mPlace.setSummary(R.string.pref_weather_place_searching);
            new Thread(() -> {
                Weather.Place place = null;
                try {
                    place = Weather.geocode(query);
                } catch (Exception e) {
                    Log.w("RavenHome", "Place lookup failed", e);
                }
                final Weather.Place found = place;
                mHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (found == null) {
                        mPlace.setSummary(getString(R.string.pref_weather_place_notfound, query));
                        return;
                    }
                    mPrefs.setPlace(found.name, found.lat, found.lon);
                    updatePlaceSummary();
                });
            }, "RavenPlaceLookup").start();
        }
    }
}
