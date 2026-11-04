package org.ravenos.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

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
                // Ring every timer that is due, not just the one this alarm was set for: two
                // can end moments apart, or the clock may have jumped past several.
                List<Timers.Timer> due = Timers.takeDue(context);
                if (!due.isEmpty()) {
                    List<String> labels = new ArrayList<>();
                    for (Timers.Timer t : due) {
                        if (!TextUtils.isEmpty(t.label)) {
                            labels.add(t.label);
                        }
                    }
                    RingService.start(context, RingService.KIND_TIMER, due.get(0).id,
                            TextUtils.join(", ", labels));
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
