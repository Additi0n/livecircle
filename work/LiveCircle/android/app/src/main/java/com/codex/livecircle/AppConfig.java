package com.codex.livecircle;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

final class AppConfig {
    static final String DEFAULT_SERVER_URL = "http://YOUR_RELAY_HOST:8787";
    static final String DEFAULT_GROUP_CODE = "CHANGE_ME";
    static final String PREFS = "livecircle";
    static final String KEY_SERVER_URL = "server_url";
    static final String KEY_GROUP_CODE = "group_code";
    static final String KEY_NAME = "name";
    static final String KEY_MEMBER_ID = "member_id";
    static final String KEY_SHARING_ENABLED = "sharing_enabled";
    static final String KEY_RECENTS_VISIBLE = "recents_visible";
    static final String ACTION_RESTART_SHARING = "com.codex.livecircle.RESTART_SHARING";
    static final String ACTION_WATCHDOG = "com.codex.livecircle.WATCHDOG";
    static final String ACTION_MEMBERS = "com.codex.livecircle.MEMBERS";
    static final String ACTION_STATUS = "com.codex.livecircle.STATUS";

    private AppConfig() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String memberId(Context context) {
        SharedPreferences prefs = prefs(context);
        String id = prefs.getString(KEY_MEMBER_ID, null);
        if (id == null || id.length() < 8) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_MEMBER_ID, id).apply();
        }
        return id;
    }

    static String serverUrl(Context context) {
        return DEFAULT_SERVER_URL;
    }

    static String groupCode(Context context) {
        return prefs(context).getString(KEY_GROUP_CODE, DEFAULT_GROUP_CODE);
    }

    static String displayName(Context context) {
        return prefs(context).getString(KEY_NAME, "Me");
    }

    static boolean isSharingEnabled(Context context) {
        return true;
    }

    static void setSharingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHARING_ENABLED, true).apply();
    }

    static boolean isRecentsVisible(Context context) {
        return prefs(context).getBoolean(KEY_RECENTS_VISIBLE, false);
    }

    static void setRecentsVisible(Context context, boolean visible) {
        prefs(context).edit().putBoolean(KEY_RECENTS_VISIBLE, visible).apply();
    }
}
