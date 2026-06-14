package com.codex.livecircle;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final long INITIAL_REFRESH_DELAY_MS = 1500;
    private static final long FOREGROUND_REFRESH_INTERVAL_MS = 10000;

    private final List<Member> members = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText groupField;
    private EditText nameField;
    private TextView statusView;
    private TextView myLocationView;
    private TextView membersView;
    private LinearLayout membersList;
    private MapView mapView;
    private AMap aMap;
    private boolean mapFocused;
    private boolean startScheduled;
    private boolean uploaderRequested;
    private boolean resumed;
    private boolean refreshRunning;
    private final Map<String, Marker> memberMarkers = new HashMap<>();
    private final Map<String, LatLng> memberPoints = new HashMap<>();

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshGroupFromServer();
            if (resumed) {
                mainHandler.postDelayed(this, FOREGROUND_REFRESH_INTERVAL_MS);
            }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AppConfig.ACTION_STATUS.equals(intent.getAction())) {
                statusView.setText(intent.getStringExtra("status"));
            } else if (AppConfig.ACTION_MEMBERS.equals(intent.getAction())) {
                applyMembers(parseMembers(intent.getStringExtra("members")));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        AppConfig.memberId(this);
        buildUi(savedInstanceState);
        scheduleForceStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        resumed = true;
        scheduleForceStart();
        mainHandler.removeCallbacks(refreshTask);
        mainHandler.postDelayed(refreshTask, INITIAL_REFRESH_DELAY_MS);
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConfig.ACTION_STATUS);
        filter.addAction(AppConfig.ACTION_MEMBERS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onPause() {
        saveConfig();
        requestUploaderSilently();
        resumed = false;
        mainHandler.removeCallbacks(refreshTask);
        unregisterReceiver(receiver);
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        requestUploaderSilently();
        super.onStop();
    }

    @Override
    protected void onUserLeaveHint() {
        requestUploaderSilently();
        super.onUserLeaveHint();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            requestUploaderSilently();
        }
    }

    @Override
    protected void onDestroy() {
        requestUploaderSilently();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    private void buildUi(Bundle savedInstanceState) {
        SharedPreferences prefs = AppConfig.prefs(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(247, 249, 248));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("LiveCircle");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(25, 47, 42));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Group location sharing with Aliyun relay and AMap markers.");
        subtitle.setTextColor(Color.rgb(80, 96, 91));
        subtitle.setTextSize(14);
        root.addView(subtitle, matchWrap());

        groupField = field("Group code", prefs.getString(AppConfig.KEY_GROUP_CODE, AppConfig.DEFAULT_GROUP_CODE));
        nameField = field("My name", prefs.getString(AppConfig.KEY_NAME, "Me"));
        root.addView(groupField, matchWrapTop(18));
        root.addView(nameField, matchWrapTop(10));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(buttons, matchWrapTop(12));

        Button settings = button("Permissions");
        settings.setOnClickListener(v -> openAppSettings());
        buttons.addView(settings, weightButton());

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        aMap.getUiSettings().setZoomControlsEnabled(true);
        aMap.getUiSettings().setMyLocationButtonEnabled(false);
        root.addView(mapView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(420)));

        statusView = new TextView(this);
        statusView.setText("Starting persistent server relay broadcast");
        statusView.setTextSize(15);
        statusView.setTextColor(Color.rgb(51, 73, 68));
        root.addView(statusView, matchWrapTop(12));

        myLocationView = new TextView(this);
        myLocationView.setText("My location: waiting for server update");
        myLocationView.setTextSize(15);
        myLocationView.setTextColor(Color.rgb(25, 88, 76));
        myLocationView.setLineSpacing(2, 1.05f);
        root.addView(myLocationView, matchWrapTop(10));

        membersView = new TextView(this);
        membersView.setTextSize(15);
        membersView.setTextColor(Color.rgb(36, 51, 47));
        membersView.setLineSpacing(2, 1.05f);
        membersView.setText("No members yet");
        root.addView(membersView, matchWrapTop(10));

        membersList = new LinearLayout(this);
        membersList.setOrientation(LinearLayout.VERTICAL);
        root.addView(membersList, matchWrapTop(8));

        setContentView(scroll);
    }

    private EditText field(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setSingleLine(true);
        editText.setTextSize(16);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        return editText;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private void forceStartSharing() {
        uploaderRequested = true;
        boolean started = requestUploaderSilently();
        statusView.setText(started ? "Uploader service requested" : "Install LiveCircleUploader, then open Permissions");
        refreshGroupFromServer();
    }

    private boolean requestUploaderSilently() {
        if (groupField == null || nameField == null) return false;
        saveConfig();
        AppConfig.setSharingEnabled(this, true);
        return UploaderBridge.startUploaderQuiet(
                this,
                groupField.getText().toString().trim(),
                nameField.getText().toString().trim(),
                AppConfig.memberId(this)
        );
    }

    private void scheduleForceStart() {
        if (startScheduled || uploaderRequested) return;
        startScheduled = true;
        mainHandler.postDelayed(() -> {
            startScheduled = false;
            if (!isFinishing() && statusView != null) {
                forceStartSharing();
            }
        }, 1200);
    }

    private void saveConfig() {
        AppConfig.prefs(this).edit()
                .putString(AppConfig.KEY_GROUP_CODE, groupField.getText().toString().trim())
                .putString(AppConfig.KEY_NAME, nameField.getText().toString().trim())
                .apply();
    }

    private void applyMembers(List<Member> next) {
        members.clear();
        members.addAll(next);
        updateMapMarkers();
        updateMyLocationView();

        if (members.isEmpty()) {
            membersView.setText("No members yet");
            membersList.removeAllViews();
            return;
        }

        membersView.setText("");
        membersList.removeAllViews();
        long now = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        for (Member member : members) {
            long ageSeconds = Math.max(0, (now - member.updatedAt) / 1000);
            StringBuilder rowText = new StringBuilder();
            rowText.append(member.name);
            if (member.memberId.equals(AppConfig.memberId(this))) rowText.append(" (me)");
            rowText.append("\n");
            rowText.append("  ").append(String.format(Locale.CHINA, "%.6f, %.6f", member.lat, member.lon));
            rowText.append("  accuracy ").append(Math.round(member.accuracy)).append("m");
            rowText.append("  ").append(format.format(new Date(member.updatedAt)));
            rowText.append("  ").append(ageSeconds).append("s ago");

            TextView row = new TextView(this);
            row.setText(rowText.toString());
            row.setTextSize(15);
            row.setTextColor(Color.rgb(28, 49, 44));
            row.setLineSpacing(2, 1.05f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(82));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundColor(Color.WHITE);
            row.setOnClickListener(v -> focusMember(member.memberId));
            membersList.addView(row, matchWrapTop(6));
        }
    }

    private void updateMyLocationView() {
        if (myLocationView == null) return;
        Member self = findSelfMember();
        if (self == null) {
            myLocationView.setText("My location: waiting for first upload");
            return;
        }

        long ageSeconds = Math.max(0, (System.currentTimeMillis() - self.updatedAt) / 1000);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        myLocationView.setText("My location: "
                + String.format(Locale.CHINA, "%.6f, %.6f", self.lat, self.lon)
                + "  accuracy " + Math.round(self.accuracy) + "m"
                + "  " + format.format(new Date(self.updatedAt))
                + "  " + ageSeconds + "s ago");
    }

    private Member findSelfMember() {
        Member latest = null;
        String selfId = AppConfig.memberId(this);
        String displayName = nameField == null ? AppConfig.displayName(this) : nameField.getText().toString().trim();
        for (Member member : members) {
            if (member.memberId.equals(selfId)) {
                return member;
            }
            if (displayName.length() > 0 && displayName.equals(member.name)) {
                latest = newer(latest, member);
            } else if (members.size() == 1) {
                latest = member;
            }
        }
        return latest;
    }

    private Member newer(Member a, Member b) {
        if (a == null) return b;
        return b.updatedAt >= a.updatedAt ? b : a;
    }

    private void refreshGroupFromServer() {
        if (refreshRunning) return;
        refreshRunning = true;
        final String groupCode = groupField == null ? AppConfig.groupCode(this) : groupField.getText().toString().trim();
        new Thread(() -> {
            try {
                List<Member> fetched = ApiClient.getGroup(AppConfig.serverUrl(MainActivity.this), groupCode);
                mainHandler.post(() -> {
                    refreshRunning = false;
                    applyMembers(fetched);
                    statusView.setText("Server group refreshed");
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    refreshRunning = false;
                    statusView.setText("Server refresh failed: " + error.getMessage());
                    updateMyLocationView();
                });
            }
        }, "livecircle-refresh").start();
    }

    private void updateMapMarkers() {
        if (aMap == null) return;
        aMap.clear();
        memberMarkers.clear();
        memberPoints.clear();
        if (members.isEmpty()) return;

        String selfId = AppConfig.memberId(this);
        LatLng focus = null;
        long now = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

        for (Member member : members) {
            double[] gcj = CoordinateTransform.wgs84ToGcj02(member.lat, member.lon);
            LatLng point = new LatLng(gcj[0], gcj[1]);
            boolean self = member.memberId.equals(selfId);
            long ageSeconds = Math.max(0, (now - member.updatedAt) / 1000);
            String title = self ? member.name + " (me)" : member.name;
            String snippet = "accuracy " + Math.round(member.accuracy) + "m, "
                    + format.format(new Date(member.updatedAt)) + ", " + ageSeconds + "s ago";

            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title(title)
                    .snippet(snippet));
            memberMarkers.put(member.memberId, marker);
            memberPoints.put(member.memberId, point);

            if (self || focus == null) {
                focus = point;
            }
        }

        if (focus != null && !mapFocused) {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, members.size() == 1 ? 16f : 12f));
            mapFocused = true;
        }
    }

    private void focusMember(String memberId) {
        if (aMap == null) return;
        LatLng point = memberPoints.get(memberId);
        if (point == null) return;
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f));
        mapFocused = true;
        Marker marker = memberMarkers.get(memberId);
        if (marker != null) {
            marker.showInfoWindow();
            statusView.setText("Focused on " + marker.getTitle());
        }
    }

    private List<Member> parseMembers(String json) {
        List<Member> parsed = new ArrayList<>();
        if (json == null) return parsed;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                parsed.add(new Member(
                        item.optString("memberId"),
                        item.optString("name", "Member"),
                        item.optDouble("lat"),
                        item.optDouble("lon"),
                        (float) item.optDouble("accuracy", 0),
                        item.optLong("updatedAt", 0)
                ));
            }
        } catch (Exception ignored) {
        }
        return parsed;
    }

    private void openAppSettings() {
        saveConfig();
        UploaderBridge.openPermissions(
                this,
                groupField.getText().toString().trim(),
                nameField.getText().toString().trim(),
                AppConfig.memberId(this)
        );
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapTop(int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams weightButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
