package org.ravenos.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/** Full-screen, full-brightness ringing screen with big Snooze/+1 min and Stop buttons. */
public class RingActivity extends Activity {
    private int mKind;
    private int mId;

    private final BroadcastReceiver mEnded = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Full brightness, even during night dimming.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);
        setContentView(R.layout.activity_ring);
        GlowBackdrop.attach(this);
        registerReceiver(mEnded, new IntentFilter(RingService.ACTION_ENDED));
        bind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        mKind = intent.getIntExtra(RingService.EXTRA_KIND, RingService.KIND_ALARM);
        mId = intent.getIntExtra(RingService.EXTRA_ID, 0);
        boolean timer = mKind == RingService.KIND_TIMER;
        String label = intent.getStringExtra(RingService.EXTRA_LABEL);
        ((ImageView) findViewById(R.id.ring_icon))
                .setImageResource(timer ? R.drawable.ic_hourglass : R.drawable.ic_alarm);
        ((TextView) findViewById(R.id.ring_label)).setText(timer
                ? getString(R.string.times_up) + "  ·  " + label : label);
        TextView secondary = findViewById(R.id.ring_secondary);
        secondary.setText(timer ? R.string.add_minute : R.string.snooze);
        secondary.setOnClickListener(v -> send(timer
                ? RingService.ACTION_ADD_MINUTE : RingService.ACTION_SNOOZE));
        findViewById(R.id.ring_stop).setOnClickListener(v -> send(RingService.ACTION_STOP));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void send(String action) {
        startService(RingService.command(this, action, mKind, mId));
        finish();
    }

    @Override
    public void onBackPressed() {
        // Stop or snooze it on purpose.
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mEnded);
        super.onDestroy();
    }
}
