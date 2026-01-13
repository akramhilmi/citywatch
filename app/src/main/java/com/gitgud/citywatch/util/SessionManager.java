package com.gitgud.citywatch.util;

import com.gitgud.citywatch.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Manages user session and authentication state
 */
public class SessionManager {

    private static final FirebaseAuth auth = FirebaseAuth.getInstance();

    /**
     * Get current Firebase user ID
     * @return User ID if authenticated, null otherwise
     */
    public static String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Get current Firebase user
     * @return FirebaseUser if authenticated, null otherwise
     */
    public static FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Get current user's email
     * @return Email if authenticated, null otherwise
     */
    public static String getCurrentUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    /**
     * Get current user's display name
     * @return Display name if set, null otherwise
     */
    public static String getCurrentUserDisplayName() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getDisplayName() : null;
    }

    /**
     * Check if user is authenticated
     * @return True if user is authenticated, false otherwise
     */
    public static boolean isUserAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Logout current user
     */
    public static void logout() {
        auth.signOut();
    }

    /**
     * Get Firebase Auth instance
     * @return FirebaseAuth instance
     */
    public static FirebaseAuth getAuth() {
        return auth;
    }

    /**
     * Redirect to MainActivity if user is not logged in
     * @param currentActivity The current activity to finish
     * @return True if redirected, false if user is authenticated
     */
    public static boolean redirectIfNotLoggedIn(android.app.Activity currentActivity) {
        if (!isUserAuthenticated()) {
            android.content.Intent intent = new android.content.Intent(
                    currentActivity, MainActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            currentActivity.startActivity(intent);
            currentActivity.finish();
            return true;
        }
        return false;
    }
}

