package com.example.instasharedownloader;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class LoginActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout header = Ui.card(this, 14, 0);
        header.setBackgroundColor(Ui.SURFACE);

        TextView title = Ui.text(this, "Instagram 로그인", 19, Ui.TEXT, true);
        header.addView(title);

        TextView hint = Ui.text(this, "로그인한 뒤 완료를 누르세요. 쿠키는 앱 내부 WebView에만 저장됩니다.", 14, Ui.MUTED, false);
        hint.setPadding(0, dp(5), 0, dp(10));
        header.addView(hint);

        Button done = new Button(this);
        done.setText("완료");
        Ui.stylePrimary(done);
        done.setOnClickListener(v -> {
            CookieManager.getInstance().flush();
            finish();
        });
        header.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        setContentView(root);
        webView.loadUrl("https://www.instagram.com/");
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
