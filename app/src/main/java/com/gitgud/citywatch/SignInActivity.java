package com.gitgud.citywatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.gitgud.citywatch.databinding.ActivitySignInBinding;
import com.gitgud.citywatch.util.MockBackend;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // disable back navigation completely
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // do nothing - back is disabled on login screen
            }
        });

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnRegister.setOnClickListener(v -> navigateToSignUp());
    }

    private void navigateToSignUp() {
        startActivity(new Intent(this, SignUpActivity.class));
    }

    private void attemptLogin() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (!validateInput(email, password)) return;

        setLoading(true);
        MockBackend.signIn(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        navigateToMain();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateInput(String email, String password) {
        // clear previous errors
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        if (!MockBackend.validateSignInInput(email, password)) {
            if (email.isEmpty()) {
                binding.tilEmail.setError("Email required");
            }
            if (password.isEmpty()) {
                binding.tilPassword.setError("Password required");
            }
            if (!password.isEmpty() && password.length() < 6) {
                binding.tilPassword.setError("Password must be at least 6 characters");
            }
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
        binding.btnRegister.setEnabled(!loading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
