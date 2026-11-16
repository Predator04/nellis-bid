package com.nellis.bid;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Fetches + shows a logged-in account list (purchases / bids). */
public class PurchasesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchases);

        String endpoint = getIntent().getStringExtra("endpoint");
        String label = getIntent().getStringExtra("label");
        if (endpoint == null) endpoint = "/v1/purchases";
        if (label == null) label = "My Purchases";

        TextView title = findViewById(R.id.p_title);
        TextView body = findViewById(R.id.p_body);
        title.setText(label);

        Button loginBtn = findViewById(R.id.p_login_btn);
        loginBtn.setOnClickListener(v -> {
            AuthApi.logout(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        String token = AuthApi.getToken(this);
        if (token == null) {
            body.setText("Log in to see " + label.toLowerCase() + ".");
            loginBtn.setText("Log in");
            loginBtn.setVisibility(View.VISIBLE);
            return;
        }

        body.setText("Loading…");
        final String ep = endpoint;
        new Thread(() -> {
            String result = fetch(token, ep);
            runOnUiThread(() -> {
                body.setText(result);
                if (result.startsWith("Session expired")) {
                    AuthApi.logout(this);
                    loginBtn.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private String fetch(String token, String endpoint) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://mobile.nellisauction.com" + endpoint).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) NellisAuction/2.1.0");
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    c.getResponseCode() == 200 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            c.disconnect();
            if (c.getResponseCode() != 200) {
                if (c.getResponseCode() == 401) return "Session expired — your login timed out.";
                try {
                    JSONObject j = new JSONObject(sb.toString());
                    String m = j.optString("message", "");
                    if (!m.isEmpty()) return m;
                } catch (Exception ignored) {
                }
                return "Error (HTTP " + c.getResponseCode() + ")";
            }
            return format(sb.toString());
        } catch (Exception e) {
            return "Failed to load: " + e.getMessage();
        }
    }

    /** Best-effort: extract readable entries from an unknown JSON shape. */
    private String format(String json) {
        try {
            JSONArray items = null;
            try {
                items = new JSONArray(json);
            } catch (Exception e) {
                JSONObject o = new JSONObject(json);
                for (String k : new String[]{"data", "items", "purchases", "results", "bids", "auctions"}) {
                    if (o.opt(k) instanceof JSONArray) { items = o.optJSONArray(k); break; }
                }
            }
            if (items == null) return json.length() > 2000 ? json.substring(0, 2000) : json;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                String name = firstNonEmpty(it, "title", "name", "itemTitle", "productTitle", "description", "id");
                if (name.isEmpty()) continue;
                String status = firstNonEmpty(it, "status", "state", "marketStatus");
                Object p = it.opt("amount");
                if (p == null) p = it.opt("price");
                if (p == null) p = it.opt("currentPrice");
                String price = p != null ? " · $" + p : "";
                sb.append("• ").append(name).append(price);
                if (!status.isEmpty()) sb.append(" (").append(status).append(")");
                sb.append("\n");
            }
            if (sb.length() == 0) return "No items found.";
            return sb.toString();
        } catch (Exception e) {
            return json.length() > 2000 ? json.substring(0, 2000) : json;
        }
    }

    private String firstNonEmpty(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (!v.isEmpty()) return v;
        }
        return "";
    }
}
