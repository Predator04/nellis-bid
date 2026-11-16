package com.nellis.bid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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

/** GitHub Releases self-updater (unauthenticated API, 12h cooldown, skip-version). */
public class UpdateChecker {
    private static final String REPO = "Predator04/nellis-bid";
    private static final String PREFS = "updater";
    private static final long COOLDOWN_MS = 12 * 3600 * 1000L;

    public static void check(Context ctx, boolean manual) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = sp.getLong("last_check", 0);
        if (!manual && System.currentTimeMillis() - last < COOLDOWN_MS) return;
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
                    if (manual) toast(ctx, "You're up to date (v" + myCode + ")");
                    return;
                }
                if (sp.getInt("skipped", 0) == latestCode) return;

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
                    return;
                }

                final String finalUrl = apkUrl;
                final int finalCode = latestCode;
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString("pending_url", finalUrl).apply();

                runOnUi(ctx, () -> {
                    new AlertDialog.Builder(ctx)
                            .setTitle("Update available")
                            .setMessage("Version v" + finalCode + " is available. Update now?")
                            .setPositiveButton("Update", (d, w) -> downloadAndInstall(ctx, finalUrl))
                            .setNegativeButton("Later", null)
                            .setNeutralButton("Skip", (d, w) ->
                                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                            .edit().putInt("skipped", finalCode).apply())
                            .show();
                });
            } catch (Exception e) {
                if (manual) toast(ctx, "Update failed: " + e.getMessage());
            }
        }).start();
    }

    private static void downloadAndInstall(Context ctx, String apkUrl) {
        new Thread(() -> {
            try {
                toast(ctx, "Downloading update…");
                File dir = new File(ctx.getCacheDir(), "updates");
                dir.mkdirs();
                File apk = new File(dir, "update.apk");
                HttpURLConnection d = (HttpURLConnection) new URL(apkUrl).openConnection();
                d.setRequestProperty("User-Agent", "NellisBid");
                d.setConnectTimeout(30000);
                d.setReadTimeout(60000);
                InputStream in = d.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.close();
                in.close();
                d.disconnect();

                Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception e) {
                toast(ctx, "Update failed: " + e.getMessage());
            }
        }).start();
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
