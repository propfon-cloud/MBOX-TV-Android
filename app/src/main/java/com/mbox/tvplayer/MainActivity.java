package com.mbox.tvplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        route();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        route();
    }

    private void route() {
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
