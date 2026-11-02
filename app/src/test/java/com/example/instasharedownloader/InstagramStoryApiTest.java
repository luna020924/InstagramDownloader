package com.example.instasharedownloader;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;

public final class InstagramStoryApiTest {
    private static final String COOKIE = "csrftoken=test-csrf; ds_user_id=42; sessionid=test%3Asession";

    @Test
    public void routesStoryReadsToJsonHostWithoutChangingQuery() throws Exception {
        assertEquals("https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=42&media_id=123",
                InstagramStoryApi.apiUrl("https://www.instagram.com/api/v1/feed/reels_media/"
                        + "?reel_ids=42&media_id=123").toString());
    }

    @Test
    public void neverSendsSessionToAnotherHostOrInsecureEndpoint() throws Exception {
        String[] rejected = {
                "https://instagram.com.evil.test/api/v1/feed/reels_tray/",
                "https://evil.test/api/v1/feed/reels_tray/",
                "http://www.instagram.com/api/v1/feed/reels_tray/",
                "https://www.instagram.com/accounts/login/",
                "https://user@www.instagram.com/api/v1/feed/reels_tray/",
                "https://www.instagram.com:8443/api/v1/feed/reels_tray/"
        };
        for (String endpoint : rejected) {
            try {
                InstagramStoryApi.fetch(endpoint, "https://www.instagram.com/", COOKIE, url -> {
                    fail("Untrusted endpoint reached the connection factory");
                    return null;
                });
                fail("Should reject endpoint");
            } catch (IOException expected) {
                assertFalse(expected.getMessage().contains("test%3Asession"));
            }
        }
    }

    @Test
    public void existingWebSessionProducesNativeAuthorizationWithoutDecodingItTwice() throws Exception {
        String authorization = InstagramStoryApi.authorization(COOKIE);
        assertTrue(authorization.startsWith("Bearer IGT:2:"));
        JSONObject session = new JSONObject(new String(
                Base64.getDecoder().decode(authorization.substring("Bearer IGT:2:".length())),
                StandardCharsets.UTF_8));
        assertEquals("42", session.getString("ds_user_id"));
        assertEquals("test%3Asession", session.getString("sessionid"));
    }

    @Test
    public void incompleteSessionRequiresLogin() throws Exception {
        for (String cookie : new String[]{null, "", "sessionid=test", "ds_user_id=42", "ds_user_id=oops; sessionid=test"}) {
            try {
                InstagramStoryApi.authorization(cookie);
                fail("Expected login requirement");
            } catch (IOException expected) {
                assertEquals("Instagram login is required for story links.", expected.getMessage());
            }
        }
    }

    @Test
    public void usesNativeHeadersAndDisconnectsAfterSuccess() throws Exception {
        FakeConnection connection = new FakeConnection(200, "{\"status\":\"ok\",\"reels\":{}}");
        String body = InstagramStoryApi.fetch("https://www.instagram.com/api/v1/feed/reels_media/",
                "https://www.instagram.com/stories/creator/", COOKIE, url -> {
                    assertEquals("i.instagram.com", url.getHost());
                    return connection;
                });
        assertEquals("ok", new JSONObject(body).getString("status"));
        assertEquals("42", connection.getRequestProperty("IG-Intended-User-ID"));
        assertEquals("124024574287414", connection.getRequestProperty("X-IG-App-ID"));
        assertTrue(connection.getRequestProperty("Authorization").startsWith("Bearer IGT:2:"));
        assertNull(connection.getRequestProperty("Cookie"));
        assertNull(connection.getRequestProperty("Origin"));
        assertFalse(connection.getInstanceFollowRedirects());
        assertTrue(connection.disconnected);
    }

    @Test
    public void rejectsHtmlEvenWhenServerReturnsHttp200() throws Exception {
        FakeConnection connection = new FakeConnection(200, "<!DOCTYPE html><title>Instagram</title>");
        IOException error = failure(connection);
        assertEquals("Instagram story API did not return JSON.", error.getMessage());
        assertTrue(connection.disconnected);
    }

    @Test
    public void redirectsAndHttpFailuresAreNotParsedAsStoriesOrLeaked() throws Exception {
        for (int status : new int[]{302, 401, 403, 429, 500}) {
            FakeConnection connection = new FakeConnection(status, "sensitive response signed-url");
            IOException error = failure(connection);
            assertTrue(error.getMessage().contains("HTTP " + status));
            assertFalse(error.getMessage().contains("sensitive"));
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void jsonFailureIsNotAcceptedAsAnEmptySuccessfulLibrary() throws Exception {
        IOException error = failure(new FakeConnection(200,
                "{\"status\":\"fail\",\"message\":\"private account details signed-url\"}"));
        assertEquals("Instagram story API could not complete the request.", error.getMessage());
        assertFalse(error.getMessage().contains("private"));
    }

    @Test
    public void acceptsJsonPrefixAndRecognizesExpiredSession() throws Exception {
        FakeConnection connection = new FakeConnection(200, "for (;;);{\"status\":\"ok\"}");
        assertEquals("{\"status\":\"ok\"}", InstagramStoryApi.fetch(
                "https://www.instagram.com/api/v1/feed/reels_tray/", "https://www.instagram.com/",
                COOKIE, url -> connection));
        assertEquals("Instagram login is required for story links.", failure(new FakeConnection(200,
                "{\"status\":\"fail\",\"message\":\"login_required\"}")).getMessage());
    }

    private static IOException failure(FakeConnection connection) throws Exception {
        try {
            InstagramStoryApi.fetch("https://www.instagram.com/api/v1/feed/reels_tray/",
                    "https://www.instagram.com/", COOKIE, url -> connection);
            fail("Expected an API failure");
            return null;
        } catch (IOException expected) {
            return expected;
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final int code;
        private final String body;
        boolean disconnected;

        FakeConnection(int code, String body) throws Exception {
            super(new URL("https://i.instagram.com/api/v1/feed/reels_tray/"));
            this.code = code;
            this.body = body;
        }

        @Override public int getResponseCode() { return code; }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
        @Override public InputStream getErrorStream() { return getInputStream(); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
