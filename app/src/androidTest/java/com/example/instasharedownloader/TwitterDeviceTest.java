package com.example.instasharedownloader;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

/** Opt-in live measurements: no account data or media addresses are printed. */
@RunWith(AndroidJUnit4.class)
public final class TwitterDeviceTest {
    @Test public void previewsDecodeAndOriginalPhotosSaveWithMeasuredTimings() throws Exception {
        Assume.assumeTrue("Live X tests are opt-in", "true".equals(
                InstrumentationRegistry.getArguments().getString("liveTwitter")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long started = SystemClock.elapsedRealtime();
        List<MediaItem> items = TwitterExtractor.extract("https://x.com/NASA/status/2041557036274475228");
        report("metadata_ms=" + (SystemClock.elapsedRealtime() - started) + " count=" + items.size());
        assertEquals("The public fixture should contain four photos", 4, items.size());

        MediaItem first = items.get(0);
        String previousPreview = first.thumbnailUrl.substring(0, first.thumbnailUrl.indexOf('?'));
        long previousBytes = measureResponse(previousPreview, "previous_preview");
        long smallBytes = measureResponse(first.thumbnailUrl, "small_preview");
        assertTrue("Small preview did not reduce transferred bytes", smallBytes < previousBytes);
        started = SystemClock.elapsedRealtime();
        for (MediaItem item : items) {
            Bitmap bitmap = ThumbnailLoader.load(item);
            assertNotNull("An X preview did not decode", bitmap);
            assertTrue(Math.max(bitmap.getWidth(), bitmap.getHeight()) <= 360);
        }
        report("all_previews_ms=" + (SystemClock.elapsedRealtime() - started));

        if (!"true".equals(InstrumentationRegistry.getArguments().getString("downloadMedia"))) return;
        List<Long> serialBytes = verifyBatch(context, items, false);
        List<Long> concurrentBytes = verifyBatch(context, items, true);
        assertEquals("Concurrent downloads changed the originals", serialBytes, concurrentBytes);
    }

    private List<Long> verifyBatch(Context context, List<MediaItem> originals, boolean concurrent) throws Exception {
        String prefix = "twitter_speed_test_" + UUID.randomUUID();
        List<MediaItem> testItems = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            MediaItem item = originals.get(i);
            testItems.add(new MediaItem(item.url, item.kind, item.thumbnailUrl, item.filePrefix,
                    item.referer, prefix + "_" + i));
        }
        List<Long> sizes = new ArrayList<>();
        long started = SystemClock.elapsedRealtime();
        try {
            if (concurrent) MediaStoreSaver.downloadAll(context, testItems, message -> { });
            else for (int i = 0; i < testItems.size(); i++)
                MediaStoreSaver.download(context, testItems.get(i), i + 1, testItems.size(), message -> { });
            report((concurrent ? "concurrent" : "serial") + "_save_ms=" + (SystemClock.elapsedRealtime() - started));
            for (int i = 0; i < testItems.size(); i++) {
                String name = prefix + "_" + i + String.format(java.util.Locale.US, "_%02d.jpg", i + 1);
                try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{"_id", "_size", "is_pending"}, "_display_name=?", new String[]{name}, null)) {
                    assertNotNull(cursor);
                    assertTrue("Saved original is missing", cursor.moveToFirst());
                    sizes.add(cursor.getLong(1));
                    assertTrue("Saved original is empty", cursor.getLong(1) > 0);
                    assertEquals(0, cursor.getInt(2));
                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(input, null, bounds);
                    }
                    assertTrue("Original did not decode", bounds.outWidth > 0 && bounds.outHeight > 0);
                }
            }
            return sizes;
        } finally {
            // This unique prefix belongs only to files created by this test.
            context.getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "_display_name LIKE ?", new String[]{prefix + "%"});
        }
    }

    private long measureResponse(String url, String label) throws Exception {
        long started = SystemClock.elapsedRealtime();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Referer", "https://x.com/");
        long bytes = 0;
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) bytes += read;
        } finally { connection.disconnect(); }
        report(label + "_ms=" + (SystemClock.elapsedRealtime() - started) + " bytes=" + bytes);
        return bytes;
    }

    private void report(String text) {
        Bundle result = new Bundle();
        result.putString("stream", "X verification: " + text + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
    }
}
