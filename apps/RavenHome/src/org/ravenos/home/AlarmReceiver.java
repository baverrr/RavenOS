package org.ravenos.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires alarms and timers (private), and restores schedules after boot or a clock change. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_ALARM = "org.ravenos.home.action.ALARM";
    static final String ACTION_TIMER = "org.ravenos.home.action.TIMER";
    static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        int id = intent.getIntExtra(EXTRA_ID, 0);
        switch (action) {
            case ACTION_ALARM: {
                Alarms.Alarm alarm = Alarms.get(context, id);
                Alarms.onFired(context, id);
                if (alarm != null) {
                    RingService.start(context, RingService.KIND_ALARM, id, alarm.label);
                }
                break;
            }
            case ACTION_TIMER: {
                Timers.Timer timer = Timers.get(context, id);
                Timers.schedule(context);
                if (timer != null) {
                    RingService.start(context, RingService.KIND_TIMER, id, timer.label);
                }
                break;
            }
            default:
                // Boot, time or time-zone change, app update.
                if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    Timers.dropExpired(context);
                } else {
                    Timers.schedule(context);
                }
                Alarms.schedule(context);
                break;
        }
    }
}
