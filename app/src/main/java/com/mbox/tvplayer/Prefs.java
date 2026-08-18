package com.mbox.tvplayer;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "mbox_tv_prefs";
    private static final String KEY_URL = "tv_url";
    private static final String KEY_LOGO_URI = "logo_uri";
    private static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_PIN = "settings_pin";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getUrl(Context c) { return p(c).getString(KEY_URL, ""); }
    public static void setUrl(Context c, String value) { p(c).edit().putString(KEY_URL, value).apply(); }

    public static String getLogoUri(Context c) { return p(c).getString(KEY_LOGO_URI, ""); }
    public static void setLogoUri(Context c, String value) { p(c).edit().putString(KEY_LOGO_URI, value).apply(); }

    public static boolean getAutostart(Context c) { return p(c).getBoolean(KEY_AUTOSTART, true); }
    public static void setAutostart(Context c, boolean value) { p(c).edit().putBoolean(KEY_AUTOSTART, value).apply(); }

    public static String getPin(Context c) { return p(c).getString(KEY_PIN, "1234"); }
    public static void setPin(Context c, String value) { p(c).edit().putString(KEY_PIN, value).apply(); }
}
