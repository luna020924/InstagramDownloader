package com.example.instasharedownloader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/** Story reads use the JSON API host; the website can return its HTML shell with HTTP 200. */
final class InstagramStoryApi {
    interface Connections {
        HttpURLConnection open(URL url) throws IOException;
    }

    private InstagramStoryApi() { }

    static String fetch(String endpoint, String referer, String cookie) throws IOException {
        return fetch(endpoint, referer, cookie, url -> (HttpURLConnection) url.openConnection());
    }

    static String fetch(String endpoint, String referer, String cookie, Connections connections)
            throws IOException {
        URL url = apiUrl(endpoint);
        HttpURLConnection connection = connections.open(url);
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            // Never forward session credentials to a redirect destination.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent",
                    "Instagram 361.0.0.35.82 (iPad13,8; iOS 18_0; en_US; en-US; "
                            + "scale=2.00; 2048x2732; 674117118) AppleWebKit/420+");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
            connection.setRequestProperty("X-IG-App-ID", "124024574287414");
            // The mobile API no longer reliably accepts web cookies alone. Convert the same
            // existing session to its native authorization form, in memory and on-device only.
            connection.setRequestProperty("Authorization", authorization(cookie));
            String userId = cookieValue(cookie, "ds_user_id");
            connection.setRequestProperty("IG-Intended-User-ID", userId);
            connection.setRequestProperty("IG-U-DS-User-ID", userId);
            String deviceId = cookieValue(cookie, "ig_did");
            if (deviceId != null) connection.setRequestProperty("X-IG-Device-ID", deviceId);
            String mid = cookieValue(cookie, "mid");
            if (mid != null) connection.setRequestProperty("X-MID", mid);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new IOException("Instagram story API returned HTTP " + status + " (redirect).");
            }
            InputStream stream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            String body = readBody(stream);
            if (status < 200 || status >= 300) {
                // Response bodies can contain account data or signed media URLs.
                throw new IOException("Instagram story API returned HTTP " + status + ".");
            }
            String json = body.trim();
            if (json.startsWith("for (;;);")) json = json.substring(9).trim();
            try {
                JSONObject response = new JSONObject(json);
                if ("fail".equals(response.optString("status"))) {
                    String reason = response.optString("message", "").toLowerCase(Locale.US);
                    if (response.optBoolean("login_required", false) || reason.contains("login_required")) {
                        throw new IOException("Instagram login is required for story links.");
                    }
                    throw new IOException("Instagram story API could not complete the request.");
                }
            } catch (JSONException error) {
                throw new IOException("Instagram story API did not return JSON.");
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }

    static URL apiUrl(String endpoint) throws IOException {
        URL source = new URL(endpoint);
        String host = source.getHost().toLowerCase(Locale.US);
        if (!"https".equals(source.getProtocol())
                || !("www.instagram.com".equals(host) || "i.instagram.com".equals(host))
                || !source.getPath().startsWith("/api/v1/")
                || source.getUserInfo() != null || source.getPort() != -1) {
            throw new IOException("Unsupported Instagram story API endpoint.");
        }
        return new URL("https://i.instagram.com" + source.getFile());
    }

    static String authorization(String cookie) throws IOException {
        String userId = cookieValue(cookie, "ds_user_id");
        String sessionId = cookieValue(cookie, "sessionid");
        if (userId == null || !userId.matches("\\d+") || sessionId == null || sessionId.isEmpty()) {
            throw new IOException("Instagram login is required for story links.");
        }
        try {
            JSONObject session = new JSONObject();
            session.put("ds_user_id", userId);
            session.put("sessionid", sessionId);
            return "Bearer IGT:2:" + Base64.getEncoder().encodeToString(
                    session.toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException error) {
            throw new IOException("Instagram login is required for story links.");
        }
    }

    private static String cookieValue(String cookie, String key) {
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String value = part.trim();
            if (value.startsWith(key + "=")) return value.substring(key.length() + 1);
        }
        return null;
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
