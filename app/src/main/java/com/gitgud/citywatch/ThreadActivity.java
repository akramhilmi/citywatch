package com.gitgud.citywatch;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gitgud.citywatch.model.Comment;
import com.gitgud.citywatch.ui.thread.CommentAdapter;
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThreadActivity extends AppCompatActivity {

    // Views
    private ImageButton btnBack;
    private ShapeableImageView ivProfile;
    private TextView tvName;
    private TextView tvTitle;
    private TextView tvTagTertiary;
    private TextView tvTagSecondary;
    private ImageView ivPhoto;
    private TextView tvDescription;
    private MaterialButton btnUpvote;
    private MaterialButton btnDownvote;
    private TextView tvVotes;
    private TextView tvComments;
    private RecyclerView rvComments;
    private TextView tvCommentsLoading;
    private TextInputLayout tilComment;
    private TextInputEditText etComment;

    // Comments
    private CommentAdapter commentAdapter;
    private List<Comment> commentList;

    // Data
    private String documentId;
    private long currentScore;
    private int currentUserVote;
    private double latitude;
    private double longitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_thread);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_thread), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        loadIntentData();
        setupClickListeners();
        loadComments();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnThreadBack);
        ivProfile = findViewById(R.id.ivThreadProfile);
        tvName = findViewById(R.id.tvThreadName);
        tvTitle = findViewById(R.id.tvThreadTitle);
        tvTagTertiary = findViewById(R.id.tvThreadTagTertiary);
        tvTagSecondary = findViewById(R.id.tvThreadTagSecondary);
        ivPhoto = findViewById(R.id.ivThreadPhoto);
        tvDescription = findViewById(R.id.tvThreadDesc);
        btnUpvote = findViewById(R.id.btnThreadUpvote);
        btnDownvote = findViewById(R.id.btnThreadDownvote);
        tvVotes = findViewById(R.id.tvThreadVotes);
        tvComments = findViewById(R.id.tvThreadComments);
        rvComments = findViewById(R.id.rvComments);
        tvCommentsLoading = findViewById(R.id.tvCommentsLoading);
        tilComment = findViewById(R.id.tilComment);
        etComment = findViewById(R.id.etComment);

        // Setup RecyclerView for comments
        commentList = new ArrayList<>();
        commentAdapter = new CommentAdapter(commentList);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);
    }

    private void loadIntentData() {
        // Get data from intent
        documentId = getIntent().getStringExtra("documentId");
        String userName = getIntent().getStringExtra("userName");
        String hazardType = getIntent().getStringExtra("hazardType");
        String locationDetails = getIntent().getStringExtra("locationDetails");
        String status = getIntent().getStringExtra("status");
        String localGov = getIntent().getStringExtra("localGov");
        String description = getIntent().getStringExtra("description");
        String photoUrl = getIntent().getStringExtra("photoUrl");
        String profilePictureUrl = getIntent().getStringExtra("profilePictureUrl");
        currentScore = getIntent().getLongExtra("score", 0);
        currentUserVote = getIntent().getIntExtra("userVote", 0);
        long createdAt = getIntent().getLongExtra("createdAt", 0);
        long commentCount = getIntent().getLongExtra("comments", 0);
        latitude = getIntent().getDoubleExtra("latitude", 0.0);
        longitude = getIntent().getDoubleExtra("longitude", 0.0);

        // Set user name with time ago
        String timeAgo = getTimeAgoEstimate(createdAt);
        tvName.setText((userName != null ? userName : "Anonymous") + " • " + timeAgo);

        // Set title
        tvTitle.setText(hazardType + " @ " + locationDetails);

        // Set status badge
        tvTagTertiary.setText(status != null ? status : "In progress");

        // Set local government badge
        tvTagSecondary.setText(localGov != null ? localGov : "");

        // Set description
        tvDescription.setText(description != null ? description : "");

        // Load profile picture
        if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
            Glide.with(this)
                    .load(profilePictureUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivProfile);
        }

        // Load report photo
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_pic)
                    .error(R.drawable.ic_pic)
                    .centerCrop()
                    .into(ivPhoto);
        }

        // Set votes and update button states
        tvVotes.setText(String.valueOf(currentScore));
        //updateVoteButtonStates();

        // Set comment count from intent
        tvComments.setText(String.valueOf(commentCount));

        // Add underline to tvThreadLocation hyperlink
        TextView tvThreadLocation = findViewById(R.id.tvThreadLocation);
        tvThreadLocation.setPaintFlags(tvThreadLocation.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    private void setupClickListeners() {
        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Location hyperlink - open map
        TextView tvThreadLocation = findViewById(R.id.tvThreadLocation);
        tvThreadLocation.setOnClickListener(v -> openMapApp());

        // Location button - open map
        ImageButton btnLocation = findViewById(R.id.btnThreadLocation);
        btnLocation.setOnClickListener(v -> openMapApp());

        // Upvote button
        btnUpvote.setOnClickListener(v -> handleVote(1));

        // Downvote button
        btnDownvote.setOnClickListener(v -> handleVote(-1));

        // Comment submit button
        com.google.android.material.button.MaterialButton btnCommentSubmit =
                findViewById(R.id.btnCommentSubmit);
        btnCommentSubmit.setOnClickListener(v -> submitComment());
    }

    private void handleVote(int voteType) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "Please log in to vote", Toast.LENGTH_SHORT).show();
            return;
        }

        // Determine actual vote type (toggle if same vote)
        int actualVoteType = currentUserVote == voteType ? 0 : voteType;

        // Store previous state for rollback
        long previousScore = currentScore;
        int previousVote = currentUserVote;

        // Optimistic UI update
        currentScore = previousScore - previousVote + (actualVoteType);
        currentUserVote = actualVoteType;
        tvVotes.setText(String.valueOf(currentScore));
        //updateVoteButtonStates();

        // Call Cloud Function
        ApiClient.voteReport(documentId, userId, actualVoteType)
                .addOnSuccessListener(result -> {
                    currentScore = result.score;
                    currentUserVote = result.userVote;
                    tvVotes.setText(String.valueOf(currentScore));
                    //updateVoteButtonStates();
                })
                .addOnFailureListener(e -> {
                    // Revert on failure
                    currentScore = previousScore;
                    currentUserVote = previousVote;
                    tvVotes.setText(String.valueOf(currentScore));
                    //updateVoteButtonStates();
                    Toast.makeText(this, "Failed to vote", Toast.LENGTH_SHORT).show();
                });
    }

//    private void updateVoteButtonStates() {
//        int activeColor = getColor(R.color.md_theme_primary);
//        int inactiveColor = getColor(R.color.md_theme_onSurfaceVariant);
//
//        if (currentUserVote == 1) {
//            btnUpvote.setColorFilter(activeColor);
//            btnDownvote.setColorFilter(inactiveColor);
//        } else if (currentUserVote == -1) {
//            btnUpvote.setColorFilter(inactiveColor);
//            btnDownvote.setColorFilter(activeColor);
//        } else {
//            btnUpvote.setColorFilter(inactiveColor);
//            btnDownvote.setColorFilter(inactiveColor);
//        }
//    }

    private String getTimeAgoEstimate(long createdAtTimestamp) {
        if (createdAtTimestamp == 0) {
            return "";
        }

        long currentTime = System.currentTimeMillis();
        long differenceMs = currentTime - createdAtTimestamp;

        long seconds = differenceMs / 1000;
        if (seconds < 60) {
            return "now";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }

        long days = hours / 24;
        if (days < 7) {
            return days + "d ago";
        }

        long weeks = days / 7;
        if (weeks < 4) {
            return weeks + "w ago";
        }

        long months = days / 30;
        if (months < 12) {
            return months + "mo ago";
        }

        long years = days / 365;
        return years + "y ago";
    }

    /**
     * Open Google Maps with the hazard location
     */
    private void openMapApp() {
        // Log coordinates for debugging
        android.util.Log.d("ThreadActivity", "openMapApp: latitude=" + latitude + ", longitude=" + longitude);

        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(this, "Location data not available", Toast.LENGTH_SHORT).show();
            android.util.Log.w("ThreadActivity", "Location data not available - coordinates are 0,0");
            return;
        }

        try {
            // Google Maps URI format
            String mapUrl = "https://maps.google.com/maps?q=" + latitude + "," + longitude;
            android.util.Log.d("ThreadActivity", "Map URL: " + mapUrl);

            android.content.Intent mapIntent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(mapUrl));

            // Set package to Google Maps if available, otherwise let system choose
            mapIntent.setPackage("com.google.android.apps.maps");

            // Try to open Google Maps
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                android.util.Log.d("ThreadActivity", "Google Maps app found, opening...");
                startActivity(mapIntent);
            } else {
                // Fallback to browser if Google Maps not installed
                android.util.Log.d("ThreadActivity", "Google Maps app not found, falling back to browser");
                mapIntent.setPackage(null);
                startActivity(mapIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("ThreadActivity", "Error opening map: " + e.getMessage());
            Toast.makeText(this, "Could not open map", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Load comments for this report from Cloud Function
     */
    private void loadComments() {
        if (documentId == null) {
            return;
        }

        // Show loading text
        tvCommentsLoading.setVisibility(View.VISIBLE);

        ApiClient.getCommentsForReport(documentId)
                .addOnSuccessListener(comments -> {
                    commentList.clear();
                    commentList.addAll(comments);

                    // Update comment count display
                    tvComments.setText(String.valueOf(comments.size()));

                    // Load user votes for comments
                    loadUserVotesForComments(comments);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ThreadActivity", "Failed to load comments: " + e.getMessage());
                    // Hide loading text on failure
                    tvCommentsLoading.setVisibility(View.GONE);
                });
    }

    /**
     * Load user's votes for comments
     */
    private void loadUserVotesForComments(List<Comment> comments) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null || comments.isEmpty()) {
            commentAdapter.notifyDataSetChanged();
            // Hide loading text
            tvCommentsLoading.setVisibility(View.GONE);
            return;
        }

        List<String> commentIds = new ArrayList<>();
        for (Comment comment : comments) {
            commentIds.add(comment.getCommentId());
        }

        ApiClient.getUserVotesForComments(commentIds, userId)
                .addOnSuccessListener(votes -> {
                    for (Comment comment : commentList) {
                        Integer vote = votes.get(comment.getCommentId());
                        comment.setUserVote(vote != null ? vote : 0);
                    }
                    commentAdapter.notifyDataSetChanged();
                    // Hide loading text
                    tvCommentsLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ThreadActivity", "Failed to load comment votes: " + e.getMessage());
                    commentAdapter.notifyDataSetChanged();
                    // Hide loading text on failure
                    tvCommentsLoading.setVisibility(View.GONE);
                });
    }

    /**
     * Submit a new comment
     */
    private void submitComment() {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "Please log in to comment", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = etComment.getText() != null ? etComment.getText().toString().trim() : "";
        if (content.isEmpty()) {
            Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable input while submitting
        tilComment.setEnabled(false);

        ApiClient.submitComment(content, documentId, userId)
                .addOnSuccessListener(commentId -> {
                    // Clear input
                    etComment.setText("");
                    tilComment.setEnabled(true);

                    // Reload comments
                    loadComments();

                    Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    tilComment.setEnabled(true);
                    Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show();
                    android.util.Log.e("ThreadActivity", "Failed to submit comment: " + e.getMessage());
                });
    }
}
