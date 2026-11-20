package com.gmcorporate.cabine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Écran d'activation : à la première ouverture sur un téléphone, le code d'activation GM Corporate est demandé.
 * Le code n'est pas stocké en clair : seule son empreinte (SHA-256 répété 10 000 fois) figure ici.
 * L'activation est liée au téléphone (ANDROID_ID) et n'est jamais sauvegardée ni transférée (allowBackup=false).
 *
 * © 2026 GM Corporate — Développeur : Giresse Mulaja.
 */
public class ActivationActivity extends Activity {

    private static final String CODE_HASH = "b5dcd88b57cab46ea938476a61bc0f54e52b46c364a1b2bcfc14e2f9b4721d1e";
    private static final String PREFS = "gm_activation";
    private static final int MAX_TRIES = 5;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isActivated(this)) {
            openApp();
            return;
        }
        setContentView(R.layout.activity_activation);

        EditText code = findViewById(R.id.code);
        TextView error = findViewById(R.id.error);
        Button activate = findViewById(R.id.activate);
        CheckBox show = findViewById(R.id.show);

        show.setOnCheckedChangeListener((b, checked) -> {
            code.setInputType(InputType.TYPE_CLASS_TEXT | (checked
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            code.setSelection(code.getText().length());
        });
        code.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { activate.performClick(); return true; }
            return false;
        });

        activate.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            long until = p.getLong("lockedUntil", 0);
            long now = System.currentTimeMillis();
            if (now < until) {
                error.setText("Trop d'essais. Réessayez dans " + ((until - now) / 1000 + 1) + " s.");
                return;
            }
            String entered = code.getText().toString().trim();
            if (entered.isEmpty()) { error.setText("Entrez le code d'activation."); return; }
            // Le calcul de l'empreinte se fait hors du fil principal : l'écran reste fluide.
            activate.setEnabled(false);
            activate.setText("Vérification…");
            error.setText("");
            new Thread(() -> {
                boolean ok = CODE_HASH.equals(hash(entered));
                runOnUiThread(() -> {
                    activate.setEnabled(true);
                    activate.setText("Activer l'application");
                    onResult(ok, code, error);
                });
            }).start();
        });
    }

    private void onResult(boolean ok, EditText code, TextView error) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (ok) {
            p.edit()
                    .putString("device", deviceTag(this))
                    .putLong("activatedAt", now)
                    .putInt("tries", 0)
                    .apply();
            openApp();
        } else {
            int tries = p.getInt("tries", 0) + 1;
            SharedPreferences.Editor ed = p.edit().putInt("tries", tries);
            if (tries >= MAX_TRIES) ed.putLong("lockedUntil", now + 60_000L * Math.min(10, tries - MAX_TRIES + 1));
            ed.apply();
            code.setText("");
            error.setText(tries >= MAX_TRIES
                    ? "Code incorrect. Activation bloquée quelques minutes."
                    : "Code d'activation incorrect.");
        }
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    /** Vrai si l'application a été activée sur CE téléphone. */
    static boolean isActivated(Context c) {
        String saved = c.getSharedPreferences(PREFS, MODE_PRIVATE).getString("device", null);
        return saved != null && saved.equals(deviceTag(c));
    }

    private static String deviceTag(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        return sha256Hex("gmc-device|" + c.getPackageName() + "|" + (id == null ? "" : id));
    }

    private static String hash(String code) {
        String h = "gm-corporate-activation|" + code;
        for (int i = 0; i < 10000; i++) h = sha256Hex("gmc" + h);
        return h;
    }

    private static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[64];
            for (int i = 0; i < 32; i++) {
                out[2 * i] = HEX[(d[i] >> 4) & 0xF];
                out[2 * i + 1] = HEX[d[i] & 0xF];
            }
            return new String(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
