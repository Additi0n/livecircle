package com.codex.livecircle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RadarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Member> members = new ArrayList<>();
    private String selfId = "";

    public RadarView(Context context) {
        super(context);
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setMembers(String selfId, List<Member> next) {
        this.selfId = selfId == null ? "" : selfId;
        members.clear();
        members.addAll(next);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = size * 0.42f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(247, 249, 248));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.rgb(204, 214, 211));
        for (int i = 1; i <= 3; i++) {
            canvas.drawCircle(cx, cy, radius * i / 3f, paint);
        }
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);

        Member self = findSelf();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(46, 125, 107));
        canvas.drawCircle(cx, cy, 13f, paint);
        drawLabel(canvas, "我", cx + 18f, cy - 18f, Color.rgb(32, 78, 68));

        if (self == null) {
            drawCentered(canvas, "等待本机定位", cx, cy + radius + 34f);
            return;
        }

        double maxDistance = 50;
        for (Member member : members) {
            if (member.memberId.equals(selfId)) continue;
            maxDistance = Math.max(maxDistance, distanceMeters(self.lat, self.lon, member.lat, member.lon));
        }
        double scaleDistance = niceScale(maxDistance);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(130, 145, 140));
        paint.setTextSize(26f);
        paint.setStrokeWidth(1.5f);
        canvas.drawText(formatMeters(scaleDistance), 18f, height - 22f, paint);

        int index = 0;
        for (Member member : members) {
            if (member.memberId.equals(selfId)) continue;
            double northMeters = (member.lat - self.lat) * 111_320d;
            double eastMeters = (member.lon - self.lon) * 111_320d * Math.cos(Math.toRadians(self.lat));
            float x = cx + (float) (eastMeters / scaleDistance * radius);
            float y = cy - (float) (northMeters / scaleDistance * radius);
            float clampedX = Math.max(cx - radius, Math.min(cx + radius, x));
            float clampedY = Math.max(cy - radius, Math.min(cy + radius, y));
            int color = colorFor(index++);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(clampedX, clampedY, 12f, paint);
            drawLabel(canvas, member.name, clampedX + 16f, clampedY - 16f, color);
        }
    }

    private Member findSelf() {
        for (Member member : members) {
            if (member.memberId.equals(selfId)) return member;
        }
        return null;
    }

    private void drawCentered(Canvas canvas, String text, float x, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(30f);
        paint.setColor(Color.rgb(84, 96, 92));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawLabel(Canvas canvas, String text, float x, float y, int color) {
        paint.setTextSize(28f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        RectF rect = new RectF(x - 8f, y - 30f, x + paint.measureText(text) + 10f, y + 8f);
        canvas.drawRoundRect(rect, 10f, 10f, paint);
        paint.setColor(color);
        canvas.drawText(text, x, y, paint);
    }

    private double niceScale(double meters) {
        if (meters <= 50) return 50;
        if (meters <= 200) return 200;
        if (meters <= 1000) return 1000;
        if (meters <= 5000) return 5000;
        if (meters <= 20000) return 20000;
        return Math.ceil(meters / 50000d) * 50000d;
    }

    private String formatMeters(double meters) {
        if (meters >= 1000) return "半径 " + Math.round(meters / 1000d) + " km";
        return "半径 " + Math.round(meters) + " m";
    }

    private int colorFor(int index) {
        int[] colors = {
                Color.rgb(191, 71, 48),
                Color.rgb(41, 98, 160),
                Color.rgb(129, 84, 169),
                Color.rgb(188, 128, 28),
                Color.rgb(76, 132, 54)
        };
        return colors[index % colors.length];
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2d) * Math.sin(dp / 2d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2d) * Math.sin(dl / 2d);
        return r * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }
}
