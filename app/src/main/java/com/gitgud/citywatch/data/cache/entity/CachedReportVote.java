package com.gitgud.citywatch.data.cache.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Room entity for caching user votes on reports
 */
@Entity(tableName = "report_votes", primaryKeys = {"reportId", "userId"})
public class CachedReportVote {
    @NonNull
    private String reportId;
    @NonNull
    private String userId;
    private int voteType; // 1 = upvote, -1 = downvote, 0 = no vote
    private long cachedAt;

    public CachedReportVote() {
        this.reportId = "";
        this.userId = "";
    }

    // Getters
    @NonNull
    public String getReportId() { return reportId; }
    @NonNull
    public String getUserId() { return userId; }
    public int getVoteType() { return voteType; }
    public long getCachedAt() { return cachedAt; }

    // Setters
    public void setReportId(@NonNull String reportId) { this.reportId = reportId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }
    public void setVoteType(int voteType) { this.voteType = voteType; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
}

