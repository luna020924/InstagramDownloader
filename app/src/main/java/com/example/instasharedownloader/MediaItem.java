package com.example.instasharedownloader;

final class MediaItem {
    enum Kind {
        IMAGE,
        VIDEO
    }

    final String url;
    final Kind kind;
    final String thumbnailUrl;
    final String filePrefix;
    final String referer;
    final String fileNameBase;
    final boolean storyItem;
    final boolean sharedStory;

    MediaItem(String url, Kind kind) {
        this(url, kind, url);
    }

    MediaItem(String url, Kind kind, String thumbnailUrl) {
        this(url, kind, thumbnailUrl, "instagram", "https://www.instagram.com/");
    }

    MediaItem(String url, Kind kind, String thumbnailUrl, String filePrefix, String referer) {
        this(url, kind, thumbnailUrl, filePrefix, referer, false, false);
    }

    MediaItem(String url, Kind kind, String thumbnailUrl, String filePrefix, String referer, String fileNameBase) {
        this(url, kind, thumbnailUrl, filePrefix, referer, fileNameBase, false, false);
    }

    MediaItem(String url, Kind kind, String thumbnailUrl, String filePrefix, String referer, boolean storyItem, boolean sharedStory) {
        this(url, kind, thumbnailUrl, filePrefix, referer, null, storyItem, sharedStory);
    }

    MediaItem(String url, Kind kind, String thumbnailUrl, String filePrefix, String referer, String fileNameBase, boolean storyItem, boolean sharedStory) {
        this.url = url;
        this.kind = kind;
        this.thumbnailUrl = thumbnailUrl == null ? url : thumbnailUrl;
        this.filePrefix = filePrefix == null || filePrefix.isEmpty() ? "media" : filePrefix;
        this.referer = referer == null || referer.isEmpty() ? "https://www.instagram.com/" : referer;
        this.fileNameBase = fileNameBase;
        this.storyItem = storyItem;
        this.sharedStory = sharedStory;
    }
}
