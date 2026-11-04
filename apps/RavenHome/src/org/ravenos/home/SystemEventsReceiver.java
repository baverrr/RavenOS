package org.ravenos.home;

/**
 * Same handling as {@link AlarmReceiver} for system broadcasts, but exported; the alarm and
 * timer actions themselves only ever reach the private receiver.
 */
public class SystemEventsReceiver extends AlarmReceiver {
    @Override
    public void onReceive(android.content.Context context, android.content.Intent intent) {
        String action = intent.getAction();
        if (ACTION_ALARM.equals(action) || ACTION_TIMER.equals(action)) {
            return;
        }
        super.onReceive(context, intent);
    }
}
