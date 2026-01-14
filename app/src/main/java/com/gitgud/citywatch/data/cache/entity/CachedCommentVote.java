package com.gitgud.citywatch.data.cache.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Room entity for caching user votes on comments
 */
@Entity(tableName = "comment_votes", primaryKeys = {"commentId", "userId"})
public class CachedCommentVote {
    @NonNull
    private String commentId;
    @NonNull
    private String userId;
    private int voteType; // 1 = upvote, -1 = downvote, 0 = no vote
    private long cachedAt;

    public CachedCommentVote() {
        this.commentId = "";
        this.userId = "";
    }

    // Getters
    @NonNull
    public String getCommentId() { return commentId; }
    @NonNull
    public String getUserId() { return userId; }
    public int getVoteType() { return voteType; }
    public long getCachedAt() { return cachedAt; }

    // Setters
    public void setCommentId(@NonNull String commentId) { this.commentId = commentId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }
    public void setVoteType(int voteType) { this.voteType = voteType; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
}

