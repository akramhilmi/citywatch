package com.gitgud.citywatch.util;

import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

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
     * Fetch user admin status from Cloud Function
     * Returns true if user has isAdmin field set to true, false otherwise
     */
    public static com.google.android.gms.tasks.Task<Boolean> getIsAdmin(String userId) {
        HttpsCallableReference getIsAdminFunc = functions.getHttpsCallable("getIsAdmin");
        return getIsAdminFunc.call(new java.util.HashMap<String, Object>() {{
            put("userId", userId);
        }}).continueWith(task -> {
            if (task.isSuccessful()) {
                Object result = task.getResult().getData();
                return result instanceof Boolean ? (Boolean) result : false;
            }
            throw task.getException() != null ? task.getException() : new Exception("Failed to get admin status");
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
     * Get report photo download URL from Firebase Storage
     * Returns the download URL for the report's photo
     */
    public static com.google.android.gms.tasks.Task<String> getReportPhotoUrl(String documentId) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference reportPhotosRef = storage.getReference().child("report_photos/" + documentId + ".jpg");

        return reportPhotosRef.getDownloadUrl()
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
     * @param userId Current user ID for reference
     * @return Task that completes with the auto-generated document ID
     */
    public static com.google.android.gms.tasks.Task<String> submitReport(
            String description,
            String hazardType,
            String localGov,
            String locationDetails,
            double latitude,
            double longitude,
            String userId) {

        HttpsCallableReference submitReportFunc = functions.getHttpsCallable("submitReport");

        Map<String, Object> data = new HashMap<>();
        data.put("description", description);
        data.put("hazardType", hazardType);
        data.put("localGov", localGov);
        data.put("locationDetails", locationDetails);
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        data.put("userId", userId);

        return submitReportFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result = (Map<String, Object>) task.getResult().getData();
                        return (String) result.get("documentId");
                    }
                    throw task.getException() != null ? task.getException() : new Exception("Failed to submit report");
                });
    }

    /**
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
        Map<String, Object> data = new HashMap<>();
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
        Map<String, Object> data = new HashMap<>();
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

    /**
     * Fetch all reports from Firestore via Cloud Function
     * Includes photo URLs and user names
     *
     * @return Task that completes with list of HazardCard objects
     */
    public static com.google.android.gms.tasks.Task<java.util.List<com.gitgud.citywatch.model.HazardCard>> getAllReports() {
        HttpsCallableReference getAllReportsFunc = functions.getHttpsCallable("getAllReports");
        Map<String, Object> data = buildAuthenticatedData(new HashMap<>());

        return getAllReportsFunc.call(data)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        java.util.List<Map<String, Object>> resultList =
                            (java.util.List<Map<String, Object>>) task.getResult().getData();

                        java.util.List<com.gitgud.citywatch.model.HazardCard> hazardCards = new java.util.ArrayList<>();

                        for (Map<String, Object> reportMap : resultList) {
                            com.gitgud.citywatch.model.HazardCard hazardCard = new com.gitgud.citywatch.model.HazardCard();
                            hazardCard.setDocumentId((String) reportMap.get("documentId"));
                            hazardCard.setDescription((String) reportMap.get("description"));
                            hazardCard.setHazardType((String) reportMap.get("hazardType"));
                            hazardCard.setLocalGov((String) reportMap.get("localGov"));
                            hazardCard.setLocationDetails((String) reportMap.get("locationDetails"));
                            hazardCard.setStatus((String) reportMap.get("status"));
                            hazardCard.setUserName((String) reportMap.get("userName"));
                            hazardCard.setUserId((String) reportMap.get("userId"));

                            // Set latitude and longitude directly
                            Number latNum = (Number) reportMap.get("latitude");
                            Number lonNum = (Number) reportMap.get("longitude");
                            hazardCard.setLatitude(latNum != null ? latNum.doubleValue() : 0.0);
                            hazardCard.setLongitude(lonNum != null ? lonNum.doubleValue() : 0.0);

                            Number votesNum = (Number) reportMap.get("votes");
                            hazardCard.setVotes(votesNum != null ? votesNum.longValue() : 0L);

                            Number createdAtNum = (Number) reportMap.get("createdAt");
                            hazardCard.setCreatedAt(createdAtNum != null ? createdAtNum.longValue() : 0L);

                            Number scoreNum = (Number) reportMap.get("score");
                            hazardCard.setScore(scoreNum != null ? scoreNum.longValue() : 0L);

                            Number commentsNum = (Number) reportMap.get("comments");
                            hazardCard.setComments(commentsNum != null ? commentsNum.longValue() : 0L);

                            hazardCards.add(hazardCard);
                        }

                        return com.google.android.gms.tasks.Tasks.whenAllSuccess(
                                fetchPhotoUrlsAndProfilePicturesForCards(hazardCards))
                                .continueWith(photoTask -> hazardCards);
                    }
                    throw task.getException() != null ? task.getException() : new Exception("Failed to fetch reports");
                });
    }

    /**
     * Fetch photo URLs and profile pictures for all hazard cards
     */
    private static java.util.List<com.google.android.gms.tasks.Task<String>> fetchPhotoUrlsAndProfilePicturesForCards(
            java.util.List<com.gitgud.citywatch.model.HazardCard> hazardCards) {
        java.util.List<com.google.android.gms.tasks.Task<String>> tasks = new java.util.ArrayList<>();

        for (com.gitgud.citywatch.model.HazardCard card : hazardCards) {
            // Fetch report photo
            com.google.android.gms.tasks.Task<String> photoTask = getReportPhotoUrl(card.getDocumentId())
                    .continueWith(task -> {
                        if (task.isSuccessful()) {
                            card.setPhotoUrl(task.getResult());
                        }
                        return task.getResult();
                    });
            tasks.add(photoTask);

            // Fetch report author's profile picture
            if (card.getUserId() != null && !card.getUserId().isEmpty()) {
                android.util.Log.d("test userid", card.getUserId());
                com.google.android.gms.tasks.Task<String> profileTask = getProfilePictureUrl(card.getUserId())
                        .continueWith(task -> {
                            if (task.isSuccessful()) {
                                card.setProfilePictureUrl(task.getResult());
                            }
                            return task.getResult();
                        });
                tasks.add(profileTask);
            }
        }

        return tasks;
    }

    /**
     * Vote on a report (upvote, downvote, or remove vote)
     * @param reportId The report document ID
     * @param userId The current user's ID
     * @param voteType 1 for upvote, -1 for downvote, 0 to remove vote
     * @return Task with new score and user's vote status
     */
    public static com.google.android.gms.tasks.Task<VoteResult> voteReport(
            String reportId, String userId, int voteType) {
        HttpsCallableReference voteReportFunc = functions.getHttpsCallable("voteReport");

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", reportId);
        data.put("userId", userId);
        data.put("voteType", voteType);

        return voteReportFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        long score = ((Number) result.get("score")).longValue();
                        int userVote = ((Number) result.get("userVote")).intValue();
                        return new VoteResult(score, userVote);
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to vote");
                });
    }

    /**
     * Get user's votes for multiple reports
     * @param reportIds List of report document IDs
     * @param userId The current user's ID
     * @return Task with map of reportId to vote status
     */
    public static Task<java.util.Map<String, Integer>> getUserVotesForReports(
            java.util.List<String> reportIds, String userId) {
        HttpsCallableReference getUserVotesFunc = functions.getHttpsCallable("getUserVotesForReports");

        Map<String, Object> data = new HashMap<>();
        data.put("reportIds", reportIds);
        data.put("userId", userId);

        return getUserVotesFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        Map<String, Object> votesRaw =
                                (Map<String, Object>) result.get("votes");

                        java.util.Map<String, Integer> votes = new HashMap<>();
                        if (votesRaw != null) {
                            for (java.util.Map.Entry<String, Object> entry : votesRaw.entrySet()) {
                                votes.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                            }
                        }
                        return votes;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get votes");
                });
    }

    /**
     * Result class for vote operation
     */
    public static class VoteResult {
        public final long score;
        public final int userVote;

        public VoteResult(long score, int userVote) {
            this.score = score;
            this.userVote = userVote;
        }
    }

    // ==================== Comment Methods ====================

    /**
     * Submit a new comment to a report via Cloud Function
     * @param content Comment content
     * @param reportId The report document ID
     * @param userId Current user ID
     * @return Task that completes with the auto-generated comment ID
     */
    public static com.google.android.gms.tasks.Task<String> submitComment(
            String content, String reportId, String userId) {
        HttpsCallableReference submitCommentFunc = functions.getHttpsCallable("submitComment");

        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("reportId", reportId);
        data.put("userId", userId);

        return submitCommentFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        return (String) result.get("commentId");
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to submit comment");
                });
    }

    /**
     * Get all comments for a specific report via Cloud Function
     * @param reportId The report document ID
     * @return Task that completes with list of Comment objects
     */
    public static com.google.android.gms.tasks.Task<java.util.List<com.gitgud.citywatch.model.Comment>> getCommentsForReport(
            String reportId) {
        HttpsCallableReference getCommentsFunc = functions.getHttpsCallable("getCommentsForReport");
        Map<String, Object> data = buildAuthenticatedData("reportId", reportId);

        return getCommentsFunc.call(data)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        java.util.List<Map<String, Object>> resultList =
                                (java.util.List<Map<String, Object>>) task.getResult().getData();

                        java.util.List<com.gitgud.citywatch.model.Comment> comments = new java.util.ArrayList<>();

                        for (Map<String, Object> commentMap : resultList) {
                            com.gitgud.citywatch.model.Comment comment = new com.gitgud.citywatch.model.Comment();
                            comment.setCommentId((String) commentMap.get("commentId"));
                            comment.setContent((String) commentMap.get("content"));
                            comment.setReportId((String) commentMap.get("reportId"));
                            comment.setUserId((String) commentMap.get("userId"));
                            comment.setUserName((String) commentMap.get("userName"));

                            Number datetimeNum = (Number) commentMap.get("datetime");
                            comment.setDatetime(datetimeNum != null ? datetimeNum.longValue() : 0L);

                            Number scoreNum = (Number) commentMap.get("score");
                            comment.setScore(scoreNum != null ? scoreNum.longValue() : 0L);

                            comments.add(comment);
                        }

                        // Fetch profile pictures for all comments
                        return com.google.android.gms.tasks.Tasks.whenAllSuccess(
                                        fetchProfilePicturesForComments(comments))
                                .continueWith(photoTask -> comments);
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to fetch comments");
                });
    }

    /**
     * Fetch profile pictures for all comments
     */
    private static java.util.List<com.google.android.gms.tasks.Task<String>> fetchProfilePicturesForComments(
            java.util.List<com.gitgud.citywatch.model.Comment> comments) {
        java.util.List<com.google.android.gms.tasks.Task<String>> tasks = new java.util.ArrayList<>();

        for (com.gitgud.citywatch.model.Comment comment : comments) {
            if (comment.getUserId() != null && !comment.getUserId().isEmpty()) {
                com.google.android.gms.tasks.Task<String> profileTask = getProfilePictureUrl(comment.getUserId())
                        .continueWith(task -> {
                            if (task.isSuccessful()) {
                                comment.setProfilePictureUrl(task.getResult());
                            }
                            return task.getResult();
                        });
                tasks.add(profileTask);
            }
        }

        return tasks;
    }

    /**
     * Vote on a comment (upvote, downvote, or remove vote)
     * @param commentId The comment document ID
     * @param userId The current user's ID
     * @param voteType 1 for upvote, -1 for downvote, 0 to remove vote
     * @return Task with new score and user's vote status
     */
    public static com.google.android.gms.tasks.Task<VoteResult> voteComment(
            String commentId, String userId, int voteType) {
        HttpsCallableReference voteCommentFunc = functions.getHttpsCallable("voteComment");

        Map<String, Object> data = new HashMap<>();
        data.put("commentId", commentId);
        data.put("userId", userId);
        data.put("voteType", voteType);

        return voteCommentFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        long score = ((Number) result.get("score")).longValue();
                        int userVote = ((Number) result.get("userVote")).intValue();
                        return new VoteResult(score, userVote);
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to vote on comment");
                });
    }

    /**
     * Delete a comment via Cloud Function
     * @param commentId The comment document ID
     * @param userId The current user's ID (must be the comment author)
     * @return Task that completes when deletion is done
     */
    public static com.google.android.gms.tasks.Task<Void> deleteComment(String commentId, String userId) {
        HttpsCallableReference deleteCommentFunc = functions.getHttpsCallable("deleteComment");

        Map<String, Object> data = new HashMap<>();
        data.put("commentId", commentId);
        data.put("userId", userId);

        return deleteCommentFunc.call(data)
                .continueWith(task -> null);
    }

    /**
     * Edit an existing comment
     * Only the comment author (userId) can edit their comment
     *
     * @param commentId The comment document ID
     * @param userId The current user's ID (must be the author)
     * @param content The new comment content
     * @return Task that completes when edit is done
     */
    public static com.google.android.gms.tasks.Task<Void> editComment(
            String commentId, String userId, String content) {

        HttpsCallableReference editCommentFunc = functions.getHttpsCallable("editComment");

        Map<String, Object> data = new HashMap<>();
        data.put("commentId", commentId);
        data.put("userId", userId);
        data.put("content", content);

        return editCommentFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to edit comment");
                });
    }

    /**
     * Get user's votes for multiple comments
     * @param commentIds List of comment document IDs
     * @param userId The current user's ID
     * @return Task with map of commentId to vote status
     */
    public static Task<java.util.Map<String, Integer>> getUserVotesForComments(
            java.util.List<String> commentIds, String userId) {
        HttpsCallableReference getUserVotesFunc = functions.getHttpsCallable("getUserVotesForComments");

        Map<String, Object> data = new HashMap<>();
        data.put("commentIds", commentIds);
        data.put("userId", userId);

        return getUserVotesFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        Map<String, Object> votesRaw =
                                (Map<String, Object>) result.get("votes");

                        java.util.Map<String, Integer> votes = new HashMap<>();
                        if (votesRaw != null) {
                            for (java.util.Map.Entry<String, Object> entry : votesRaw.entrySet()) {
                                votes.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                            }
                        }
                        return votes;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get comment votes");
                });
    }

    /**
     * Get comment count for a report
     * @param reportId The report document ID
     * @return Task with comment count
     */
    public static com.google.android.gms.tasks.Task<Integer> getCommentCount(String reportId) {
        HttpsCallableReference getCommentCountFunc = functions.getHttpsCallable("getCommentCount");
        Map<String, Object> data = buildAuthenticatedData("reportId", reportId);

        return getCommentCountFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        Number count = (Number) result.get("count");
                        return count != null ? count.intValue() : 0;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get comment count");
                });
    }

    /**
     * Get current user ID from Firebase Auth
     * @return Current user ID or null if not authenticated
     */
    public static String getCurrentUserId() {
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Build a data map with userId parameter for cloud function calls
     * Includes userId if user is authenticated, otherwise returns empty map
     * @param additionalData Additional key-value pairs to include in the map
     * @return Map with userId and additional data
     */
    private static Map<String, Object> buildAuthenticatedData(
            Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        String userId = getCurrentUserId();

        if (userId != null) {
            data.put("userId", userId);
        }

        if (additionalData != null) {
            data.putAll(additionalData);
        }

        return data;
    }

    /**
     * Build a data map with userId parameter for cloud function calls
     * Convenience overload for single key-value pair
     * @param key The key for the additional parameter
     * @param value The value for the additional parameter
     * @return Map with userId and the additional key-value pair
     */
    private static Map<String, Object> buildAuthenticatedData(String key, Object value) {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put(key, value);
        return buildAuthenticatedData(additionalData);
    }

    // ==================== Cache Checksum Methods ====================

    // ==================== Cache Checksum Methods ====================

    /**
     * Get all checksums from server in a single lightweight call
     * Checksums are pre-calculated on server-side on every data change
     * @return Task with map of checksum keys to checksum values
     */
    public static com.google.android.gms.tasks.Task<java.util.Map<String, String>> getAllChecksums() {
        HttpsCallableReference getChecksumsFunc = functions.getHttpsCallable("getChecksums");

        return getChecksumsFunc.call(new HashMap<>())
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        java.util.Map<String, String> checksums = new HashMap<>();
                        if (result != null) {
                            for (java.util.Map.Entry<String, Object> entry : result.entrySet()) {
                                if (entry.getValue() != null) {
                                    checksums.put(entry.getKey(), entry.getValue().toString());
                                }
                            }
                        }
                        return checksums;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get checksums");
                });
    }

    /**
     * Result class for cache checksum operation (kept for compatibility)
     */
    public static class CacheChecksum {
        public final String checksum;
        public final int count;
        public final long latestTimestamp;

        public CacheChecksum(String checksum, int count, long latestTimestamp) {
            this.checksum = checksum;
            this.count = count;
            this.latestTimestamp = latestTimestamp;
        }
    }

    /**
     * Get cache checksum for reports to efficiently check for updates
     * @return Task with CacheChecksum containing checksum, count, and latest timestamp
     * @deprecated Use getAllChecksums() instead for better efficiency
     */
    public static com.google.android.gms.tasks.Task<CacheChecksum> getReportsCacheChecksum() {
        HttpsCallableReference getCacheChecksumFunc = functions.getHttpsCallable("getCacheChecksum");

        Map<String, Object> data = new HashMap<>();
        data.put("dataType", "reports");

        return getCacheChecksumFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        String checksum = (String) result.get("checksum");
                        Number countNum = (Number) result.get("count");
                        Number timestampNum = (Number) result.get("latestTimestamp");
                        return new CacheChecksum(
                                checksum,
                                countNum != null ? countNum.intValue() : 0,
                                timestampNum != null ? timestampNum.longValue() : 0
                        );
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get cache checksum");
                });
    }

    /**
     * Get cache checksum for comments to efficiently check for updates
     * @param reportId The report document ID
     * @return Task with CacheChecksum containing checksum, count, and latest timestamp
     * @deprecated Use getAllChecksums() instead for better efficiency
     */
    public static com.google.android.gms.tasks.Task<CacheChecksum> getCommentsCacheChecksum(
            String reportId) {
        HttpsCallableReference getCacheChecksumFunc = functions.getHttpsCallable("getCacheChecksum");

        Map<String, Object> data = new HashMap<>();
        data.put("dataType", "comments");
        data.put("reportId", reportId);

        return getCacheChecksumFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        String checksum = (String) result.get("checksum");
                        Number countNum = (Number) result.get("count");
                        Number timestampNum = (Number) result.get("latestTimestamp");
                        return new CacheChecksum(
                                checksum,
                                countNum != null ? countNum.intValue() : 0,
                                timestampNum != null ? timestampNum.longValue() : 0
                        );
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get cache checksum");
                });
    }

    /**
     * Get all report votes for the current user
     * Used to initialize vote cache on app startup
     * @param userId The current user's ID
     * @return Task with map of reportId to vote type
     */
    public static Task<java.util.Map<String, Integer>> getAllReportVotesForUser(
            String userId) {
        HttpsCallableReference getAllVotesFunc = functions.getHttpsCallable("getAllReportVotesForUser");

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);

        return getAllVotesFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        Map<String, Object> votesRaw =
                                (Map<String, Object>) result.get("votes");

                        java.util.Map<String, Integer> votes = new HashMap<>();
                        if (votesRaw != null) {
                            for (java.util.Map.Entry<String, Object> entry : votesRaw.entrySet()) {
                                votes.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                            }
                        }
                        return votes;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get report votes");
                });
    }

    /**
     * Get all comment votes for the current user
     * Used to initialize vote cache on app startup
     * @param userId The current user's ID
     * @return Task with map of commentId to vote type
     */
    public static Task<java.util.Map<String, Integer>> getAllCommentVotesForUser(
            String userId) {
        HttpsCallableReference getAllVotesFunc = functions.getHttpsCallable("getAllCommentVotesForUser");

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);

        return getAllVotesFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> result =
                                (Map<String, Object>) task.getResult().getData();
                        Map<String, Object> votesRaw =
                                (Map<String, Object>) result.get("votes");

                        java.util.Map<String, Integer> votes = new HashMap<>();
                        if (votesRaw != null) {
                            for (java.util.Map.Entry<String, Object> entry : votesRaw.entrySet()) {
                                votes.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                            }
                        }
                        return votes;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to get comment votes");
                });
    }

    /**
     * Edit an existing report
     * Only the report owner (userId) can edit their report
     *
     * @param reportId The report document ID
     * @param userId The current user's ID (must be the owner)
     * @param description Updated description
     * @param hazardType Updated hazard type
     * @param localGov Updated local government
     * @param locationDetails Updated location details
     * @param latitude Updated latitude
     * @param longitude Updated longitude
     * @param status Updated status
     * @return Task that completes when edit is done
     */
    public static com.google.android.gms.tasks.Task<Void> editReport(
            String reportId,
            String userId,
            String description,
            String hazardType,
            String localGov,
            String locationDetails,
            double latitude,
            double longitude,
            String status) {

        HttpsCallableReference editReportFunc = functions.getHttpsCallable("editReport");

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", reportId);
        data.put("userId", userId);
        data.put("description", description);
        data.put("hazardType", hazardType);
        data.put("localGov", localGov);
        data.put("locationDetails", locationDetails);
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        data.put("status", status);

        return editReportFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to edit report");
                });
    }

    /**
     * Delete a report and its associated photo
     * Only the report owner (userId) can delete their report
     *
     * @param reportId The report document ID
     * @param userId The current user's ID (must be the owner)
     * @return Task that completes when delete is done
     */
    public static com.google.android.gms.tasks.Task<Void> deleteReport(
            String reportId, String userId) {

        HttpsCallableReference deleteReportFunc = functions.getHttpsCallable("deleteReport");

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", reportId);
        data.put("userId", userId);

        return deleteReportFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to delete report");
                });
    }

    /**
     * Update report status (admin only)
     * @param reportId The report document ID
     * @param newStatus The new status (Submitted, Confirmed, In progress, Resolved)
     * @param userId The current user's ID (must be an admin)
     * @return Task that completes when update is done
     */
    public static com.google.android.gms.tasks.Task<Void> updateReportStatus(
            String reportId, String newStatus, String userId) {

        HttpsCallableReference updateStatusFunc = functions.getHttpsCallable("updateReportStatus");

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", reportId);
        data.put("newStatus", newStatus);
        data.put("userId", userId);

        return updateStatusFunc.call(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    throw task.getException() != null ?
                            task.getException() : new Exception("Failed to update status");
                });
    }

    /**
     * Get report statistics
     */
    public static Task<java.util.Map<String, Integer>> getStats() {
        HttpsCallableReference getStatsFunc = functions.getHttpsCallable("getStats");

        return getStatsFunc.call()
            .continueWith(task -> {
                if (task.isSuccessful()) {
                    Map<String, Object> result =
                        (Map<String, Object>) task.getResult().getData();

                    java.util.Map<String, Integer> stats = new HashMap<>();
                    if (result != null) {
                        for (java.util.Map.Entry<String, Object> entry : result.entrySet()) {
                            stats.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                        }
                    }
                    return stats;
                }
                throw task.getException() != null ?
                    task.getException() : new Exception("Failed to get comment votes");
        });
    }

    /**
     * Increment any of the four stats by one
     * @return Task that completes after the increment
     */
//    public static Task<Void> incrementStat(String stat) {
//        HttpsCallableReference incrementStatFunc = functions.getHttpsCallable("incrementStat");
//
//        return incrementStatFunc.call(new HashMap<String, String>().put("stat", stat))
//                .continueWith(task -> {
//                    if (task.isSuccessful()) {
//                        return null;
//                    }
//                    throw task.getException() != null ?
//                        task.getException() : new Exception("Failed to increment " + stat + " by 1");
//                });
//    }

    /**
     * Decrement any of the four stats by one
     * @return Task that completes after the decrement
     */
//    public static Task<Void> decrementStat(String stat) {
//        HttpsCallableReference decrementStatFunc = functions.getHttpsCallable("decrementStat");
//
//        return decrementStatFunc.call(new HashMap<String, String>().put("stat", stat))
//            .continueWith(task -> {
//                if (task.isSuccessful()) {
//                    return null;
//                }
//                throw task.getException() != null ?
//                    task.getException() : new Exception("Failed to decrement " + stat + " by 1");
//            });
//    }
}
