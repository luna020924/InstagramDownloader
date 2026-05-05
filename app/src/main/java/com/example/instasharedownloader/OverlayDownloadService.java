package com.example.instasharedownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OverlayDownloadService extends Service {
    static final String EXTRA_URL = "com.example.instasharedownloader.EXTRA_URL";

    private static final String CHANNEL_ID = "instagram_overlay_download";
    private static final int NOTIFICATION_ID = 2301;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ExecutorService executor;
    private ExecutorService thumbnailExecutor;
    private WindowManager windowManager;
    private FrameLayout overlayRoot;
    private LinearLayout panel;
    private TextView statusView;
    private WebView webView;
    private boolean stopping;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        thumbnailExecutor = Executors.newFixedThreadPool(3);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        stopping = false;
        startInForeground("Instagram 링크 분석 중");

        String instagramUrl = intent == null ? null : intent.getStringExtra(EXTRA_URL);
        if (instagramUrl == null || !Settings.canDrawOverlays(this)) {
            closeAndStop();
            return START_NOT_STICKY;
        }

        showProgressOverlay("Instagram 공유 다운로드", "공유 링크를 분석하는 중입니다.");
        startExtraction(instagramUrl);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cleanupWebView();
        removeOverlayView();
        if (executor != null) executor.shutdownNow();
        if (thumbnailExecutor != null) thumbnailExecutor.shutdownNow();
        super.onDestroy();
    }

    private void startExtraction(String instagramUrl) {
        appendStatus("분석 시작: " + instagramUrl);
        if (!hasInstagramSession()) {
            appendStatus("Instagram 로그인 세션이 없으면 일부 게시물은 실패할 수 있습니다.");
        }

        executor.execute(() -> {
            try {
                List<MediaItem> items = InstagramExtractor.extract(instagramUrl);
                mainHandler.post(() -> showSelectionOverlay(items));
            } catch (IOException e) {
                if (shouldRenderWithWebView(e)) {
                    postStatus("HTTP 추출 실패: " + e.getMessage());
                    postStatus("저장된 Instagram 세션으로 페이지를 렌더링합니다.");
                    mainHandler.post(() -> extractWithRenderedWebView(instagramUrl));
                } else {
                    mainHandler.post(() -> showDoneOverlay(
                            "다운로드 실패",
                            "실패: " + e.getMessage() + "\nInstagram 로그인 세션이 필요할 수 있습니다."));
                }
            } catch (Exception e) {
                mainHandler.post(() -> showDoneOverlay(
                        "다운로드 실패",
                        "실패: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
            }
        });
    }

    private boolean shouldRenderWithWebView(IOException e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("No downloadable")
                || message.contains("login is required")
                || message.contains("HTTP 401")
                || message.contains("HTTP 403"));
    }

    private void extractWithRenderedWebView(String instagramUrl) {
        if (overlayRoot == null || stopping) return;

        cleanupWebView();
        webView = new WebView(this);
        webView.setAlpha(0f);
        webView.setEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
        params.gravity = Gravity.START | Gravity.TOP;
        overlayRoot.addView(webView, params);

        final boolean[] finished = {false};
        final int[] attempts = {0};

        Runnable[] probe = new Runnable[1];
        probe[0] = () -> {
            if (finished[0] || webView == null || stopping) return;
            attempts[0]++;
            webView.evaluateJavascript(MainActivity.RENDERED_MEDIA_SCRIPT, value -> {
                if (finished[0] || stopping) return;
                String dump = InstagramExtractor.decodeJavascriptString(value);
                List<MediaItem> items = InstagramExtractor.extractFromRenderedDump(dump);
                if (!items.isEmpty()) {
                    finished[0] = true;
                    cleanupWebView();
                    showSelectionOverlay(items);
                    return;
                }
                if (attempts[0] < 10) {
                    mainHandler.postDelayed(probe[0], 1500);
                } else {
                    finished[0] = true;
                    cleanupWebView();
                    showDoneOverlay(
                            "다운로드 실패",
                            "로그인 세션으로도 미디어 URL을 찾지 못했습니다.\n앱에서 Instagram 로그인 상태를 확인한 뒤 다시 공유해보세요.");
                }
            });
        };

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.postDelayed(probe[0], 1200);
            }
        });

        mainHandler.postDelayed(() -> {
            if (!finished[0]) {
                finished[0] = true;
                cleanupWebView();
                showDoneOverlay("다운로드 실패", "Instagram 렌더링 시간이 초과되었습니다.");
            }
        }, 25000);

        webView.loadUrl(instagramUrl);
    }

    private void showSelectionOverlay(List<MediaItem> items) {
        if (items == null || items.isEmpty()) {
            showDoneOverlay("다운로드 실패", "선택할 미디어가 없습니다.");
            return;
        }

        ensureOverlayRoot();
        if (panel == null) return;
        panel.removeAllViews();
        statusView = null;

        addHeader("다운로드할 미디어 선택");

        TextView subtitle = new TextView(this);
        subtitle.setText("기본으로 모두 선택되어 있습니다.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Ui.MUTED);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        panel.addView(subtitle);

        boolean[] selected = new boolean[items.size()];
        Arrays.fill(selected, true);
        CheckBox[] checkBoxes = new CheckBox[items.size()];
        FrameLayout[] cells = new FrameLayout[items.size()];
        final Runnable[] updateSelectionState = new Runnable[1];

        LinearLayout selectTools = new LinearLayout(this);
        selectTools.setOrientation(LinearLayout.HORIZONTAL);
        selectTools.setGravity(Gravity.END);

        Button selectAll = new Button(this);
        selectAll.setText("전체 선택");
        Ui.styleSecondary(selectAll);
        selectTools.addView(selectAll, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button clearAll = new Button(this);
        clearAll.setText("전체 해제");
        Ui.styleGhost(clearAll);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        clearParams.setMargins(dp(8), 0, 0, 0);
        selectTools.addView(clearAll, clearParams);
        panel.addView(selectTools);

        ScrollView scrollView = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellSize = Math.max(dp(84), (screenWidth - dp(74)) / 3);

        for (int i = 0; i < items.size(); i++) {
            MediaItem item = items.get(i);
            int index = i;
            FrameLayout cell = new FrameLayout(this);
            cell.setBackground(Ui.roundedStroke(this, Color.rgb(235, 238, 241), 16, Color.rgb(211, 218, 225), 1));
            cell.setPadding(dp(1), dp(1), dp(1), dp(1));
            cells[index] = cell;

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(225, 229, 233));
            cell.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            loadThumbnailInto(image, item.thumbnailUrl);

            TextView badge = new TextView(this);
            badge.setText(item.kind == MediaItem.Kind.VIDEO ? "VIDEO" : String.valueOf(index + 1));
            badge.setTextSize(11);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
            badge.setBackgroundColor(Color.argb(180, 20, 27, 34));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(52), dp(24));
            badgeParams.gravity = Gravity.START | Gravity.BOTTOM;
            cell.addView(badge, badgeParams);

            CheckBox checkBox = new CheckBox(this);
            checkBox.setChecked(true);
            checkBoxes[index] = checkBox;
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(48), dp(48));
            checkParams.gravity = Gravity.END | Gravity.TOP;
            cell.addView(checkBox, checkParams);

            View.OnClickListener toggle = v -> {
                selected[index] = !selected[index];
                checkBox.setChecked(selected[index]);
                cell.setAlpha(selected[index] ? 1f : 0.45f);
                if (updateSelectionState[0] != null) updateSelectionState[0].run();
            };
            cell.setOnClickListener(toggle);
            checkBox.setOnClickListener(v -> {
                selected[index] = checkBox.isChecked();
                cell.setAlpha(selected[index] ? 1f : 0.45f);
                if (updateSelectionState[0] != null) updateSelectionState[0].run();
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(cell, params);
        }

        updateSelectionState[0] = () -> {
            int count = 0;
            for (int i = 0; i < selected.length; i++) {
                if (selected[i]) count++;
                if (checkBoxes[i] != null) checkBoxes[i].setChecked(selected[i]);
                if (cells[i] != null) cells[i].setAlpha(selected[i] ? 1f : 0.42f);
            }
            subtitle.setTextColor(Ui.MUTED);
            subtitle.setText("선택됨 " + count + "/" + items.size());
        };
        updateSelectionState[0].run();
        selectAll.setOnClickListener(v -> {
            Arrays.fill(selected, true);
            updateSelectionState[0].run();
        });
        clearAll.setOnClickListener(v -> {
            Arrays.fill(selected, false);
            updateSelectionState[0].run();
        });

        scrollView.addView(grid);
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dp(420), cellSize * 3 + dp(18))));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);

        Button cancel = new Button(this);
        cancel.setText("취소");
        Ui.styleGhost(cancel);
        cancel.setOnClickListener(v -> closeAndStop());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button download = new Button(this);
        download.setText("선택 다운로드");
        Ui.stylePrimary(download);
        download.setOnClickListener(v -> {
            ArrayList<MediaItem> chosen = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (selected[i]) chosen.add(items.get(i));
            }
            if (chosen.isEmpty()) {
                subtitle.setText("최소 한 개를 선택해주세요.");
                subtitle.setTextColor(Ui.DANGER);
                return;
            }
            showProgressOverlay("다운로드 중", "선택한 미디어 " + chosen.size() + "개 다운로드 시작");
            startSelectedDownload(chosen);
        });
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(0, dp(48), 1.4f);
        downloadParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(download, downloadParams);

        panel.addView(actions);
    }

    private void startSelectedDownload(List<MediaItem> selectedItems) {
        executor.execute(() -> {
            try {
                int index = 1;
                for (MediaItem item : selectedItems) {
                    int current = index;
                    postStatus(current + "/" + selectedItems.size() + " 다운로드 시작");
                    String name = MediaStoreSaver.download(
                            this,
                            item,
                            current,
                            selectedItems.size(),
                            this::postStatus);
                    postStatus("저장 완료: " + name);
                    index++;
                }
                mainHandler.post(() -> showDoneOverlay(
                        "다운로드 완료",
                        "사진/동영상 앱의 InstaShareDownloader 폴더에 저장했습니다."));
            } catch (IOException e) {
                mainHandler.post(() -> showDoneOverlay("다운로드 실패", "실패: " + e.getMessage()));
            } catch (Exception e) {
                mainHandler.post(() -> showDoneOverlay(
                        "다운로드 실패",
                        "실패: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
            }
        });
    }

    private void showProgressOverlay(String title, String message) {
        ensureOverlayRoot();
        if (panel == null) return;
        panel.removeAllViews();
        statusView = null;
        addHeader(title);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(8), 0, dp(8));
        panel.addView(progressBar, progressParams);

        statusView = new TextView(this);
        statusView.setText(message);
        statusView.setTextColor(Ui.TEXT);
        statusView.setTextSize(14);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackground(Ui.rounded(this, Ui.PANEL, 16));
        panel.addView(statusView);
    }

    private void showDoneOverlay(String title, String message) {
        ensureOverlayRoot();
        if (panel == null) return;
        panel.removeAllViews();
        statusView = null;
        addHeader(title);

        TextView body = new TextView(this);
        body.setText(message);
        body.setTextColor(Ui.MUTED);
        body.setTextSize(14);
        body.setPadding(0, dp(8), 0, dp(14));
        panel.addView(body);

        Button close = new Button(this);
        close.setText("닫기");
        Ui.stylePrimary(close);
        close.setOnClickListener(v -> closeAndStop());
        panel.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));
    }

    private void ensureOverlayRoot() {
        if (overlayRoot != null) return;
        if (!Settings.canDrawOverlays(this)) {
            closeAndStop();
            return;
        }

        overlayRoot = new FrameLayout(this);
        overlayRoot.setBackgroundColor(Color.argb(130, 0, 0, 0));
        overlayRoot.setFocusableInTouchMode(true);
        overlayRoot.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                closeAndStop();
                return true;
            }
            return false;
        });

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 26, Ui.LINE, 1));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        panelParams.setMargins(dp(16), dp(24), dp(16), dp(24));
        overlayRoot.addView(panel, panelParams);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.START | Gravity.TOP;

        try {
            windowManager.addView(overlayRoot, windowParams);
            overlayRoot.requestFocus();
        } catch (RuntimeException e) {
            overlayRoot = null;
            panel = null;
            closeAndStop();
        }
    }

    private void addHeader(String titleText) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Ui.text(this, titleText, 20, Ui.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        Button close = new Button(this);
        close.setText("X");
        Ui.styleGhost(close);
        close.setOnClickListener(v -> closeAndStop());
        header.addView(close, new LinearLayout.LayoutParams(dp(46), dp(42)));

        panel.addView(header);
    }

    private void appendStatus(String message) {
        if (statusView == null) return;
        statusView.append((statusView.length() == 0 ? "" : "\n") + message);
    }

    private void postStatus(String message) {
        mainHandler.post(() -> appendStatus(message));
    }

    private boolean hasInstagramSession() {
        String cookie = CookieManager.getInstance().getCookie("https://www.instagram.com/");
        return cookie != null && cookie.contains("sessionid=");
    }

    private void loadThumbnailInto(ImageView imageView, String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) return;
        thumbnailExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = downloadThumbnail(thumbnailUrl);
            } catch (Exception ignored) {
            }
            Bitmap finalBitmap = bitmap;
            mainHandler.post(() -> {
                if (finalBitmap != null) {
                    imageView.setImageBitmap(finalBitmap);
                }
            });
        });
    }

    private Bitmap downloadThumbnail(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36");
        connection.setRequestProperty("Referer", "https://www.instagram.com/");
        try (InputStream input = connection.getInputStream()) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) return null;

            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max <= 360) return bitmap;

            float scale = 360f / max;
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)),
                    true);
            if (scaled != bitmap) bitmap.recycle();
            return scaled;
        }
    }

    private void cleanupWebView() {
        if (webView == null) return;
        try {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
        } catch (Exception ignored) {
        }
        try {
            webView.destroy();
        } catch (Exception ignored) {
        }
        webView = null;
    }

    private void removeOverlayView() {
        cleanupWebView();
        if (overlayRoot == null) return;
        try {
            windowManager.removeView(overlayRoot);
        } catch (Exception ignored) {
        }
        overlayRoot = null;
        panel = null;
        statusView = null;
    }

    private void closeAndStop() {
        stopping = true;
        removeOverlayView();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Instagram downloads",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void startInForeground(String text) {
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Instagram 공유 다운로드")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
