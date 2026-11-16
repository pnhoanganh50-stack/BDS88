package vn.khachbds.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Màn hình duy nhất của app: hiển thị giao diện (assets/index.html) trong WebView.
 * Dữ liệu lưu trong bộ nhớ riêng của app (xem Bridge.java), không cần mạng.
 */
public class MainActivity extends Activity {

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Reminders.createChannel(this);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#F2F6F5"));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }
        });

        webView.addJavascriptInterface(new Bridge(this), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        askNotificationPermission();
    }

    /** Liên kết gọi điện, SMS, email, Zalo, web: mở bằng app tương ứng trên điện thoại. */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("file") || scheme.equals("about") || scheme.equals("javascript")) {
            return false;
        }
        Intent intent;
        if (scheme.equals("tel")) {
            intent = new Intent(Intent.ACTION_DIAL, uri);
        } else if (scheme.equals("sms") || scheme.equals("smsto") || scheme.equals("mailto")) {
            intent = new Intent(Intent.ACTION_SENDTO, uri);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, uri);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Không tìm thấy ứng dụng để mở", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** Android 13 trở lên phải xin quyền hiện thông báo nhắc hẹn. */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    /** Nút Back: đóng màn hình / bảng đang mở trong app trước, hết mới thoát. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript("(window.appBack && window.appBack()) ? 'y' : 'n'", value -> {
            if (value == null || !value.contains("y")) {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
