package com.gitgud.citywatch.ui.thread;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gitgud.citywatch.R;
import com.gitgud.citywatch.model.Comment;
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/**
 * RecyclerView adapter for displaying comments
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> comments;

    public CommentAdapter(List<Comment> comments) {
        this.comments = comments;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.thread_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.bind(comment);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void updateList(List<Comment> newList) {
        this.comments = newList;
        notifyDataSetChanged();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivProfile;
        private final TextView tvName;
        private final TextView tvContent;
        private final ImageButton btnUpvote;
        private final ImageButton btnDownvote;
        private final TextView tvVotes;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.ivThreadCommentProfile);
            tvName = itemView.findViewById(R.id.tvThreadCommentName);
            tvContent = itemView.findViewById(R.id.tvThreadCommentContent);
            btnUpvote = itemView.findViewById(R.id.btnThreadCommentUpvote);
            btnDownvote = itemView.findViewById(R.id.btnThreadCommentDownvote);
            tvVotes = itemView.findViewById(R.id.tvThreadCommentVotes);
        }

        void bind(Comment comment) {
            // Set user name with time ago
            String userName = comment.getUserName() != null ? comment.getUserName() : "Anonymous";
            String timeAgo = getTimeAgoEstimate(comment.getDatetime());
            tvName.setText(userName + " • " + timeAgo);

            // Set comment content
            tvContent.setText(comment.getContent());

            // Load profile picture
            if (comment.getProfilePictureUrl() != null && !comment.getProfilePictureUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(comment.getProfilePictureUrl())
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(ivProfile);
            } else {
                ivProfile.setImageResource(R.drawable.ic_profile);
            }

            // Set score and update vote button states
            tvVotes.setText(String.valueOf(comment.getScore()));
            updateVoteButtonStates(comment.getUserVote());

            // Upvote button listener
            btnUpvote.setOnClickListener(v -> handleVote(comment, 1));

            // Downvote button listener
            btnDownvote.setOnClickListener(v -> handleVote(comment, -1));
        }

        private void handleVote(Comment comment, int voteType) {
            String userId = SessionManager.getCurrentUserId();
            if (userId == null) {
                Toast.makeText(itemView.getContext(),
                        "Please log in to vote", Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine actual vote type (toggle if same vote)
            int actualVoteType = comment.getUserVote() == voteType ? 0 : voteType;

            // Store previous state for rollback
            int previousVote = comment.getUserVote();
            long previousScore = comment.getScore();

            // Optimistic UI update
            long newScore = previousScore - previousVote + (actualVoteType == 0 ? 0 : actualVoteType);
            comment.setScore(newScore);
            comment.setUserVote(actualVoteType);
            tvVotes.setText(String.valueOf(newScore));
            updateVoteButtonStates(actualVoteType);

            // Call Cloud Function
            ApiClient.voteComment(comment.getCommentId(), userId, actualVoteType)
                    .addOnSuccessListener(result -> {
                        comment.setScore(result.score);
                        comment.setUserVote(result.userVote);
                        tvVotes.setText(String.valueOf(result.score));
                        updateVoteButtonStates(result.userVote);
                    })
                    .addOnFailureListener(e -> {
                        // Revert on failure
                        comment.setScore(previousScore);
                        comment.setUserVote(previousVote);
                        tvVotes.setText(String.valueOf(previousScore));
                        updateVoteButtonStates(previousVote);
                        Toast.makeText(itemView.getContext(),
                                "Failed to vote", Toast.LENGTH_SHORT).show();
                    });
        }

        private void updateVoteButtonStates(int userVote) {
            int activeColor = itemView.getContext().getColor(R.color.md_theme_primary);
            int inactiveColor = itemView.getContext().getColor(R.color.md_theme_onSurfaceVariant);

            if (userVote == 1) {
                btnUpvote.setColorFilter(activeColor);
                btnDownvote.setColorFilter(inactiveColor);
            } else if (userVote == -1) {
                btnUpvote.setColorFilter(inactiveColor);
                btnDownvote.setColorFilter(activeColor);
            } else {
                btnUpvote.setColorFilter(inactiveColor);
                btnDownvote.setColorFilter(inactiveColor);
            }
        }

        private String getTimeAgoEstimate(long timestamp) {
            if (timestamp == 0) {
                return "";
            }

            long currentTime = System.currentTimeMillis();
            long differenceMs = currentTime - timestamp;

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
    }
}

