package com.gitgud.citywatch.data.cache.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for caching comment data
 */
@Entity(tableName = "comments")
public class CachedComment {
    @PrimaryKey
    @NonNull
    private String commentId;
    private String content;
    private long datetime;
    private String reportId;
    private String userId;
    private String userName;
    private String profilePictureUrl;
    private long score;
    private long cachedAt;
    private boolean userIsAdmin; // Whether the comment author is an admin

    public CachedComment() {
        this.commentId = "";
    }

    // Getters
    @NonNull
    public String getCommentId() { return commentId; }
    public String getContent() { return content; }
    public long getDatetime() { return datetime; }
    public String getReportId() { return reportId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public long getScore() { return score; }
    public long getCachedAt() { return cachedAt; }
    public boolean isUserIsAdmin() { return userIsAdmin; }

    // Setters
    public void setCommentId(@NonNull String commentId) { this.commentId = commentId; }
    public void setContent(String content) { this.content = content; }
    public void setDatetime(long datetime) { this.datetime = datetime; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public void setScore(long score) { this.score = score; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
    public void setUserIsAdmin(boolean userIsAdmin) { this.userIsAdmin = userIsAdmin; }
}

