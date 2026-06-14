package com.codex.livecircle.uploader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.UUID;

final class AppConfig {
    static final String DEFAULT_SERVER_URL = "http://YOUR_RELAY_HOST:8787";
    static final String DEFAULT_GROUP_CODE = "CHANGE_ME";
    static final String VIEWER_PACKAGE = "com.codex.livecircle";
    static final String PREFS = "livecircle_uploader";
    static final String KEY_GROUP_CODE = "group_code";
    static final String KEY_NAME = "name";
    static final String KEY_MEMBER_ID = "member_id";
    static final String KEY_SHARING_ENABLED = "sharing_enabled";
    static final String ACTION_START_UPLOAD = "com.codex.livecircle.uploader.START_UPLOAD";
    static final String ACTION_PERMISSIONS = "com.codex.livecircle.uploader.PERMISSIONS";
    static final String ACTION_RESTART_SHARING = "com.codex.livecircle.uploader.RESTART_SHARING";
    static final String ACTION_WATCHDOG = "com.codex.livecircle.uploader.WATCHDOG";
    static final String ACTION_MEMBERS = "com.codex.livecircle.MEMBERS";
    static final String ACTION_STATUS = "com.codex.livecircle.STATUS";
    static final String EXTRA_GROUP_CODE = "groupCode";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_MEMBER_ID = "memberId";

    private AppConfig() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void configureFromIntent(Context context, Intent intent) {
        if (intent == null) return;
        SharedPreferences.Editor editor = prefs(context).edit();
        String groupCode = clean(intent.getStringExtra(EXTRA_GROUP_CODE));
        String name = clean(intent.getStringExtra(EXTRA_NAME));
        String memberId = clean(intent.getStringExtra(EXTRA_MEMBER_ID));
        if (groupCode != null) editor.putString(KEY_GROUP_CODE, groupCode);
        if (name != null) editor.putString(KEY_NAME, name);
        if (memberId != null) editor.putString(KEY_MEMBER_ID, memberId);
        editor.apply();
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
        return prefs(context).getBoolean(KEY_SHARING_ENABLED, true);
    }

    static void setSharingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHARING_ENABLED, true).apply();
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
