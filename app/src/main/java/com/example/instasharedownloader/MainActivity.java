package com.example.instasharedownloader;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE = 1001;
    private static final String RENDERED_MEDIA_SCRIPT =
            "(function(){"
                    + "const candidates=[],seen=new Set();"
                    + "const clean=u=>String(u||'').replace(/\\\\u0026/g,'&').replace(/\\\\\\//g,'/').replace(/\\\\/g,'');"
                    + "const ok=u=>/^https?:\\/\\//i.test(u)&&/(cdninstagram\\.com|fbcdn\\.net)/i.test(u)&&!/static\\.cdninstagram\\.com|\\/rsrc\\.php\\//i.test(u);"
                    + "const badText=e=>((e.alt||'')+' '+(e.className||'')+' '+(e.getAttribute('aria-label')||'')).toLowerCase();"
                    + "const score=(e,kind)=>{"
                    + "const r=e.getBoundingClientRect();"
                    + "const nw=e.naturalWidth||e.videoWidth||0,nh=e.naturalHeight||e.videoHeight||0;"
                    + "const natural=nw*nh,shown=Math.max(0,r.width)*Math.max(0,r.height);"
                    + "let s=Math.max(natural,shown);"
                    + "if(e.closest('article'))s+=10000000;"
                    + "if(e.closest('main'))s+=1000000;"
                    + "if(e.closest('header,nav,footer,aside'))s-=12000000;"
                    + "if(kind==='video')s+=20000000;"
                    + "const t=badText(e);"
                    + "if(/profile|avatar|프로필|사진첩|스토리|story|emoji|sprite/.test(t))s-=12000000;"
                    + "const u=clean(e.currentSrc||e.src||e.poster||'').toLowerCase();"
                    + "if(/s150x150|p150x150|s320x320|profile_pic|default_profile|ig_cache_key/.test(u))s-=12000000;"
                    + "if(kind==='image'&&Math.max(nw,r.width)<300&&Math.max(nh,r.height)<300)s-=12000000;"
                    + "return s;"
                    + "};"
                    + "const add=(kind,u,e,thumb)=>{"
                    + "u=clean(u);if(!ok(u))return;"
                    + "thumb=clean(thumb||u);if(!ok(thumb))thumb=u;"
                    + "const s=e?score(e,kind):0;if(s<100000)return;"
                    + "const k=u.replace(/[?&](?:_nc_ht|_nc_cat|ccb|oh|oe|dl|stp|efg|_nc_sid|_nc_ohc)=[^&]+/g,'');"
                    + "if(seen.has(k))return;seen.add(k);candidates.push({kind,u,thumb,s});"
                    + "};"
                    + "document.querySelectorAll('article video,main video,video').forEach(e=>{add('video',e.currentSrc||e.src,e,e.poster);add('image',e.poster,e,e.poster);});"
                    + "document.querySelectorAll('article img,main img').forEach(e=>add('image',e.currentSrc||e.src,e,e.currentSrc||e.src));"
                    + "if(!candidates.length)document.querySelectorAll('img').forEach(e=>add('image',e.currentSrc||e.src,e,e.currentSrc||e.src));"
                    + "candidates.sort((a,b)=>b.s-a.s);"
                    + "const hasVideo=candidates.some(c=>c.kind==='video');"
                    + "return candidates.filter(c=>!hasVideo||c.kind==='video').slice(0,20).map(c=>c.kind+'\\t'+c.u+'\\t'+c.thumb).join('\\n');"
                    + "})()";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText urlField;
    private Button downloadButton;
    private Button loginButton;
    private TextView logView;
    private String pendingUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        thumbnailExecutor.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingUrl != null) {
            startDownload(pendingUrl);
        }
    }

    private View buildContentView() {
        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding + dp(12), padding, padding);
        root.setBackgroundColor(Color.rgb(247, 248, 250));

        TextView title = new TextView(this);
        title.setText(R.string.main_title);
        title.setTextSize(24);
        title.setTextColor(Color.rgb(24, 30, 36));
        title.setGravity(Gravity.START);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.main_subtitle);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(80, 90, 100));
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        urlField = new EditText(this);
        urlField.setSingleLine(false);
        urlField.setMinLines(2);
        urlField.setMaxLines(4);
        urlField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        urlField.setHint(R.string.url_hint);
        urlField.setTextSize(15);
        root.addView(urlField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        loginButton = new Button(this);
        loginButton.setText(R.string.login);
        loginButton.setAllCaps(false);
        loginButton.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        loginParams.setMargins(0, dp(12), 0, 0);
        root.addView(loginButton, loginParams);

        downloadButton = new Button(this);
        downloadButton.setText(R.string.download);
        downloadButton.setAllCaps(false);
        downloadButton.setOnClickListener(v -> startDownload(urlField.getText().toString()));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        buttonParams.setMargins(0, dp(8), 0, dp(12));
        root.addView(downloadButton, buttonParams);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(14);
        logView.setTextColor(Color.rgb(38, 45, 52));
        logView.setText(R.string.waiting);
        logView.setPadding(dp(14), dp(14), dp(14), dp(14));
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        return root;
    }

    private void handleIncomingIntent(Intent intent) {
        String candidate = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            candidate = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) candidate = data.toString();
        }

        String instagramUrl = InstagramExtractor.extractInstagramUrl(candidate);
        if (instagramUrl != null) {
            urlField.setText(instagramUrl);
            appendLog("공유 링크 감지: " + instagramUrl);
            startDownload(instagramUrl);
        }
    }

    private void startDownload(String rawInput) {
        String instagramUrl = InstagramExtractor.extractInstagramUrl(rawInput);
        if (instagramUrl == null) {
            appendLog("Instagram 링크를 찾지 못했습니다.");
            return;
        }
        pendingUrl = instagramUrl;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }

        downloadButton.setEnabled(false);
        loginButton.setEnabled(false);
        logView.setText("");
        appendLog("분석 시작: " + instagramUrl);
        if (!hasInstagramSession()) {
            appendLog("앱 내부 Instagram 로그인 세션이 없습니다. 실패하면 Instagram 로그인 버튼으로 먼저 로그인하세요.");
        }

        executor.execute(() -> {
            try {
                List<MediaItem> items = InstagramExtractor.extract(instagramUrl);
                mainHandler.post(() -> showSelectionDialog(items));
            } catch (IOException e) {
                if (shouldRenderWithWebView(e)) {
                    postLog("HTTP 추출 실패: " + e.getMessage());
                    postLog("저장된 Instagram 세션으로 페이지를 렌더링해서 다시 찾는 중");
                    mainHandler.post(() -> extractWithRenderedWebView(instagramUrl));
                } else {
                    postFailure(e);
                    mainHandler.post(this::setIdle);
                }
            } catch (Exception e) {
                postLog("실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                mainHandler.post(this::setIdle);
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
        WebView webView = new WebView(this);
        webView.setAlpha(0f);
        webView.setEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        addContentView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        final boolean[] finished = {false};
        final int[] attempts = {0};

        Runnable cleanup = () -> {
            try {
                ((ViewGroup) webView.getParent()).removeView(webView);
            } catch (Exception ignored) {
            }
            webView.destroy();
        };

        Runnable[] probe = new Runnable[1];
        probe[0] = () -> {
            if (finished[0]) return;
            attempts[0]++;
            webView.evaluateJavascript(RENDERED_MEDIA_SCRIPT, value -> {
                if (finished[0]) return;
                String dump = InstagramExtractor.decodeJavascriptString(value);
                List<MediaItem> items = InstagramExtractor.extractFromRenderedDump(dump);
                if (!items.isEmpty()) {
                    finished[0] = true;
                    cleanup.run();
                    showSelectionDialog(items);
                    return;
                }
                if (attempts[0] < 10) {
                    mainHandler.postDelayed(probe[0], 1500);
                } else {
                    finished[0] = true;
                    cleanup.run();
                    postLog("실패: 로그인 세션으로도 미디어 URL을 찾지 못했습니다.");
                    postLog("앱의 Instagram 로그인 버튼에서 로그인 상태를 확인한 뒤 다시 공유해보세요.");
                    setIdle();
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
                cleanup.run();
                postLog("실패: Instagram 렌더링 시간이 초과되었습니다.");
                setIdle();
            }
        }, 25000);

        webView.loadUrl(instagramUrl);
    }

    private void showSelectionDialog(List<MediaItem> items) {
        if (items == null || items.isEmpty()) {
            appendLog("선택할 미디어가 없습니다.");
            setIdle();
            return;
        }

        appendLog("미디어 " + items.size() + "개 발견. 선택 창을 표시합니다.");

        Dialog dialog = new Dialog(this);
        boolean[] handled = {false};
        boolean[] selected = new boolean[items.size()];
        Arrays.fill(selected, true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("다운로드할 미디어 선택");
        title.setTextSize(19);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(Color.rgb(24, 30, 36));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("기본으로 모두 선택되어 있습니다.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.rgb(92, 100, 108));
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);
        int cellSize = Math.max(dp(84), (getResources().getDisplayMetrics().widthPixels - dp(64)) / 3);

        for (int i = 0; i < items.size(); i++) {
            MediaItem item = items.get(i);
            int index = i;
            FrameLayout cell = new FrameLayout(this);
            cell.setBackgroundColor(Color.rgb(235, 238, 241));
            cell.setPadding(dp(1), dp(1), dp(1), dp(1));

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
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(48), dp(48));
            checkParams.gravity = Gravity.END | Gravity.TOP;
            cell.addView(checkBox, checkParams);

            View.OnClickListener toggle = v -> {
                selected[index] = !selected[index];
                checkBox.setChecked(selected[index]);
                cell.setAlpha(selected[index] ? 1f : 0.45f);
            };
            cell.setOnClickListener(toggle);
            checkBox.setOnClickListener(v -> {
                selected[index] = checkBox.isChecked();
                cell.setAlpha(selected[index] ? 1f : 0.45f);
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(cell, params);
        }

        scrollView.addView(grid);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dp(420), cellSize * 3 + dp(18))));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);

        Button cancel = new Button(this);
        cancel.setText("취소");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button download = new Button(this);
        download.setText("선택 다운로드");
        download.setAllCaps(false);
        download.setOnClickListener(v -> {
            ArrayList<MediaItem> chosen = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (selected[i]) chosen.add(items.get(i));
            }
            if (chosen.isEmpty()) {
                appendLog("선택된 미디어가 없습니다.");
                return;
            }
            handled[0] = true;
            dialog.dismiss();
            logView.setText("");
            appendLog("선택한 미디어 " + chosen.size() + "개 다운로드 시작");
            startSelectedDownload(chosen);
        });
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(0, dp(48), 1.4f);
        downloadParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(download, downloadParams);

        root.addView(actions);

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (!handled[0]) setIdle();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void startSelectedDownload(List<MediaItem> selectedItems) {
        executor.execute(() -> {
            try {
                downloadItems(selectedItems);
            } catch (IOException e) {
                postLog("실패: " + e.getMessage());
            } catch (Exception e) {
                postLog("실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } finally {
                mainHandler.post(this::setIdle);
            }
        });
    }

    private void downloadItems(List<MediaItem> items) throws IOException {
        postLog("미디어 " + items.size() + "개 발견");
        int index = 1;
        for (MediaItem item : items) {
            int current = index;
            postLog(current + "/" + items.size() + " 다운로드 시작");
            String name = MediaStoreSaver.download(this, item, current, items.size(), this::postLog);
            postLog("저장 완료: " + name);
            index++;
        }
        postLog("완료. 사진/동영상 앱의 InstaShareDownloader 폴더를 확인하세요.");
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

    private void postFailure(IOException e) {
        postLog("실패: " + e.getMessage());
        postLog("Scriptable 방식처럼 앱 내부 Instagram 로그인 세션이 필요할 수 있습니다.");
    }

    private boolean hasInstagramSession() {
        String cookie = CookieManager.getInstance().getCookie("https://www.instagram.com/");
        return cookie != null && cookie.contains("sessionid=");
    }

    private void setIdle() {
        downloadButton.setEnabled(true);
        loginButton.setEnabled(true);
    }

    private void appendLog(String message) {
        logView.append((logView.length() == 0 ? "" : "\n") + message);
    }

    private void postLog(String message) {
        mainHandler.post(() -> appendLog(message));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
