package com.example.instasharedownloader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TwitterExtractor {
    private static final String USER_AGENT = "Mozilla/5.0 XDownloader/1.0";
    private static final Pattern STATUS_URL = Pattern.compile(
            "(https?://(?:www\\.)?(?:x\\.com|twitter\\.com|mobile\\.twitter\\.com)/(?:i/web/status|[^\\s/?#]+/status(?:es)?)/(\\d+)[^\\s]*)",
            Pattern.CASE_INSENSITIVE);

    private TwitterExtractor() {
    }

    static String extractTwitterUrl(String input) {
        if (input == null) return null;
        Matcher matcher = STATUS_URL.matcher(input);
        if (!matcher.find()) return null;
        return matcher.group(1)
                .replaceAll("[)>\"'\\]\\u3000]+$", "")
                .trim();
    }

    static boolean isTwitterUrl(String url) {
        return extractTweetId(url) != null;
    }

    static List<MediaItem> extract(String sharedUrl) throws IOException {
        String tweetId = extractTweetId(sharedUrl);
        if (tweetId == null) {
            throw new IOException("Twitter/X 게시물 링크를 찾지 못했습니다.");
        }

        try {
            JSONObject root = new JSONObject(fetchTweetJson(tweetId));
            JSONArray mediaDetails = root.optJSONArray("mediaDetails");
            if (mediaDetails == null || mediaDetails.length() == 0) {
                throw new IOException("No downloadable Twitter/X media was found.");
            }

            List<MediaItem> items = new ArrayList<>();
            for (int i = 0; i < mediaDetails.length(); i++) {
                JSONObject media = mediaDetails.optJSONObject(i);
                if (media == null) continue;
                MediaItem item = toMediaItem(media);
                if (item != null) items.add(item);
            }

            if (items.isEmpty()) {
                throw new IOException("No downloadable Twitter/X photos or MP4 variants were found.");
            }
            return items;
        } catch (JSONException e) {
            throw new IOException("Twitter/X media metadata could not be parsed.", e);
        }
    }

    private static MediaItem toMediaItem(JSONObject media) {
        String type = cleanString(media, "type");
        String mediaUrl = cleanString(media, "media_url_https");
        if (type == null) return null;

        switch (type) {
            case "photo":
                if (mediaUrl == null) return null;
                return new MediaItem(
                        toOriginalImageUrl(mediaUrl),
                        MediaItem.Kind.IMAGE,
                        mediaUrl,
                        "twitter",
                        "https://x.com/");
            case "video":
            case "animated_gif":
                String video = bestMp4Variant(media.optJSONObject("video_info"));
                if (video == null) return null;
                return new MediaItem(
                        video,
                        MediaItem.Kind.VIDEO,
                        mediaUrl == null ? video : mediaUrl,
                        "twitter",
                        "https://x.com/");
            default:
                return null;
        }
    }

    private static String bestMp4Variant(JSONObject videoInfo) {
        JSONArray variants = videoInfo == null ? null : videoInfo.optJSONArray("variants");
        if (variants == null) return null;

        String bestUrl = null;
        int bestBitrate = -1;
        for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant == null) continue;
            if (!"video/mp4".equals(cleanString(variant, "content_type"))) continue;

            String url = cleanString(variant, "url");
            if (url == null) continue;
            int bitrate = variant.optInt("bitrate", 0);
            if (bestUrl == null || bitrate > bestBitrate) {
                bestUrl = url;
                bestBitrate = bitrate;
            }
        }
        return bestUrl;
    }

    private static String fetchTweetJson(String tweetId) throws IOException {
        URL url = new URL("https://cdn.syndication.twimg.com/tweet-result?id=" + tweetId + "&token=0");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            throw new IOException("X returned HTTP " + code + " for post " + tweetId + ".");
        }
        String body = readFully(stream);
        if (code < 200 || code > 299) {
            throw new IOException("X returned HTTP " + code + " for post " + tweetId + ".");
        }
        return body;
    }

    private static String readFully(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String extractTweetId(String input) {
        if (input == null) return null;
        Matcher matcher = STATUS_URL.matcher(input);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static String toOriginalImageUrl(String url) {
        int fragmentIndex = url.indexOf('#');
        String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
        String withoutFragment = fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;
        int queryIndex = withoutFragment.indexOf('?');
        String path = queryIndex >= 0 ? withoutFragment.substring(0, queryIndex) : withoutFragment;
        String query = queryIndex >= 0 ? withoutFragment.substring(queryIndex + 1) : "";
        path = path.replaceAll(":(?:small|medium|large|orig)$", "");

        StringBuilder kept = new StringBuilder();
        if (!query.isEmpty()) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.isEmpty()) continue;
                String key = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                if ("name".equalsIgnoreCase(key)) continue;
                if (kept.length() > 0) kept.append('&');
                kept.append(param);
            }
        }
        if (kept.length() > 0) kept.append('&');
        kept.append("name=orig");
        return path + "?" + kept + fragment;
    }

    private static String cleanString(JSONObject object, String name) {
        String value = object.optString(name, "").trim();
        if (value.isEmpty() || "null".equals(value.toLowerCase(Locale.US))) return null;
        return value;
    }
}
