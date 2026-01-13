package com.gitgud.citywatch.model;

import com.google.firebase.firestore.GeoPoint;

/**
 * Data model for a hazard report card
 */
public class HazardCard {
    private String documentId;
    private String description;
    private String hazardType;
    private String localGov;
    private String locationDetails;
    private GeoPoint mapsLocation;
    private String status;
    private String photoUrl;
    private String profilePictureUrl;
    private String userName;
    private String userId;
    private long votes;
    private long createdAt;
    private long score;
    private int userVote; // 1 = upvoted, -1 = downvoted, 0 = no vote

    public HazardCard() {
        // Required for Firestore deserialization
    }

    public HazardCard(String documentId, String description, String hazardType,
                      String localGov, String locationDetails, GeoPoint mapsLocation,
                      String status, String photoUrl, String userName, long votes) {
        this.documentId = documentId;
        this.description = description;
        this.hazardType = hazardType;
        this.localGov = localGov;
        this.locationDetails = locationDetails;
        this.mapsLocation = mapsLocation;
        this.status = status;
        this.photoUrl = photoUrl;
        this.userName = userName;
        this.votes = votes;
    }

    // Getters
    public String getDocumentId() { return documentId; }
    public String getDescription() { return description; }
    public String getHazardType() { return hazardType; }
    public String getLocalGov() { return localGov; }
    public String getLocationDetails() { return locationDetails; }
    public GeoPoint getMapsLocation() { return mapsLocation; }
    public String getStatus() { return status; }
    public String getPhotoUrl() { return photoUrl; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public String getUserName() { return userName; }
    public String getUserId() { return userId; }
    public long getVotes() { return votes; }
    public long getCreatedAt() { return createdAt; }
    public long getScore() { return score; }
    public int getUserVote() { return userVote; }

    // Setters
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public void setDescription(String description) { this.description = description; }
    public void setHazardType(String hazardType) { this.hazardType = hazardType; }
    public void setLocalGov(String localGov) { this.localGov = localGov; }
    public void setLocationDetails(String locationDetails) { this.locationDetails = locationDetails; }
    public void setMapsLocation(GeoPoint mapsLocation) { this.mapsLocation = mapsLocation; }
    public void setStatus(String status) { this.status = status; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setVotes(long votes) { this.votes = votes; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setScore(long score) { this.score = score; }
    public void setUserVote(int userVote) { this.userVote = userVote; }
}

