package com.example.instasharedownloader;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class OverlayRollbackDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void realXShareKeepsThePickerDuringASwipeAndClosesAfterSaving() throws Exception {
        Assume.assumeTrue("Live X UI tests are opt-in", "true".equals(
                InstrumentationRegistry.getArguments().getString("liveTwitter")) && "true".equals(
                InstrumentationRegistry.getArguments().getString("downloadMedia")));
        Context context = instrumentation.getTargetContext();
        assertFalse("Unlock the phone before running the share UI test",
                context.getSystemService(KeyguardManager.class).isDeviceLocked());
        assertTrue("Restored overlay permission is missing", Settings.canDrawOverlays(context));
        String post = "https://x.com/NASA/status/2041557036274475228";
        List<MediaItem> items = TwitterExtractor.extract(post);
        assertEquals(4, items.size());
        String prefix = items.get(0).fileNameBase;
        Set<Long> before = photoIds(context, prefix);
        try {
            context.startActivity(new Intent(context, ShareActivity.class).setAction(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, post).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            assertNotNull("Original selection overlay did not appear", waitForText("선택 다운로드", 25000));
            Thread.sleep(1000);
            AccessibilityNodeInfo scroll = findScroll(instrumentation.getUiAutomation().getRootInActiveWindow());
            assertNotNull("Original grid scroll view is missing", scroll);
            Rect bounds = new Rect();
            scroll.getBoundsInScreen(bounds);
            swipe(bounds.centerX(), bounds.bottom - 40, bounds.centerY());
            assertNotNull("A grid swipe dismissed the overlay", findText("선택 다운로드"));
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            File capture = new File(context.getCacheDir(), "rollback-twitter-selection.png");
            try (FileOutputStream output = new FileOutputStream(capture)) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally { screenshot.recycle(); }

            long started = SystemClock.elapsedRealtime();
            assertTrue(findText("선택 다운로드").performAction(AccessibilityNodeInfo.ACTION_CLICK));
            long deadline = started + 60000;
            Set<Long> created = new HashSet<>();
            while (SystemClock.elapsedRealtime() < deadline) {
                created = photoIds(context, prefix);
                created.removeAll(before);
                if (created.size() == 4 && !hasDownloadWindow()) break;
                Thread.sleep(100);
            }
            assertEquals("The share action did not save four originals", 4, created.size());
            assertFalse("Successful saving left a confirmation window open", hasDownloadWindow());
            Bundle result = new Bundle();
            result.putString("stream", "X share: saved=4 swipe_preserved=true auto_closed=true click_to_close_ms="
                    + (SystemClock.elapsedRealtime() - started) + "\n");
            instrumentation.sendStatus(0, result);
        } finally {
            context.stopService(new Intent(context, OverlayDownloadService.class));
            Set<Long> created = photoIds(context, prefix);
            created.removeAll(before);
            // Delete only the exact new IDs created for this public fixture during this test.
            for (Long id : created) context.getContentResolver().delete(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null);
        }
    }

    private Set<Long> photoIds(Context context, String prefix) {
        Set<Long> ids = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{"_id"}, "owner_package_name=? AND _display_name LIKE ?",
                new String[]{context.getPackageName(), prefix + "%"}, null)) {
            if (cursor != null) while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        return ids;
    }

    private boolean hasDownloadWindow() {
        return findText("다운로드 중") != null || findText("다운로드 완료") != null
                || findText("다운로드 실패") != null || findText("다운로드할 미디어 선택") != null;
    }

    private AccessibilityNodeInfo findText(String text) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null || !instrumentation.getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
            if (text.equals(String.valueOf(node.getText()))) return node;
        return null;
    }

    private AccessibilityNodeInfo waitForText(String text, long timeout) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        AccessibilityNodeInfo node;
        do {
            node = findText(text);
            if (node != null) return node;
            Thread.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        return null;
    }

    private AccessibilityNodeInfo findScroll(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("android.widget.ScrollView".equals(String.valueOf(node.getClassName()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findScroll(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    private void swipe(float x, float fromY, float toY) throws InterruptedException {
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 12; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    x, fromY + (toY - fromY) * i / 12f, 0);
            instrumentation.getUiAutomation().injectInputEvent(event, true);
            event.recycle();
            Thread.sleep(16);
        }
    }
}
