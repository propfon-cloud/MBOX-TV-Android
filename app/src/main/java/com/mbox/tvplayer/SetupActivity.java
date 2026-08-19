package com.mbox.tvplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SetupActivity extends Activity {
    private static final int PICK_LOGO = 1001;

    private EditText urlEdit;
    private EditText pinEdit;
    private CheckBox autoStartCheck;
    private CheckBox iframeModeCheck;
    private TextView logoStatus;
    private ScrollView setupScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        urlEdit = findViewById(R.id.urlEdit);
        pinEdit = findViewById(R.id.pinEdit);
        autoStartCheck = findViewById(R.id.autoStartCheck);
        iframeModeCheck = findViewById(R.id.iframeModeCheck);
        logoStatus = findViewById(R.id.logoStatus);
        setupScroll = findViewById(R.id.setupScroll);

        Button chooseLogoButton = findViewById(R.id.chooseLogoButton);
        Button resetLogoButton = findViewById(R.id.resetLogoButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button restartButton = findViewById(R.id.restartButton);
        Button exitButton = findViewById(R.id.exitButton);
        Button homeSettingsButton = findViewById(R.id.homeSettingsButton);

        String savedUrl = Prefs.getUrl(this).trim();
        urlEdit.setText(savedUrl.isEmpty() ? "http://192.168.1.117/tv/" : savedUrl);

        String savedPin = Prefs.getPin(this).trim();
        pinEdit.setText(savedPin.isEmpty() ? "1234" : savedPin);

        // Quick Setup defaults: recommended options are ON.
        autoStartCheck.setChecked(true);
        iframeModeCheck.setChecked(false);
        updateLogoStatus();

        chooseLogoButton.setOnClickListener(v -> chooseLogo());
        resetLogoButton.setOnClickListener(v -> {
            File logoFile = new File(getFilesDir(), "mbox_connect_logo");
            if (logoFile.exists()) logoFile.delete();
            Prefs.setLogoUri(this, "");
            updateLogoStatus();
        });
        saveButton.setOnClickListener(v -> saveAndStart());
        restartButton.setOnClickListener(v -> startPlayerWithoutSaving());
        exitButton.setOnClickListener(v -> AppExit.exitToAndroid(this));
        homeSettingsButton.setOnClickListener(v -> openHomeSettings());

        // TV address is outside the ScrollView and can never be scrolled away.
        // Put focus on it at setup start.
        urlEdit.post(() -> {
            urlEdit.requestFocus();
            urlEdit.setSelection(urlEdit.getText().length());
            setupScroll.scrollTo(0, 0);
        });
    }

    private void chooseLogo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_LOGO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_LOGO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            File logoFile = new File(getFilesDir(), "mbox_connect_logo");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(logoFile, false)) {
                if (in == null) throw new IllegalStateException("Cannot open selected image");
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                Prefs.setLogoUri(this, Uri.fromFile(logoFile).toString());
                updateLogoStatus();
            } catch (Exception e) {
                toast("Не успях да запазя логото. Опитай друга картинка.");
            }
        }
    }

    private void updateLogoStatus() {
        String uri = Prefs.getLogoUri(this);
        logoStatus.setText(uri.isEmpty() ? "Стандартно MBOX TV лого" : "Избрано собствено лого");
    }

    private boolean validateAndSave() {
        String url = urlEdit.getText().toString().trim();
        String pin = pinEdit.getText().toString().trim();

        if (url.isEmpty()) {
            toast("Въведи TV адрес.");
            urlEdit.requestFocus();
            return false;
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            toast("Адресът трябва да започва с http:// или https://");
            urlEdit.requestFocus();
            return false;
        }
        String low = url.toLowerCase();
        if (low.contains("localhost") || low.contains("127.0.0.1")) {
            toast("На TV Box localhost означава самия бокс. Използвай IP адреса на TV сървъра, например 192.168.1.117.");
            urlEdit.requestFocus();
            return false;
        }
        if (pin.length() < 4) {
            toast("PIN трябва да е поне 4 цифри.");
            pinEdit.requestFocus();
            return false;
        }

        Prefs.setUrl(this, url);
        Prefs.setPin(this, pin);
        Prefs.setAutostart(this, autoStartCheck.isChecked());
        Prefs.setIframeMode(this, iframeModeCheck.isChecked());
        Prefs.setManualExit(this, false);
        Prefs.markSetupShownForCurrentVersion(this);
        return true;
    }

    private void saveAndStart() {
        if (!validateAndSave()) return;
        launchPlayer();
    }

    private void startPlayerWithoutSaving() {
        if (!validateAndSave()) return;
        launchPlayer();
    }

    private void launchPlayer() {
        Intent player = new Intent(this, PlayerActivity.class);
        player.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(player);
        finish();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                toast("Този TV Box няма директно меню за избор на Home приложение.");
            }
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
