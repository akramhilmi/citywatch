package com.gitgud.citywatch.data.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gitgud.citywatch.data.cache.dao.CacheMetadataDao;
import com.gitgud.citywatch.data.cache.dao.CommentDao;
import com.gitgud.citywatch.data.cache.dao.CommentVoteDao;
import com.gitgud.citywatch.data.cache.dao.ReportDao;
import com.gitgud.citywatch.data.cache.dao.ReportVoteDao;
import com.gitgud.citywatch.data.cache.dao.UserProfileDao;
import com.gitgud.citywatch.data.cache.entity.CacheMetadata;
import com.gitgud.citywatch.data.cache.entity.CachedComment;
import com.gitgud.citywatch.data.cache.entity.CachedCommentVote;
import com.gitgud.citywatch.data.cache.entity.CachedReport;
import com.gitgud.citywatch.data.cache.entity.CachedReportVote;
import com.gitgud.citywatch.data.cache.entity.CachedUserProfile;
import com.gitgud.citywatch.model.Comment;
import com.gitgud.citywatch.model.HazardCard;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages local caching of Firebase data
 * Provides cache-first data access with background refresh
 */
public class CacheManager {
    private static final String TAG = "CacheManager";
    private static volatile CacheManager INSTANCE;

    // Cache keys
    public static final String KEY_ALL_REPORTS = "all_reports";
    public static final String KEY_COMMENTS_PREFIX = "comments_";
    public static final String KEY_USER_PROFILE_PREFIX = "user_profile_";

    // Cache expiry times (milliseconds)
    private static final long CACHE_EXPIRY_REPORTS = 5 * 60 * 1000; // 5 minutes
    private static final long CACHE_EXPIRY_COMMENTS = 2 * 60 * 1000; // 2 minutes
    private static final long CACHE_EXPIRY_USER_PROFILE = 10 * 60 * 1000; // 10 minutes

    private final AppDatabase database;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private CacheManager(Context context) {
        this.database = AppDatabase.getInstance(context);
        this.executor = Executors.newFixedThreadPool(4);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Get singleton instance
     */
    public static CacheManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CacheManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CacheManager(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ==================== Reports ====================

    /**
     * Get cached reports
     * @return List of HazardCard from cache, or empty list if no cache
     */
    public void getCachedReports(CacheCallback<List<HazardCard>> callback) {
        executor.execute(() -> {
            try {
                List<CachedReport> cachedReports = database.reportDao().getAllReports();
                List<HazardCard> hazardCards = convertToHazardCards(cachedReports);
                mainHandler.post(() -> callback.onResult(hazardCards));
            } catch (Exception e) {
                Log.e(TAG, "Error getting cached reports", e);
                mainHandler.post(() -> callback.onResult(new ArrayList<>()));
            }
        });
    }

    /**
     * Cache reports
     */
    public void cacheReports(List<HazardCard> reports, String dataHash) {
        executor.execute(() -> {
            try {
                List<CachedReport> cachedReports = convertToCachedReports(reports);
                database.reportDao().deleteAll();
                database.reportDao().insertAll(cachedReports);

                // Update metadata
                CacheMetadata metadata = new CacheMetadata();
                metadata.setCacheKey(KEY_ALL_REPORTS);
                metadata.setDataHash(dataHash);
                metadata.setLastUpdated(System.currentTimeMillis());
                metadata.setItemCount(reports.size());
                database.cacheMetadataDao().insert(metadata);

                Log.d(TAG, "Cached " + reports.size() + " reports");
            } catch (Exception e) {
                Log.e(TAG, "Error caching reports", e);
            }
        });
    }

    /**
     * Check if reports cache is stale
     */
    public void isReportsCacheStale(CacheCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                Long lastUpdated = database.cacheMetadataDao().getLastUpdated(KEY_ALL_REPORTS);
                boolean isStale = lastUpdated == null ||
                        (System.currentTimeMillis() - lastUpdated) > CACHE_EXPIRY_REPORTS;
                mainHandler.post(() -> callback.onResult(isStale));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(true));
            }
        });
    }

    /**
     * Get cached reports hash for comparison
     */
    public void getReportsCacheHash(CacheCallback<String> callback) {
        executor.execute(() -> {
            try {
                String hash = database.cacheMetadataDao().getDataHash(KEY_ALL_REPORTS);
                mainHandler.post(() -> callback.onResult(hash));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Update a single report's score in cache
     */
    public void updateReportScore(String documentId, long score) {
        executor.execute(() -> {
            try {
                database.reportDao().updateScore(documentId, score);
            } catch (Exception e) {
                Log.e(TAG, "Error updating report score", e);
            }
        });
    }

    /**
     * Update a single report's comment count in cache
     */
    public void updateReportCommentCount(String documentId, long commentCount) {
        executor.execute(() -> {
            try {
                database.reportDao().updateCommentCount(documentId, commentCount);
            } catch (Exception e) {
                Log.e(TAG, "Error updating report comment count", e);
            }
        });
    }

    // ==================== Comments ====================

    /**
     * Get cached comments for a report
     */
    public void getCachedComments(String reportId, CacheCallback<List<Comment>> callback) {
        executor.execute(() -> {
            try {
                List<CachedComment> cachedComments = database.commentDao().getCommentsForReport(reportId);
                List<Comment> comments = convertToComments(cachedComments);
                mainHandler.post(() -> callback.onResult(comments));
            } catch (Exception e) {
                Log.e(TAG, "Error getting cached comments", e);
                mainHandler.post(() -> callback.onResult(new ArrayList<>()));
            }
        });
    }

    /**
     * Cache comments for a report
     */
    public void cacheComments(String reportId, List<Comment> comments, String dataHash) {
        executor.execute(() -> {
            try {
                List<CachedComment> cachedComments = convertToCachedComments(comments);
                database.commentDao().deleteByReportId(reportId);
                database.commentDao().insertAll(cachedComments);

                // Update metadata
                String cacheKey = KEY_COMMENTS_PREFIX + reportId;
                CacheMetadata metadata = new CacheMetadata();
                metadata.setCacheKey(cacheKey);
                metadata.setDataHash(dataHash);
                metadata.setLastUpdated(System.currentTimeMillis());
                metadata.setItemCount(comments.size());
                database.cacheMetadataDao().insert(metadata);

                Log.d(TAG, "Cached " + comments.size() + " comments for report " + reportId);
            } catch (Exception e) {
                Log.e(TAG, "Error caching comments", e);
            }
        });
    }

    /**
     * Check if comments cache is stale
     */
    public void isCommentsCacheStale(String reportId, CacheCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String cacheKey = KEY_COMMENTS_PREFIX + reportId;
                Long lastUpdated = database.cacheMetadataDao().getLastUpdated(cacheKey);
                boolean isStale = lastUpdated == null ||
                        (System.currentTimeMillis() - lastUpdated) > CACHE_EXPIRY_COMMENTS;
                mainHandler.post(() -> callback.onResult(isStale));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(true));
            }
        });
    }

    /**
     * Update a comment's score in cache
     */
    public void updateCommentScore(String commentId, long score) {
        executor.execute(() -> {
            try {
                database.commentDao().updateScore(commentId, score);
            } catch (Exception e) {
                Log.e(TAG, "Error updating comment score", e);
            }
        });
    }

    /**
     * Update a comment's content in cache
     * Used for optimistic UI updates after editing
     */
    public void updateCachedComment(Comment comment) {
        executor.execute(() -> {
            try {
                database.commentDao().updateContent(comment.getCommentId(), comment.getContent());
                Log.d(TAG, "Updated comment content in cache: " + comment.getCommentId());
            } catch (Exception e) {
                Log.e(TAG, "Error updating comment content", e);
            }
        });
    }

    /**
     * Remove a comment from cache
     * Used for optimistic UI updates after deletion
     */
    public void removeCachedComment(String commentId) {
        executor.execute(() -> {
            try {
                database.commentDao().deleteByCommentId(commentId);
                Log.d(TAG, "Removed comment from cache: " + commentId);
            } catch (Exception e) {
                Log.e(TAG, "Error removing comment from cache", e);
            }
        });
    }

    // ==================== User Profiles ====================

    /**
     * Get cached user profile
     */
    public void getCachedUserProfile(String userId, CacheCallback<CachedUserProfile> callback) {
        executor.execute(() -> {
            try {
                CachedUserProfile profile = database.userProfileDao().getUserProfile(userId);
                mainHandler.post(() -> callback.onResult(profile));
            } catch (Exception e) {
                Log.e(TAG, "Error getting cached user profile", e);
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Cache user profile
     */
    public void cacheUserProfile(String userId, String name, String phone, String email,
                                  String profilePictureUrl) {
        executor.execute(() -> {
            try {
                CachedUserProfile profile = new CachedUserProfile();
                profile.setUserId(userId);
                profile.setName(name);
                profile.setPhone(phone);
                profile.setEmail(email);
                profile.setProfilePictureUrl(profilePictureUrl);
                profile.setCachedAt(System.currentTimeMillis());
                database.userProfileDao().insert(profile);

                Log.d(TAG, "Cached user profile for " + userId);
            } catch (Exception e) {
                Log.e(TAG, "Error caching user profile", e);
            }
        });
    }

    /**
     * Update cached user name
     */
    public void updateCachedUserName(String userId, String name) {
        executor.execute(() -> {
            try {
                database.userProfileDao().updateName(userId, name, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Error updating cached user name", e);
            }
        });
    }

    /**
     * Update cached user phone
     */
    public void updateCachedUserPhone(String userId, String phone) {
        executor.execute(() -> {
            try {
                database.userProfileDao().updatePhone(userId, phone, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Error updating cached user phone", e);
            }
        });
    }

    /**
     * Update cached profile picture URL
     */
    public void updateCachedProfilePictureUrl(String userId, String url) {
        executor.execute(() -> {
            try {
                database.userProfileDao().updateProfilePictureUrl(userId, url,
                        System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Error updating cached profile picture URL", e);
            }
        });
    }

    /**
     * Check if user profile cache is stale
     */
    public void isUserProfileCacheStale(String userId, CacheCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                CachedUserProfile profile = database.userProfileDao().getUserProfile(userId);
                boolean isStale = profile == null ||
                        (System.currentTimeMillis() - profile.getCachedAt()) > CACHE_EXPIRY_USER_PROFILE;
                mainHandler.post(() -> callback.onResult(isStale));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(true));
            }
        });
    }

    // ==================== Votes ====================

    /**
     * Get cached report votes for a user
     */
    public void getCachedReportVotes(String userId, CacheCallback<Map<String, Integer>> callback) {
        executor.execute(() -> {
            try {
                List<CachedReportVote> votes = database.reportVoteDao().getAllVotesForUser(userId);
                Map<String, Integer> voteMap = new HashMap<>();
                for (CachedReportVote vote : votes) {
                    voteMap.put(vote.getReportId(), vote.getVoteType());
                }
                mainHandler.post(() -> callback.onResult(voteMap));
            } catch (Exception e) {
                Log.e(TAG, "Error getting cached report votes", e);
                mainHandler.post(() -> callback.onResult(new HashMap<>()));
            }
        });
    }

    /**
     * Cache report votes
     */
    public void cacheReportVotes(String userId, Map<String, Integer> votes) {
        executor.execute(() -> {
            try {
                List<CachedReportVote> cachedVotes = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Integer> entry : votes.entrySet()) {
                    CachedReportVote vote = new CachedReportVote();
                    vote.setReportId(entry.getKey());
                    vote.setUserId(userId);
                    vote.setVoteType(entry.getValue());
                    vote.setCachedAt(now);
                    cachedVotes.add(vote);
                }
                database.reportVoteDao().insertAll(cachedVotes);
                Log.d(TAG, "Cached " + votes.size() + " report votes");
            } catch (Exception e) {
                Log.e(TAG, "Error caching report votes", e);
            }
        });
    }

    /**
     * Update a single report vote in cache
     */
    public void updateReportVote(String reportId, String userId, int voteType) {
        executor.execute(() -> {
            try {
                CachedReportVote vote = new CachedReportVote();
                vote.setReportId(reportId);
                vote.setUserId(userId);
                vote.setVoteType(voteType);
                vote.setCachedAt(System.currentTimeMillis());
                database.reportVoteDao().insert(vote);
            } catch (Exception e) {
                Log.e(TAG, "Error updating cached report vote", e);
            }
        });
    }

    /**
     * Get cached comment votes for a user
     */
    public void getCachedCommentVotes(String userId, CacheCallback<Map<String, Integer>> callback) {
        executor.execute(() -> {
            try {
                List<CachedCommentVote> votes = database.commentVoteDao().getAllVotesForUser(userId);
                Map<String, Integer> voteMap = new HashMap<>();
                for (CachedCommentVote vote : votes) {
                    voteMap.put(vote.getCommentId(), vote.getVoteType());
                }
                mainHandler.post(() -> callback.onResult(voteMap));
            } catch (Exception e) {
                Log.e(TAG, "Error getting cached comment votes", e);
                mainHandler.post(() -> callback.onResult(new HashMap<>()));
            }
        });
    }

    /**
     * Cache comment votes
     */
    public void cacheCommentVotes(String userId, Map<String, Integer> votes) {
        executor.execute(() -> {
            try {
                List<CachedCommentVote> cachedVotes = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Integer> entry : votes.entrySet()) {
                    CachedCommentVote vote = new CachedCommentVote();
                    vote.setCommentId(entry.getKey());
                    vote.setUserId(userId);
                    vote.setVoteType(entry.getValue());
                    vote.setCachedAt(now);
                    cachedVotes.add(vote);
                }
                database.commentVoteDao().insertAll(cachedVotes);
                Log.d(TAG, "Cached " + votes.size() + " comment votes");
            } catch (Exception e) {
                Log.e(TAG, "Error caching comment votes", e);
            }
        });
    }

    /**
     * Update a single comment vote in cache
     */
    public void updateCommentVote(String commentId, String userId, int voteType) {
        executor.execute(() -> {
            try {
                CachedCommentVote vote = new CachedCommentVote();
                vote.setCommentId(commentId);
                vote.setUserId(userId);
                vote.setVoteType(voteType);
                vote.setCachedAt(System.currentTimeMillis());
                database.commentVoteDao().insert(vote);
            } catch (Exception e) {
                Log.e(TAG, "Error updating cached comment vote", e);
            }
        });
    }

    // ==================== Utility Methods ====================

    /**
     * Clear all caches (e.g., on logout)
     */
    public void clearAllCaches() {
        executor.execute(() -> {
            try {
                database.reportDao().deleteAll();
                database.commentDao().deleteAll();
                database.userProfileDao().deleteAll();
                database.reportVoteDao().deleteAll();
                database.commentVoteDao().deleteAll();
                database.cacheMetadataDao().deleteAll();
                Log.d(TAG, "Cleared all caches");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing caches", e);
            }
        });
    }

    /**
     * Generate hash for data comparison
     */
    public static String generateHash(List<?> data) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Object item : data) {
                sb.append(item.hashCode());
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    // ==================== Conversion Methods ====================

    private List<HazardCard> convertToHazardCards(List<CachedReport> cachedReports) {
        List<HazardCard> hazardCards = new ArrayList<>();
        for (CachedReport cached : cachedReports) {
            HazardCard card = new HazardCard();
            card.setDocumentId(cached.getDocumentId());
            card.setDescription(cached.getDescription());
            card.setHazardType(cached.getHazardType());
            card.setLocalGov(cached.getLocalGov());
            card.setLocationDetails(cached.getLocationDetails());
            card.setLatitude(cached.getLatitude());
            card.setLongitude(cached.getLongitude());
            card.setStatus(cached.getStatus());
            card.setPhotoUrl(cached.getPhotoUrl());
            card.setProfilePictureUrl(cached.getProfilePictureUrl());
            card.setUserName(cached.getUserName());
            card.setUserId(cached.getUserId());
            card.setVotes(cached.getVotes());
            card.setCreatedAt(cached.getCreatedAt());
            card.setScore(cached.getScore());
            card.setComments(cached.getComments());
            hazardCards.add(card);
        }
        return hazardCards;
    }

    private List<CachedReport> convertToCachedReports(List<HazardCard> hazardCards) {
        List<CachedReport> cachedReports = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (HazardCard card : hazardCards) {
            CachedReport cached = new CachedReport();
            cached.setDocumentId(card.getDocumentId());
            cached.setDescription(card.getDescription());
            cached.setHazardType(card.getHazardType());
            cached.setLocalGov(card.getLocalGov());
            cached.setLocationDetails(card.getLocationDetails());
            cached.setLatitude(card.getLatitude());
            cached.setLongitude(card.getLongitude());
            cached.setStatus(card.getStatus());
            cached.setPhotoUrl(card.getPhotoUrl());
            cached.setProfilePictureUrl(card.getProfilePictureUrl());
            cached.setUserName(card.getUserName());
            cached.setUserId(card.getUserId());
            cached.setVotes(card.getVotes());
            cached.setCreatedAt(card.getCreatedAt());
            cached.setScore(card.getScore());
            cached.setComments(card.getComments());
            cached.setCachedAt(now);
            cachedReports.add(cached);
        }
        return cachedReports;
    }

    private List<Comment> convertToComments(List<CachedComment> cachedComments) {
        List<Comment> comments = new ArrayList<>();
        for (CachedComment cached : cachedComments) {
            Comment comment = new Comment();
            comment.setCommentId(cached.getCommentId());
            comment.setContent(cached.getContent());
            comment.setDatetime(cached.getDatetime());
            comment.setReportId(cached.getReportId());
            comment.setUserId(cached.getUserId());
            comment.setUserName(cached.getUserName());
            comment.setProfilePictureUrl(cached.getProfilePictureUrl());
            comment.setScore(cached.getScore());
            comments.add(comment);
        }
        return comments;
    }

    private List<CachedComment> convertToCachedComments(List<Comment> comments) {
        List<CachedComment> cachedComments = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Comment comment : comments) {
            CachedComment cached = new CachedComment();
            cached.setCommentId(comment.getCommentId());
            cached.setContent(comment.getContent());
            cached.setDatetime(comment.getDatetime());
            cached.setReportId(comment.getReportId());
            cached.setUserId(comment.getUserId());
            cached.setUserName(comment.getUserName());
            cached.setProfilePictureUrl(comment.getProfilePictureUrl());
            cached.setScore(comment.getScore());
            cached.setCachedAt(now);
            cachedComments.add(cached);
        }
        return cachedComments;
    }

    /**
     * Callback interface for async cache operations
     */
    public interface CacheCallback<T> {
        void onResult(T result);
    }
}
