package com.gitgud.citywatch;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
    private View llCommentInput;
    private androidx.core.widget.NestedScrollView nestedScrollView;

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

        initViews();

        // Handle window insets manually for EdgeToEdge and Keyboard
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_thread), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            // 1. Apply status bar height to top navigation
            btnBack.setPadding(btnBack.getPaddingLeft(), systemBars.top,
                    btnBack.getPaddingRight(), btnBack.getPaddingBottom());

            // 2. Apply keyboard height (IME) to the comment input layout
            // We use the MAX of navigation bar or keyboard height
            int bottomPadding = Math.max(systemBars.bottom, imeInsets.bottom);
            llCommentInput.setPadding(llCommentInput.getPaddingLeft(),
                    (int)(12 * getResources().getDisplayMetrics().density), // Original 12dp padding
                    llCommentInput.getPaddingRight(),
                    bottomPadding + (int)(12 * getResources().getDisplayMetrics().density));

            return insets;
        });

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
        llCommentInput = findViewById(R.id.llCommentInput);
        nestedScrollView = findViewById(R.id.nestedScrollView);

        // Setup RecyclerView for comments
        commentList = new ArrayList<>();
        commentAdapter = new CommentAdapter(commentList);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);

        // Auto-scroll to bottom when typing a comment
        etComment.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                nestedScrollView.postDelayed(() -> nestedScrollView.fullScroll(View.FOCUS_DOWN), 300);
            }
        });
    }

    private void loadIntentData() {
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

        String timeAgo = getTimeAgoEstimate(createdAt);
        tvName.setText((userName != null ? userName : "Anonymous") + " • " + timeAgo);
        tvTitle.setText(hazardType + " @ " + locationDetails);
        tvTagTertiary.setText(status != null ? status : "In progress");
        tvTagSecondary.setText(localGov != null ? localGov : "");
        tvDescription.setText(description != null ? description : "");

        if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
            Glide.with(this).load(profilePictureUrl).placeholder(R.drawable.ic_profile).circleCrop().into(ivProfile);
        }
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this).load(photoUrl).placeholder(R.drawable.ic_pic).centerCrop().into(ivPhoto);
        }

        tvVotes.setText(String.valueOf(currentScore));
        tvComments.setText(String.valueOf(commentCount));

        TextView tvThreadLocation = findViewById(R.id.tvThreadLocation);
        if (tvThreadLocation != null) {
            tvThreadLocation.setPaintFlags(tvThreadLocation.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        TextView tvThreadLocation = findViewById(R.id.tvThreadLocation);
        if (tvThreadLocation != null) tvThreadLocation.setOnClickListener(v -> openMapApp());

        ImageButton btnLocation = findViewById(R.id.btnThreadLocation);
        if (btnLocation != null) btnLocation.setOnClickListener(v -> openMapApp());

        btnUpvote.setOnClickListener(v -> handleVote(1));
        btnDownvote.setOnClickListener(v -> handleVote(-1));

        findViewById(R.id.btnCommentSubmit).setOnClickListener(v -> submitComment());
    }

    private void handleVote(int voteType) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "Please log in to vote", Toast.LENGTH_SHORT).show();
            return;
        }
        int actualVoteType = currentUserVote == voteType ? 0 : voteType;
        long previousScore = currentScore;
        int previousVote = currentUserVote;
        currentScore = previousScore - previousVote + actualVoteType;
        currentUserVote = actualVoteType;
        tvVotes.setText(String.valueOf(currentScore));

        ApiClient.voteReport(documentId, userId, actualVoteType)
                .addOnSuccessListener(result -> {
                    currentScore = result.score;
                    currentUserVote = result.userVote;
                    tvVotes.setText(String.valueOf(currentScore));
                })
                .addOnFailureListener(e -> {
                    currentScore = previousScore;
                    currentUserVote = previousVote;
                    tvVotes.setText(String.valueOf(currentScore));
                    Toast.makeText(this, "Failed to vote", Toast.LENGTH_SHORT).show();
                });
    }

    private String getTimeAgoEstimate(long createdAtTimestamp) {
        if (createdAtTimestamp == 0) return "";
        long diff = System.currentTimeMillis() - createdAtTimestamp;
        long seconds = diff / 1000;
        if (seconds < 60) return "now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    private void openMapApp() {
        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String mapUrl = "https://maps.google.com/maps?q=" + latitude + "," + longitude;
            android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(mapUrl));
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) startActivity(mapIntent);
            else {
                mapIntent.setPackage(null);
                startActivity(mapIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open map", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadComments() {
        if (documentId == null) return;
        tvCommentsLoading.setVisibility(View.VISIBLE);
        ApiClient.getCommentsForReport(documentId)
                .addOnSuccessListener(comments -> {
                    commentList.clear();
                    commentList.addAll(comments);
                    tvComments.setText(String.valueOf(comments.size()));
                    loadUserVotesForComments(comments);
                })
                .addOnFailureListener(e -> tvCommentsLoading.setVisibility(View.GONE));
    }

    private void loadUserVotesForComments(List<Comment> comments) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null || comments.isEmpty()) {
            commentAdapter.notifyDataSetChanged();
            tvCommentsLoading.setVisibility(View.GONE);
            return;
        }
        List<String> ids = new ArrayList<>();
        for (Comment c : comments) ids.add(c.getCommentId());
        ApiClient.getUserVotesForComments(ids, userId)
                .addOnSuccessListener(votes -> {
                    for (Comment c : commentList) {
                        Integer v = votes.get(c.getCommentId());
                        c.setUserVote(v != null ? v : 0);
                    }
                    commentAdapter.notifyDataSetChanged();
                    tvCommentsLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    commentAdapter.notifyDataSetChanged();
                    tvCommentsLoading.setVisibility(View.GONE);
                });
    }

    private void submitComment() {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "Login to comment", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = etComment.getText() != null ? etComment.getText().toString().trim() : "";
        if (content.isEmpty()) return;

        tilComment.setEnabled(false);
        ApiClient.submitComment(content, documentId, userId)
                .addOnSuccessListener(id -> {
                    etComment.setText("");
                    tilComment.setEnabled(true);
                    loadComments();
                    Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    tilComment.setEnabled(true);
                    Toast.makeText(this, "Failed to post", Toast.LENGTH_SHORT).show();
                });
    }
}
