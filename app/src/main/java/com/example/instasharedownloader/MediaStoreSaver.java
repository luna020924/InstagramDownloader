package com.example.instasharedownloader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

final class MediaStoreSaver {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/143.0.0.0 Mobile Safari/537.36";

    interface Progress {
        void onProgress(String message);
    }

    private MediaStoreSaver() {
    }

    static void downloadAll(Context context, List<MediaItem> items, Progress progress) throws IOException {
        if (items.size() < 2 || items.stream().anyMatch(item -> item.kind != MediaItem.Kind.IMAGE)) {
            for (int i = 0; i < items.size(); i++) {
                progress.onProgress("다운로드 중... (" + i + "/" + items.size() + ")");
                download(context, items.get(i), i + 1, items.size(), progress);
            }
            return;
        }
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(3, items.size()));
        AtomicInteger completed = new AtomicInteger();
        List<Future<?>> downloads = new ArrayList<>();
        try {
            for (int i = 0; i < items.size(); i++) {
                final int index = i;
                downloads.add(workers.submit(() -> {
                    download(context, items.get(index), index + 1, items.size(), progress);
                    progress.onProgress("다운로드 중... (" + completed.incrementAndGet() + "/" + items.size() + ")");
                    return null;
                }));
            }
            IOException failure = null;
            for (Future<?> download : downloads) {
                try { download.get(); }
                catch (ExecutionException error) {
                    if (failure == null) failure = error.getCause() instanceof IOException
                            ? (IOException) error.getCause() : new IOException("Media save failed.", error.getCause());
                }
            }
            if (failure != null) throw failure;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted.", error);
        } finally { workers.shutdownNow(); }
    }

    static String download(Context context, MediaItem item, int index, int total, Progress progress) throws IOException {
        long savedAtMillis = System.currentTimeMillis();
        long savedAtSeconds = savedAtMillis / 1000L;
        HttpURLConnection connection = null;
        File tempFile = null;
        Uri destination = null;
        InputStream source = null;
        ContentResolver resolver = context.getContentResolver();
        try {
            connection = (HttpURLConnection) new URL(item.url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", item.referer);

            int code = connection.getResponseCode();
            if (code >= 400) {
                throw new IOException("Media returned HTTP " + code);
            }

            String contentType = connection.getContentType();
            String extension = extensionFor(item, contentType, item.url);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String displayName = displayNameFor(item, stamp, index, total, extension);

            if (item.kind == MediaItem.Kind.VIDEO && ".mp4".equals(extension)) {
                tempFile = File.createTempFile("insta_share_video_", ".mp4", context.getCacheDir());
                try (InputStream input = connection.getInputStream();
                     OutputStream output = new FileOutputStream(tempFile)) {
                    copy(input, output);
                }
                patchMp4DownloadTimestamp(tempFile, savedAtMillis);
                source = new FileInputStream(tempFile);
            } else {
                source = connection.getInputStream();
            }

            Uri collection = collectionFor(item);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(item, contentType, extension));
            putDownloadTimestamps(values, item, savedAtMillis, savedAtSeconds, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathFor(item));
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }

            destination = resolver.insert(collection, values);
            if (destination == null) {
                throw new IOException("Could not create MediaStore item.");
            }

            try (InputStream input = source;
                 OutputStream output = resolver.openOutputStream(destination)) {
                source = null;
                if (output == null) throw new IOException("Could not open destination stream.");
                copy(input, output);
            } catch (IOException e) {
                resolver.delete(destination, null, null);
                throw e;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(destination, done, null, null);
            }
            updateDownloadTimestampBestEffort(resolver, destination, item, savedAtMillis, savedAtSeconds);

            return displayName;
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) connection.disconnect();
            if (tempFile != null && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static String displayNameFor(MediaItem item, String stamp, int index, int total, String extension) {
        String customBase = sanitizeFileNameBase(item.fileNameBase);
        if (customBase != null) {
            String suffix = total > 1 ? String.format(Locale.US, "_%02d", index) : "";
            return customBase + suffix + extension;
        }
        return String.format(Locale.US, "%s_%s_%02d%s", item.filePrefix, stamp, index, extension);
    }

    private static String sanitizeFileNameBase(String raw) {
        if (raw == null) return null;
        String cleaned = raw
                .replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() > 90 ? cleaned.substring(0, 90).trim() : cleaned;
    }

    private static void putDownloadTimestamps(
            ContentValues values,
            MediaItem item,
            long millis,
            long seconds,
            boolean includeDateAdded) {
        if (includeDateAdded) {
            values.put(MediaStore.MediaColumns.DATE_ADDED, seconds);
        }
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, seconds);
        if (item.kind == MediaItem.Kind.VIDEO) {
            values.put(MediaStore.Video.Media.DATE_TAKEN, millis);
        } else {
            values.put(MediaStore.Images.Media.DATE_TAKEN, millis);
        }
    }

    private static void updateDownloadTimestampBestEffort(
            ContentResolver resolver,
            Uri destination,
            MediaItem item,
            long millis,
            long seconds) {
        ContentValues timestamps = new ContentValues();
        putDownloadTimestamps(timestamps, item, millis, seconds, false);
        try {
            resolver.update(destination, timestamps, null, null);
        } catch (RuntimeException ignored) {
            // Some gallery providers reject date updates; the inserted values above still cover normal MediaStore.
        }
    }

    private static void patchMp4DownloadTimestamp(File file, long millis) {
        long quickTimeSeconds = millis / 1000L + 2082844800L;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            patchMp4Boxes(raf, 0, raf.length(), quickTimeSeconds);
        } catch (IOException ignored) {
        }
    }

    private static void patchMp4Boxes(RandomAccessFile raf, long start, long end, long quickTimeSeconds) throws IOException {
        long position = start;
        while (position + 8 <= end) {
            raf.seek(position);
            long size = readUInt32(raf);
            String type = readType(raf);
            long headerSize = 8;

            if (size == 1) {
                if (position + 16 > end) return;
                size = readUInt64(raf);
                headerSize = 16;
            } else if (size == 0) {
                size = end - position;
            }

            if (size < headerSize || position + size > end) return;

            long payloadStart = position + headerSize;
            long boxEnd = position + size;
            if ("mvhd".equals(type) || "tkhd".equals(type) || "mdhd".equals(type)) {
                patchMp4FullBoxTimestamp(raf, payloadStart, boxEnd, quickTimeSeconds);
            } else if (isMp4Container(type)) {
                patchMp4Boxes(raf, payloadStart, boxEnd, quickTimeSeconds);
            }
            position += size;
        }
    }

    private static void patchMp4FullBoxTimestamp(RandomAccessFile raf, long payloadStart, long boxEnd, long quickTimeSeconds) throws IOException {
        if (payloadStart + 12 > boxEnd) return;
        raf.seek(payloadStart);
        int version = raf.readUnsignedByte();
        if (version == 1) {
            if (payloadStart + 20 > boxEnd) return;
            raf.seek(payloadStart + 4);
            writeUInt64(raf, quickTimeSeconds);
            writeUInt64(raf, quickTimeSeconds);
        } else {
            if (payloadStart + 12 > boxEnd) return;
            raf.seek(payloadStart + 4);
            writeUInt32(raf, quickTimeSeconds);
            writeUInt32(raf, quickTimeSeconds);
        }
    }

    private static boolean isMp4Container(String type) {
        return "moov".equals(type) || "trak".equals(type) || "mdia".equals(type);
    }

    private static long readUInt32(RandomAccessFile raf) throws IOException {
        return ((long) raf.readUnsignedByte() << 24)
                | ((long) raf.readUnsignedByte() << 16)
                | ((long) raf.readUnsignedByte() << 8)
                | raf.readUnsignedByte();
    }

    private static long readUInt64(RandomAccessFile raf) throws IOException {
        long high = readUInt32(raf);
        long low = readUInt32(raf);
        return (high << 32) | low;
    }

    private static String readType(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[4];
        raf.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void writeUInt32(RandomAccessFile raf, long value) throws IOException {
        raf.write((int) ((value >> 24) & 0xff));
        raf.write((int) ((value >> 16) & 0xff));
        raf.write((int) ((value >> 8) & 0xff));
        raf.write((int) (value & 0xff));
    }

    private static void writeUInt64(RandomAccessFile raf, long value) throws IOException {
        writeUInt32(raf, (value >> 32) & 0xffffffffL);
        writeUInt32(raf, value & 0xffffffffL);
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
