# 인스타 공유 다운로더

안드로이드 공유 메뉴에서 Instagram 또는 Twitter/X 링크를 받아 이미지와 동영상을 다운로드하는 앱입니다. 앱 안에 링크를 붙여넣는 방식이 아니라, Instagram이나 Twitter/X에서 공유 버튼을 누른 뒤 `Insta Share Downloader`로 공유하면 화면 위 오버레이에서 받을 항목을 고를 수 있습니다.

## 버전

- 현재 버전: `0.1.0`
- 릴리즈 태그: `v0.1.0`
- APK 파일: `InstaShareDownloader-0.1.0-debug.apk`

## 다운로드

최신 APK는 GitHub 릴리즈에서 받을 수 있습니다.

- [v0.1.0 릴리즈](releases/tag/v0.1.0)
- [APK 바로 다운로드](releases/download/v0.1.0/InstaShareDownloader-0.1.0-debug.apk)

## 주요 기능

- Instagram, Twitter/X 등에서 공유한 `text/plain` 링크를 받습니다.
- `instagram.com`, `x.com`, `twitter.com` 링크를 자동으로 추출합니다.
- 안드로이드 `다른 앱 위에 표시` 권한을 사용해 현재 화면 위에 선택 오버레이를 띄웁니다.
- Instagram 로그인 세션을 앱 안 WebView에 저장해 iOS Scriptable 단축어와 비슷한 방식으로 비공개 API 요청에 활용합니다.
- Instagram 게시물, 릴스, 캐러셀 게시물, 공유받은 스토리 링크를 처리합니다.
- Instagram 스토리 링크는 `이 스토리만 받기`를 누르면 격자 선택 없이 바로 다운로드합니다.
- `다른 스토리도 보기`를 누르면 해당 계정의 현재 스토리들을 격자로 보여주고 원하는 항목만 받을 수 있습니다.
- Twitter/X 공개 게시물의 이미지와 MP4 영상을 추출합니다.
- 격자 선택 화면에서는 기본적으로 모든 항목이 체크되어 있고, 사용자가 원하는 항목만 해제할 수 있습니다.
- 이미지는 `Pictures/InstaShareDownloader`에 저장합니다.
- 동영상은 `Movies/InstaShareDownloader`에 저장합니다.
- 앱은 쿠키나 로그인 정보를 외부 서버로 전송하지 않습니다.

## 제한 사항

- 친한 친구 스토리, 만료된 스토리, 계정 권한이 필요한 일부 화면은 Instagram 정책이나 세션 상태에 따라 막힐 수 있습니다.
- Instagram과 Twitter/X는 내부 응답 구조를 자주 바꾸기 때문에, 추출 로직은 이후 업데이트가 필요할 수 있습니다.
- 현재 APK는 디버그 빌드입니다. 설치할 때 안드로이드의 `알 수 없는 앱 설치` 허용이 필요할 수 있습니다.

## 설치 및 사용법

1. GitHub 릴리즈에서 APK를 다운로드해 안드로이드 기기에 설치합니다.
2. 앱을 열고 `Instagram 로그인`을 누릅니다.
3. Instagram에 로그인한 뒤 `완료`를 누릅니다.
4. `다른 앱 위에 표시 권한`을 눌러 오버레이 권한을 허용합니다.
5. Instagram 또는 Twitter/X에서 게시물, 릴스, 스토리, 트윗 링크를 `Insta Share Downloader`로 공유합니다.
6. 스토리 링크라면 `이 스토리만 받기` 또는 `다른 스토리도 보기` 중 하나를 선택합니다.
7. 게시물, 릴스, Twitter/X 미디어라면 오버레이 격자에서 받을 항목을 고릅니다.
8. `선택 다운로드`를 누릅니다.
9. 갤러리 또는 파일 앱에서 `InstaShareDownloader` 앨범/폴더를 확인합니다.

## 빌드

```bash
./gradlew :app:assembleDebug
```

빌드 결과 APK:

```text
app/build/outputs/apk/debug/InstaShareDownloader-0.1.0-debug.apk
```

## 개인정보

- 소스코드에는 Instagram 쿠키, 세션값, API 키, 토큰을 하드코딩하지 않습니다.
- 로그인 쿠키는 안드로이드 WebView의 쿠키 저장소에서 런타임에 읽어 Instagram 요청에만 사용합니다.
- 별도 서버를 두지 않으며, 다운로드 처리는 기기 안에서 수행합니다.
