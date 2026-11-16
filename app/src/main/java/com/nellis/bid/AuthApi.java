package com.nellis.bid;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

/** Auth + bidding against the Nellis mobile API (mobile.nellisauction.com). */
public class AuthApi {
    private static final String BASE = "https://mobile.nellisauction.com";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) NellisAuction/2.1.0";
    private static final String PREFS = "auth";

    /** Token storage — plain SharedPreferences so the login reliably survives app updates. */
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Log in with email+password, store the token, return it. Throws on failure. */
    public static String login(Context ctx, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        String resp = post("/v1/auth/sign-in", body.toString(), null);
        String token = findToken(resp);
        if (token == null) throw new Exception("Login failed — couldn't find a token. Response: " + resp);
        String refresh = findField(resp, "refreshToken", "refresh_token");
        prefs(ctx).edit()
                .putString("accessToken", token)
                .putString("refreshToken", refresh == null ? "" : refresh)
                .putString("email", email)
                .apply();
        return token;
    }

    /** Place a bid. Returns the raw response body on success; throws on error. */
    public static String placeBid(String token, String productId, double amount) throws Exception {
        JSONObject body = new JSONObject();
        body.put("productId", productId);
        body.put("amount", amount);
        return post("/v1/bids", body.toString(), token);
    }

    public static String getToken(Context ctx) {
        return prefs(ctx).getString("accessToken", null);
    }

    public static String getEmail(Context ctx) {
        return prefs(ctx).getString("email", null);
    }

    public static void logout(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    /** Recursively find the first string token under a token-ish key. */
    private static String findToken(String json) {
        String t = findField(json, "accessToken", "access_token", "token", "jwt", "id_token", "idToken");
        return t;
    }

    private static String findField(String json, String... keys) {
        try {
            JSONObject j = new JSONObject(json);
            String v = scan(j, keys);
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static String scan(Object node, String[] keys) {
        try {
            if (node instanceof JSONObject) {
                JSONObject o = (JSONObject) node;
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    for (String key : keys) {
                        if (k.equalsIgnoreCase(key)) {
                            Object v = o.opt(k);
                            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
                        }
                    }
                }
                // recurse
                it = o.keys();
                while (it.hasNext()) {
                    String r = scan(o.opt(it.next()), keys);
                    if (r != null) return r;
                }
            } else if (node instanceof JSONArray) {
                JSONArray a = (JSONArray) node;
                for (int i = 0; i < a.length(); i++) {
                    String r = scan(a.opt(i), keys);
                    if (r != null) return r;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String post(String path, String jsonBody, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", UA);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(jsonBody.getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();

        if (code >= 400) {
            throw new Exception(extractMessage(sb.toString(), code));
        }
        return sb.toString();
    }

    private static String extractMessage(String body, int code) {
        try {
            JSONObject j = new JSONObject(body);
            String msg = j.optString("message", "");
            if (!msg.isEmpty()) return msg;
            msg = j.optString("error", "");
            if (!msg.isEmpty()) return msg;
        } catch (Exception ignored) {
        }
        return "HTTP " + code + (body.isEmpty() ? "" : ": " + body.substring(0, Math.min(200, body.length())));
    }
}
