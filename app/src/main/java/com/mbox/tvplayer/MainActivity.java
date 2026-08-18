package com.mbox.tvplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        route(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        route(intent);
    }

    private void route(Intent sourceIntent) {
        boolean launchedAsHome = sourceIntent != null && sourceIntent.hasCategory(Intent.CATEGORY_HOME);

        // After a manual EXIT, never trap the user back in MBOX TV through HOME.
        if (Prefs.getManualExit(this) && launchedAsHome) {
            if (!AppExit.openHomeSettings(this)) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
            finish();
            return;
        }

        // A deliberate launch from the Android app list re-enables the application.
        if (!launchedAsHome) {
            Prefs.setManualExit(this, false);
        }

        Intent intent;
        if (Prefs.getUrl(this).trim().isEmpty()) {
            intent = new Intent(this, SetupActivity.class);
        } else {
            intent = new Intent(this, PlayerActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
