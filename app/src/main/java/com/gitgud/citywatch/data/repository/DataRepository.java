package com.gitgud.citywatch.data.repository;

import android.content.Context;
import android.util.Log;

import com.gitgud.citywatch.data.cache.CacheManager;
import com.gitgud.citywatch.data.cache.entity.CachedUserProfile;
import com.gitgud.citywatch.model.Comment;
import com.gitgud.citywatch.model.HazardCard;
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;

import java.util.List;
import java.util.Map;

/**
 * Repository that implements cache-first pattern for all Firebase data.
 * Shows cached data immediately, then checks for updates in background.
 */
public class DataRepository {
    private static final String TAG = "DataRepository";
    private static volatile DataRepository INSTANCE;

    private final CacheManager cacheManager;

    // Flags to track when cache is known to be stale (after local changes)
    private volatile boolean reportsCacheInvalidated = false;
    private volatile java.util.Set<String> commentsCacheInvalidated = new java.util.HashSet<>();

    // Cached server checksums (fetched on navigation change)
    private volatile java.util.Map<String, String> serverChecksums = new java.util.HashMap<>();
    private volatile long lastChecksumFetch = 0;
    private static final long CHECKSUM_FETCH_THROTTLE_MS = 2000; // Throttle to avoid rapid fetches

    private DataRepository(Context context) {
        this.cacheManager = CacheManager.getInstance(context);
    }

    /**
     * Get singleton instance
     */
    public static DataRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DataRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DataRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Initialize local cache with user's all votings on app startup
     * This ensures votes are available immediately without network calls
     */
    public void initializeUserVotes() {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            Log.w(TAG, "Cannot initialize votes: user not logged in");
            return;
        }

        Log.d(TAG, "Initializing user votes for: " + userId);

        // Load all report votes
        ApiClient.getAllReportVotesForUser(userId)
                .addOnSuccessListener(reportVotes -> {
                    if (!reportVotes.isEmpty()) {
                        cacheManager.cacheReportVotes(userId, reportVotes);
                        Log.d(TAG, "Cached " + reportVotes.size() + " report votes");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load report votes", e));

        // Load all comment votes
        ApiClient.getAllCommentVotesForUser(userId)
                .addOnSuccessListener(commentVotes -> {
                    if (!commentVotes.isEmpty()) {
                        cacheManager.cacheCommentVotes(userId, commentVotes);
                        Log.d(TAG, "Cached " + commentVotes.size() + " comment votes");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load comment votes", e));
    }

    // ==================== Navigation-Triggered Checksum Validation ====================

    /**
     * Called when bottom navigation changes
     * Fetches all server checksums in one lightweight call and compares with local
     * @param callback Called with map of invalidated cache keys (e.g., "reports", "comments_xxx")
     */
    public void validateCachesOnNavigation(ChecksumValidationCallback callback) {
        // Throttle rapid fetches
        long now = System.currentTimeMillis();
        if (now - lastChecksumFetch < CHECKSUM_FETCH_THROTTLE_MS) {
            Log.d(TAG, "Checksum fetch throttled");
            callback.onValidated(new java.util.HashSet<>());
            return;
        }
        lastChecksumFetch = now;

        Log.d(TAG, "Fetching server checksums for validation...");
        ApiClient.getAllChecksums()
                .addOnSuccessListener(checksums -> {
                    serverChecksums = checksums;
                    java.util.Set<String> invalidatedKeys = new java.util.HashSet<>();

                    // Compare reports checksum
                    String serverReportsChecksum = checksums.get("reports");
                    if (serverReportsChecksum != null) {
                        cacheManager.getReportsCacheHash(localHash -> {
                            if (localHash == null || !localHash.equals(serverReportsChecksum)) {
                                Log.d(TAG, "Reports cache invalid: local=" + localHash +
                                        ", server=" + serverReportsChecksum);
                                invalidatedKeys.add("reports");
                                reportsCacheInvalidated = true;
                            }
                            callback.onValidated(invalidatedKeys);
                        });
                    } else {
                        callback.onValidated(invalidatedKeys);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch checksums", e);
                    callback.onValidated(new java.util.HashSet<>());
                });
    }

    /**
     * Validate comments cache for a specific report
     * @param reportId The report ID to validate comments for
     * @param callback Called with true if cache is valid, false if stale
     */
    public void validateCommentsCache(String reportId, CacheValidCallback callback) {
        String serverChecksum = serverChecksums.get("comments_" + reportId);
        if (serverChecksum == null) {
            // No server checksum available, fetch fresh
            callback.onResult(false);
            return;
        }

        String cacheKey = CacheManager.KEY_COMMENTS_PREFIX + reportId;
        cacheManager.getReportsCacheHash(localHash -> {
            boolean isValid = localHash != null && localHash.equals(serverChecksum);
            if (!isValid) {
                commentsCacheInvalidated.add(reportId);
            }
            callback.onResult(isValid);
        });
    }

    /**
     * Get the server checksum for reports (already fetched)
     */
    public String getServerReportsChecksum() {
        return serverChecksums.get("reports");
    }

    /**
     * Get the server checksum for comments of a report (already fetched)
     */
    public String getServerCommentsChecksum(String reportId) {
        return serverChecksums.get("comments_" + reportId);
    }

    /**
     * Callback for checksum validation
     */
    public interface ChecksumValidationCallback {
        void onValidated(java.util.Set<String> invalidatedKeys);
    }

    /**
     * Callback for single cache validation
     */
    public interface CacheValidCallback {
        void onResult(boolean isValid);
    }

    // ==================== Reports ====================

    /**
     * Get all reports with cache-first strategy
     * @param callback Called with cached data first, then updated data if available
     */
    public void getAllReports(DataCallback<List<HazardCard>> callback) {
        // Check if cache is known to be stale (after local changes)
        if (reportsCacheInvalidated) {
            Log.d(TAG, "Cache invalidated flag set, fetching fresh data immediately");
            callback.onLoading(true);
            reportsCacheInvalidated = false; // Reset flag
            fetchReportsFromApi(callback, new java.util.ArrayList<>(), null);
            return;
        }

        // Step 1: Return cached data immediately
        cacheManager.getCachedReports(cachedReports -> {
            if (!cachedReports.isEmpty()) {
                Log.d(TAG, "Returning " + cachedReports.size() + " cached reports");
                callback.onCacheData(cachedReports);
            }

            // Step 2: Check for updates using lightweight checksum
            callback.onLoading(true);
            checkForReportUpdates(cachedReports, callback);
        });
    }

    private void checkForReportUpdates(List<HazardCard> cachedReports,
                                       DataCallback<List<HazardCard>> callback) {
        // First check if we already have server checksum from navigation validation
        String serverChecksum = serverChecksums.get("reports");
        if (serverChecksum != null) {
            cacheManager.getReportsCacheHash(cachedHash -> {
                boolean needsRefresh = cachedHash == null ||
                        !cachedHash.equals(serverChecksum) ||
                        cachedReports.isEmpty();

                if (needsRefresh) {
                    Log.d(TAG, "Reports checksum mismatch (pre-fetched), fetching fresh");
                    fetchReportsFromApi(callback, cachedReports, serverChecksum);
                } else {
                    Log.d(TAG, "Reports checksum matches (pre-fetched), using cache");
                    callback.onLoading(false);
                    if (!cachedReports.isEmpty()) {
                        applyUserVotesToReports(cachedReports, callback);
                    }
                }
            });
            return;
        }

        // Fallback: fetch checksum from server
        ApiClient.getReportsCacheChecksum()
                .addOnSuccessListener(checksum -> {
                    cacheManager.getReportsCacheHash(cachedHash -> {
                        boolean needsRefresh = cachedHash == null ||
                                !cachedHash.equals(checksum.checksum) ||
                                cachedReports.isEmpty();

                        if (needsRefresh) {
                            Log.d(TAG, "Reports checksum mismatch, fetching fresh data");
                            fetchReportsFromApi(callback, cachedReports, checksum.checksum);
                        } else {
                            Log.d(TAG, "Reports checksum matches, using cache");
                            callback.onLoading(false);
                            if (!cachedReports.isEmpty()) {
                                applyUserVotesToReports(cachedReports, callback);
                            }
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get checksum, falling back to staleness check", e);;
                    // Fallback to time-based check
                    cacheManager.isReportsCacheStale(isStale -> {
                        if (isStale || cachedReports.isEmpty()) {
                            fetchReportsFromApi(callback, cachedReports, null);
                        } else {
                            callback.onLoading(false);
                            applyUserVotesToReports(cachedReports, callback);
                        }
                    });
                });
    }

    private void fetchReportsFromApi(DataCallback<List<HazardCard>> callback,
                                     List<HazardCard> cachedReports,
                                     String newChecksum) {
        ApiClient.getAllReports()
                .addOnSuccessListener(freshReports -> {
                    // Use provided checksum or generate one
                    String hash = newChecksum != null ? newChecksum :
                            CacheManager.generateHash(freshReports);

                    Log.d(TAG, "Fetched " + freshReports.size() + " fresh reports");
                    cacheManager.cacheReports(freshReports, hash);

                    // Apply user votes and return fresh data
                    applyUserVotesToReports(freshReports, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch reports", e);
                    callback.onLoading(false);
                    if (cachedReports.isEmpty()) {
                        callback.onError(e);
                    }
                });
    }

    private void applyUserVotesToReports(List<HazardCard> reports,
                                         DataCallback<List<HazardCard>> callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null || reports.isEmpty()) {
            callback.onFreshData(reports);
            callback.onLoading(false);
            return;
        }

        // Try cached votes first
        cacheManager.getCachedReportVotes(userId, cachedVotes -> {
            if (!cachedVotes.isEmpty()) {
                for (HazardCard card : reports) {
                    Integer vote = cachedVotes.get(card.getDocumentId());
                    card.setUserVote(vote != null ? vote : 0);
                }
            }

            // Fetch fresh votes
            java.util.List<String> reportIds = new java.util.ArrayList<>();
            for (HazardCard card : reports) {
                reportIds.add(card.getDocumentId());
            }

            ApiClient.getUserVotesForReports(reportIds, userId)
                    .addOnSuccessListener(freshVotes -> {
                        // Cache and apply fresh votes
                        cacheManager.cacheReportVotes(userId, freshVotes);
                        for (HazardCard card : reports) {
                            Integer vote = freshVotes.get(card.getDocumentId());
                            card.setUserVote(vote != null ? vote : 0);
                        }
                        callback.onFreshData(reports);
                        callback.onLoading(false);
                    })
                    .addOnFailureListener(e -> {
                        // Still return reports even if votes failed
                        callback.onFreshData(reports);
                        callback.onLoading(false);
                    });
        });
    }

    // ==================== Comments ====================

    /**
     * Get comments for a report with cache-first strategy
     */
    public void getCommentsForReport(String reportId, DataCallback<List<Comment>> callback) {
        // Check if cache is known to be stale for this report
        if (commentsCacheInvalidated.contains(reportId)) {
            Log.d(TAG, "Comments cache invalidated flag set for " + reportId);
            callback.onLoading(true);
            commentsCacheInvalidated.remove(reportId); // Reset flag
            fetchCommentsFromApi(reportId, callback, new java.util.ArrayList<>(), null);
            return;
        }

        // Step 1: Return cached comments immediately
        cacheManager.getCachedComments(reportId, cachedComments -> {
            if (!cachedComments.isEmpty()) {
                Log.d(TAG, "Returning " + cachedComments.size() + " cached comments");
                callback.onCacheData(cachedComments);
            }

            // Step 2: Check for updates using lightweight checksum
            callback.onLoading(true);
            checkForCommentUpdates(reportId, cachedComments, callback);
        });
    }

    private void checkForCommentUpdates(String reportId, List<Comment> cachedComments,
                                        DataCallback<List<Comment>> callback) {
        String cacheKey = CacheManager.KEY_COMMENTS_PREFIX + reportId;

        ApiClient.getCommentsCacheChecksum(reportId)
                .addOnSuccessListener(checksum -> {
                    cacheManager.getReportsCacheHash(cachedHash -> {
                        boolean needsRefresh = cachedHash == null ||
                                !cachedHash.equals(checksum.checksum) ||
                                cachedComments.isEmpty();

                        if (needsRefresh) {
                            Log.d(TAG, "Comments checksum mismatch, fetching fresh");
                            fetchCommentsFromApi(reportId, callback, cachedComments,
                                    checksum.checksum);
                        } else {
                            Log.d(TAG, "Comments checksum matches, using cache");
                            callback.onLoading(false);
                            if (!cachedComments.isEmpty()) {
                                applyUserVotesToComments(cachedComments, callback);
                            }
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get comments checksum, falling back", e);
                    // Fallback to time-based check
                    cacheManager.isCommentsCacheStale(reportId, isStale -> {
                        if (isStale || cachedComments.isEmpty()) {
                            fetchCommentsFromApi(reportId, callback, cachedComments, null);
                        } else {
                            callback.onLoading(false);
                            applyUserVotesToComments(cachedComments, callback);
                        }
                    });
                });
    }

    private void fetchCommentsFromApi(String reportId, DataCallback<List<Comment>> callback,
                                       List<Comment> cachedComments, String newChecksum) {
        ApiClient.getCommentsForReport(reportId)
                .addOnSuccessListener(freshComments -> {
                    String hash = newChecksum != null ? newChecksum :
                            CacheManager.generateHash(freshComments);
                    Log.d(TAG, "Fetched " + freshComments.size() + " fresh comments");
                    cacheManager.cacheComments(reportId, freshComments, hash);
                    applyUserVotesToComments(freshComments, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch comments", e);
                    callback.onLoading(false);
                    if (cachedComments.isEmpty()) {
                        callback.onError(e);
                    }
                });
    }

    private void applyUserVotesToComments(List<Comment> comments,
                                          DataCallback<List<Comment>> callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null || comments.isEmpty()) {
            callback.onFreshData(comments);
            callback.onLoading(false);
            return;
        }

        // Try cached votes first - apply immediately
        cacheManager.getCachedCommentVotes(userId, cachedVotes -> {
            if (!cachedVotes.isEmpty()) {
                for (Comment comment : comments) {
                    Integer vote = cachedVotes.get(comment.getCommentId());
                    comment.setUserVote(vote != null ? vote : 0);
                }
            }

            // Call callback immediately with cached votes applied
            callback.onFreshData(comments);
            callback.onLoading(false);

            // Fetch fresh votes in background
            java.util.List<String> commentIds = new java.util.ArrayList<>();
            for (Comment comment : comments) {
                commentIds.add(comment.getCommentId());
            }

            ApiClient.getUserVotesForComments(commentIds, userId)
                    .addOnSuccessListener(freshVotes -> {
                        cacheManager.cacheCommentVotes(userId, freshVotes);
                        for (Comment comment : comments) {
                            Integer vote = freshVotes.get(comment.getCommentId());
                            comment.setUserVote(vote != null ? vote : 0);
                        }
                        // Update UI with fresh votes
                        callback.onFreshData(comments);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch fresh comment votes", e);
                        // Keep using cached votes, no need to call callback again
                    });
        });
    }

    // ==================== User Profile ====================

    /**
     * Get user profile with cache-first strategy
     */
    public void getUserProfile(String userId, UserProfileCallback callback) {
        // Step 1: Return cached profile immediately
        cacheManager.getCachedUserProfile(userId, cachedProfile -> {
            if (cachedProfile != null) {
                Log.d(TAG, "Returning cached profile for " + userId);
                callback.onCacheData(
                        cachedProfile.getName(),
                        cachedProfile.getPhone(),
                        cachedProfile.getProfilePictureUrl()
                );
            }

            // Step 2: Check if cache is stale
            cacheManager.isUserProfileCacheStale(userId, isStale -> {
                if (isStale || cachedProfile == null) {
                    Log.d(TAG, "Profile cache is stale or empty, fetching fresh");
                    callback.onLoading(true);
                    fetchUserProfileFromApi(userId, callback);
                } else {
                    callback.onLoading(false);
                }
            });
        });
    }

    private void fetchUserProfileFromApi(String userId, UserProfileCallback callback) {
        final String[] name = {null};
        final String[] phone = {null};
        final String[] pictureUrl = {null};
        final int[] completedCalls = {0};
        final int totalCalls = 3;

        Runnable checkComplete = () -> {
            if (completedCalls[0] >= totalCalls) {
                // Cache the profile
                cacheManager.cacheUserProfile(userId, name[0], phone[0],
                        ApiClient.getCurrentUserEmail(), pictureUrl[0]);
                callback.onFreshData(name[0], phone[0], pictureUrl[0]);
                callback.onLoading(false);
            }
        };

        // Fetch name
        ApiClient.getUserName(userId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        name[0] = task.getResult();
                    }
                    completedCalls[0]++;
                    checkComplete.run();
                });

        // Fetch phone
        ApiClient.getUserPhone(userId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        phone[0] = task.getResult();
                    }
                    completedCalls[0]++;
                    checkComplete.run();
                });

        // Fetch profile picture
        ApiClient.getProfilePictureUrl(userId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        pictureUrl[0] = task.getResult();
                    }
                    completedCalls[0]++;
                    checkComplete.run();
                });
    }

    // ==================== Vote Operations ====================

    /**
     * Vote on a report (with optimistic update)
     */
    public void voteReport(String reportId, int voteType,
                           VoteCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.voteReport(reportId, userId, voteType)
                .addOnSuccessListener(result -> {
                    // Update cache
                    cacheManager.updateReportVote(reportId, userId, result.userVote);
                    cacheManager.updateReportScore(reportId, result.score);
                    callback.onSuccess(result.score, result.userVote);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Vote on a comment (with optimistic update)
     */
    public void voteComment(String commentId, int voteType,
                            VoteCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.voteComment(commentId, userId, voteType)
                .addOnSuccessListener(result -> {
                    // Update cache
                    cacheManager.updateCommentVote(commentId, userId, result.userVote);
                    cacheManager.updateCommentScore(commentId, result.score);
                    callback.onSuccess(result.score, result.userVote);
                })
                .addOnFailureListener(callback::onError);
    }

    // ==================== Profile Update Operations ====================

    /**
     * Update user name (updates cache immediately)
     * Also invalidates reports cache since reports display usernames
     */
    public void updateUserName(String newName, SimpleCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.updateUserName(userId, newName)
                .addOnSuccessListener(aVoid -> {
                    cacheManager.updateCachedUserName(userId, newName);
                    // Invalidate reports cache since reports display usernames
                    invalidateReportsCache();
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Update user phone (updates cache immediately)
     */
    public void updateUserPhone(String newPhone, SimpleCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.updateUserPhone(userId, newPhone)
                .addOnSuccessListener(aVoid -> {
                    cacheManager.updateCachedUserPhone(userId, newPhone);
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }

    // ==================== Report Submission ====================

    /**
     * Submit a new report and invalidate cache to trigger refresh
     */
    public void submitReport(String description, String hazardType, String localGov,
                             String locationDetails, double latitude, double longitude,
                             ReportSubmitCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.submitReport(description, hazardType, localGov, locationDetails,
                        latitude, longitude, userId)
                .addOnSuccessListener(documentId -> {
                    // Invalidate reports cache to force refresh
                    invalidateReportsCache();
                    callback.onSuccess(documentId);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Upload report photo
     */
    public void uploadReportPhoto(String documentId, String imageBase64,
                                  SimpleCallback callback) {
        ApiClient.uploadReportPhoto(documentId, imageBase64)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    /**
     * Upload report photo from bitmap
     */
    public void uploadReportPhotoBitmap(String documentId, String imageBase64,
                                        SimpleCallback callback) {
        ApiClient.uploadReportPhotoBitmap(documentId, imageBase64)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    // ==================== User Reports (Home Fragment) ====================

    /**
     * Get current user's reports with cache-first strategy
     */
    public void getUserReports(DataCallback<List<HazardCard>> callback) {
        String currentUserId = SessionManager.getCurrentUserId();
        if (currentUserId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        // Use getAllReports and filter for current user
        getAllReports(new DataCallback<List<HazardCard>>() {
            @Override
            public void onCacheData(List<HazardCard> data) {
                List<HazardCard> userReports = filterByUserId(data, currentUserId);
                callback.onCacheData(userReports);
            }

            @Override
            public void onFreshData(List<HazardCard> data) {
                List<HazardCard> userReports = filterByUserId(data, currentUserId);
                callback.onFreshData(userReports);
            }

            @Override
            public void onLoading(boolean isLoading) {
                callback.onLoading(isLoading);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private List<HazardCard> filterByUserId(List<HazardCard> reports, String userId) {
        List<HazardCard> filtered = new java.util.ArrayList<>();
        for (HazardCard report : reports) {
            if (userId.equals(report.getUserId())) {
                filtered.add(report);
            }
        }
        // Sort by createdAt descending (newest first)
        filtered.sort((c1, c2) -> Long.compare(c2.getCreatedAt(), c1.getCreatedAt()));
        return filtered;
    }

    // ==================== Comment Submission ====================

    /**
     * Submit a comment and update cache
     * Invalidates reports cache to force fresh fetch with correct comment count
     */
    public void submitComment(String content, String reportId,
                              CommentSubmitCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.submitComment(content, reportId, userId)
                .addOnSuccessListener(commentId -> {
                    // Invalidate comments cache to force refresh
                    invalidateCommentsCache(reportId);
                    // Also invalidate reports cache so comment count is fetched fresh
                    invalidateReportsCache();
                    callback.onSuccess(commentId);
                })
                .addOnFailureListener(callback::onError);
    }

    // ==================== Profile Picture ====================

    /**
     * Upload profile picture and update cache
     * Also invalidates reports cache since reports display profile pictures
     */
    public void uploadProfilePicture(android.net.Uri imageUri,
                                      ProfilePictureCallback callback) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User not logged in"));
            return;
        }

        ApiClient.uploadProfilePicture(userId, imageUri)
                .addOnSuccessListener(downloadUrl -> {
                    // Update cached profile picture URL
                    cacheManager.updateCachedProfilePictureUrl(userId, downloadUrl);
                    // Invalidate reports cache since reports display profile pictures
                    invalidateReportsCache();
                    callback.onSuccess(downloadUrl);
                })
                .addOnFailureListener(callback::onError);
    }

    // ==================== Cache Management ====================

    /**
     * Invalidate reports cache (force refresh on next access)
     * Sets flag so next getAllReports() skips showing stale cached data
     */
    public void invalidateReportsCache() {
        reportsCacheInvalidated = true;
        cacheManager.cacheReports(new java.util.ArrayList<>(), "");
        Log.d(TAG, "Reports cache invalidated - will fetch fresh on next load");
    }

    /**
     * Invalidate comments cache for a report
     * Sets flag so next getCommentsForReport() skips showing stale cached data
     */
    public void invalidateCommentsCache(String reportId) {
        commentsCacheInvalidated.add(reportId);
        cacheManager.cacheComments(reportId, new java.util.ArrayList<>(), "");
        Log.d(TAG, "Comments cache invalidated for " + reportId + " - will fetch fresh on next load");
    }

    /**
     * Update a specific comment in the cache
     * Used for optimistic UI updates after editing
     */
    public void updateCommentInCache(Comment comment) {
        cacheManager.updateCachedComment(comment);
        Log.d(TAG, "Updated comment in cache: " + comment.getCommentId());
    }

    /**
     * Remove a specific comment from the cache
     * Used for optimistic UI updates after deletion
     */
    public void removeCommentFromCache(String commentId, String reportId) {
        cacheManager.removeCachedComment(commentId);
        Log.d(TAG, "Removed comment from cache: " + commentId);
    }

    /**
     * Update a specific report in the cache
     * Used for optimistic UI updates after editing
     */
    public void updateReportInCache(HazardCard report) {
        cacheManager.updateCachedReport(report);
        Log.d(TAG, "Updated report in cache: " + report.getDocumentId());
    }

    /**
     * Remove a specific report from the cache
     * Used for optimistic UI updates after deletion
     */
    public void removeReportFromCache(String reportId) {
        cacheManager.removeCachedReport(reportId);
        Log.d(TAG, "Removed report from cache: " + reportId);
    }

    /**
     * Clear all caches (e.g., on logout)
     */
    public void clearAllCaches() {
        reportsCacheInvalidated = false;
        commentsCacheInvalidated.clear();
        cacheManager.clearAllCaches();
    }

    // ==================== Callback Interfaces ====================

    /**
     * Callback for data operations with cache-first pattern
     */
    public interface DataCallback<T> {
        /**
         * Called when cached data is available
         */
        void onCacheData(T data);

        /**
         * Called when fresh data is available from API
         */
        void onFreshData(T data);

        /**
         * Called when loading state changes
         */
        void onLoading(boolean isLoading);

        /**
         * Called when an error occurs
         */
        void onError(Exception e);
    }

    /**
     * Callback for user profile operations
     */
    public interface UserProfileCallback {
        void onCacheData(String name, String phone, String profilePictureUrl);
        void onFreshData(String name, String phone, String profilePictureUrl);
        void onLoading(boolean isLoading);
        void onError(Exception e);
    }

    /**
     * Callback for vote operations
     */
    public interface VoteCallback {
        void onSuccess(long score, int userVote);
        void onError(Exception e);
    }

    /**
     * Simple callback for operations without return data
     */
    public interface SimpleCallback {
        void onSuccess();
        void onError(Exception e);
    }

    /**
     * Callback for report submission
     */
    public interface ReportSubmitCallback {
        void onSuccess(String documentId);
        void onError(Exception e);
    }

    /**
     * Callback for comment submission
     */
    public interface CommentSubmitCallback {
        void onSuccess(String commentId);
        void onError(Exception e);
    }

    /**
     * Callback for profile picture upload
     */
    public interface ProfilePictureCallback {
        void onSuccess(String downloadUrl);
        void onError(Exception e);
    }
}
