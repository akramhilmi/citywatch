package com.gitgud.citywatch.util;

import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Simulates server backend behavior since we couldn't afford one. (;-;)
 * In practice this would be replaced with actual network calls to the backend.
 */
public class MockBackend {
    private static final FirebaseAuth auth = FirebaseAuth.getInstance();

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
     * Attempts to sign up with email and password, then updates user profile with name and stores phone in Firestore
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
                                    // Store phone number in Firestore
                                    return db.collection("users").document(userId)
                                            .set(new java.util.HashMap<String, Object>() {{
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
}
