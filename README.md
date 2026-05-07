# Insta Share Downloader

Android MVP that appears in the share sheet, reads an Instagram or Twitter/X link, opens a system overlay selection grid above the current screen, and downloads the selected items.

## Version

- Current version: `0.1.0`
- APK file name: `InstaShareDownloader-0.1.0-debug.apk`
- GitHub release tag: `v0.1.0`

## What Works

- Receives `text/plain` shares from Instagram, Twitter/X, or other apps.
- Requests Android's `다른 앱 위에 표시` permission and uses it for the share selection overlay.
- Extracts `instagram.com`, `x.com`, and `twitter.com` URLs from shared text.
- Supports an in-app Instagram login session, similar to the iOS Scriptable shortcut flow.
- Reads post/reel media info with the saved WebView session when available.
- Supports shared story links with the saved WebView login session.
- For Instagram story links, asks whether to download only the shared story or view the account's other current stories.
- Downloads only the shared story immediately when `이 스토리만 받기` is selected.
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

## Download

The latest APK is attached to the GitHub release:

```text
v0.1.0 / InstaShareDownloader-0.1.0-debug.apk
```

## Use

1. Install the debug APK on an Android device.
2. Open this app and tap `Instagram 로그인`.
3. Sign in to Instagram, then tap `완료`.
4. Tap `다른 앱 위에 표시 권한` and allow overlay permission.
5. Open Instagram or Twitter/X and share a post/reel/story/status link to `Insta Share Downloader`.
6. For a story link, choose `이 스토리만 받기` for immediate download or `다른 스토리도 보기` to pick from the full story grid.
7. For post/reel/Twitter media, choose the items in the overlay grid. Everything is checked by default.
8. Tap `선택 다운로드`.
9. Check the `InstaShareDownloader` album/folder in Photos or Gallery.
