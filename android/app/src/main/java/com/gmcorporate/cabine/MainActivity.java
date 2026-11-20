package com.gmcorporate.cabine;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Application GM Corporate : affiche l'outil (jetons ou registre) embarqué dans l'APK, sans internet.
 * Les pages sont servies depuis https://appassets.androidplatform.net/ (origine sécurisée) pour que
 * la caméra, le stockage local et IndexedDB fonctionnent comme dans Chrome.
 * Le pont « GMAndroid » donne à la page l'enregistrement de fichiers et l'impression natifs.
 *
 * © 2026 GM Corporate — Développeur : Giresse Mulaja.
 */
public class MainActivity extends Activity {

    private static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQ_FILE = 11;
    private static final int REQ_CAM_PERMISSION_WEB = 21;
    private static final int REQ_CAM_PERMISSION_PHOTO = 22;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private WebChromeClient.FileChooserParams pendingParams;
    private Uri photoUri;
    private PermissionRequest pendingWebPermission;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ActivationActivity.isActivated(this)) {
            startActivity(new Intent(this, ActivationActivity.class));
            finish();
            return;
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.gm_bg));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsCamera = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                    }
                    if (!wantsCamera) { request.deny(); return; }
                    if (hasCamera()) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingWebPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM_PERMISSION_WEB);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                pendingParams = params;
                boolean image = false;
                for (String t : params.getAcceptTypes()) if (t != null && t.startsWith("image")) image = true;
                if (image && params.isCaptureEnabled()) {
                    if (hasCamera()) launchCamera();
                    else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM_PERMISSION_PHOTO);
                } else {
                    launchPicker(params);
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "GMAndroid");
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOME);
    }

    // ---------- Caméra et choix de fichier ----------

    private boolean hasCamera() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "photos");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, "photo-" + System.currentTimeMillis() + ".jpg");
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_FILE);
        } catch (Exception e) {
            photoUri = null;
            if (pendingParams != null) launchPicker(pendingParams); else cancelFile();
        }
    }

    private void launchPicker(WebChromeClient.FileChooserParams params) {
        photoUri = null;
        try {
            Intent i = params.createIntent();
            String[] types = params.getAcceptTypes();
            if (types.length > 0 && types[0] != null && types[0].contains("json")) {
                i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
            }
            startActivityForResult(Intent.createChooser(i, "Choisir un fichier"), REQ_FILE);
        } catch (Exception e) {
            cancelFile();
        }
    }

    private void cancelFile() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = null;
        pendingParams = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (photoUri != null) result = new Uri[]{photoUri};
            else if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
        pendingParams = null;
        photoUri = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_CAM_PERMISSION_WEB && pendingWebPermission != null) {
            if (granted) pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        } else if (requestCode == REQ_CAM_PERMISSION_PHOTO) {
            if (granted) launchCamera();
            else {
                Toast.makeText(this, "Sans la caméra, choisissez une photo existante.", Toast.LENGTH_LONG).show();
                if (pendingParams != null) launchPicker(pendingParams); else cancelFile();
            }
        }
    }

    // ---------- Pont JavaScript : fichiers et impression ----------

    private final Map<Integer, PendingFile> files = new HashMap<>();
    private int nextFileId = 1;

    private static class PendingFile {
        String name, mime;
        File tmp;
        OutputStream out;
    }

    private class Bridge {
        @JavascriptInterface
        public synchronized int beginFile(String name, String mime) {
            try {
                PendingFile p = new PendingFile();
                p.name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                p.mime = mime;
                File dir = new File(getCacheDir(), "exports");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                p.tmp = new File(dir, p.name);
                p.out = new FileOutputStream(p.tmp);
                int id = nextFileId++;
                files.put(id, p);
                return id;
            } catch (Exception e) {
                toast("Impossible de créer le fichier.");
                return -1;
            }
        }

        @JavascriptInterface
        public synchronized void appendChunk(int id, String base64) {
            PendingFile p = files.get(id);
            if (p == null) return;
            try { p.out.write(Base64.decode(base64, Base64.DEFAULT)); } catch (Exception e) { toast("Erreur d'écriture du fichier."); }
        }

        @JavascriptInterface
        public synchronized void endFile(int id) {
            PendingFile p = files.remove(id);
            if (p == null) return;
            try { p.out.close(); } catch (Exception ignored) { }
            runOnUiThread(() -> deliver(p));
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(MainActivity.this::printPage);
        }

        @JavascriptInterface
        public String platform() {
            return "android";
        }
    }

    /** Copie le fichier dans Téléchargements/GM Corporate puis propose de l'ouvrir ou de le partager. */
    private void deliver(PendingFile p) {
        String where = saveToDownloads(p);
        toast(where != null ? "Enregistré dans " + where : "Fichier prêt");
        Uri share = FileProvider.getUriForFile(this, getPackageName() + ".files", p.tmp);
        Intent i;
        if ("application/pdf".equals(p.mime)) {
            i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(share, p.mime);
        } else {
            i = new Intent(Intent.ACTION_SEND);
            i.setType(p.mime);
            i.putExtra(Intent.EXTRA_STREAM, share);
            i.putExtra(Intent.EXTRA_SUBJECT, p.name);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "application/pdf".equals(p.mime) ? "Ouvrir le PDF avec" : "Envoyer la sauvegarde vers"));
        } catch (ActivityNotFoundException ignored) { }
    }

    private String saveToDownloads(PendingFile p) {
        try (InputStream in = new FileInputStream(p.tmp)) {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, p.name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, p.mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GM Corporate");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return null;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { copy(in, out); }
                return "Téléchargements/GM Corporate";
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (OutputStream out = new FileOutputStream(new File(dir, p.name))) { copy(in, out); }
                return "le dossier de l'application";
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private void printPage() {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        String job = getString(R.string.app_name) + " — GM Corporate";
        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(job);
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        pm.print(job, adapter, attrs);
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    // ---------- Cycle de vie ----------

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        // Le bouton Retour met l'application en arrière-plan au lieu de la fermer : rien n'est perdu.
        moveTaskToBack(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("GMAndroid");
            web.destroy();
        }
        super.onDestroy();
    }
}
