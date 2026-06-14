package com.codex.livecircle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ApiClient {
    private ApiClient() {
    }

    static List<Member> postLocation(String serverUrl, String groupCode, String memberId, String name,
                                     double lat, double lon, float accuracy) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("groupCode", groupCode);
        payload.put("memberId", memberId);
        payload.put("name", name);
        payload.put("lat", lat);
        payload.put("lon", lon);
        payload.put("accuracy", accuracy);
        String response = request("POST", trimServer(serverUrl) + "/api/location", payload.toString());
        return parseMembers(response);
    }

    static List<Member> getGroup(String serverUrl, String groupCode) throws Exception {
        String encoded = URLEncoder.encode(groupCode, "UTF-8");
        String response = request("GET", trimServer(serverUrl) + "/api/group?groupCode=" + encoded, null);
        return parseMembers(response);
    }

    static String eventsUrl(String serverUrl, String groupCode) throws Exception {
        String encoded = URLEncoder.encode(groupCode, "UTF-8");
        return trimServer(serverUrl) + "/api/events?groupCode=" + encoded;
    }

    static List<Member> parseMembersPayload(String response) throws Exception {
        return parseMembers(response);
    }

    private static String trimServer(String serverUrl) {
        String value = serverUrl == null ? "" : serverUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String request(String method, String target, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
            writer.write(body);
            writer.flush();
            writer.close();
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) text.append(line);
        reader.close();
        if (code < 200 || code >= 300) throw new IllegalStateException(text.toString());
        return text.toString();
    }

    private static List<Member> parseMembers(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONArray array = root.optJSONArray("members");
        List<Member> members = new ArrayList<>();
        if (array == null) return members;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            members.add(new Member(
                    item.optString("memberId"),
                    item.optString("name", "Member"),
                    item.optDouble("lat"),
                    item.optDouble("lon"),
                    (float) item.optDouble("accuracy", 0),
                    item.optLong("updatedAt", 0)
            ));
        }
        return members;
    }
}
