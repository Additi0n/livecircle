package com.codex.livecircle;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

final class UploaderBridge {
    private static final Target[] TARGETS = new Target[] {
            new Target("com.codex.livecircle.uploader")
    };
    static final String EXTRA_GROUP_CODE = "groupCode";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_MEMBER_ID = "memberId";

    private UploaderBridge() {
    }

    static boolean startUploaderQuiet(Context context, String groupCode, String name, String memberId) {
        for (Target target : TARGETS) {
            sendStartBroadcast(context, target, groupCode, name, memberId);
            if (startService(context, target, groupCode, name, memberId)) {
                return true;
            }
        }
        return false;
    }

    static void openPermissions(Activity activity, String groupCode, String name, String memberId) {
        startUploaderQuiet(activity, groupCode, name, memberId);
        for (Target target : TARGETS) {
            try {
                Intent intent = new Intent(target.permissionsAction);
                intent.setComponent(new ComponentName(target.packageName, target.permissionsActivity));
                putConfig(intent, groupCode, name, memberId);
                activity.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + TARGETS[0].packageName));
        activity.startActivity(intent);
    }

    private static void putConfig(Intent intent, String groupCode, String name, String memberId) {
        intent.putExtra(EXTRA_GROUP_CODE, groupCode);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_MEMBER_ID, memberId);
    }

    private static void sendStartBroadcast(Context context, Target target, String groupCode, String name, String memberId) {
        try {
            Intent intent = new Intent(target.startAction);
            intent.setComponent(new ComponentName(target.packageName, target.startReceiver));
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            putConfig(intent, groupCode, name, memberId);
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

    private static boolean startService(Context context, Target target, String groupCode, String name, String memberId) {
        try {
            Intent intent = new Intent(target.startAction);
            intent.setComponent(new ComponentName(target.packageName, target.service));
            putConfig(intent, groupCode, name, memberId);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static final class Target {
        final String packageName;
        final String service;
        final String startReceiver;
        final String permissionsActivity;
        final String startAction;
        final String permissionsAction;

        Target(String packageName) {
            this.packageName = packageName;
            this.service = packageName + ".LocationShareService";
            this.startReceiver = packageName + ".StartReceiver";
            this.permissionsActivity = packageName + ".PermissionActivity";
            this.startAction = packageName + ".START_UPLOAD";
            this.permissionsAction = packageName + ".PERMISSIONS";
        }
    }
}
