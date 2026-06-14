package com.codex.livecircle;

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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LocationShareService extends Service implements LocationListener {
    private static final String CHANNEL_ID = "livecircle_location";
    private static final int NOTIFICATION_ID = 41;
    private static final long LOCATION_INTERVAL_MS = 20000;
    private static final float LOCATION_DISTANCE_M = 10f;
    private static final long UPLOAD_INTERVAL_MS = 20000;
    private static final long EVENT_RECONNECT_MS = 3000;

    private HandlerThread workerThread;
    private Handler worker;
    private LocationManager locationManager;
    private Location lastLocation;
    private HttpURLConnection eventConnection;
    private Thread eventThread;
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
        workerThread = new HandlerThread("livecircle-relay");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppConfig.setSharingEnabled(this, true);
        SharingStarter.scheduleWatchdog(this);
        running = true;
        startForeground(NOTIFICATION_ID, buildNotification("Server relay sharing is running"));
        startLocationUpdates();
        startEventStream();
        worker.removeCallbacks(uploadTask);
        worker.postDelayed(uploadTask, UPLOAD_INTERVAL_MS);
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
        if (eventConnection != null) eventConnection.disconnect();
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
        lastLocation = location;
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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL_MS, LOCATION_DISTANCE_M, this, workerThread.getLooper());
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL_MS, LOCATION_DISTANCE_M, this, workerThread.getLooper());
        } catch (Exception error) {
            broadcastStatus("Location start failed: " + error.getMessage());
        }
    }

    private Location better(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getTime() >= b.getTime() ? a : b;
    }

    private void uploadCurrentLocation() {
        Location location = lastLocation;
        if (location == null) {
            broadcastStatus("Waiting for current latitude and longitude");
            return;
        }

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

    private void startEventStream() {
        if (eventThread != null && eventThread.isAlive()) return;
        eventThread = new Thread(() -> {
            while (running) {
                try {
                    listenForGroupEvents();
                } catch (Exception error) {
                    if (running) broadcastStatus("Event stream reconnecting: " + error.getMessage());
                }
                if (running) {
                    try {
                        Thread.sleep(EVENT_RECONNECT_MS);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "livecircle-events");
        eventThread.start();
    }

    private void listenForGroupEvents() throws Exception {
        URL url = new URL(ApiClient.eventsUrl(AppConfig.serverUrl(this), AppConfig.groupCode(this)));
        eventConnection = (HttpURLConnection) url.openConnection();
        eventConnection.setRequestMethod("GET");
        eventConnection.setRequestProperty("Accept", "text/event-stream");
        eventConnection.setConnectTimeout(8000);
        eventConnection.setReadTimeout(0);

        int code = eventConnection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

        BufferedReader reader = new BufferedReader(new InputStreamReader(eventConnection.getInputStream(), StandardCharsets.UTF_8));
        String line;
        StringBuilder data = new StringBuilder();
        while (running && (line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                data.append(line.substring(5).trim());
            } else if (line.length() == 0 && data.length() > 0) {
                List<Member> members = ApiClient.parseMembersPayload(data.toString());
                MemberBroadcaster.send(this, members);
                data.setLength(0);
            }
        }
    }

    private void broadcastStatus(String text) {
        Intent intent = new Intent(AppConfig.ACTION_STATUS);
        intent.setPackage(getPackageName());
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
        channel.setDescription("Keeps server-relayed location sharing active");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
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
