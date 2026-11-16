package com.nellis.bid;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText email = findViewById(R.id.l_email);
        EditText password = findViewById(R.id.l_password);
        Button loginBtn = findViewById(R.id.l_login_btn);
        ProgressBar progress = findViewById(R.id.l_progress);
        TextView status = findViewById(R.id.l_status);

        loginBtn.setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();
            if (e.isEmpty() || p.isEmpty()) {
                status.setText("Enter your email and password");
                return;
            }
            progress.setVisibility(View.VISIBLE);
            loginBtn.setEnabled(false);
            status.setText("");
            new Thread(() -> {
                try {
                    String token = AuthApi.login(LoginActivity.this, e, p);
                    runOnUiThread(() -> {
                        String raw = AuthApi.lastLoginResponse(LoginActivity.this);
                        String cookie = AuthApi.getCookie(LoginActivity.this);
                        String t = (token != null && token.length() > 24)
                                ? token.substring(0, 24) + "…" : token;
                        new AlertDialog.Builder(LoginActivity.this)
                                .setTitle("Logged in — diagnostic")
                                .setMessage("Token: " + t + "\nCookie: " + cookie + "\n\nResponse:\n" + (raw.length() > 500 ? raw.substring(0, 500) : raw))
                                .setPositiveButton("Done", (d, w) -> finish())
                                .setNeutralButton("Copy response", (d, w) -> {
                                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    cm.setPrimaryClip(ClipData.newPlainText("login", raw));
                                    Toast.makeText(LoginActivity.this, "Copied — paste it to me", Toast.LENGTH_LONG).show();
                                })
                                .setCancelable(false)
                                .show();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        loginBtn.setEnabled(true);
                        status.setText(ex.getMessage());
                    });
                }
            }).start();
        });
    }
}
