package com.gitgud.citywatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gitgud.citywatch.databinding.ActivitySignUpBinding;
import com.gitgud.citywatch.util.ApiClient;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.btnSignUp.setOnClickListener(v -> attemptSignUp());
    }

    private void attemptSignUp() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        if (!validateInput(name, email, phone, password, confirmPassword)) return;

        setLoading(true);
        ApiClient.signUp(email, password, name, phone)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        navigateToMain();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateInput(String name, String email, String phone, String password, String confirmPassword) {
        // clear previous errors
        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPhone.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);

        if (!ApiClient.validateSignUpInput(name, email, phone, password, confirmPassword)) {
            if (name.isEmpty()) {
                binding.tilName.setError("Name required");
            }
            if (email.isEmpty()) {
                binding.tilEmail.setError("Email required");
            }
            if (phone.isEmpty()) {
                binding.tilPhone.setError("Phone number required");
            }
            if (password.isEmpty()) {
                binding.tilPassword.setError("Password required");
            } else if (password.length() < 6) {
                binding.tilPassword.setError("Password must be at least 6 characters");
            }
            if (!password.equals(confirmPassword)) {
                binding.tilConfirmPassword.setError("Passwords do not match");
            }
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSignUp.setEnabled(!loading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

