package com.rademacher.muelles;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * Rademacher Muelles: app del chófer.
 * - Abre la vista del chófer del servidor de muelles (/chofer) en un WebView.
 * - Mantiene la pantalla encendida mientras la app está abierta.
 * - Puente JavaScript "RademacherApp": vibración y notificación nativa cuando llega
 *   el turno o un mensaje de oficina, aunque la app esté en segundo plano.
 * - La URL del servidor se fija al compilar (SERVER_URL) o se pide en el primer arranque.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "rademacher";
    private static final String KEY_SERVER = "server_url";
    private static final String CHANNEL_ID = "turnos";

    private WebView webView;
    private String serverUrl;
    private volatile boolean inForeground;
    private boolean errorShown;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int brand = Color.parseColor("#0B3D91");
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Color.parseColor("#F3F4F6"));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        getWindow().setStatusBarColor(brand);
        getWindow().setNavigationBarColor(Color.BLACK);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                 // recuerda código e idioma del chófer
        s.setMediaPlaybackRequiresUserGesture(false); // pitido de aviso sin tocar la pantalla
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);

        webView.addJavascriptInterface(new Bridge(this), "RademacherApp");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                Uri base = Uri.parse(serverUrl);
                if (uri.getHost() != null && uri.getHost().equals(base.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri)); // enlaces ajenos: navegador
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showConnectionError();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                errorShown = false;
            }
        });

        createChannel();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        serverUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SERVER, BuildConfig.SERVER_URL);
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else if (serverUrl == null || serverUrl.isEmpty()) {
            askServer();
        } else {
            openDriverPage();
        }
    }

    private void openDriverPage() {
        webView.loadUrl(serverUrl + "/chofer");
    }

    /** Pide la dirección del servidor (primer arranque o "Cambiar servidor"). */
    private void askServer() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.server_hint);
        if (serverUrl != null) input.setText(serverUrl);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.server_title)
                .setMessage(R.string.server_msg)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String url = input.getText().toString().trim();
                    while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
                    if (url.isEmpty()) { askServer(); return; }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
                    serverUrl = url;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SERVER, url).apply();
                    openDriverPage();
                })
                .show();
    }

    private void showConnectionError() {
        if (errorShown || isFinishing()) return;
        errorShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(R.string.error_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.retry, (d, w) -> { errorShown = false; openDriverPage(); })
                .setNegativeButton(R.string.change_server, (d, w) -> { errorShown = false; askServer(); })
                .show();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 150, 400, 150, 400});
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** Métodos accesibles desde la página web como window.RademacherApp.*. */
    private class Bridge {
        private final Context ctx;
        Bridge(Context ctx) { this.ctx = ctx; }

        @JavascriptInterface
        public void alert(String text) {
            vibrate();
            if (!inForeground) notifyDriver(text);
        }

        private void vibrate() {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 150, 400, 150, 400}, -1));
        }

        private void notifyDriver(String text) {
            if (Build.VERSION.SDK_INT >= 33
                    && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Rademacher")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            ctx.getSystemService(NotificationManager.class).notify(1, n);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        inForeground = true;
        getSystemService(NotificationManager.class).cancel(1);
        // Al volver a la app, refresca el estado por si cambió mientras estaba en segundo plano.
        if (webView != null) webView.evaluateJavascript("typeof load==='function'&&load()", null);
    }

    @Override
    protected void onPause() {
        // Nota: no se llama a webView.onPause()/pauseTimers() para que la página siga
        // recibiendo actualizaciones en segundo plano y pueda avisar del turno.
        inForeground = false;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true); // no cerrar: así sigue escuchando el aviso de turno
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
