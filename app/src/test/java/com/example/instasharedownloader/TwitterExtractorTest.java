package com.example.instasharedownloader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class TwitterExtractorTest {
    @Test public void photoUsesSmallPreviewAndKeepsTheOriginalForSaving() throws Exception {
        List<MediaItem> items = TwitterExtractor.parseTweetJson("{\"mediaDetails\":[{\"type\":\"photo\","
                + "\"media_url_https\":\"https://pbs.twimg.com/media/example.jpg?name=large\"}]}");
        assertEquals(1, items.size());
        assertEquals("https://pbs.twimg.com/media/example.jpg?name=orig", items.get(0).url);
        assertEquals("https://pbs.twimg.com/media/example.jpg?format=jpg&name=small", items.get(0).thumbnailUrl);
        assertEquals("https://x.com/", items.get(0).referer);
    }

    @Test public void videoAndGifKeepTheBestMp4AndUseAnImagePreview() throws Exception {
        String variants = "\"video_info\":{\"variants\":[{\"content_type\":\"video/mp4\",\"bitrate\":1,"
                + "\"url\":\"https://video.twimg.com/low.mp4\"},{\"content_type\":\"video/mp4\",\"bitrate\":9,"
                + "\"url\":\"https://video.twimg.com/best.mp4\"}]}";
        for (String type : new String[]{"video", "animated_gif"}) {
            List<MediaItem> items = TwitterExtractor.parseTweetJson("{\"mediaDetails\":[{\"type\":\"" + type
                    + "\",\"media_url_https\":\"https://pbs.twimg.com/media/cover.jpg\"," + variants + "}]}");
            assertEquals(MediaItem.Kind.VIDEO, items.get(0).kind);
            assertEquals("https://video.twimg.com/best.mp4", items.get(0).url);
            assertTrue(items.get(0).thumbnailUrl.endsWith("format=jpg&name=small"));
        }
    }

    @Test public void alternateHostsAndSharedPhotoSuffixStillResolveThePost() {
        assertTrue(TwitterExtractor.isTwitterUrl("https://x.com/example/status/12345/photo/3?s=20"));
        assertTrue(TwitterExtractor.isTwitterUrl("https://twitter.com/i/web/status/12345"));
        assertNull(TwitterExtractor.extractTwitterUrl("https://example.com/status/12345"));
    }
}
