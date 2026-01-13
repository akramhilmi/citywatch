package com.gitgud.citywatch.model;

/**
 * Model class for comment data
 */
public class Comment {
    private String commentId;
    private String content;
    private long datetime;
    private String reportId;
    private String userId;
    private String userName;
    private String profilePictureUrl;
    private long score;
    private int userVote; // 1 = upvoted, -1 = downvoted, 0 = no vote

    public Comment() {
        // Required for deserialization
    }

    // Getters
    public String getCommentId() { return commentId; }
    public String getContent() { return content; }
    public long getDatetime() { return datetime; }
    public String getReportId() { return reportId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public long getScore() { return score; }
    public int getUserVote() { return userVote; }

    // Setters
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public void setContent(String content) { this.content = content; }
    public void setDatetime(long datetime) { this.datetime = datetime; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public void setScore(long score) { this.score = score; }
    public void setUserVote(int userVote) { this.userVote = userVote; }
}

