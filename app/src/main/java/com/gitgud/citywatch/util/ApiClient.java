package com.gitgud.citywatch.util;

import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableReference;

/**
 * Communicates with Firebase backend services and Cloud Functions.
 * Handles all API calls to the backend, bridging the app and server-side logic.
 */
public class ApiClient {
    private static final FirebaseAuth auth = FirebaseAuth.getInstance();
    private static final FirebaseFunctions functions = FirebaseFunctions.getInstance();

    /**
     * Validates sign in input
     */
    public static boolean validateSignInInput(String email, String password) {
        return !TextUtils.isEmpty(email) && !TextUtils.isEmpty(password) && password.length() >= 6;
    }

    /**
     * Validates sign up input
     */
    public static boolean validateSignUpInput(String name, String email, String phone, String password, String confirmPassword) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(phone)) {
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            return false;
        }
        return password.equals(confirmPassword);
    }

    /**
     * Attempts to sign in with email and password
     */
    public static com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> signIn(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    /**
     * Attempts to sign up with email and password, then updates user profile with name and stores name and phone in Firestore
     */
    public static com.google.android.gms.tasks.Task<Void> signUp(String email, String password, String name, String phone) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        return auth.createUserWithEmailAndPassword(email, password)
                .continueWithTask(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String userId = auth.getCurrentUser().getUid();

                        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build();

                        return auth.getCurrentUser().updateProfile(profileUpdate)
                                .continueWithTask(profileTask -> {
                                    // Store name and phone number in Firestore
                                    return db.collection("users").document(userId)
                                            .set(new java.util.HashMap<String, Object>() {{
                                                put("name", name);
                                                put("phone", phone);
                                            }});
                                });
                    }
                    throw task.getException() != null ? task.getException() : new Exception("Registration failed");
                });
    }

    /**
     * Get current Firebase Auth instance
     */
    public static FirebaseAuth getAuth() {
        return auth;
    }

    /**
     * Fetch user name from Cloud Function
     */
    public static com.google.android.gms.tasks.Task<String> getUserName(String userId) {
        HttpsCallableReference getUserNameFunc = functions.getHttpsCallable("getUserName");
        return getUserNameFunc.call(new java.util.HashMap<String, Object>() {{
            put("userId", userId);
        }}).continueWith(task -> {
            if (task.isSuccessful()) {
                return (String) task.getResult().getData();
            }
            throw task.getException() != null ? task.getException() : new Exception("Failed to get user name");
        });
    }

    /**
     * Fetch user phone from Cloud Function
     */
    public static com.google.android.gms.tasks.Task<String> getUserPhone(String userId) {
        HttpsCallableReference getUserPhoneFunc = functions.getHttpsCallable("getUserPhone");
        return getUserPhoneFunc.call(new java.util.HashMap<String, Object>() {{
            put("userId", userId);
        }}).continueWith(task -> {
            if (task.isSuccessful()) {
                return (String) task.getResult().getData();
            }
            throw task.getException() != null ? task.getException() : new Exception("Failed to get user phone");
        });
    }

    /**
     * Delete user account via Cloud Function
     */
    public static com.google.android.gms.tasks.Task<Void> deleteAccount(String userId) {
        HttpsCallableReference deleteAccountFunc = functions.getHttpsCallable("deleteAccount");
        return deleteAccountFunc.call(new java.util.HashMap<String, Object>() {{
            put("userId", userId);
        }}).continueWith(task -> null);
    }

    /**
     * Get current Firebase user
     */
    public static com.google.firebase.auth.FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Get current user's email
     */
    public static String getCurrentUserEmail() {
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    /**
     * Send password reset email via Cloud Function
     */
    public static com.google.android.gms.tasks.Task<Void> sendPasswordResetEmail(String email) {
        HttpsCallableReference sendPasswordResetFunc = functions.getHttpsCallable("sendPasswordResetEmail");
        return sendPasswordResetFunc.call(new java.util.HashMap<String, Object>() {{
            put("email", email);
        }}).continueWith(task -> null);
    }

    /**
     * Update user name via Cloud Function
     */
    public static com.google.android.gms.tasks.Task<Void> updateUserName(String userId, String newName) {
        HttpsCallableReference updateUserNameFunc = functions.getHttpsCallable("updateUserName");
        return updateUserNameFunc.call(new java.util.HashMap<String, Object>() {{
            put("userId", userId);
            put("newName", newName);
        }}).continueWith(task -> null);
    }

    /**
     * Update user phone number via Cloud Function
     */
    public static com.google.android.gms.tasks.Task<Void> updateUserPhone(String userId, String newPhone) {
        HttpsCallableReference updateUserPhoneFunc = functions.getHttpsCallable("updateUserPhone");
        return updateUserPhoneFunc.call(new java.util.HashMap<String, Object>() {{
            put("userId", userId);
            put("newPhone", newPhone);
        }}).continueWith(task -> null);
    }
}

