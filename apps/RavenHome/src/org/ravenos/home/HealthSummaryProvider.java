package org.ravenos.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Gives Settings › About a live summary under "Device health" (CPU temperature, memory in use,
 * free storage). Settings calls {@code getDynamicSummary} through the tile's
 * com.android.settings.summary_uri meta-data.
 */
public class HealthSummaryProvider extends ContentProvider {
    private static final String METHOD_GET_DYNAMIC_SUMMARY = "getDynamicSummary";
    private static final String KEY_SUMMARY = "com.android.settings.summary";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (METHOD_GET_DYNAMIC_SUMMARY.equals(method)) {
            result.putString(KEY_SUMMARY, new DeviceStats(getContext()).summary());
        }
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
