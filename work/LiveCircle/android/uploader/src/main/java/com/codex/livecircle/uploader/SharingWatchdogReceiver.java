package com.codex.livecircle.uploader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SharingWatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && AppConfig.ACTION_WATCHDOG.equals(intent.getAction())) {
            SharingStarter.scheduleWatchdog(context);
            SharingStarter.startIfEnabled(context);
        }
    }
}
