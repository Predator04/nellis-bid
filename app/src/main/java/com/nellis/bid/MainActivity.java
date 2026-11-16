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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
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
    private Spinner locationSpinner, categorySpinner;
    private Button updateBanner;

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

        // Request notification permission (for auction-ending reminders) on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        searchInput = findViewById(R.id.search_input);
        Button searchBtn = findViewById(R.id.search_btn);
        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        loginBtn = findViewById(R.id.login_btn);
        locationSpinner = findViewById(R.id.location_spinner);
        categorySpinner = findViewById(R.id.category_spinner);
        updateBanner = findViewById(R.id.update_banner);
        setupFilters();

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
            pm.getMenu().add("Watchlist");
            pm.getMenu().add("My Purchases");
            pm.getMenu().add("My Bids");
            pm.getMenu().add("Save this search");
            pm.getMenu().add("Saved searches");
            pm.getMenu().add("Check for updates");
            pm.getMenu().add("Disclaimer");
            pm.getMenu().add(AuthApi.getToken(this) != null ? "Log out" : "Log in");
            pm.setOnMenuItemClickListener(item -> {
                String t = item.getTitle().toString();
                if (t.equals("Watchlist")) {
                    startActivity(new Intent(this, WatchlistActivity.class));
                    return true;
                } else if (t.equals("My Purchases")) {
                    openAccount("/v1/purchases", "My Purchases");
                    return true;
                } else if (t.equals("My Bids")) {
                    openAccount("/v1/auctions", "My Bids");
                    return true;
                } else if (t.equals("Save this search")) {
                    String q = searchInput.getText().toString().trim();
                    String loc = locationSpinner.getSelectedItemPosition() > 0 ? locationSpinner.getSelectedItem().toString() : "";
                    String cat = categorySpinner.getSelectedItemPosition() > 0 ? categorySpinner.getSelectedItem().toString() : "";
                    if (q.isEmpty() && loc.isEmpty() && cat.isEmpty()) {
                        Toast.makeText(this, "Nothing to save — enter a search or filter", Toast.LENGTH_SHORT).show();
                    } else {
                        SavedSearch.add(this, q, loc, cat);
                        Toast.makeText(this, "Search saved", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                } else if (t.equals("Saved searches")) {
                    showSavedSearches();
                    return true;
                } else if (t.equals("Check for updates")) {
                    UpdateChecker.check(this, true, this::refreshUpdateBanner);
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

        // Filter listeners (set AFTER the initial load so setup doesn't re-trigger)
        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { doSearch(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        locationSpinner.setOnItemSelectedListener(filterListener);
        categorySpinner.setOnItemSelectedListener(filterListener);

        // Check for app updates (12h cooldown, silent unless a new version exists)
        UpdateChecker.check(this, false, this::refreshUpdateBanner);

        // Update banner: tap to download the pending update
        updateBanner.setOnClickListener(v -> {
            String url = UpdateChecker.pendingUrl(this);
            if (url != null) UpdateChecker.download(this, url);
            else UpdateChecker.check(this, true, this::refreshUpdateBanner);
        });
        refreshUpdateBanner();
    }

    private void setupFilters() {
        String[] locations = {"All locations", "SW Las Vegas", "North Las Vegas", "Legacy Bids", "Nellis Outlet", "Wild Finds Henderson"};
        String[] categories = {"All categories", "Electronics", "Home Improvement", "Furniture & Appliances", "Outdoors & Sports", "Patio & Garden", "Automotive", "Home & Household Essentials", "Clothing, Shoes & Accessories", "Toys & Games", "Beauty & Personal Care"};
        ArrayAdapter<String> locA = new ArrayAdapter<>(this, R.layout.spinner_item, locations);
        locA.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(locA);
        ArrayAdapter<String> catA = new ArrayAdapter<>(this, R.layout.spinner_item, categories);
        catA.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(catA);
    }

    private void openAccount(String endpoint, String label) {
        Intent i = new Intent(this, PurchasesActivity.class);
        i.putExtra("endpoint", endpoint);
        i.putExtra("label", label);
        startActivity(i);
    }

    private void showSavedSearches() {
        List<String[]> saved = SavedSearch.getAll(this);
        if (saved.isEmpty()) {
            Toast.makeText(this, "No saved searches yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[saved.size()];
        for (int i = 0; i < saved.size(); i++) {
            String[] s = saved.get(i);
            String label = s[0];
            if (!s[1].isEmpty()) label += (label.isEmpty() ? "" : " · ") + s[1];
            if (!s[2].isEmpty()) label += (label.isEmpty() ? "" : " · ") + s[2];
            labels[i] = label.isEmpty() ? "(empty)" : label;
        }
        new AlertDialog.Builder(this)
                .setTitle("Saved searches")
                .setItems(labels, (d, which) -> {
                    String[] s = saved.get(which);
                    searchInput.setText(s[0]);
                    setSpinnerValue(locationSpinner, s[1]);
                    setSpinnerValue(categorySpinner, s[2]);
                    doSearch();
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private void setSpinnerValue(Spinner spinner, String value) {
        if (value == null || value.isEmpty()) { spinner.setSelection(0); return; }
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) { spinner.setSelection(i); return; }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLoginButton();
        refreshUpdateBanner();
    }

    private void updateLoginButton() {
        loginBtn.setText(AuthApi.getEmail(this) != null ? "Log out" : "Login");
    }

    private void refreshUpdateBanner() {
        int pending = UpdateChecker.pendingVersion(this);
        if (pending > BuildConfig.VERSION_CODE) {
            updateBanner.setVisibility(View.VISIBLE);
            updateBanner.setText("⬆ Update available (v" + pending + ") — tap to download");
        } else {
            updateBanner.setVisibility(View.GONE);
        }
    }

    private void doSearch() {
        String q = searchInput.getText().toString().trim();
        String loc = locationSpinner != null && locationSpinner.getSelectedItemPosition() > 0
                ? locationSpinner.getSelectedItem().toString() : "";
        String cat = categorySpinner != null && categorySpinner.getSelectedItemPosition() > 0
                ? categorySpinner.getSelectedItem().toString() : "";
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                List<Item> items = NellisApi.search(q, loc, cat);
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
