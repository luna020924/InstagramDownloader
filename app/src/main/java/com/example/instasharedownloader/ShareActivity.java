package com.example.instasharedownloader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ShareActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE = 2001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String pendingUrl;
    private boolean openedOverlaySettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUrl != null && openedOverlaySettings) {
            continueShareFlow();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            continueShareFlow();
        } else if (requestCode == REQUEST_WRITE_STORAGE) {
            showMessage("저장 권한이 필요합니다.", "닫기", this::finish);
        }
    }

    private void handleIntent(Intent intent) {
        String candidate = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            candidate = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) candidate = data.toString();
        }

        pendingUrl = InstagramExtractor.extractInstagramUrl(candidate);
        if (pendingUrl == null) {
            showMessage("Instagram 링크를 찾지 못했습니다.", "닫기", this::finish);
            return;
        }

        continueShareFlow();
    }

    private void continueShareFlow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionView();
            if (!openedOverlaySettings) {
                openedOverlaySettings = true;
                mainHandler.postDelayed(this::openOverlaySettings, 250);
            }
            return;
        }

        Intent service = new Intent(this, OverlayDownloadService.class)
                .putExtra(OverlayDownloadService.EXTRA_URL, pendingUrl);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void showOverlayPermissionView() {
        LinearLayout root = buildPanel("오버레이 권한 필요",
                "공유한 화면 위에 선택창을 띄우려면 Android 설정에서 '다른 앱 위에 표시'를 허용해야 합니다.");

        Button open = new Button(this);
        open.setText("권한 설정 열기");
        open.setAllCaps(false);
        open.setOnClickListener(v -> openOverlaySettings());
        root.addView(open, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));

        Button close = new Button(this);
        close.setText("취소");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        closeParams.setMargins(0, dp(8), 0, 0);
        root.addView(close, closeParams);
    }

    private void showMessage(String message, String action, Runnable callback) {
        LinearLayout root = buildPanel("Instagram 공유 다운로드", message);
        Button button = new Button(this);
        button.setText(action);
        button.setAllCaps(false);
        button.setOnClickListener(v -> callback.run());
        root.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));
    }

    private LinearLayout buildPanel(String titleText, String bodyText) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(dp(18), dp(18), dp(18), dp(18));
        outer.setBackgroundColor(Color.argb(120, 0, 0, 0));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.rgb(24, 30, 36));
        title.setTextSize(19);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        panel.addView(title);

        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextColor(Color.rgb(76, 84, 92));
        body.setTextSize(14);
        body.setPadding(0, dp(8), 0, dp(14));
        panel.addView(body);

        outer.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(outer);
        return panel;
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
