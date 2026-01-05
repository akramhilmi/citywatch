package com.gitgud.citywatch.util;

import android.app.Activity;
import android.content.Intent;

import com.gitgud.citywatch.SignInActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Manages Firebase auth session state.
 * Provides helpers to check login status and redirect when needed.
 */
public class SessionManager {

    private final FirebaseAuth auth;

    public SessionManager() {
        this.auth = FirebaseAuth.getInstance();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getToken() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void logout() {
        auth.signOut();
    }

    /**
     * Redirects to LoginActivity if not authenticated.
     * Clears back stack so user can't navigate back.
     * @return true if redirected, false if already logged in
     */
    public boolean redirectIfNotLoggedIn(Activity activity) {
        if (isLoggedIn()) return false;

        Intent intent = new Intent(activity, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}

