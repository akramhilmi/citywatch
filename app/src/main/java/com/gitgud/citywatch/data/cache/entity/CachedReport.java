package com.gitgud.citywatch.data.cache.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for caching report data
 */
@Entity(tableName = "reports")
public class CachedReport {
    @PrimaryKey
    @NonNull
    private String documentId;
    private String description;
    private String hazardType;
    private String localGov;
    private String locationDetails;
    private double latitude;
    private double longitude;
    private String status;
    private String photoUrl;
    private String profilePictureUrl;
    private String userName;
    private String userId;
    private long votes;
    private long createdAt;
    private long score;
    private long comments;
    private long cachedAt; // Timestamp when cached
    private boolean userIsAdmin; // Whether the user is an admin

    public CachedReport() {
        this.documentId = "";
    }

    // Getters
    @NonNull
    public String getDocumentId() { return documentId; }
    public String getDescription() { return description; }
    public String getHazardType() { return hazardType; }
    public String getLocalGov() { return localGov; }
    public String getLocationDetails() { return locationDetails; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getPhotoUrl() { return photoUrl; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public String getUserName() { return userName; }
    public String getUserId() { return userId; }
    public long getVotes() { return votes; }
    public long getCreatedAt() { return createdAt; }
    public long getScore() { return score; }
    public long getComments() { return comments; }
    public long getCachedAt() { return cachedAt; }
    public boolean isUserIsAdmin() { return userIsAdmin; }


    // Setters
    public void setDocumentId(@NonNull String documentId) { this.documentId = documentId; }
    public void setDescription(String description) { this.description = description; }
    public void setHazardType(String hazardType) { this.hazardType = hazardType; }
    public void setLocalGov(String localGov) { this.localGov = localGov; }
    public void setLocationDetails(String locationDetails) { this.locationDetails = locationDetails; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setStatus(String status) { this.status = status; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setVotes(long votes) { this.votes = votes; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setScore(long score) { this.score = score; }
    public void setComments(long comments) { this.comments = comments; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
    public void setUserIsAdmin(boolean userIsAdmin) { this.userIsAdmin = userIsAdmin; }
}

