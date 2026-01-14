package com.gitgud.citywatch;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.gitgud.citywatch.data.repository.DataRepository;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private SessionManager sessionManager;
    private DataRepository dataRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager();
        dataRepository = DataRepository.getInstance(this);

        // redirect to login if not authenticated
        if (sessionManager.redirectIfNotLoggedIn(this)) return;

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup bottom navigation after view hierarchy is created
        setupBottomNavMenu();

        // Initialize user votes cache on startup
        dataRepository.initializeUserVotes();

        // Trigger initial checksum validation
        validateCachesOnStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // re-check on resume in case token expired or user logged out elsewhere
        sessionManager.redirectIfNotLoggedIn(this);
    }

    private void setupBottomNavMenu() {
        try {
            NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);

            assert host != null;
            NavController navController = host.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            NavigationUI.setupWithNavController(bottomNav, navController);

            // Add listener for navigation changes to trigger cache validation
            bottomNav.setOnItemSelectedListener(item -> {
                // Trigger lightweight checksum validation on every navigation change
                validateCachesOnNavigation();

                // Let NavigationUI handle the actual navigation
                return NavigationUI.onNavDestinationSelected(item, navController);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Validate caches when app starts
     */
    private void validateCachesOnStart() {
        Log.d(TAG, "Validating caches on app start");
        dataRepository.validateCachesOnNavigation(invalidatedKeys -> {
            if (!invalidatedKeys.isEmpty()) {
                Log.d(TAG, "Invalidated caches on start: " + invalidatedKeys);
            } else {
                Log.d(TAG, "All caches valid on start");
            }
        });
    }

    /**
     * Validate caches when bottom navigation changes
     */
    private void validateCachesOnNavigation() {
        Log.d(TAG, "Validating caches on navigation change");
        dataRepository.validateCachesOnNavigation(invalidatedKeys -> {
            if (!invalidatedKeys.isEmpty()) {
                Log.d(TAG, "Invalidated caches: " + invalidatedKeys);
                // The fragments will automatically refresh when they load
                // because reportsCacheInvalidated flag is set
            }
        });
    }
}
