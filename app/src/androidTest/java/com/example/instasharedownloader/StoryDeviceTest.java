package com.example.instasharedownloader;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.CookieManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/** Opt-in live test. Session values and media URLs never leave the app process or test output. */
@RunWith(AndroidJUnit4.class)
public final class StoryDeviceTest {
    @Test
    public void canResolveAnAvailableStoryWithStoredSession() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Live Instagram test is opt-in", "true".equals(args.getString("liveInstagram")));
        String cookie = CookieManager.getInstance().getCookie("https://www.instagram.com/");
        assertNotNull("Instagram session is missing", cookie);
        assertFalse("Instagram session is missing", !cookie.contains("sessionid="));

        String storyUrl = args.getString("storyUrl");
        if (storyUrl == null) {
            JSONObject tray = fetchJson("https://www.instagram.com/api/v1/feed/reels_tray/", cookie);
            JSONArray reels = tray.optJSONArray("tray");
            assertNotNull("Story tray response has no tray array", reels);
            for (int i = 0; i < reels.length(); i++) {
                JSONObject reel = reels.optJSONObject(i);
                JSONObject user = reel == null ? null : reel.optJSONObject("user");
                if (user == null || user.optBoolean("is_private", false)) continue;
                String username = user.optString("username", "");
                if (!username.isEmpty() && reel.optLong("latest_reel_media", 0) > 0) {
                    storyUrl = "https://www.instagram.com/stories/" + username + "/";
                    break;
                }
            }
            assertNotNull("No active public story in the current tray", storyUrl);
        }
        InstagramExtractor.updateSessionCookie(cookie);
        List<MediaItem> items;
        try {
            items = InstagramExtractor.extract(storyUrl);
        } catch (Exception error) {
            throw new AssertionError("Story extraction failed: " + safeError(error));
        }
        assertFalse("Story extraction returned an empty list", items.isEmpty());
        Bundle summary = new Bundle();
        summary.putString("stream", "Live story extraction: count=" + items.size()
                + " images=" + items.stream().filter(item -> item.kind == MediaItem.Kind.IMAGE).count()
                + " videos=" + items.stream().filter(item -> item.kind == MediaItem.Kind.VIDEO).count() + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, summary);
    }

    @Test
    public void canSaveImageAndVideoFromExactSharedStoryLinks() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Media save test is opt-in", "true".equals(args.getString("downloadMedia")));
        String cookie = CookieManager.getInstance().getCookie("https://www.instagram.com/");
        assertNotNull("Instagram session is missing", cookie);
        InstagramExtractor.updateSessionCookie(cookie);
        JSONArray tray = fetchJson("https://www.instagram.com/api/v1/feed/reels_tray/", cookie)
                .getJSONArray("tray");
        boolean imageSaved = false;
        boolean videoSaved = false;
        int checked = 0;
        for (int i = 0; i < tray.length() && checked < 8 && !(imageSaved && videoSaved); i++) {
            JSONObject entry = tray.optJSONObject(i);
            JSONObject user = entry == null ? null : entry.optJSONObject("user");
            if (user == null || user.optBoolean("is_private", false)
                    || entry.optLong("latest_reel_media", 0) <= 0) continue;
            String username = user.optString("username", "");
            String userId = user.optString("pk", user.optString("id", ""));
            if (username.isEmpty() || userId.isEmpty()) continue;
            checked++;
            JSONObject response = fetchJson("https://www.instagram.com/api/v1/feed/user/" + userId + "/story/", cookie);
            JSONObject reel = response.optJSONObject("reel");
            JSONArray items = reel == null ? null : reel.optJSONArray("items");
            if (items == null) continue;
            for (int j = 0; j < items.length() && !(imageSaved && videoSaved); j++) {
                JSONObject item = items.getJSONObject(j);
                String mediaId = item.optString("pk", item.optString("id", "")).split("_")[0];
                List<MediaItem> parsed = InstagramExtractor.parseStoryMedia(
                        new JSONObject().put("reel", new JSONObject().put("items", new JSONArray().put(item))).toString(),
                        mediaId);
                if (parsed.isEmpty()) continue;
                MediaItem.Kind kind = parsed.get(0).kind;
                if (kind == MediaItem.Kind.IMAGE ? imageSaved : videoSaved) continue;
                List<MediaItem> resolved;
                try {
                    resolved = InstagramExtractor.extract("https://www.instagram.com/stories/"
                            + username + "/" + mediaId + "/");
                } catch (Exception error) {
                    throw new AssertionError("Exact shared story failed: " + safeError(error));
                }
                assertTrue("Exact shared story was not identified", InstagramExtractor.hasSharedStory(resolved));
                List<MediaItem> selected = InstagramExtractor.sharedStoryItemsOnly(resolved);
                assertEquals("Shared story should select exactly one media item", 1, selected.size());
                assertEquals(kind, selected.get(0).kind);
                assertTrue("Selected media did not match the shared story",
                        new java.net.URL(parsed.get(0).url).getPath().equals(
                                new java.net.URL(selected.get(0).url).getPath()));
                verifySave(selected.get(0));
                if (kind == MediaItem.Kind.IMAGE) imageSaved = true; else videoSaved = true;
            }
        }
        assertTrue("No image story was available to verify", imageSaved);
        assertTrue("No video story was available to verify", videoSaved);
    }

    private void verifySave(MediaItem item) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ContentResolver resolver = context.getContentResolver();
        MediaItem testItem = new MediaItem(item.url, item.kind, item.thumbnailUrl,
                item.filePrefix, item.referer, "story_fix_test_" + UUID.randomUUID(), true, true);
        String displayName;
        try {
            displayName = MediaStoreSaver.download(context, testItem, 1, 1, message -> { });
        } catch (Exception error) {
            throw new AssertionError("Media save failed: " + safeError(error));
        }
        Uri collection = item.kind == MediaItem.Kind.IMAGE
                ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri saved;
        long bytes;
        try (Cursor cursor = resolver.query(collection, new String[]{"_id", "_size", "is_pending"},
                "_display_name=?", new String[]{displayName}, null)) {
            assertNotNull(cursor);
            assertTrue("Saved media was not published", cursor.moveToFirst());
            saved = ContentUris.withAppendedId(collection, cursor.getLong(0));
            bytes = cursor.getLong(1);
            assertTrue("Saved media was empty", bytes > 0);
            assertEquals("Saved media remained pending", 0, cursor.getInt(2));
        }
        try {
            if (item.kind == MediaItem.Kind.IMAGE) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = resolver.openInputStream(saved)) {
                    BitmapFactory.decodeStream(input, null, options);
                }
                assertTrue("Image did not decode", options.outWidth > 0 && options.outHeight > 0);
            } else {
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(context, saved);
                    String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    assertNotNull("Video could not be read", duration);
                    assertTrue("Video had no duration", Long.parseLong(duration) > 0);
                } finally {
                    metadata.release();
                }
            }
            Bundle result = new Bundle();
            result.putString("stream", "Verified story save: kind=" + item.kind + " bytes=" + bytes + "\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
        } finally {
            // The uniquely named test media was created above; existing user downloads are never touched.
            resolver.delete(saved, null, null);
        }
    }

    private JSONObject fetchJson(String endpoint, String cookie) throws Exception {
        try {
            return new JSONObject(InstagramStoryApi.fetch(endpoint, "https://www.instagram.com/", cookie));
        } catch (Exception error) {
            throw new AssertionError("Story API probe failed: " + safeError(error));
        }
    }

    private static String safeError(Throwable error) {
        String message = error.getMessage();
        java.util.regex.Matcher status = java.util.regex.Pattern.compile("HTTP (\\d{3})")
                .matcher(message == null ? "" : message);
        return error.getClass().getSimpleName() + (status.find() ? " HTTP " + status.group(1) : "");
    }

}
