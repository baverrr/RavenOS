package org.ravenos.home;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A grid of every launchable app, each icon redrawn in the RavenOS look by {@link ThemedIcons}. */
public class AppsActivity extends Activity {
    private final List<ResolveInfo> mApps = new ArrayList<>();
    private final AppAdapter mAdapter = new AppAdapter();
    private PackageManager mPm;
    private ThemedIcons mIcons;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPm = getPackageManager();
        mIcons = new ThemedIcons(this, dp(72));

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(150));
        grid.setVerticalSpacing(dp(16));
        grid.setHorizontalSpacing(dp(16));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(dp(48), dp(8), dp(48), dp(48));
        grid.setClipToPadding(false);
        // Each tile has its own glass ripple; no grey list highlight on top. The selector is
        // only the focus ring the remote's D-pad moves from tile to tile, drawn over the tile.
        grid.setSelector(R.drawable.focus_ring_card);
        grid.setDrawSelectorOnTop(true);
        grid.setAdapter(mAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            ResolveInfo ri = mApps.get(position);
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(ri.activityInfo.packageName,
                            ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            finish();
        });
        TextView title = new TextView(this);
        title.setTextAppearance(R.style.Raven_Title);
        title.setText(R.string.all_apps);
        title.setPadding(dp(48), dp(40), dp(48), dp(16));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(title);
        page.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        GlowBackdrop.attach(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mApps.clear();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : mPm.queryIntentActivities(query, 0)) {
            if (!getPackageName().equals(ri.activityInfo.packageName)
                    || ri.activityInfo.name.endsWith(".SettingsActivity")) {
                mApps.add(ri);
            }
        }
        Collections.sort(mApps, new ResolveInfo.DisplayNameComparator(mPm));
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mApps.size();
        }

        @Override
        public Object getItem(int position) {
            return mApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) convertView;
            if (cell == null) {
                cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setBackgroundResource(R.drawable.app_tile_bg);
                cell.setPadding(dp(12), dp(20), dp(12), dp(16));
                ImageView icon = new ImageView(AppsActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
                TextView label = new TextView(AppsActivity.this);
                label.setGravity(Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setTextSize(16);
                label.setTextColor(getColor(R.color.raven_text));
                label.setPadding(0, dp(8), 0, 0);
                cell.addView(label);
            }
            ResolveInfo ri = mApps.get(position);
            ImageView icon = (ImageView) cell.getChildAt(0);
            icon.setImageDrawable(null);
            icon.setTag(ri);
            mExecutor.execute(() -> {
                Bitmap themed = mIcons.get(ri.activityInfo);
                icon.post(() -> {
                    if (icon.getTag() == ri) {
                        icon.setImageBitmap(themed);
                    }
                });
            });
            ((TextView) cell.getChildAt(1)).setText(ri.loadLabel(mPm));
            return cell;
        }
    }
}
