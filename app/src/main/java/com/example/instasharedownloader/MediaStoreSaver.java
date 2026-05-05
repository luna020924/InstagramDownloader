package com.example.instasharedownloader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class MediaStoreSaver {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/143.0.0.0 Mobile Safari/537.36";

    interface Progress {
        void onProgress(String message);
    }

    private MediaStoreSaver() {
    }

    static String download(Context context, MediaItem item, int index, int total, Progress progress) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(item.url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://www.instagram.com/");

        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("Media returned HTTP " + code);
        }

        String contentType = connection.getContentType();
        String extension = extensionFor(item, contentType, item.url);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayName = String.format(Locale.US, "instagram_%s_%02d%s", stamp, index, extension);

        ContentResolver resolver = context.getContentResolver();
        Uri collection = collectionFor(item);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(item, contentType, extension));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathFor(item));
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        Uri destination = resolver.insert(collection, values);
        if (destination == null) {
            throw new IOException("Could not create MediaStore item.");
        }

        try (InputStream input = connection.getInputStream();
             OutputStream output = resolver.openOutputStream(destination)) {
            if (output == null) throw new IOException("Could not open destination stream.");
            byte[] buffer = new byte[64 * 1024];
            long copied = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                copied += read;
                if (copied > 0 && copied % (1024 * 1024) < buffer.length) {
                    progress.onProgress(index + "/" + total + " 저장 중: " + copied / (1024 * 1024) + " MB");
                }
            }
        } catch (IOException e) {
            resolver.delete(destination, null, null);
            throw e;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(destination, done, null, null);
        }

        return displayName;
    }

    private static Uri collectionFor(MediaItem item) {
        if (item.kind == MediaItem.Kind.VIDEO) {
            return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    private static String relativePathFor(MediaItem item) {
        if (item.kind == MediaItem.Kind.VIDEO) {
            return Environment.DIRECTORY_MOVIES + "/InstaShareDownloader";
        }
        return Environment.DIRECTORY_PICTURES + "/InstaShareDownloader";
    }

    private static String extensionFor(MediaItem item, String contentType, String url) {
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (lowerType.contains("mp4")) return ".mp4";
        if (lowerType.contains("webp")) return ".webp";
        if (lowerType.contains("png")) return ".png";
        if (lowerType.contains("jpeg") || lowerType.contains("jpg")) return ".jpg";

        String lowerUrl = url.toLowerCase(Locale.US);
        int query = lowerUrl.indexOf('?');
        if (query >= 0) lowerUrl = lowerUrl.substring(0, query);
        if (lowerUrl.endsWith(".mp4")) return ".mp4";
        if (lowerUrl.endsWith(".webp")) return ".webp";
        if (lowerUrl.endsWith(".png")) return ".png";
        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) return ".jpg";

        return item.kind == MediaItem.Kind.VIDEO ? ".mp4" : ".jpg";
    }

    private static String mimeFor(MediaItem item, String contentType, String extension) {
        if (contentType != null && contentType.contains("/")) {
            return contentType.split(";")[0].trim();
        }
        switch (extension) {
            case ".mp4":
                return "video/mp4";
            case ".webp":
                return "image/webp";
            case ".png":
                return "image/png";
            default:
                return item.kind == MediaItem.Kind.VIDEO ? "video/mp4" : "image/jpeg";
        }
    }
}
