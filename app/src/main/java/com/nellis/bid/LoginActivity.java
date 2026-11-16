package com.nellis.bid;

import android.app.AlertDialog;
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
                        String t = (token != null && token.length() > 24)
                                ? token.substring(0, 24) + "…" : token;
                        new AlertDialog.Builder(LoginActivity.this)
                                .setTitle("Logged in — diagnostic")
                                .setMessage("Token: " + t + "\n\nResponse:\n" + (raw.length() > 600 ? raw.substring(0, 600) : raw))
                                .setPositiveButton("Done", (d, w) -> finish())
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
