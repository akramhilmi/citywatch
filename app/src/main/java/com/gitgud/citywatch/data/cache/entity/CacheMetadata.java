package com.gitgud.citywatch.data.cache.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing cache metadata (hashes, timestamps)
 * Used to check if data needs to be refreshed
 */
@Entity(tableName = "cache_metadata")
public class CacheMetadata {
    @PrimaryKey
    @NonNull
    private String cacheKey; // e.g., "reports", "comments_reportId", "user_userId"
    private String dataHash; // Hash of the data for change detection
    private long lastUpdated;
    private int itemCount;

    public CacheMetadata() {
        this.cacheKey = "";
    }

    // Getters
    @NonNull
    public String getCacheKey() { return cacheKey; }
    public String getDataHash() { return dataHash; }
    public long getLastUpdated() { return lastUpdated; }
    public int getItemCount() { return itemCount; }

    // Setters
    public void setCacheKey(@NonNull String cacheKey) { this.cacheKey = cacheKey; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
}

