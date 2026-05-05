# Insta Share Downloader

Android MVP that appears in the share sheet, reads an Instagram link, lets the user choose media in a grid, and downloads the selected items.

## What Works

- Receives `text/plain` shares from Instagram or other apps.
- Extracts `instagram.com` URLs from shared text.
- Supports an in-app Instagram login session, similar to the iOS Scriptable shortcut flow.
- Reads post/reel media info with the saved WebView session when available.
- Shows extracted media in a selection grid before downloading.
- Defaults every item to checked, then downloads only the selected items.
- Supports carousel posts through Instagram's media info response.
- Saves images to `Pictures/InstaShareDownloader`.
- Saves videos to `Movies/InstaShareDownloader`.

## Limitations

- Close-friends stories and some login-gated surfaces are not supported.
- Instagram changes its HTML/API often, so extraction may need parser updates.
- This app does not collect or send cookies/login data to any server.

## Build

```bash
./gradlew :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. Install the debug APK on an Android device.
2. Open this app and tap `Instagram 로그인`.
3. Sign in to Instagram, then tap `완료`.
4. Open Instagram and share a post/reel link to `Insta Share Downloader`.
5. Choose the items in the grid popup. Everything is checked by default.
6. Tap `선택 다운로드`.
7. Check the `InstaShareDownloader` album/folder in Photos or Gallery.
