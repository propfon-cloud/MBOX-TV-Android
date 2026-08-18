package com.mbox.tvplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!(Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))) {
            return;
        }

        // A real device reboot/update clears the temporary manual-exit lock.
        Prefs.setManualExit(context, false);

        if (!Prefs.getAutostart(context)) return;

        try {
            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
        } catch (Exception ignored) {
            // Some Android builds block background Activity starts.
            // Direct flavor can still be selected as the Home/Launcher if required.
        }
    }
}
