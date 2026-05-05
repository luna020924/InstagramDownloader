package com.example.instasharedownloader;

final class MediaItem {
    enum Kind {
        IMAGE,
        VIDEO
    }

    final String url;
    final Kind kind;
    final String thumbnailUrl;

    MediaItem(String url, Kind kind) {
        this(url, kind, url);
    }

    MediaItem(String url, Kind kind, String thumbnailUrl) {
        this.url = url;
        this.kind = kind;
        this.thumbnailUrl = thumbnailUrl == null ? url : thumbnailUrl;
    }
}
