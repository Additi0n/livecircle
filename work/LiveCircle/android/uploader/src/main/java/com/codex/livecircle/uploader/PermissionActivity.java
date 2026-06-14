package com.codex.livecircle.uploader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class PermissionActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 7;
    private boolean requestedRuntimePermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppConfig.configureFromIntent(this, getIntent());
        showStatus();
        requestNeededPermissions();
        requestBatteryOptimizationExemption();
        SharingStarter.start(this);
        finishSoon();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        requestedRuntimePermissions = false;
        SharingStarter.start(this);
        finishSoon();
    }

    private void showStatus() {
        TextView status = new TextView(this);
        status.setText("LiveCircle Uploader permissions");
        status.setTextSize(18);
        status.setTextColor(Color.rgb(30, 55, 50));
        status.setGravity(Gravity.CENTER);
        setContentView(status);
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        boolean hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!hasCoarse) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (permissions.isEmpty()
                && Build.VERSION.SDK_INT == 29
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (!permissions.isEmpty()) {
            requestedRuntimePermissions = true;
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void finishSoon() {
        if (requestedRuntimePermissions) return;
        new Handler().postDelayed(this::finish, 900);
    }
}
