package com.codex.livecircle;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

final class MemberBroadcaster {
    private MemberBroadcaster() {
    }

    static void send(Context context, List<Member> members) {
        try {
            JSONArray array = new JSONArray();
            for (Member member : members) {
                JSONObject item = new JSONObject();
                item.put("memberId", member.memberId);
                item.put("name", member.name);
                item.put("lat", member.lat);
                item.put("lon", member.lon);
                item.put("accuracy", member.accuracy);
                item.put("updatedAt", member.updatedAt);
                array.put(item);
            }
            Intent intent = new Intent(AppConfig.ACTION_MEMBERS);
            intent.setPackage(context.getPackageName());
            intent.putExtra("members", array.toString());
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}
