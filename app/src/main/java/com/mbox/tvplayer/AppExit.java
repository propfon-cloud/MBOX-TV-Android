package com.mbox.tvplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

public final class AppExit {
    private AppExit() {}

    public static void exitToAndroid(Activity activity) {
        Prefs.setManualExit(activity, true);

        PackageManager pm = activity.getPackageManager();
        try {
            // If MBOX TV was chosen as the Home app, remove its own preferred state.
            pm.clearPackagePreferredActivities(activity.getPackageName());
        } catch (Exception ignored) {}

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        boolean homeStillPointsToMbox = false;
        try {
            ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            homeStillPointsToMbox = resolved != null
                    && resolved.activityInfo != null
                    && activity.getPackageName().equals(resolved.activityInfo.packageName);
        } catch (Exception ignored) {}

        boolean externalScreenOpened = false;
        if (!homeStillPointsToMbox) {
            try {
                activity.startActivity(home);
                externalScreenOpened = true;
            } catch (Exception ignored) {}
        }

        if (!externalScreenOpened) {
            externalScreenOpened = openHomeSettings(activity);
        }

        if (!externalScreenOpened) {
            try {
                Intent settings = new Intent(Settings.ACTION_SETTINGS);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(settings);
            } catch (Exception ignored) {}
        }

        activity.finishAffinity();

        // Give Android enough time to display Home/Settings, then terminate MBOX TV.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }, 700);
    }

    public static boolean openHomeSettings(Activity activity) {
        try {
            Intent homeSettings = new Intent(Settings.ACTION_HOME_SETTINGS);
            homeSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(homeSettings);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
