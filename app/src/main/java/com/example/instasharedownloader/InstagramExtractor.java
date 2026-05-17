package com.example.instasharedownloader;

import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstagramExtractor {
    private static final String INSTAGRAM_ORIGIN = "https://www.instagram.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/143.0.0.0 Mobile Safari/537.36";
    private static volatile String sessionCookieOverride;

    private static final Pattern META_TAG = Pattern.compile(
            "<meta\\s+[^>]*(?:property|name)=[\"']([^\"']+)[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>|"
                    + "<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']([^\"']+)[\"'][^>]*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ESCAPED_VIDEO_URL = Pattern.compile(
            "\\\\?\"video_url\\\\?\"\\s*:\\s*\\\\?\"(https?:\\\\?/\\\\?/[^\"\\\\]+(?:\\\\.[^\"\\\\]+)*)\\\\?\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ESCAPED_IMAGE_URL = Pattern.compile(
            "\\\\?\"(?:display_url|url)\\\\?\"\\s*:\\s*\\\\?\"(https?:\\\\?/\\\\?/[^\"\\\\]+(?:\\\\.[^\"\\\\]+)*)\\\\?\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOOSE_MEDIA_URL = Pattern.compile(
            "https?:\\\\?/\\\\?/[^\\s\"'<>]+?(?:cdninstagram\\.com|fbcdn\\.net)[^\\s\"'<>]*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORTCODE = Pattern.compile(
            "/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final String SHORTCODE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private InstagramExtractor() {
    }

    static void updateSessionCookie(String cookie) {
        if (cookie != null && !cookie.isEmpty()) {
            sessionCookieOverride = cookie;
        }
    }

    private static String instagramCookie() {
        String cookie = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN);
        if (cookie == null || cookie.isEmpty()) {
            cookie = sessionCookieOverride;
        }
        return cookie;
    }

    static String extractInstagramUrl(String input) {
        if (input == null) return null;

        Matcher matcher = Pattern.compile("(https?://(?:www\\.)?instagram\\.com/[^\\s]+|(?:www\\.)?instagram\\.com/[^\\s]+)",
                Pattern.CASE_INSENSITIVE).matcher(input);
        if (!matcher.find()) return null;

        String raw = matcher.group(1)
                .replaceAll("[)>\"'\\]\\u3000]+$", "")
                .trim();
        if (!raw.startsWith("http")) raw = "https://" + raw;
        return normalizeUrl(raw);
    }

    static String normalizeUrl(String raw) {
        try {
            URI uri = new URI(raw);
            String host = uri.getHost();
            if (host == null || !host.toLowerCase(Locale.US).endsWith("instagram.com")) {
                return raw;
            }
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (!path.endsWith("/")) path += "/";
            return new URI("https", "www.instagram.com", path, null).toString();
        } catch (URISyntaxException e) {
            return raw;
        }
    }

    private static StoryRequest storyRequestFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null) return null;

            String[] parts = path.split("/");
            if (parts.length >= 4
                    && "stories".equalsIgnoreCase(parts[1])
                    && "highlights".equalsIgnoreCase(parts[2])) {
                return new StoryRequest(true, parts[3], null, null);
            }

            if (parts.length >= 3 && "stories".equalsIgnoreCase(parts[1])) {
                String username = parts[2].isEmpty() ? null : parts[2];
                String mediaId = parts.length >= 4 && parts[3].matches("\\d+") ? parts[3] : null;
                return new StoryRequest(false, null, username, mediaId);
            }

            if (parts.length >= 3 && "s".equalsIgnoreCase(parts[1])) {
                String mediaId = parts[2].matches("\\d+") ? parts[2] : null;
                return new StoryRequest(false, null, null, mediaId);
            }
        } catch (URISyntaxException ignored) {
        }
        return null;
    }

    static List<MediaItem> extract(String sharedUrl) throws IOException {
        String url = normalizeUrl(sharedUrl);

        StoryRequest storyRequest = storyRequestFromUrl(url);
        if (storyRequest != null) {
            List<MediaItem> storyItems = tryExtractStoryMedia(url, storyRequest);
            if (!storyItems.isEmpty()) {
                return storyItems;
            }
            throw new IOException("No downloadable story media was found.");
        }

        List<MediaItem> apiItems = tryExtractFromMediaInfoApi(url);
        if (!apiItems.isEmpty()) {
            return apiItems;
        }

        String html = fetch(url);
        if (looksLoginOnly(html)) {
            throw new IOException("Instagram login is required for this link.");
        }

        LinkedHashMap<String, MediaItem> results = new LinkedHashMap<>();
        collectMetaMedia(html, results);
        collectJsonMedia(html, results);

        boolean isVideoPage = url.contains("/reel/") || url.contains("/reels/") || url.contains("/tv/");
        boolean hasVideo = false;
        for (MediaItem item : results.values()) {
            if (item.kind == MediaItem.Kind.VIDEO) {
                hasVideo = true;
                break;
            }
        }

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : results.values()) {
            if (isVideoPage && hasVideo && item.kind == MediaItem.Kind.IMAGE) continue;
            filtered.add(item);
            if (filtered.size() >= 20) break;
        }

        if (filtered.isEmpty()) {
            throw new IOException("No downloadable public media was found.");
        }
        return filtered;
    }

    private static List<MediaItem> tryExtractFromMediaInfoApi(String pageUrl) {
        String shortcode = shortcodeFromUrl(pageUrl);
        String mediaId = mediaIdFromShortcode(shortcode);
        String cookie = instagramCookie();
        if (mediaId == null || cookie == null || !cookie.contains("sessionid=")) {
            return new ArrayList<>();
        }

        try {
            String endpoint = "https://www.instagram.com/api/v1/media/" + mediaId + "/info/";
            String body = fetchApi(endpoint, pageUrl, cookie);
            return parseMediaInfo(body);
        } catch (IOException | JSONException e) {
            return new ArrayList<>();
        }
    }

    private static List<MediaItem> tryExtractStoryMedia(String pageUrl, StoryRequest request) throws IOException {
        String cookie = instagramCookie();
        if (cookie == null || !cookie.contains("sessionid=")) {
            throw new IOException("Instagram login is required for story links.");
        }

        try {
            if (request.highlight) {
                String endpoint = "https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=highlight%3A"
                        + urlEncode(request.reelId);
                return fetchStoryEndpoint(endpoint, pageUrl, cookie, null);
            }

            String userId = resolveStoryUserId(pageUrl, request, cookie);
            List<MediaItem> fullStoryCandidate = new ArrayList<>();
            if (userId != null) {
                String endpoint = "https://www.instagram.com/api/v1/feed/user/"
                        + urlEncode(userId)
                        + "/story/";
                List<MediaItem> allStories = fetchStoryEndpoint(endpoint, pageUrl, cookie, request.mediaId, false);
                if (!allStories.isEmpty()) {
                    if (request.mediaId == null || hasSharedStory(allStories)) return allStories;
                    fullStoryCandidate = allStories;
                }

                endpoint = "https://www.instagram.com/api/v1/feed/reels_media/?reel_ids="
                        + urlEncode(userId);
                allStories = fetchStoryEndpoint(endpoint, pageUrl, cookie, request.mediaId, false);
                if (!allStories.isEmpty()) {
                    if (request.mediaId == null || hasSharedStory(allStories)) return allStories;
                    if (fullStoryCandidate.isEmpty()) fullStoryCandidate = allStories;
                }
            }

            if (userId != null && request.mediaId != null) {
                String endpoint = "https://www.instagram.com/api/v1/feed/reels_media/?media_id="
                        + urlEncode(request.mediaId)
                        + "&reel_ids="
                        + urlEncode(userId);
                List<MediaItem> selected = fetchStoryEndpoint(endpoint, pageUrl, cookie, request.mediaId, true);
                if (!selected.isEmpty()) {
                    if (!fullStoryCandidate.isEmpty()) {
                        List<MediaItem> markedStories = markSharedStoryFromSelection(fullStoryCandidate, selected);
                        if (hasSharedStory(markedStories)) return markedStories;
                    }
                    return selected;
                }
            }

            if (!fullStoryCandidate.isEmpty()) {
                return fullStoryCandidate;
            }

            if (request.mediaId != null) {
                String endpoint = "https://www.instagram.com/api/v1/feed/reels_media/?media_id="
                        + urlEncode(request.mediaId);
                return fetchStoryEndpoint(endpoint, pageUrl, cookie, request.mediaId, true);
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }

        return new ArrayList<>();
    }

    private static List<MediaItem> fetchStoryEndpoint(String endpoint, String pageUrl, String cookie, String requestedMediaId) throws JSONException {
        return fetchStoryEndpoint(endpoint, pageUrl, cookie, requestedMediaId, true);
    }

    private static List<MediaItem> fetchStoryEndpoint(String endpoint, String pageUrl, String cookie, String requestedMediaId, boolean markFirstIfMissing) throws JSONException {
        try {
            String body = fetchApi(endpoint, pageUrl, cookie);
            return parseStoryMedia(body, requestedMediaId, markFirstIfMissing);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static List<MediaItem> parseStoryMedia(String json, String requestedMediaId) throws JSONException {
        return parseStoryMedia(json, requestedMediaId, true);
    }

    private static List<MediaItem> parseStoryMedia(String json, String requestedMediaId, boolean markFirstIfMissing) throws JSONException {
        JSONObject root = new JSONObject(stripJsonPrefix(json));
        JSONObject reel = firstStoryReel(root);
        if (reel == null) return new ArrayList<>();

        JSONArray items = reel.optJSONArray("items");
        if (items == null) return new ArrayList<>();

        LinkedHashMap<String, MediaItem> all = new LinkedHashMap<>();
        boolean foundRequested = requestedMediaId == null;
        String reelAuthor = instagramAuthorName(reel);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            boolean sharedStory = storyItemMatches(item, requestedMediaId);
            if (sharedStory) foundRequested = true;
            collectStoryMedia(item, all, sharedStory, reelAuthor);
        }

        if (requestedMediaId != null && markFirstIfMissing && !foundRequested && !all.isEmpty()) {
            markFirstStoryAsShared(all);
        }
        return new ArrayList<>(all.values());
    }

    private static List<MediaItem> markSharedStoryFromSelection(List<MediaItem> allStories, List<MediaItem> selectedStories) {
        List<MediaItem> marked = new ArrayList<>();
        boolean matched = false;
        for (MediaItem item : allStories) {
            boolean shared = item.sharedStory || matchesAnyStoryMedia(item, selectedStories);
            if (shared) matched = true;
            marked.add(new MediaItem(
                    item.url,
                    item.kind,
                    item.thumbnailUrl,
                    item.filePrefix,
                    item.referer,
                    item.fileNameBase,
                    item.storyItem,
                    shared));
        }
        return matched ? marked : allStories;
    }

    private static boolean matchesAnyStoryMedia(MediaItem item, List<MediaItem> selectedStories) {
        for (MediaItem selected : selectedStories) {
            if (sameStoryMedia(item, selected)) return true;
        }
        return false;
    }

    private static boolean sameStoryMedia(MediaItem first, MediaItem second) {
        return first.url.equals(second.url)
                || first.thumbnailUrl.equals(second.thumbnailUrl)
                || first.url.equals(second.thumbnailUrl)
                || first.thumbnailUrl.equals(second.url);
    }

    private static void collectStoryMedia(JSONObject media, Map<String, MediaItem> out, boolean sharedStory, String inheritedAuthor) {
        collectSingleMedia(media, out, true, sharedStory, inheritedAuthor, null);
    }

    private static void markFirstStoryAsShared(LinkedHashMap<String, MediaItem> out) {
        if (out.isEmpty()) return;
        String firstKey = out.keySet().iterator().next();
        MediaItem item = out.get(firstKey);
        if (item == null) return;
        out.put(firstKey, new MediaItem(
                item.url,
                item.kind,
                item.thumbnailUrl,
                item.filePrefix,
                item.referer,
                item.fileNameBase,
                true,
                true));
    }

    static boolean isStoryUrl(String url) {
        return storyRequestFromUrl(normalizeUrl(url)) != null;
    }

    static boolean hasSharedStory(List<MediaItem> items) {
        for (MediaItem item : items) {
            if (item.storyItem && item.sharedStory) return true;
        }
        return false;
    }

    static boolean hasOtherStoryItems(List<MediaItem> items) {
        boolean hasShared = false;
        boolean hasOther = false;
        for (MediaItem item : items) {
            if (!item.storyItem) continue;
            if (item.sharedStory) {
                hasShared = true;
            } else {
                hasOther = true;
            }
        }
        return hasShared && hasOther;
    }

    static List<MediaItem> sharedStoryItemsOnly(List<MediaItem> items) {
        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : items) {
            if (item.sharedStory) filtered.add(item);
        }
        return filtered.isEmpty() ? items : filtered;
    }

    private static JSONObject firstStoryReel(JSONObject root) {
        JSONObject reel = root.optJSONObject("reel");
        if (reel != null) return reel;

        JSONArray reelsMedia = root.optJSONArray("reels_media");
        if (reelsMedia != null && reelsMedia.length() > 0) {
            JSONObject first = reelsMedia.optJSONObject(0);
            if (first != null) return first;
        }

        JSONObject reels = root.optJSONObject("reels");
        if (reels != null) {
            JSONArray names = reels.names();
            if (names != null && names.length() > 0) {
                return reels.optJSONObject(names.optString(0));
            }
        }
        return null;
    }

    private static boolean storyItemMatches(JSONObject item, String requestedMediaId) {
        if (requestedMediaId == null) return false;
        String id = item.optString("id", "");
        String pk = item.optString("pk", "");
        return id.equals(requestedMediaId)
                || id.startsWith(requestedMediaId + "_")
                || pk.equals(requestedMediaId);
    }

    private static String resolveStoryUserId(String pageUrl, StoryRequest request, String cookie) {
        String userId = null;

        if (request.username != null) {
            userId = resolveReelsTrayUserId(request.username, pageUrl, cookie);
            if ((userId == null || userId.isEmpty()) && request.mediaId != null) {
                userId = resolveMediaInfoUserId(request.mediaId, pageUrl, cookie);
            }
            if (userId == null || userId.isEmpty()) {
                String endpoint = "https://www.instagram.com/api/v1/users/web_profile_info/?username="
                        + urlEncode(request.username);
                userId = resolveProfileUserId(endpoint, pageUrl, cookie);
            }
            if (userId == null || userId.isEmpty()) {
                String endpoint = "https://www.instagram.com/api/v1/users/web_profile_info/?username="
                        + urlEncode(request.username);
                userId = resolveProfileUserId(endpoint, pageUrl, null);
            }
            if (userId != null && !userId.isEmpty()) return userId;
        }

        try {
            String html = fetch(pageUrl);
            userId = firstMatch(html,
                    "StoriesPage_(\\d+)",
                    "\"owner\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"(\\d+)\"");
        } catch (IOException ignored) {
        }

        return userId;
    }

    private static String resolveReelsTrayUserId(String username, String pageUrl, String cookie) {
        if (cookie == null || cookie.isEmpty()) return null;
        try {
            String body = fetchApi("https://www.instagram.com/api/v1/feed/reels_tray/", pageUrl, cookie);
            JSONObject root = new JSONObject(stripJsonPrefix(body));
            JSONArray tray = root.optJSONArray("tray");
            if (tray == null) return null;

            for (int i = 0; i < tray.length(); i++) {
                JSONObject reel = tray.optJSONObject(i);
                if (reel == null) continue;
                JSONObject user = reel.optJSONObject("user");
                String reelUsername = user == null ? null : user.optString("username", null);
                if (reelUsername == null || !reelUsername.equalsIgnoreCase(username)) continue;

                String userId = reel.optString("id", null);
                if (userId == null || userId.isEmpty()) userId = user.optString("pk", null);
                if (userId == null || userId.isEmpty()) userId = user.optString("id", null);
                return userId == null || userId.isEmpty() ? null : userId;
            }
        } catch (IOException | JSONException e) {
        }
        return null;
    }

    private static String resolveMediaInfoUserId(String mediaId, String pageUrl, String cookie) {
        if (cookie == null || cookie.isEmpty()) return null;
        try {
            String endpoint = "https://www.instagram.com/api/v1/media/"
                    + urlEncode(mediaId)
                    + "/info/";
            String body = fetchApi(endpoint, pageUrl, cookie);
            JSONObject root = new JSONObject(stripJsonPrefix(body));
            JSONArray items = root.optJSONArray("items");
            JSONObject item = items == null || items.length() == 0 ? null : items.optJSONObject(0);
            JSONObject user = item == null ? null : item.optJSONObject("user");
            if (user == null) return null;

            String userId = user.optString("pk", null);
            if (userId == null || userId.isEmpty()) userId = user.optString("id", null);
            return userId == null || userId.isEmpty() ? null : userId;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static String resolveProfileUserId(String endpoint, String pageUrl, String cookie) {
        try {
            String body = fetchApi(endpoint, pageUrl, cookie);
            JSONObject root = new JSONObject(stripJsonPrefix(body));
            JSONObject data = root.optJSONObject("data");
            JSONObject user = data == null ? null : data.optJSONObject("user");
            if (user == null) return null;
            String userId = user.optString("id", null);
            if (userId == null || userId.isEmpty()) userId = user.optString("pk", null);
            return userId == null || userId.isEmpty() ? null : userId;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static List<MediaItem> parseMediaInfo(String json) throws JSONException {
        LinkedHashMap<String, MediaItem> results = new LinkedHashMap<>();
        JSONObject root = new JSONObject(stripJsonPrefix(json));
        JSONArray items = root.optJSONArray("items");
        if (items == null) {
            collectMediaObject(root, results);
        } else {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) collectMediaObject(item, results);
            }
        }
        return new ArrayList<>(results.values());
    }

    private static void collectMediaObject(JSONObject media, Map<String, MediaItem> out) {
        String inheritedAuthor = instagramAuthorName(media);
        String inheritedFileNameBase = instagramFileNameBase(media, null);
        JSONArray carousel = media.optJSONArray("carousel_media");
        if (carousel != null) {
            for (int i = 0; i < carousel.length(); i++) {
                JSONObject child = carousel.optJSONObject(i);
                if (child != null) {
                    collectSingleMedia(child, out, false, false, inheritedAuthor, inheritedFileNameBase);
                }
            }
            return;
        }
        collectSingleMedia(media, out, false, false, inheritedAuthor, inheritedFileNameBase);
    }

    private static void collectSingleMedia(JSONObject media, Map<String, MediaItem> out) {
        collectSingleMedia(media, out, false, false);
    }

    private static void collectSingleMedia(JSONObject media, Map<String, MediaItem> out, boolean storyItem, boolean sharedStory) {
        collectSingleMedia(media, out, storyItem, sharedStory, null, null);
    }

    private static void collectSingleMedia(
            JSONObject media,
            Map<String, MediaItem> out,
            boolean storyItem,
            boolean sharedStory,
            String inheritedAuthor,
            String inheritedFileNameBase) {
        String fileNameBase = instagramFileNameBase(media, inheritedAuthor);
        if (fileNameBase == null) fileNameBase = inheritedFileNameBase;

        JSONObject versions = media.optJSONObject("image_versions2");
        JSONArray imageCandidates = versions == null ? null : versions.optJSONArray("candidates");
        String image = bestImageUrl(imageCandidates);
        String thumbnail = thumbnailImageUrl(imageCandidates);
        String video = bestVideoUrl(media.optJSONArray("video_versions"));
        if (storyItem && storyShouldPreferImage(media) && image != null) {
            add(out, image, MediaItem.Kind.IMAGE, thumbnail == null ? image : thumbnail, fileNameBase, true, sharedStory);
            return;
        }

        if (video != null) {
            add(out, video, MediaItem.Kind.VIDEO, thumbnail == null ? image : thumbnail, fileNameBase, storyItem, sharedStory);
            return;
        }

        if (image != null) {
            add(out, image, MediaItem.Kind.IMAGE, thumbnail == null ? image : thumbnail, fileNameBase, storyItem, sharedStory);
        }
    }

    private static String instagramFileNameBase(JSONObject media, String inheritedAuthor) {
        String ownAuthor = instagramAuthorName(media);
        String author = ownAuthor == null ? cleanText(inheritedAuthor) : ownAuthor;
        String date = instagramTakenDate(media);
        if (author == null && date == null) return null;
        if (author == null) author = "instagram";
        if (date == null) return ownAuthor == null ? null : author;
        return author + " " + date;
    }

    private static String instagramAuthorName(JSONObject media) {
        if (media == null) return null;

        JSONObject user = media.optJSONObject("user");
        String userName = preferredInstagramUserName(user);
        if (userName != null) return userName;

        JSONObject owner = media.optJSONObject("owner");
        String ownerName = preferredInstagramUserName(owner);
        if (ownerName != null) return ownerName;

        JSONObject caption = media.optJSONObject("caption");
        JSONObject captionUser = caption == null ? null : caption.optJSONObject("user");
        String captionUserName = preferredInstagramUserName(captionUser);
        if (captionUserName != null) return captionUserName;

        String directFullName = cleanString(media, "full_name");
        if (directFullName != null) return directFullName;

        return cleanString(media, "username");
    }

    private static String preferredInstagramUserName(JSONObject user) {
        if (user == null) return null;
        String fullName = cleanString(user, "full_name");
        if (fullName != null) return fullName;
        String username = cleanString(user, "username");
        if (username != null) return username;
        return cleanString(user, "name");
    }

    private static String instagramTakenDate(JSONObject media) {
        Long seconds = instagramTakenAtSeconds(media);
        if (seconds == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(seconds * 1000L));
    }

    private static Long instagramTakenAtSeconds(JSONObject media) {
        if (media == null) return null;

        Long value = epochSeconds(media, "taken_at");
        if (value != null) return value;

        value = epochSeconds(media, "taken_at_timestamp");
        if (value != null) return value;

        value = epochSeconds(media, "created_at");
        if (value != null) return value;

        JSONObject caption = media.optJSONObject("caption");
        if (caption != null) {
            value = epochSeconds(caption, "created_at");
            if (value != null) return value;
        }

        return epochSeconds(media, "date");
    }

    private static Long epochSeconds(JSONObject object, String name) {
        Object value = object == null ? null : object.opt(name);
        if (value == null || value == JSONObject.NULL) return null;

        long raw;
        if (value instanceof Number) {
            raw = ((Number) value).longValue();
        } else {
            try {
                raw = Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (raw <= 0) return null;
        if (raw > 1000000000000000L) raw /= 1000000L;
        if (raw > 10000000000L) raw /= 1000L;
        return raw;
    }

    private static boolean storyShouldPreferImage(JSONObject media) {
        return media.optInt("original_media_type", 0) == 1
                || media.optBoolean("is_story_image_with_music", false);
    }

    private static String bestVideoUrl(JSONArray versions) {
        if (versions == null) return null;

        String best = null;
        long bestScore = -1;
        for (int i = 0; i < versions.length(); i++) {
            JSONObject candidate = versions.optJSONObject(i);
            if (candidate == null) continue;
            String url = candidate.optString("url", null);
            long score = (long) candidate.optInt("width", 0) * candidate.optInt("height", 0);
            score += (long) candidate.optInt("type", 0) * 100000000L;
            if (url != null && score > bestScore) {
                best = url;
                bestScore = score;
            }
        }
        return best;
    }

    private static String bestImageUrl(JSONArray candidates) {
        if (candidates == null) return null;

        String best = null;
        long bestScore = -1;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) continue;
            String url = candidate.optString("url", null);
            long score = (long) candidate.optInt("width", 0) * candidate.optInt("height", 0);
            if (url != null && score > bestScore) {
                best = url;
                bestScore = score;
            }
        }
        return best;
    }

    private static String thumbnailImageUrl(JSONArray candidates) {
        if (candidates == null) return null;

        String best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) continue;
            String url = candidate.optString("url", null);
            int width = candidate.optInt("width", 0);
            int height = candidate.optInt("height", 0);
            long distance = Math.abs((long) Math.max(width, height) - 480L);
            if (url != null && distance < bestDistance) {
                best = url;
                bestDistance = distance;
            }
        }
        return best;
    }

    static List<MediaItem> extractFromRenderedDump(String dump) {
        LinkedHashMap<String, MediaItem> results = new LinkedHashMap<>();
        if (dump == null) return new ArrayList<>();

        String[] lines = dump.split("\\n");
        for (String line : lines) {
            int separator = line.indexOf('\t');
            if (separator < 0) continue;

            String kind = line.substring(0, separator);
            String url = line.substring(separator + 1);
            String thumbnailUrl = null;
            int secondSeparator = line.indexOf('\t', separator + 1);
            if (secondSeparator > 0) {
                url = line.substring(separator + 1, secondSeparator);
                thumbnailUrl = line.substring(secondSeparator + 1);
            }

            if ("video".equals(kind)) {
                add(results, url, MediaItem.Kind.VIDEO, thumbnailUrl);
            } else if ("image".equals(kind)) {
                add(results, url, MediaItem.Kind.IMAGE, thumbnailUrl);
            } else {
                add(results, url, kindFromUrl(url), thumbnailUrl);
            }
        }

        return new ArrayList<>(results.values());
    }

    static String decodeJavascriptString(String encoded) {
        if (encoded == null || encoded.equals("null")) return "";

        String value = encoded;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }

            char n = value.charAt(++i);
            switch (n) {
                case 'n':
                    out.append('\n');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case '"':
                case '\\':
                case '/':
                    out.append(n);
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try {
                            out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append("\\u");
                        }
                    } else {
                        out.append("\\u");
                    }
                    break;
                default:
                    out.append(n);
                    break;
            }
        }
        return out.toString();
    }

    private static void collectMetaMedia(String html, Map<String, MediaItem> out) {
        Matcher matcher = META_TAG.matcher(html);
        while (matcher.find()) {
            String property = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
            String content = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (property == null || content == null) continue;

            String key = property.toLowerCase(Locale.US);
            String url = htmlDecode(content);
            if (key.equals("og:video") || key.equals("og:video:secure_url")) {
                add(out, url, MediaItem.Kind.VIDEO);
            } else if (key.equals("og:image") || key.equals("twitter:image")) {
                add(out, url, MediaItem.Kind.IMAGE);
            }
        }
    }

    private static void collectJsonMedia(String html, Map<String, MediaItem> out) {
        collectMatches(html, ESCAPED_VIDEO_URL, MediaItem.Kind.VIDEO, out);
        collectMatches(html, ESCAPED_IMAGE_URL, MediaItem.Kind.IMAGE, out);
    }

    private static void collectLooseMedia(String text, Map<String, MediaItem> out) {
        Matcher matcher = LOOSE_MEDIA_URL.matcher(text);
        while (matcher.find()) {
            String url = unescapeJsonUrl(matcher.group());
            add(out, url, kindFromUrl(url));
        }
    }

    private static void collectMatches(String html, Pattern pattern, MediaItem.Kind kind, Map<String, MediaItem> out) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String url = unescapeJsonUrl(matcher.group(1));
            if (kind == MediaItem.Kind.IMAGE && !url.contains("scontent")) continue;
            add(out, url, kind);
        }
    }

    private static void add(Map<String, MediaItem> out, String url, MediaItem.Kind kind) {
        add(out, url, kind, url);
    }

    private static void add(Map<String, MediaItem> out, String url, MediaItem.Kind kind, String thumbnailUrl) {
        add(out, url, kind, thumbnailUrl, false, false);
    }

    private static void add(Map<String, MediaItem> out, String url, MediaItem.Kind kind, String thumbnailUrl, boolean storyItem, boolean sharedStory) {
        add(out, url, kind, thumbnailUrl, null, storyItem, sharedStory);
    }

    private static void add(
            Map<String, MediaItem> out,
            String url,
            MediaItem.Kind kind,
            String thumbnailUrl,
            String fileNameBase,
            boolean storyItem,
            boolean sharedStory) {
        url = cleanupMediaUrl(url);
        if (!looksLikeMediaUrl(url)) return;
        thumbnailUrl = cleanupMediaUrl(thumbnailUrl);
        if (!looksLikeMediaUrl(thumbnailUrl)) thumbnailUrl = url;

        String dedupeKey = url.replaceAll("[?&](_nc_ht|_nc_cat|ccb|oh|oe|dl|stp|efg|_nc_sid|_nc_ohc)=[^&]+", "");
        out.putIfAbsent(dedupeKey, new MediaItem(
                url,
                kind,
                thumbnailUrl,
                "instagram",
                "https://www.instagram.com/",
                fileNameBase,
                storyItem,
                sharedStory));
    }

    private static boolean looksLoginOnly(String html) {
        String lower = html.toLowerCase(Locale.US);
        return lower.contains("login_required")
                || lower.contains("accounts/login")
                || lower.contains("polarispostsecretreelroot");
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        String cookie = instagramCookie();
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            throw new IOException("Instagram returned HTTP " + code);
        }
        String body = readFully(stream);
        if (code >= 400) {
            throw new IOException("Instagram returned HTTP " + code);
        }
        return body;
    }

    private static String fetchApi(String url, String referer, String cookie) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("X-IG-App-ID", "1217981644879628");
        connection.setRequestProperty("X-ASBD-ID", "129477");
        connection.setRequestProperty("X-IG-WWW-Claim", "0");
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
            String csrf = cookieValue(cookie, "csrftoken");
            if (csrf != null) {
                connection.setRequestProperty("X-CSRFToken", csrf);
            }
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            throw new IOException("Instagram API returned HTTP " + code);
        }
        String body = readFully(stream);
        if (code >= 400) {
            throw new IOException("Instagram API returned HTTP " + code + ": " + body);
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

    private static String unescapeJsonUrl(String value) {
        return htmlDecode(value)
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003D", "=")
                .replace("\\u0025", "%");
    }

    private static String cleanupMediaUrl(String url) {
        if (url == null) return null;
        String cleaned = unescapeJsonUrl(url).trim();
        cleaned = cleaned.replace("\\", "");
        int fragment = cleaned.indexOf('#');
        if (fragment >= 0) cleaned = cleaned.substring(0, fragment);
        return cleaned;
    }

    private static String shortcodeFromUrl(String url) {
        if (url == null) return null;
        Matcher matcher = SHORTCODE.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String mediaIdFromShortcode(String shortcode) {
        if (shortcode == null || shortcode.isEmpty()) return null;

        BigInteger value = BigInteger.ZERO;
        int length = Math.min(shortcode.length(), 11);
        for (int i = 0; i < length; i++) {
            int idx = SHORTCODE_ALPHABET.indexOf(shortcode.charAt(i));
            if (idx < 0) return null;
            value = value.shiftLeft(6).add(BigInteger.valueOf(idx));
        }
        return value.toString();
    }

    private static String cookieValue(String cookie, String name) {
        if (cookie == null) return null;
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static String firstMatch(String text, String... patterns) {
        if (text == null) return null;
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static String stripJsonPrefix(String value) {
        if (value != null && value.startsWith("for (;;);")) {
            return value.substring("for (;;);".length());
        }
        return value;
    }

    private static boolean looksLikeMediaUrl(String url) {
        if (url == null || !url.startsWith("https://")) return false;

        String lower = url.toLowerCase(Locale.US);
        if (!lower.contains("cdninstagram.com") && !lower.contains("fbcdn.net")) return false;
        if (lower.contains("static.cdninstagram.com")) return false;
        if (lower.contains("/rsrc.php/")) return false;
        if (lower.contains("/images/") && !lower.contains("scontent")) return false;

        return lower.contains("scontent")
                || lower.contains("/v/t51.")
                || lower.contains("/v/t50.")
                || lower.matches(".*\\.(jpg|jpeg|png|webp|mp4)(\\?|$).*");
    }

    private static MediaItem.Kind kindFromUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.US);
        if (lower.contains(".mp4") || lower.contains("/v/t50.") || lower.contains("video")) {
            return MediaItem.Kind.VIDEO;
        }
        return MediaItem.Kind.IMAGE;
    }

    private static String cleanString(JSONObject object, String name) {
        if (object == null) return null;
        return cleanText(object.optString(name, null));
    }

    private static String cleanText(String value) {
        if (value == null) return null;
        String cleaned = htmlDecode(value).trim();
        if (cleaned.isEmpty() || "null".equals(cleaned.toLowerCase(Locale.US))) return null;
        return cleaned;
    }

    private static String htmlDecode(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static final class StoryRequest {
        final boolean highlight;
        final String reelId;
        final String username;
        final String mediaId;

        StoryRequest(boolean highlight, String reelId, String username, String mediaId) {
            this.highlight = highlight;
            this.reelId = reelId;
            this.username = username;
            this.mediaId = mediaId;
        }
    }
}
