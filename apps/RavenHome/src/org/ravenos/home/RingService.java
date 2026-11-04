package org.ravenos.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

/** Plays an alarm or timer sound in the foreground and shows the ringing screen. */
public class RingService extends Service {
    private static final String TAG = "RavenRing";

    static final int KIND_ALARM = 0;
    static final int KIND_TIMER = 1;
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_ID = "id";
    static final String EXTRA_LABEL = "label";

    static final String ACTION_START = "org.ravenos.home.ring.START";
    static final String ACTION_STOP = "org.ravenos.home.ring.STOP";
    static final String ACTION_SNOOZE = "org.ravenos.home.ring.SNOOZE";
    static final String ACTION_ADD_MINUTE = "org.ravenos.home.ring.ADD_MINUTE";
    /** Sent (to this package only) when ringing ends, so the ringing screen closes. */
    static final String ACTION_ENDED = "org.ravenos.home.ring.ENDED";

    private static final String CHANNEL = "ring";
    private static final int NOTIFICATION_ID = 1;
    private static final long AUTO_STOP_MS = 10 * 60_000L;
    private static final long RAMP_STEP_MS = 1_000L;
    private static final float RAMP_START = 0.1f;
    private static final float RAMP_STEP = 0.03f;
    private static final String TIMER_SOUND = "/product/media/audio/alarms/Timer.ogg";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mPlayer;
    private PowerManager.WakeLock mWakeLock;
    private float mVolume;

    private final Runnable mRamp = new Runnable() {
        @Override
        public void run() {
            if (mPlayer == null || mVolume >= 1f) {
                return;
            }
            mVolume = Math.min(1f, mVolume + RAMP_STEP);
            mPlayer.setVolume(mVolume, mVolume);
            mHandler.postDelayed(this, RAMP_STEP_MS);
        }
    };

    private final Runnable mAutoStop = this::finishRinging;

    static void start(Context context, int kind, int id, String label) {
        context.startForegroundService(new Intent(context, RingService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_LABEL, label == null ? "" : label));
    }

    static Intent command(Context context, String action, int kind, int id) {
        return new Intent(context, RingService.class).setAction(action)
                .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int kind = intent.getIntExtra(EXTRA_KIND, KIND_ALARM);
        int id = intent.getIntExtra(EXTRA_ID, 0);
        switch (action) {
            case ACTION_START:
                startRinging(kind, id, intent.getStringExtra(EXTRA_LABEL));
                break;
            case ACTION_SNOOZE:
                Alarms.snooze(this, id);
                finishRinging();
                break;
            case ACTION_ADD_MINUTE:
                Timers.addMinute(this, id);
                finishRinging();
                break;
            case ACTION_STOP:
            default:
                if (kind == KIND_TIMER) {
                    Timers.remove(this, id);
                }
                finishRinging();
                break;
        }
        return START_NOT_STICKY;
    }

    private void startRinging(int kind, int id, String label) {
        // A new ring replaces one already going.
        stopSound();
        String title = !TextUtils.isEmpty(label) ? label : getString(kind == KIND_TIMER
                ? R.string.timer_default_label : R.string.alarm_default_label);

        Intent screen = new Intent(this, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_LABEL, title);
        startForeground(NOTIFICATION_ID, buildNotification(kind, id, title, screen));

        PowerManager pm = getSystemService(PowerManager.class);
        if (mWakeLock == null) {
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RavenHome:ring");
        }
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire(AUTO_STOP_MS + 60_000L);
        }
        playSound(kind, id);
        startActivity(screen);
        mHandler.removeCallbacks(mAutoStop);
        mHandler.postDelayed(mAutoStop, AUTO_STOP_MS);
    }

    private Notification buildNotification(int kind, int id, String title, Intent screen) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                getString(R.string.ring_channel), NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
        PendingIntent full = PendingIntent.getActivity(this, 0, screen,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                command(this, ACTION_STOP, kind, id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(kind == KIND_TIMER ? R.drawable.ic_hourglass : R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(getString(kind == KIND_TIMER
                        ? R.string.ring_notification_timer : R.string.ring_notification_alarm))
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(new Notification.Action.Builder(null, getString(R.string.stop), stop)
                        .build())
                .build();
    }

    private void playSound(int kind, int id) {
        AudioManager audio = getSystemService(AudioManager.class);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            // A muted alarm is worse than a loud one.
            audio.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, max * 7 / 10), 0);
        }
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (kind == KIND_TIMER && new File(TIMER_SOUND).canRead()) {
                player.setDataSource(TIMER_SOUND);
            } else {
                player.setDataSource(this, alarmSound(id));
            }
            player.setLooping(true);
            player.prepare();
        } catch (Exception e) {
            Log.w(TAG, "Can't play the alarm sound", e);
            player.release();
            return;
        }
        mPlayer = player;
        // Alarms start quietly and rise over ~30 s; timers play at full volume.
        mVolume = kind == KIND_ALARM ? RAMP_START : 1f;
        player.setVolume(mVolume, mVolume);
        player.start();
        if (kind == KIND_ALARM) {
            mHandler.postDelayed(mRamp, RAMP_STEP_MS);
        }
    }

    private Uri alarmSound(int id) {
        Alarms.Alarm alarm = Alarms.get(this, id);
        if (alarm != null && !TextUtils.isEmpty(alarm.sound)) {
            return Uri.parse(alarm.sound);
        }
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri != null) {
            return uri;
        }
        // No default set: use the first alarm sound on the device.
        RingtoneManager rm = new RingtoneManager(this);
        rm.setType(RingtoneManager.TYPE_ALARM);
        Cursor c = rm.getCursor();
        if (c != null && c.moveToFirst()) {
            return rm.getRingtoneUri(0);
        }
        return Uri.fromFile(new File("/product/media/audio/alarms/Hassium.ogg"));
    }

    private void stopSound() {
        mHandler.removeCallbacks(mRamp);
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void finishRinging() {
        mHandler.removeCallbacks(mAutoStop);
        stopSound();
        sendBroadcast(new Intent(ACTION_ENDED).setPackage(getPackageName()));
        stopForeground(true);
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopSound();
        mHandler.removeCallbacksAndMessages(null);
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        super.onDestroy();
    }
}
