package com.codex.livecircle;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class SharingStarter {
    private static final int WATCHDOG_REQUEST_CODE = 92;
    private static final long WATCHDOG_INTERVAL_MS = 30000;

    private SharingStarter() {
    }

    static void startIfEnabled(Context context) {
        if (!AppConfig.isSharingEnabled(context)) return;
        scheduleWatchdog(context);
        start(context);
    }

    static void start(Context context) {
        scheduleWatchdog(context);
        try {
            Intent intent = new Intent(context, LocationShareService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    static void scheduleWatchdog(Context context) {
        scheduleWatchdog(context, WATCHDOG_INTERVAL_MS);
    }

    static void scheduleWatchdog(Context context, long delayMs) {
        if (!AppConfig.isSharingEnabled(context)) return;
        Intent intent = new Intent(context, SharingWatchdogReceiver.class);
        intent.setAction(AppConfig.ACTION_WATCHDOG);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                intent,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        long triggerAt = System.currentTimeMillis() + delayMs;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (Exception error) {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }
    }
}
