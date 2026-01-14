package com.gitgud.citywatch.util;

import android.util.Log;

/**
 * Global context for managing admin status of the current user
 * Provides a single source of truth for admin status across the entire app
 * Cache is cleared on logout
 */
public class AdminContext {
    private static final String TAG = "AdminContext";
    private static volatile Boolean isAdminCache = null;
    private static volatile boolean isLoading = false;
    private static final Object lock = new Object();

    // Callback interface for when admin status is determined
    public interface AdminStatusCallback {
        void onAdminStatusLoaded(boolean isAdmin);
        void onError(Exception e);
    }

    /**
     * Get cached admin status if available
     * Returns null if not yet determined
     */
    public static Boolean getIsAdminCached() {
        synchronized (lock) {
            return isAdminCache;
        }
    }

    /**
     * Check if currently loading admin status
     */
    public static boolean isLoading() {
        synchronized (lock) {
            return isLoading;
        }
    }

    /**
     * Fetch admin status from backend and cache it
     * Only fetches if not already cached or loading
     */
    public static void loadAdminStatus(AdminStatusCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onAdminStatusLoaded(false);
            }
            return;
        }

        // Return cached value if available
        synchronized (lock) {
            if (isAdminCache != null) {
                Log.d(TAG, "Using cached admin status: " + isAdminCache);
                if (callback != null) {
                    callback.onAdminStatusLoaded(isAdminCache);
                }
                return;
            }

            // Avoid duplicate fetches
            if (isLoading) {
                Log.d(TAG, "Admin status already loading");
                return;
            }

            isLoading = true;
        }

        // Fetch from backend
        ApiClient.getIsAdmin(userId)
                .addOnSuccessListener(isAdmin -> {
                    synchronized (lock) {
                        isAdminCache = isAdmin;
                        isLoading = false;
                    }
                    Log.d(TAG, "Admin status loaded: " + isAdmin);
                    if (callback != null) {
                        callback.onAdminStatusLoaded(isAdmin);
                    }
                })
                .addOnFailureListener(e -> {
                    synchronized (lock) {
                        isLoading = false;
                        // Cache as non-admin on error to prevent repeated failed attempts
                        isAdminCache = false;
                    }
                    Log.e(TAG, "Failed to load admin status", e);
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
    }

    /**
     * Force refresh admin status from backend
     */
    public static void refreshAdminStatus(AdminStatusCallback callback) {
        synchronized (lock) {
            isAdminCache = null;
            isLoading = false;
        }
        loadAdminStatus(callback);
    }

    /**
     * Clear admin context (called on logout)
     */
    public static void clear() {
        synchronized (lock) {
            isAdminCache = null;
            isLoading = false;
        }
        Log.d(TAG, "Admin context cleared");
    }

    /**
     * Set admin status directly (for testing or manual override)
     */
    public static void setIsAdmin(boolean isAdmin) {
        synchronized (lock) {
            isAdminCache = isAdmin;
        }
        Log.d(TAG, "Admin status set to: " + isAdmin);
    }
}
