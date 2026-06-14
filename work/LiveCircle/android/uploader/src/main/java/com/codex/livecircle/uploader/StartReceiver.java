package com.codex.livecircle.uploader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && AppConfig.ACTION_START_UPLOAD.equals(intent.getAction())) {
            AppConfig.configureFromIntent(context, intent);
            SharingStarter.start(context);
        }
    }
}
