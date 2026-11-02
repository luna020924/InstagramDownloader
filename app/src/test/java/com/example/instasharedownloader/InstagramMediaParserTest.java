package com.example.instasharedownloader;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public final class InstagramMediaParserTest {
    private static final String IMAGE = "{\"pk\":\"3000000000000000001\",\"taken_at\":1700000000,"
            + "\"image_versions2\":{\"candidates\":[{\"url\":\"https://scontent.cdninstagram.com/story.jpg\","
            + "\"width\":1080,\"height\":1920}]}}";
    private static final String VIDEO = "{\"id\":\"3000000000000000002_42\",\"taken_at\":1700000100,"
            + "\"video_versions\":[{\"url\":\"https://scontent.cdninstagram.com/story.mp4\","
            + "\"width\":1080,\"height\":1920}]}";
    private static final String REEL = "{\"user\":{\"username\":\"creator\"},\"items\":[" + IMAGE + "," + VIDEO + "]}";

    @Test
    public void singleStoryAndAllStoriesRemainDistinct() throws Exception {
        List<MediaItem> stories = InstagramExtractor.parseStoryMedia(
                "{\"reel\":" + REEL + "}", "3000000000000000002");
        assertEquals(2, stories.size());
        assertEquals(MediaItem.Kind.IMAGE, stories.get(0).kind);
        assertEquals(MediaItem.Kind.VIDEO, stories.get(1).kind);
        assertTrue(stories.get(0).storyItem);
        assertFalse(stories.get(0).sharedStory);
        assertTrue(stories.get(1).sharedStory);
        assertTrue(InstagramExtractor.hasOtherStoryItems(stories));
        assertEquals(1, InstagramExtractor.sharedStoryItemsOnly(stories).size());
        assertTrue(stories.get(1).fileNameBase.startsWith("creator "));
    }

    @Test
    public void acceptsAllThreeStoryResponseEnvelopes() throws Exception {
        String[] documents = {"{\"reel\":" + REEL + "}", "{\"reels_media\":[" + REEL + "]}",
                "{\"reels\":{\"42\":" + REEL + "}}"};
        for (String document : documents) {
            assertEquals(2, InstagramExtractor.parseStoryMedia(document, null).size());
        }
        assertTrue(InstagramExtractor.parseStoryMedia("{\"reel\":null}", null).isEmpty());
    }

    @Test
    public void musicPhotoStillDownloadsThePhoto() throws Exception {
        String music = IMAGE.substring(0, IMAGE.length() - 1)
                + ",\"is_story_image_with_music\":true,\"video_versions\":[{"
                + "\"url\":\"https://scontent.cdninstagram.com/music.mp4\"}]}";
        List<MediaItem> items = InstagramExtractor.parseStoryMedia(
                "{\"reel\":{\"items\":[" + music + "]}}", null);
        assertEquals(1, items.size());
        assertEquals(MediaItem.Kind.IMAGE, items.get(0).kind);
    }

    @Test
    public void postCarouselExtractionIsUnchanged() throws Exception {
        String post = "{\"items\":[{\"user\":{\"username\":\"creator\"},\"taken_at\":1700000000,"
                + "\"carousel_media\":[" + IMAGE + "," + VIDEO + "]}]}";
        List<MediaItem> items = InstagramExtractor.parseMediaInfo(post);
        assertEquals(2, items.size());
        assertFalse(items.get(0).storyItem);
        assertFalse(items.get(1).storyItem);
        assertEquals(MediaItem.Kind.IMAGE, items.get(0).kind);
        assertEquals(MediaItem.Kind.VIDEO, items.get(1).kind);
    }

    @Test
    public void sharedStoryIdSurvivesUrlNormalizationWithoutNumericTruncation() {
        String url = MediaExtractor.extractSupportedUrl(
                "https://www.instagram.com/stories/creator/3000000000000000002/?igsh=example");
        assertEquals("https://www.instagram.com/stories/creator/3000000000000000002/", url);
        assertTrue(MediaExtractor.isInstagramStoryUrl(url));
        assertEquals("https://www.instagram.com/p/ABC_def-123/", MediaExtractor.extractSupportedUrl(
                "https://www.instagram.com/p/ABC_def-123/?igsh=example"));
    }
}
