package com.gitgud.citywatch;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager();

        // redirect to login if not authenticated
        if (sessionManager.redirectIfNotLoggedIn(this)) return;

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // re-check on resume in case token expired or user logged out elsewhere
        sessionManager.redirectIfNotLoggedIn(this);
    }

    private void setupBottomNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_profile) {
                startActivity(new Intent(this, PageActivity.class));
                return true;
            }
            return item.getItemId() == R.id.nav_home;
        });
    }
}
