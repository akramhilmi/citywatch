package com.gitgud.citywatch.util;

import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;

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

    /**
     * Update user email in Firebase Auth with verification
     * Sends verification link to new email before updating
     */
    public static com.google.android.gms.tasks.Task<Void> updateUserEmail(String newEmail) {
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.verifyBeforeUpdateEmail(newEmail);
        }
        throw new IllegalStateException("No current user to update email");
    }

    /**
     * Upload profile picture to Firebase Storage
     * Stores image in profile_pictures/{userId}.jpg
     */
    public static com.google.android.gms.tasks.Task<String> uploadProfilePicture(String userId, Uri imageUri) {
        if (imageUri == null) {
            throw new IllegalArgumentException("Image URI cannot be null");
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference profilePicturesRef = storage.getReference().child("profile_pictures/" + userId + ".jpg");

        return profilePicturesRef.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() : new Exception("Upload failed");
                    }
                    return profilePicturesRef.getDownloadUrl();
                })
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        return downloadUri != null ? downloadUri.toString() : null;
                    }
                    throw task.getException() != null ? task.getException() : new Exception("Failed to get download URL");
                });
    }

    /**
     * Get profile picture download URL from Firebase Storage
     * Returns the download URL for the user's profile picture
     */
    public static com.google.android.gms.tasks.Task<String> getProfilePictureUrl(String userId) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference profilePicturesRef = storage.getReference().child("profile_pictures/" + userId + ".jpg");

        return profilePicturesRef.getDownloadUrl()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        return downloadUri != null ? downloadUri.toString() : null;
                    }
                    // If file doesn't exist, return null instead of throwing error
                    return null;
                });
    }

    /**
     * Submit a new report to Firestore via Cloud Function
     * Creates a new document with auto-generated ID in the 'reports' collection
     *
     * @param description Report description
     * @param hazardType Type of hazard (e.g., "Pothole")
     * @param localGov Local government authority
     * @param locationDetails Street/area details
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Task that completes with the auto-generated document ID
     */
    public static com.google.android.gms.tasks.Task<String> submitReport(
            String description,
            String hazardType,
            String localGov,
            String locationDetails,
            double latitude,
            double longitude) {

        HttpsCallableReference submitReportFunc = functions.getHttpsCallable("submitReport");

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("description", description);
        data.put("hazardType", hazardType);
        data.put("localGov", localGov);
        data.put("locationDetails", locationDetails);
        data.put("latitude", latitude);
        data.put("longitude", longitude);

        return submitReportFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        java.util.Map<String, Object> result = (java.util.Map<String, Object>) task.getResult().getData();
                        return (String) result.get("documentId");
                    }
                    throw task.getException() != null ? task.getException() : new Exception("Failed to submit report");
                });
    }

    /**
     * Upload report photo to Firebase Storage
     * Stores image in report_photos/{documentId}.jpg
     *
     * @param documentId The report document ID
     * @param imageUri The image URI from gallery picker
    /**
     * Upload report photo to Firebase Storage via Cloud Function
     * Stores image in report_photos/{documentId}.jpg
     *
     * @param documentId The report document ID
     * @param imageBase64 Base64 encoded image data
     * @return Task that completes when upload is done
     */
    public static com.google.android.gms.tasks.Task<Void> uploadReportPhoto(String documentId, String imageBase64) {
        if (documentId == null || imageBase64 == null) {
            throw new IllegalArgumentException("Document ID and image data are required");
        }

        // Call Cloud Function
        HttpsCallableReference uploadReportPhotoFunc = functions.getHttpsCallable("uploadReportPhoto");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("documentId", documentId);
        data.put("imageBase64", imageBase64);

        return uploadReportPhotoFunc.call(data)
                .continueWith(task -> null);
    }

    /**
     * Upload report photo from bitmap to Firebase Storage via Cloud Function
     * Stores image in report_photos/{documentId}.jpg
     *
     * @param documentId The report document ID
     * @param imageBase64 Base64 encoded bitmap image data
     * @return Task that completes when upload is done
     */
    public static com.google.android.gms.tasks.Task<Void> uploadReportPhotoBitmap(String documentId, String imageBase64) {
        if (documentId == null || imageBase64 == null) {
            throw new IllegalArgumentException("Document ID and image data are required");
        }

        // Call Cloud Function
        HttpsCallableReference uploadReportPhotoFunc = functions.getHttpsCallable("uploadReportPhoto");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("documentId", documentId);
        data.put("imageBase64", imageBase64);

        return uploadReportPhotoFunc.call(data)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() : new Exception("Upload failed");
                    }
                    return null;
                });
    }
}

