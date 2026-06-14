package com.codex.livecircle.uploader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RestartSharingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && AppConfig.ACTION_RESTART_SHARING.equals(intent.getAction())) {
            SharingStarter.scheduleWatchdog(context);
            SharingStarter.startIfEnabled(context);
        }
    }
}
