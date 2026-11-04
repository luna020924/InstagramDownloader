package com.example.instasharedownloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class ThumbnailLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };

    private ThumbnailLoader() { }

    static Bitmap load(MediaItem item) throws IOException {
        Bitmap cached = CACHE.get(item.thumbnailUrl);
        if (cached != null) return cached;
        HttpURLConnection connection = (HttpURLConnection) new URL(item.thumbnailUrl).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36");
        connection.setRequestProperty("Referer", item.referer);
        try {
            if (connection.getResponseCode() != 200) return null;
            byte[] bytes;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > 16 * 1024 * 1024) throw new IOException("Preview is too large.");
                    output.write(buffer, 0, read);
                }
                bytes = output.toByteArray();
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 720) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) return null;
            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max > 360) {
                float scale = 360f / max;
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                if (resized != bitmap) bitmap.recycle();
                bitmap = resized;
            }
            CACHE.put(item.thumbnailUrl, bitmap);
            return bitmap;
        } finally { connection.disconnect(); }
    }
}
