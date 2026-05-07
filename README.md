# Insta Share Downloader

Android용 MVP 앱입니다.

Instagram이나 Twitter/X에서 게시물, 릴스, 스토리, 트윗 링크를 공유하면 Android 공유 시트에 `Insta Share Downloader`가 표시됩니다.
공유된 링크를 읽은 뒤, 현재 화면 위에 선택용 그리드를 띄워 원하는 이미지나 영상을 골라 다운로드할 수 있습니다.

## 버전

- 현재 버전: `0.1.0`
- APK 파일명: `InstaShareDownloader-0.1.0-debug.apk`
- GitHub 릴리스 태그: `v0.1.0`

## 현재 지원하는 기능

- Instagram, Twitter/X, 기타 앱에서 공유된 `text/plain` 형식의 텍스트를 받을 수 있습니다.
- 공유된 텍스트에서 `instagram.com`, `x.com`, `twitter.com` 링크를 찾아냅니다.
- Android의 `다른 앱 위에 표시` 권한을 요청하고, 이 권한을 이용해 선택용 오버레이 화면을 띄웁니다.
- 앱 안에서 Instagram에 로그인할 수 있습니다.
  iOS Scriptable 단축어에서 로그인 세션을 활용하던 방식과 비슷합니다.
- 저장된 WebView 로그인 세션이 있으면 Instagram 게시물과 릴스의 미디어 정보를 읽을 수 있습니다.
- Instagram 스토리 링크도 저장된 WebView 로그인 세션을 이용해 처리합니다.
- Instagram 스토리 링크를 공유했을 때, 공유한 스토리만 받을지 해당 계정의 현재 스토리들을 함께 볼지 선택할 수 있습니다.
- `이 스토리만 받기`를 선택하면 공유된 스토리만 바로 다운로드합니다.
- Twitter/X 게시물의 공개 이미지와 MP4 영상 정보를 X의 syndication 메타데이터를 통해 읽어옵니다.
- 추출된 미디어는 다운로드 전에 선택용 그리드에서 확인할 수 있습니다.
- 처음에는 모든 항목이 선택된 상태로 표시되며, 사용자가 체크한 항목만 다운로드합니다.
- Instagram 캐러셀 게시물도 지원합니다.
- 이미지는 `Pictures/InstaShareDownloader` 폴더에 저장됩니다.
- 영상은 `Movies/InstaShareDownloader` 폴더에 저장됩니다.

## 아직 제한이 있는 부분

- 친한 친구 공개 스토리나 일부 로그인 제한이 강한 화면은 Instagram 쪽에서 차단될 수 있습니다.
- Instagram과 Twitter/X는 HTML 구조나 내부 API가 자주 바뀌기 때문에, 추출 로직은 이후에도 수정이 필요할 수 있습니다.
- 이 앱은 쿠키나 로그인 정보를 외부 서버로 보내지 않습니다. 로그인 세션은 기기 안에서만 사용됩니다.

## 빌드 방법

```bash
./gradlew :app:assembleDebug
```

디버그 APK는 아래 경로에 생성됩니다.

```text
app/build/outputs/apk/debug/InstaShareDownloader-0.1.0-debug.apk
```

## 다운로드

최신 APK는 GitHub 릴리스에 첨부되어 있습니다.

```text
v0.1.0 / InstaShareDownloader-0.1.0-debug.apk
```

## 사용 방법

1. Android 기기에 디버그 APK를 설치합니다.
2. 앱을 열고 Instagram 로그인을 누릅니다.
3. Instagram에 로그인한 뒤 완료를 누릅니다.
4. 다른 앱 위에 표시 권한을 누르고 오버레이 권한을 허용합니다.
5. Instagram 또는 Twitter/X에서 게시물, 릴스, 스토리, 트윗 링크를 Insta Share Downloader로 공유합니다.
6. 스토리 링크를 공유한 경우:
   - 이 스토리만 받기를 누르면 해당 스토리만 바로 다운로드합니다.
   - 다른 스토리도 보기를 누르면 현재 볼 수 있는 스토리 목록에서 직접 선택할 수 있습니다.
7. 게시물, 릴스, Twitter/X 미디어는 오버레이 그리드에서 받을 항목을 선택합니다.
   기본값은 전체 선택입니다.
8. 선택 다운로드를 누릅니다.
9. 사진 앱이나 갤러리에서 InstaShareDownloader 앨범 또는 폴더를 확인합니다.
