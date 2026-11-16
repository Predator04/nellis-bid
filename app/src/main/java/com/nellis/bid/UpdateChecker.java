package com.nellis.bid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * GitHub Releases update checker — NOT auto-installing.
 * Checks for a new version and NOTIFIES; only when the user taps Download does it
 * download (with a live progress bar + speed) and then open the installer.
 */
public class UpdateChecker {
    private static final String REPO = "Predator04/nellis-bid";
    private static final String PREFS = "updater";
    private static final long COOLDOWN_MS = 12 * 3600 * 1000L;
    private static final String KEY_PENDING = "pending_code";
    private static final String KEY_PENDING_URL = "pending_url";

    /** Version code of a pending update (0 = none). */
    public static int pendingVersion(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PENDING, 0);
    }

    /** APK URL of a pending update, or null if none. */
    public static String pendingUrl(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING_URL, null);
    }

    public static void check(Context ctx, boolean manual) {
        check(ctx, manual, null);
    }

    public static void check(Context ctx, boolean manual, Runnable onDone) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = sp.getLong("last_check", 0);
        if (!manual && System.currentTimeMillis() - last < COOLDOWN_MS) {
            if (onDone != null) runOnUi(ctx, onDone);
            return;
        }
        sp.edit().putLong("last_check", System.currentTimeMillis()).apply();

        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                c.setRequestProperty("User-Agent", "NellisBid");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() != 200) {
                    if (manual) toast(ctx, "Update check failed (HTTP " + c.getResponseCode() + ")");
                    if (onDone != null) runOnUi(ctx, onDone);
                    return;
                }
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                c.disconnect();

                JSONObject rel = new JSONObject(sb.toString());
                String tag = rel.optString("tag_name", "v0").replaceFirst("^v", "");
                int latestCode;
                try { latestCode = Integer.parseInt(tag); } catch (Exception e) { latestCode = 0; }
                int myCode = BuildConfig.VERSION_CODE;

                if (latestCode <= myCode) {
                    sp.edit().putInt(KEY_PENDING, 0).remove(KEY_PENDING_URL).apply();
                    if (manual) toast(ctx, "You're up to date (v" + myCode + ")");
                    if (onDone != null) runOnUi(ctx, onDone);
                    return;
                }
                if (sp.getInt("skipped", 0) == latestCode && !manual) {
                    if (onDone != null) runOnUi(ctx, onDone);
                    return;
                }

                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url");
                            break;
                        }
                    }
                }
                if (apkUrl == null) {
                    if (manual) toast(ctx, "No APK in the latest release");
                    if (onDone != null) runOnUi(ctx, onDone);
                    return;
                }

                final String finalUrl = apkUrl;
                final int finalCode = latestCode;
                sp.edit().putInt(KEY_PENDING, latestCode).putString(KEY_PENDING_URL, apkUrl).apply();
                if (onDone != null) runOnUi(ctx, onDone);
                runOnUi(ctx, () -> new AlertDialog.Builder(ctx)
                        .setTitle("New update available")
                        .setMessage("Version v" + finalCode + " is available (you're on v" + myCode + "). Download it now?")
                        .setPositiveButton("Download", (d, w) -> download(ctx, finalUrl))
                        .setNegativeButton("Later", null)
                        .setNeutralButton("Skip this version", (d, w) ->
                                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                        .putInt("skipped", finalCode)
                                        .putInt(KEY_PENDING, 0)
                                        .remove(KEY_PENDING_URL)
                                        .apply())
                        .show());
            } catch (Exception e) {
                if (manual) toast(ctx, "Update check failed: " + e.getMessage());
                if (onDone != null) runOnUi(ctx, onDone);
            }
        }).start();
    }

    /** Download the APK with a live progress bar + speed readout, then open the installer. */
    public static void download(Context ctx, String apkUrl) {
        ProgressBar bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        TextView txt = new TextView(ctx);
        txt.setGravity(Gravity.CENTER);
        txt.setText("0% · starting…");
        int pad = (int) (24 * ctx.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = pad / 2;
        layout.addView(txt, tp);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("Downloading update")
                .setView(layout)
                .setCancelable(false)
                .create();
        dialog.show();

        new Thread(() -> {
            try {
                File dir = new File(ctx.getCacheDir(), "updates");
                dir.mkdirs();
                File apk = new File(dir, "update.apk");

                HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
                c.setRequestProperty("User-Agent", "NellisBid");
                c.setConnectTimeout(30000);
                c.setReadTimeout(120000);
                c.connect();
                long total = c.getContentLengthLong();
                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);

                long downloaded = 0;
                long start = System.currentTimeMillis();
                long lastUpdate = 0;
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate >= 300) {
                        lastUpdate = now;
                        double secs = Math.max(0.001, (now - start) / 1000.0);
                        double speed = downloaded / secs; // bytes/sec
                        int pct = total > 0 ? (int) (downloaded * 100 / total) : 0;
                        final int fp = pct;
                        final String status = pct + "% · " + fmtSpeed(speed)
                                + " · " + fmtSize(downloaded) + (total > 0 ? " / " + fmtSize(total) : "");
                        runOnUi(ctx, () -> {
                            bar.setProgress(fp);
                            txt.setText(status);
                        });
                    }
                }
                out.close();
                in.close();
                c.disconnect();

                runOnUi(ctx, () -> {
                    try { dialog.dismiss(); } catch (Exception ignored) {}
                    install(ctx, apk);
                });
            } catch (Exception e) {
                runOnUi(ctx, () -> {
                    try { dialog.dismiss(); } catch (Exception ignored) {}
                    toast(ctx, "Download failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void install(Context ctx, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            toast(ctx, "Install failed: " + e.getMessage());
        }
    }

    private static String fmtSpeed(double bytesPerSec) {
        if (bytesPerSec >= 1024 * 1024) return String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024 * 1024));
        if (bytesPerSec >= 1024) return String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024);
        return String.format(Locale.US, "%.0f B/s", bytesPerSec);
    }

    private static String fmtSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        if (bytes >= 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private static void toast(Context ctx, String msg) {
        runOnUi(ctx, () -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }

    private static void runOnUi(Context ctx, Runnable r) {
        if (ctx instanceof android.app.Activity) {
            ((android.app.Activity) ctx).runOnUiThread(r);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
        }
    }
}
