package com.nellis.bid;

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
                    AuthApi.login(LoginActivity.this, e, p);
                    runOnUiThread(() -> {
                        Toast.makeText(LoginActivity.this, "Logged in", Toast.LENGTH_SHORT).show();
                        finish();
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
