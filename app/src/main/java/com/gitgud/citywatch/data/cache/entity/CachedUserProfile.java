package com.gitgud.citywatch.data.cache.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for caching user profile data
 */
@Entity(tableName = "user_profiles")
public class CachedUserProfile {
    @PrimaryKey
    @NonNull
    private String userId;
    private String name;
    private String phone;
    private String email;
    private String profilePictureUrl;
    private long cachedAt;

    public CachedUserProfile() {
        this.userId = "";
    }

    // Getters
    @NonNull
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public long getCachedAt() { return cachedAt; }

    // Setters
    public void setUserId(@NonNull String userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
}

