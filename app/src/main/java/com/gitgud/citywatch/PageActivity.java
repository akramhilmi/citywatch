package com.gitgud.citywatch;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class PageActivity extends AppCompatActivity {
    private TextView tvNameHeader, tvNameValue, tvEmailValue, tvPhoneValue;
    private FirebaseAuth mAuth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_page);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager();

        initViews();
        loadUserData();
        setupClickListeners();
        setupBottomNavigation();
    }

    private void initViews() {
        tvNameHeader = findViewById(R.id.tvNameHeader);
        tvNameValue = findViewById(R.id.tvNameValue);
        tvEmailValue = findViewById(R.id.tvEmailValue);
        tvPhoneValue = findViewById(R.id.tvPhoneValue);
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String email = user.getEmail();
            String phone = user.getPhoneNumber();
            //fallbacks for missing profile page data
            tvNameHeader.setText(name != null && !name.isEmpty() ? name : "User");
            tvNameValue.setText(name != null && !name.isEmpty() ? name : "Not set");
            tvEmailValue.setText(email != null ? email : "Not set");
            tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "Not set");
        }
    }

    private void setupClickListeners() {
        //sign out
        findViewById(R.id.btnSignOut).setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        //change password
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> {
            String email = mAuth.getCurrentUser().getEmail();
            if (email != null) {
                mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset email sent to " + email, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        //delete account
        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> showDeleteConfirmation());

        //contactUs(email)
        findViewById(R.id.btnContactUs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@citywatch.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "CityWatch Support Request");
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        //darkmode switch
        MaterialSwitch switchDark = findViewById(R.id.switchDarkMode);
        switchDark.setOnCheckedChangeListener((btn, isChecked) -> {
            AppCompatDelegate.setDefaultNightMode(isChecked ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        });

        // TODO :create real edit flow, use placeholders for now
        findViewById(R.id.btnEditName).setOnClickListener(v -> Toast.makeText(this, "Edit Name feature coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnUpdatePhoto).setOnClickListener(v -> Toast.makeText(this, "Camera access coming soon", Toast.LENGTH_SHORT).show());
    }
    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    user.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            sessionManager.logout();
                            startActivity(new Intent(this, SignInActivity.class));
                            finish();
                        }
                    });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    private void setupBottomNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_profile);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                return true;
            }
            return item.getItemId() == R.id.nav_profile;
        });
    }
}
