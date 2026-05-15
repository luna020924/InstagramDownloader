package com.example.instasharedownloader;

import java.io.IOException;
import java.util.List;

final class MediaExtractor {
    private MediaExtractor() {
    }

    static String extractSupportedUrl(String input) {
        String instagram = InstagramExtractor.extractInstagramUrl(input);
        if (instagram != null) return instagram;
        return TwitterExtractor.extractTwitterUrl(input);
    }

    static List<MediaItem> extract(String sharedUrl) throws IOException {
        if (TwitterExtractor.isTwitterUrl(sharedUrl)) {
            return TwitterExtractor.extract(sharedUrl);
        }
        return InstagramExtractor.extract(sharedUrl);
    }

    static boolean shouldRenderWithInstagramWebView(IOException e, String sharedUrl) {
        if (TwitterExtractor.isTwitterUrl(sharedUrl)) return false;
        String message = e.getMessage();
        return message != null
                && (message.contains("No downloadable")
                || message.contains("story media")
                || message.contains("login is required")
                || message.contains("HTTP 401")
                || message.contains("HTTP 403"));
    }

    static boolean isTwitterUrl(String sharedUrl) {
        return TwitterExtractor.isTwitterUrl(sharedUrl);
    }

    static boolean isInstagramStoryUrl(String sharedUrl) {
        return !TwitterExtractor.isTwitterUrl(sharedUrl) && InstagramExtractor.isStoryUrl(sharedUrl);
    }

    static boolean shouldAskStoryScope(String sharedUrl, List<MediaItem> items) {
        return isInstagramStoryUrl(sharedUrl) && InstagramExtractor.hasOtherStoryItems(items);
    }

    static boolean shouldDownloadDirectly(List<MediaItem> items) {
        return items != null && items.size() == 1;
    }

    static List<MediaItem> sharedStoryItemsOnly(List<MediaItem> items) {
        return InstagramExtractor.sharedStoryItemsOnly(items);
    }
}
