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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends Activity {
    private static final long RETRY_MS = 1500;
    private static final long BACK_HOLD_MS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private WebView webView;
    private LinearLayout connectOverlay;
    private ImageView customLogo;
    private TextView defaultLogo;
    private TextView connectText;
    private LinearLayout addressBar;
    private EditText playerUrlEdit;
    private Button openUrlButton;
    private Button hideAddressBarButton;

    private boolean pageLoaded = false;
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
        addressBar = findViewById(R.id.addressBar);
        playerUrlEdit = findViewById(R.id.playerUrlEdit);
        openUrlButton = findViewById(R.id.openUrlButton);
        hideAddressBarButton = findViewById(R.id.hideAddressBarButton);

        playerUrlEdit.setText(Prefs.getUrl(this));
        addressBar.setVisibility(View.VISIBLE);
        openUrlButton.setOnClickListener(v -> openUrlFromAddressBar());
        hideAddressBarButton.setOnClickListener(v -> hideAddressBar());

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
        webView.addJavascriptInterface(new ShellBridge(), "MboxAndroid");

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
                if ("about:blank".equals(url)) return;
                CookieManager.getInstance().flush();

                if (Prefs.getIframeMode(PlayerActivity.this)) {
                    // The local shell will call frameLoaded() when the TV iframe is ready.
                    // Show it after a short safety timeout even on unusual WebView builds.
                    handler.postDelayed(PlayerActivity.this::showPlayerSurface, 5000);
                } else {
                    scheduleDirectAutoPlayAttempts();
                }
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
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request != null && request.isForMainFrame() && errorResponse != null
                        && errorResponse.getStatusCode() >= 400) {
                    scheduleReload("Сървърът отговори " + errorResponse.getStatusCode() + ". Нов опит…", 2500);
                }
            }
        });
    }

    private void startSmartConnection() {
        if (destroyed || pageLoaded) return;

        String url = Prefs.getUrl(this).trim();
        if (url.isEmpty()) {
            showConnecting("Въведи TV адрес в горната лента и натисни ОТВОРИ.");
            showAddressBar();
            return;
        }

        showConnecting(hasNetwork() ? "Свързване…" : "Изчакване на мрежата…");
        executor.execute(() -> {
            boolean reachable = hasNetwork() && isReachable(url);
            handler.post(() -> {
                if (destroyed || pageLoaded) return;
                if (reachable) {
                    connectText.setText(Prefs.getIframeMode(this) ? "Отваряне в TV браузъра…" : "Зареждане…");
                    loadTv(url);
                } else {
                    handler.postDelayed(this::startSmartConnection, RETRY_MS);
                }
            });
        });
    }

    private void loadTv(String targetUrl) {
        reloadScheduled = false;
        if (Prefs.getIframeMode(this)) {
            loadIframeShell(targetUrl);
        } else {
            webView.loadUrl(targetUrl);
        }
    }

    private void openUrlFromAddressBar() {
        String url = playerUrlEdit.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Въведи TV адрес.", Toast.LENGTH_LONG).show();
            playerUrlEdit.requestFocus();
            return;
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(this, "Адресът трябва да започва с http:// или https://", Toast.LENGTH_LONG).show();
            playerUrlEdit.requestFocus();
            return;
        }
        String low = url.toLowerCase();
        if (low.contains("localhost") || low.contains("127.0.0.1")) {
            Toast.makeText(this, "На TV Box localhost е самият Android Box. Използвай IP адреса на TV сървъра.", Toast.LENGTH_LONG).show();
            playerUrlEdit.requestFocus();
            return;
        }

        Prefs.setUrl(this, url);
        Prefs.setManualExit(this, false);
        pageLoaded = false;
        reloadScheduled = false;
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
        }
        showConnecting("Свързване…");
        handler.postDelayed(this::startSmartConnection, 200);
    }

    private void showAddressBar() {
        if (addressBar != null) {
            addressBar.setVisibility(View.VISIBLE);
            addressBar.bringToFront();
        }
        if (playerUrlEdit != null && playerUrlEdit.getText().toString().trim().isEmpty()) {
            playerUrlEdit.setText(Prefs.getUrl(this));
        }
        immersive();
    }

    private void hideAddressBar() {
        if (addressBar != null) addressBar.setVisibility(View.GONE);
        if (webView != null) webView.requestFocus();
        immersive();
    }

    /**
     * Loads a local HTML shell but assigns it the TV server origin with loadDataWithBaseURL().
     * For a URL such as http://192.168.1.117/tv/ the shell and iframe therefore share the
     * same host/port. That lets the shell preserve PHP cookies/session and, when allowed by
     * the server, reach the legacy #start/video elements to start playback automatically.
     */
    private void loadIframeShell(String targetUrl) {
        String baseOrigin = originOf(targetUrl);
        String quotedTarget = JSONObject.quote(targetUrl);

        String html = "<!doctype html>" +
                "<html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}" +
                "#tv{position:fixed;inset:0;width:100%;height:100%;border:0;background:#000}</style>" +
                "</head><body>" +
                "<iframe id='tv' allow='autoplay; fullscreen; encrypted-media' allowfullscreen></iframe>" +
                "<script>" +
                "const TARGET=" + quotedTarget + ";" +
                "const f=document.getElementById('tv');" +
                "let announced=false;let downCount=0;" +
                "function announce(){if(!announced){announced=true;try{MboxAndroid.frameLoaded();}catch(e){}}}" +
                "function kick(){" +
                " try{" +
                "  const w=f.contentWindow,d=f.contentDocument;if(!d)return false;" +
                "  const start=d.getElementById('start');" +
                "  const layout=d.querySelector('.layoutHolder');" +
                "  const screen=d.getElementById('screen-layout');" +
                "  const player=d.getElementById('player');" +
                "  if(layout)layout.style.setProperty('display','block','important');" +
                "  if(screen)screen.style.setProperty('display','block','important');" +
                "  if(start)start.style.setProperty('display','none','important');" +
                "  try{if(w.jQuery){w.jQuery(d).off('fullscreenchange webkitfullscreenchange mozfullscreenchange MSFullscreenChange');w.jQuery('#start').off('click');}}catch(e){}" +
                "  if(player){player.style.setProperty('display','block','important');player.setAttribute('playsinline','');player.autoplay=true;" +
                "   const p=player.play();if(p&&p.catch)p.catch(()=>{});}" +
                "  if(player||start){announce();return true;}" +
                " }catch(e){announce();}" +
                " return false;" +
                "}" +
                "f.addEventListener('load',()=>{announce();setTimeout(kick,50);setTimeout(kick,300);setTimeout(kick,1000);setTimeout(kick,2500);});" +
                "setInterval(kick,1000);" +
                "document.addEventListener('keydown',e=>{if(e.key==='Enter'||e.keyCode===13||e.keyCode===23)kick();},true);" +
                "setInterval(()=>{fetch(TARGET,{cache:'no-store',credentials:'include'}).then(r=>{downCount=0;}).catch(()=>{downCount++;if(downCount>=2){try{MboxAndroid.serverDown();}catch(e){}}});},10000);" +
                "f.src=TARGET;" +
                "</script></body></html>";

        webView.loadDataWithBaseURL(baseOrigin, html, "text/html", "UTF-8", null);
    }

    private String originOf(String address) {
        try {
            URL url = new URL(address);
            int port = url.getPort();
            String portPart = port > 0 && port != url.getDefaultPort() ? ":" + port : "";
            return url.getProtocol() + "://" + url.getHost() + portPart + "/";
        } catch (Exception e) {
            return address;
        }
    }

    private final class ShellBridge {
        @JavascriptInterface
        public void frameLoaded() {
            handler.post(PlayerActivity.this::showPlayerSurface);
        }

        @JavascriptInterface
        public void serverDown() {
            handler.post(() -> scheduleReload("TV сървърът не отговаря. Нов опит…", RETRY_MS));
        }
    }

    private void showPlayerSurface() {
        if (destroyed || webView == null) return;
        pageLoaded = true;
        connectOverlay.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus();
        immersive();
    }

    // Direct mode is retained as a fallback for servers that explicitly block iframes.
    private void scheduleDirectAutoPlayAttempts() {
        handler.postDelayed(this::injectDirectAutoPlay, 100);
        handler.postDelayed(this::injectDirectAutoPlay, 500);
        handler.postDelayed(this::injectDirectAutoPlay, 1200);
        handler.postDelayed(this::injectDirectAutoPlay, 2500);
        handler.postDelayed(this::injectDirectAutoPlay, 5000);
    }

    private void injectDirectAutoPlay() {
        if (destroyed || webView == null) return;

        String script =
                "(function(){try{" +
                "if(!window.__MBOX_TV_V3_INSTALLED){window.__MBOX_TV_V3_INSTALLED=true;" +
                "window.__mboxKick=function(){" +
                "var start=document.getElementById('start'),layout=document.querySelector('.layoutHolder')," +
                "screen=document.getElementById('screen-layout'),player=document.getElementById('player');" +
                "if(layout)layout.style.setProperty('display','block','important');" +
                "if(screen)screen.style.setProperty('display','block','important');" +
                "if(start)start.style.setProperty('display','none','important');" +
                "if(player){player.style.setProperty('display','block','important');player.setAttribute('playsinline','');player.autoplay=true;" +
                "var p=player.play();if(p&&p.catch)p.catch(function(){});}" +
                "return !!(player||start);};" +
                "try{if(window.jQuery){jQuery(document).off('fullscreenchange webkitfullscreenchange mozfullscreenchange MSFullscreenChange');jQuery('#start').off('click');}}catch(e){}" +
                "try{new MutationObserver(function(){window.__mboxKick();}).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}" +
                "setInterval(function(){window.__mboxKick();},1000);}" +
                "return window.__mboxKick&&window.__mboxKick()?'ready':'waiting';" +
                "}catch(e){return 'error';}})();";

        webView.evaluateJavascript(script, value -> {
            if (destroyed) return;
            if (value != null && value.contains("ready")) showPlayerSurface();
        });
    }

    private void scheduleReload(String message, long delay) {
        if (destroyed || reloadScheduled) return;
        reloadScheduled = true;
        pageLoaded = false;
        showConnecting(message);
        handler.postDelayed(this::reloadFromStart, delay);
    }

    private void reloadFromStart() {
        if (destroyed) return;
        reloadScheduled = false;
        pageLoaded = false;
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
        }
        handler.postDelayed(this::startSmartConnection, 250);
    }

    private boolean isReachable(String address) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(address);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(1600);
            conn.setReadTimeout(1600);
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

        if (event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            if (Prefs.getIframeMode(this)) {
                webView.evaluateJavascript("(function(){try{var f=document.getElementById('tv');if(f&&f.contentDocument){var p=f.contentDocument.getElementById('player');if(p){p.play();return 'ok';}}}catch(e){}return 'no';})()", null);
            } else {
                injectDirectAutoPlay();
            }
        }

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

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (Prefs.getPin(this).equals(input.getText().toString())) {
                dialog.dismiss();
                showControlMenu();
            } else {
                Toast.makeText(this, "Грешен PIN", Toast.LENGTH_SHORT).show();
                input.selectAll();
            }
        }));
        dialog.setOnDismissListener(d -> immersive());
        dialog.show();
    }

    private void showControlMenu() {
        String[] actions = {
                "АДРЕСНА ЛЕНТА",
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
                            showAddressBar();
                            break;
                        case 1:
                            openSetup();
                            break;
                        case 2:
                            reloadFromStart();
                            break;
                        case 3:
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
        executor.shutdownNow();

        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception ignored) {}
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
        if (pageLoaded && !Prefs.getIframeMode(this)) scheduleDirectAutoPlayAttempts();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
