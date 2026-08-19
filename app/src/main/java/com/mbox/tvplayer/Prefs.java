package com.mbox.tvplayer;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "mbox_tv_prefs";
    private static final String KEY_URL = "tv_url";
    private static final String KEY_LOGO_URI = "logo_uri";
    private static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_PIN = "settings_pin";
    private static final String KEY_IFRAME_MODE = "iframe_mode";
    private static final String KEY_MANUAL_EXIT = "manual_exit";
    private static final String KEY_SETUP_REVISION = "setup_revision";
    public static final int CURRENT_SETUP_REVISION = 6;

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getUrl(Context c) { return p(c).getString(KEY_URL, "http://192.168.1.117/tv/"); }
    public static void setUrl(Context c, String value) { p(c).edit().putString(KEY_URL, value).apply(); }

    public static String getLogoUri(Context c) { return p(c).getString(KEY_LOGO_URI, ""); }
    public static void setLogoUri(Context c, String value) { p(c).edit().putString(KEY_LOGO_URI, value).apply(); }

    public static boolean getAutostart(Context c) { return p(c).getBoolean(KEY_AUTOSTART, true); }
    public static void setAutostart(Context c, boolean value) { p(c).edit().putBoolean(KEY_AUTOSTART, value).apply(); }

    public static String getPin(Context c) { return p(c).getString(KEY_PIN, "1234"); }
    public static void setPin(Context c, String value) { p(c).edit().putString(KEY_PIN, value).apply(); }

    public static boolean getIframeMode(Context c) { return p(c).getBoolean(KEY_IFRAME_MODE, false); }
    public static void setIframeMode(Context c, boolean value) { p(c).edit().putBoolean(KEY_IFRAME_MODE, value).apply(); }

    public static boolean getManualExit(Context c) { return p(c).getBoolean(KEY_MANUAL_EXIT, false); }
    public static void setManualExit(Context c, boolean value) { p(c).edit().putBoolean(KEY_MANUAL_EXIT, value).apply(); }

    public static boolean needsSetupAfterUpdate(Context c) {
        return p(c).getInt(KEY_SETUP_REVISION, 0) < CURRENT_SETUP_REVISION;
    }

    public static void markSetupShownForCurrentVersion(Context c) {
        p(c).edit().putInt(KEY_SETUP_REVISION, CURRENT_SETUP_REVISION).apply();
    }
}
