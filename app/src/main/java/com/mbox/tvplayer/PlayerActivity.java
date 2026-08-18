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
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

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
                connectText.setText("Зареждане…");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                scheduleAutoPlayAttempts();
                immersive();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    pageLoaded = false;
                    showConnecting("Връзката прекъсна. Нов опит…");
                    handler.postDelayed(PlayerActivity.this::reloadFromStart, RETRY_MS);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request != null && request.isForMainFrame() && errorResponse != null
                        && errorResponse.getStatusCode() >= 400) {
                    pageLoaded = false;
                    showConnecting("Сървърът отговори " + errorResponse.getStatusCode() + ". Нов опит…");
                    handler.postDelayed(PlayerActivity.this::reloadFromStart, 2500);
                }
            }
        });
    }

    /**
     * The legacy /tv/player page expects a browser fullscreen click. MBOX TV is already
     * a fullscreen Android application, so we make the player visible and start HTML5
     * video directly. We also keep a MutationObserver + timer inside the page so this
     * survives redirects, slow PHP rendering and schedule/player DOM updates.
     */
    private void scheduleAutoPlayAttempts() {
        handler.postDelayed(this::injectAutoPlay, 100);
        handler.postDelayed(this::injectAutoPlay, 500);
        handler.postDelayed(this::injectAutoPlay, 1200);
        handler.postDelayed(this::injectAutoPlay, 2500);
        handler.postDelayed(this::injectAutoPlay, 5000);
    }

    private void injectAutoPlay() {
        if (destroyed || webView == null) return;

        String script =
                "(function(){" +
                "try{" +
                "if(!window.__MBOX_TV_V2_INSTALLED){" +
                "window.__MBOX_TV_V2_INSTALLED=true;" +
                "window.__mboxKick=function(){" +
                "var start=document.getElementById('start');" +
                "var layout=document.querySelector('.layoutHolder');" +
                "var screen=document.getElementById('screen-layout');" +
                "var player=document.getElementById('player');" +
                "if(layout){layout.style.setProperty('display','block','important');}" +
                "if(screen){screen.style.setProperty('display','block','important');}" +
                "if(start){start.style.setProperty('display','none','important');}" +
                "if(player){" +
                "player.style.setProperty('display','block','important');" +
                "player.setAttribute('playsinline','');" +
                "player.autoplay=true;" +
                "var p=player.play();" +
                "if(p&&typeof p.catch==='function'){p.catch(function(){});}" +
                "}" +
                "return !!(player||start);" +
                "};" +
                "try{if(window.jQuery){jQuery(document).off('fullscreenchange webkitfullscreenchange mozfullscreenchange MSFullscreenChange');}}catch(e){}" +
                "try{new MutationObserver(function(){window.__mboxKick();}).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}" +
                "setInterval(function(){window.__mboxKick();},1000);" +
                "document.addEventListener('keydown',function(e){if(e.key==='Enter'||e.keyCode===13||e.keyCode===23){window.__mboxKick();}},true);" +
                "}" +
                "return window.__mboxKick&&window.__mboxKick()?'ready':'waiting';" +
                "}catch(e){return 'error';}" +
                "})();";

        webView.evaluateJavascript(script, value -> {
            if (destroyed) return;
            if (value != null && value.contains("ready")) {
                pageLoaded = true;
                connectOverlay.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.requestFocus();
                immersive();
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

    private void reloadFromStart() {
        if (destroyed) return;
        pageLoaded = false;
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
        }
        handler.postDelayed(this::startSmartConnection, 200);
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
                if (!backHeldTriggered) immersive();
                return true;
            }
        }

        // Remote OK/ENTER is also a manual fallback for unusual WebView builds.
        if (event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            injectAutoPlay();
            return true;
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
        if (pageLoaded) scheduleAutoPlayAttempts();
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
