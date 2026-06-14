package com.codex.livecircle.uploader;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.List;

public class LocationShareService extends Service implements LocationListener {
    private static final String CHANNEL_ID = "livecircle_location";
    private static final int NOTIFICATION_ID = 41;
    private static final long NETWORK_LOCATION_INTERVAL_MS = 20000;
    private static final float NETWORK_LOCATION_DISTANCE_M = 10f;
    private static final long GPS_LOCATION_INTERVAL_MS = 120000;
    private static final float GPS_LOCATION_DISTANCE_M = 50f;
    private static final long PASSIVE_LOCATION_INTERVAL_MS = 20000;
    private static final long UPLOAD_INTERVAL_MS = 20000;
    private static final long FRESH_LOCATION_MAX_AGE_MS = 60000;
    private static final long GPS_NUDGE_MIN_INTERVAL_MS = 60000;
    private static final float GOOD_ACCURACY_M = 50f;
    private static final float POOR_ACCURACY_M = 100f;

    private HandlerThread workerThread;
    private Handler worker;
    private LocationManager locationManager;
    private Location lastLocation;
    private long lastGpsNudgeAt;
    private boolean running;

    private final Runnable uploadTask = new Runnable() {
        @Override
        public void run() {
            uploadCurrentLocation();
            if (running && worker != null) {
                worker.postDelayed(this, UPLOAD_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        workerThread = new HandlerThread("livecircle-uploader");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppConfig.configureFromIntent(this, intent);
        AppConfig.setSharingEnabled(this, true);
        SharingStarter.scheduleWatchdog(this);
        running = true;
        startForeground(NOTIFICATION_ID, buildNotification("Location sharing is running"));
        startLocationUpdates();
        worker.removeCallbacks(uploadTask);
        worker.post(uploadTask);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (AppConfig.isSharingEnabled(this)) scheduleRestart();
        running = false;
        if (locationManager != null) locationManager.removeUpdates(this);
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = better(lastLocation, location);
        broadcastStatus("Current lat/lon: " + location.getLatitude() + ", " + location.getLongitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        broadcastStatus("Location provider disabled: " + provider);
    }

    private void startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            broadcastStatus("Location permission is missing");
            return;
        }

        try {
            locationManager.removeUpdates(this);
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            lastLocation = better(gps, network);
            requestProviderUpdates(LocationManager.NETWORK_PROVIDER, NETWORK_LOCATION_INTERVAL_MS, NETWORK_LOCATION_DISTANCE_M);
            requestProviderUpdates(LocationManager.PASSIVE_PROVIDER, PASSIVE_LOCATION_INTERVAL_MS, 0f);
            requestProviderUpdates(LocationManager.GPS_PROVIDER, GPS_LOCATION_INTERVAL_MS, GPS_LOCATION_DISTANCE_M);
        } catch (Exception error) {
            broadcastStatus("Location start failed: " + error.getMessage());
        }
    }

    private void requestProviderUpdates(String provider, long intervalMs, float distanceM) {
        try {
            if (locationManager != null && locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, intervalMs, distanceM, this, workerThread.getLooper());
            }
        } catch (Exception ignored) {
        }
    }

    private Location better(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        long now = System.currentTimeMillis();
        long ageDelta = b.getTime() - a.getTime();
        float aAccuracy = accuracyOrMax(a);
        float bAccuracy = accuracyOrMax(b);
        if (now - a.getTime() > FRESH_LOCATION_MAX_AGE_MS && ageDelta > 0) return b;
        if (bAccuracy <= GOOD_ACCURACY_M && (ageDelta >= 0 || bAccuracy < aAccuracy)) return b;
        if (bAccuracy + 25f < aAccuracy) return b;
        if (ageDelta > FRESH_LOCATION_MAX_AGE_MS && bAccuracy <= Math.max(aAccuracy * 1.5f, POOR_ACCURACY_M)) return b;
        return a;
    }

    private float accuracyOrMax(Location location) {
        return location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
    }

    private void uploadCurrentLocation() {
        Location location = lastLocation;
        if (location == null) {
            requestGpsNudgeIfNeeded(null);
            broadcastStatus("Waiting for current latitude and longitude");
            return;
        }
        requestGpsNudgeIfNeeded(location);

        try {
            List<Member> members = ApiClient.postLocation(
                    AppConfig.serverUrl(this),
                    AppConfig.groupCode(this),
                    AppConfig.memberId(this),
                    AppConfig.displayName(this),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAccuracy()
            );
            MemberBroadcaster.send(this, members);
            broadcastStatus("Broadcasted current lat/lon to group via server");
        } catch (Exception error) {
            broadcastStatus("Upload failed: " + error.getMessage());
        }
    }

    private void requestGpsNudgeIfNeeded(Location location) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        if (locationManager == null) return;
        long now = System.currentTimeMillis();
        if (now - lastGpsNudgeAt < GPS_NUDGE_MIN_INTERVAL_MS) return;
        boolean needsNudge = location == null
                || now - location.getTime() > FRESH_LOCATION_MAX_AGE_MS
                || !location.hasAccuracy()
                || location.getAccuracy() > POOR_ACCURACY_M;
        if (!needsNudge) return;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastGpsNudgeAt = now;
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, workerThread.getLooper());
            }
        } catch (Exception ignored) {
        }
    }

    private void broadcastStatus(String text) {
        Intent intent = new Intent(AppConfig.ACTION_STATUS);
        intent.setPackage(AppConfig.VIEWER_PACKAGE);
        intent.putExtra("status", text);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "LiveCircle location sharing",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps location sharing active");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent openIntent = getPackageManager().getLaunchIntentForPackage(AppConfig.VIEWER_PACKAGE);
        if (openIntent == null) {
            openIntent = new Intent(this, PermissionActivity.class);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("LiveCircle")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void scheduleRestart() {
        if (!AppConfig.isSharingEnabled(this)) return;
        Intent intent = new Intent(this, RestartSharingReceiver.class);
        intent.setAction(AppConfig.ACTION_RESTART_SHARING);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                91,
                intent,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long triggerAt = System.currentTimeMillis() + 3000;
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }
}
