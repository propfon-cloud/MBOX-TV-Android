package com.mbox.tvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final long RETRY_MS = 1500;
    private static final long SETTINGS_HOLD_MS = 3000;

    private WebView webView;
    private LinearLayout connectOverlay;
    private ImageView customLogo;
    private TextView defaultLogo;
    private TextView connectText;
    private boolean pageLoaded = false;
    private boolean destroyed = false;
    private boolean backHeldTriggered = false;

    private final Runnable openSettingsRunnable = () -> {
        backHeldTriggered = true;
        showPinDialog();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        immersive();

        webView = findViewById(R.id.webView);
        connectOverlay = findViewById(R.id.connectOverlay);
        customLogo = findViewById(R.id.customLogo);
        defaultLogo = findViewById(R.id.defaultLogo);
        connectText = findViewById(R.id.connectText);

        configureWebView();
        loadLogo();
        startSmartConnection();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setBackgroundColor(Color.BLACK);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                connectOverlay.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                immersive();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!pageLoaded) {
                    showConnecting("Връзката прекъсна. Нов опит…");
                    handler.postDelayed(PlayerActivity.this::startSmartConnection, RETRY_MS);
                }
            }
        });
    }

    private void loadLogo() {
        String logoUri = Prefs.getLogoUri(this);
        if (logoUri.isEmpty()) {
            customLogo.setVisibility(View.GONE);
            defaultLogo.setVisibility(View.VISIBLE);
            return;
        }
        try {
            customLogo.setImageURI(Uri.parse(logoUri));
            customLogo.setVisibility(View.VISIBLE);
            defaultLogo.setVisibility(View.GONE);
        } catch (Exception e) {
            customLogo.setVisibility(View.GONE);
            defaultLogo.setVisibility(View.VISIBLE);
        }
    }

    private void startSmartConnection() {
        if (destroyed || pageLoaded) return;
        String url = Prefs.getUrl(this).trim();
        if (url.isEmpty()) {
            openSetup();
            return;
        }

        showConnecting(hasNetwork() ? "Свързване…" : "Изчакване на мрежата…");
        executor.execute(() -> {
            boolean reachable = hasNetwork() && isReachable(url);
            handler.post(() -> {
                if (destroyed || pageLoaded) return;
                if (reachable) {
                    connectText.setText("Зареждане…");
                    webView.loadUrl(url);
                } else {
                    handler.postDelayed(this::startSmartConnection, RETRY_MS);
                }
            });
        });
    }

    private boolean isReachable(String address) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(address);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(1400);
            conn.setReadTimeout(1400);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cache-Control", "no-cache");
            int code = conn.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean hasNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network active = cm.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        } catch (Exception e) {
            return true;
        }
    }

    private void showConnecting(String message) {
        webView.setVisibility(View.GONE);
        connectOverlay.setVisibility(View.VISIBLE);
        connectText.setText(message);
        loadLogo();
        immersive();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                backHeldTriggered = false;
                handler.postDelayed(openSettingsRunnable, SETTINGS_HOLD_MS);
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(openSettingsRunnable);
                if (!backHeldTriggered) {
                    // Short BACK does nothing: keeps client inside MBOX TV.
                    immersive();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showPinDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("PIN");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("MBOX TV – Настройки")
                .setMessage("Въведи PIN")
                .setView(input)
                .setPositiveButton("ОТВОРИ", (dialog, which) -> {
                    if (Prefs.getPin(this).equals(input.getText().toString())) {
                        openSetup();
                    } else {
                        showConnecting("Грешен PIN");
                        handler.postDelayed(this::startSmartConnection, 900);
                    }
                })
                .setNegativeButton("ОТКАЗ", (dialog, which) -> immersive())
                .setOnDismissListener(dialog -> immersive())
                .show();
    }

    private void openSetup() {
        startActivity(new Intent(this, SetupActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
