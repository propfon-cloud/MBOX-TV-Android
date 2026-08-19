package com.mbox.tvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity {
    private static final long RETRY_MS = 1500;
    private static final long BACK_HOLD_MS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private LinearLayout connectOverlay;
    private ImageView customLogo;
    private TextView defaultLogo;
    private TextView connectText;

    private boolean destroyed = false;
    private boolean backHeldTriggered = false;
    private boolean reloadScheduled = false;

    private final Runnable openControlMenuRunnable = () -> {
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

        // Browser Mode: one direct WebView. No iframe, no bootstrap route,
        // no DOM/CSS/JavaScript manipulation of the TV page.
        Prefs.setIframeMode(this, false);

        configureWebView();
        loadLogo();
        openConfiguredAddress();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();

        // Behave as closely as possible to a normal Android browser.
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Remove the explicit WebView marker from the UA where present.
        String ua = s.getUserAgentString();
        if (ua != null && !ua.isEmpty()) {
            s.setUserAgentString(ua.replace("; wv", ""));
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.BLACK);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                reloadScheduled = false;
                showConnecting("Зареждане…");
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                showBrowserSurface();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ("about:blank".equals(url)) return;
                CookieManager.getInstance().flush();
                showBrowserSurface();
                immersive();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    scheduleReload("Връзката прекъсна. Нов опит…", RETRY_MS);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request != null && request.isForMainFrame()
                        && errorResponse != null
                        && errorResponse.getStatusCode() >= 400) {
                    scheduleReload(
                            "Сървърът отговори " + errorResponse.getStatusCode() + ". Нов опит…",
                            2500
                    );
                }
            }
        });
    }

    private void openConfiguredAddress() {
        if (destroyed || webView == null) return;

        String url = Prefs.getUrl(this).trim();
        if (url.isEmpty()) {
            openSetup();
            return;
        }

        reloadScheduled = false;
        showConnecting("Свързване…");

        // EXACTLY the configured address, just like typing it in a browser and pressing Enter.
        webView.loadUrl(url);
    }

    private void showBrowserSurface() {
        if (destroyed || webView == null) return;
        connectOverlay.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus();
        immersive();
    }

    private void scheduleReload(String message, long delay) {
        if (destroyed || reloadScheduled) return;
        reloadScheduled = true;
        showConnecting(message);
        handler.postDelayed(() -> {
            if (destroyed || webView == null) return;
            reloadScheduled = false;
            webView.stopLoading();
            webView.loadUrl(Prefs.getUrl(PlayerActivity.this).trim());
        }, delay);
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

    private void showConnecting(String message) {
        if (webView != null) webView.setVisibility(View.GONE);
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
                handler.postDelayed(openControlMenuRunnable, BACK_HOLD_MS);
                return true;
            }

            if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(openControlMenuRunnable);
                if (!backHeldTriggered) immersive();
                return true;
            }
        }

        // All other keys go to the WebView/page exactly as in a normal browser.
        return super.dispatchKeyEvent(event);
    }

    private void showPinDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("PIN");
        input.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("MBOX TV")
                .setMessage("Въведи PIN")
                .setView(input)
                .setPositiveButton("ОТВОРИ", null)
                .setNegativeButton("ОТКАЗ", (d, which) -> immersive())
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (Prefs.getPin(this).equals(input.getText().toString())) {
                        dialog.dismiss();
                        showControlMenu();
                    } else {
                        Toast.makeText(this, "Грешен PIN", Toast.LENGTH_SHORT).show();
                        input.selectAll();
                    }
                })
        );

        dialog.setOnDismissListener(d -> immersive());
        dialog.show();
    }

    private void showControlMenu() {
        String[] actions = {
                "НАСТРОЙКИ",
                "РЕСТАРТИРАЙ ПЛЕЪРА",
                "ИЗХОД КЪМ ANDROID",
                "ОТКАЗ"
        };

        new AlertDialog.Builder(this)
                .setTitle("MBOX TV – Управление")
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openSetup();
                            break;
                        case 1:
                            openConfiguredAddress();
                            break;
                        case 2:
                            exitApplication();
                            break;
                        default:
                            immersive();
                            break;
                    }
                })
                .setOnDismissListener(dialog -> immersive())
                .show();
    }

    private void exitApplication() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }

        AppExit.exitToAndroid(this);
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

        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
}
