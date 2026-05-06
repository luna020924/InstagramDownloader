# Insta Share Downloader

Android MVP that appears in the share sheet, reads an Instagram or Twitter/X link, opens a system overlay selection grid above the current screen, and downloads the selected items.

## What Works

- Receives `text/plain` shares from Instagram, Twitter/X, or other apps.
- Requests Android's `다른 앱 위에 표시` permission and uses it for the share selection overlay.
- Extracts `instagram.com`, `x.com`, and `twitter.com` URLs from shared text.
- Supports an in-app Instagram login session, similar to the iOS Scriptable shortcut flow.
- Reads post/reel media info with the saved WebView session when available.
- Supports shared story links with the saved WebView login session.
- Reads public Twitter/X post photos and MP4 variants through X's syndication metadata.
- Shows extracted media in a selection grid before downloading.
- Defaults every item to checked, then downloads only the selected items.
- Supports carousel posts through Instagram's media info response.
- Saves images to `Pictures/InstaShareDownloader`.
- Saves videos to `Movies/InstaShareDownloader`.

## Limitations

- Close-friends stories and some login-gated surfaces may still be blocked by Instagram.
- Instagram and Twitter/X change their HTML/API often, so extraction may need parser updates.
- This app does not collect or send cookies/login data to any server.

## Build

```bash
./gradlew :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/InstaShareDownloader-0.1.0-debug.apk
```

## Use

1. Install the debug APK on an Android device.
2. Open this app and tap `Instagram 로그인`.
3. Sign in to Instagram, then tap `완료`.
4. Tap `다른 앱 위에 표시 권한` and allow overlay permission.
5. Open Instagram or Twitter/X and share a post/reel/story/status link to `Insta Share Downloader`.
6. Choose the items in the overlay grid. Everything is checked by default.
7. Tap `선택 다운로드`.
8. Check the `InstaShareDownloader` album/folder in Photos or Gallery.
