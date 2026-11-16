package com.nellis.bid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private EditText searchInput;
    private RecyclerView list;
    private ItemAdapter adapter;
    private ProgressBar progress;
    private TextView status;
    private Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // First-launch legal gate (third-party client)
        SharedPreferences gate = getSharedPreferences("legal", MODE_PRIVATE);
        if (!gate.getBoolean("accepted", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Independent app")
                    .setMessage("Nellis Bid is an independent third-party client. It is NOT affiliated with, endorsed by, or supported by Nellis Auction (Cret LLC). Bidding is done through your own Nellis account, and you accept full responsibility for all bids and purchases.")
                    .setCancelable(false)
                    .setPositiveButton("I accept", (d, w) -> gate.edit().putBoolean("accepted", true).apply())
                    .setNegativeButton("Decline", (d, w) -> finish())
                    .show();
        }

        searchInput = findViewById(R.id.search_input);
        Button searchBtn = findViewById(R.id.search_btn);
        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        loginBtn = findViewById(R.id.login_btn);

        adapter = new ItemAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        searchBtn.setOnClickListener(v -> doSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        updateLoginButton();
        loginBtn.setOnClickListener(v -> {
            if (AuthApi.getToken(this) != null) {
                AuthApi.logout(this);
                updateLoginButton();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });

        Button settingsBtn = findViewById(R.id.settings_btn);
        settingsBtn.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add("Check for updates");
            pm.getMenu().add("Disclaimer");
            pm.getMenu().add(AuthApi.getToken(this) != null ? "Log out" : "Log in");
            pm.setOnMenuItemClickListener(item -> {
                String t = item.getTitle().toString();
                if (t.equals("Check for updates")) {
                    UpdateChecker.check(this, true);
                    return true;
                } else if (t.equals("Disclaimer")) {
                    new AlertDialog.Builder(this)
                            .setTitle("Disclaimer")
                            .setMessage("Nellis Bid is an independent third-party client. It is NOT affiliated with, endorsed by, or supported by Nellis Auction (Cret LLC). Bidding is done through your own Nellis account, and you accept full responsibility for all bids and purchases.")
                            .setPositiveButton("OK", null)
                            .show();
                    return true;
                } else if (t.equals("Log out")) {
                    AuthApi.logout(this);
                    updateLoginButton();
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                    return true;
                } else {
                    startActivity(new Intent(this, LoginActivity.class));
                    return true;
                }
            });
            pm.show();
        });

        // Default feed on launch (empty query -> Nellis's default discovery)
        doSearch();

        // Check for app updates (12h cooldown, silent unless a new version exists)
        UpdateChecker.check(this, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLoginButton();
    }

    private void updateLoginButton() {
        loginBtn.setText(AuthApi.getEmail(this) != null ? "Log out" : "Login");
    }

    private void doSearch() {
        String q = searchInput.getText().toString().trim();
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                List<Item> items = NellisApi.search(q);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    adapter.setItems(items);
                    status.setText(items.size() + " items · all-in = bid + 15% premium + 8.375% tax");
                    status.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText(friendlyError(e));
                    status.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private String friendlyError(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("UnknownHost") || m.contains("ConnectException")
                || m.contains("SocketTimeout") || m.contains("timed out")
                || m.contains("ENETUNREACH") || m.contains("Connection refused")) {
            return "Can't reach Nellis — check your internet connection and try again.";
        }
        return "Something went wrong: " + m;
    }
}
