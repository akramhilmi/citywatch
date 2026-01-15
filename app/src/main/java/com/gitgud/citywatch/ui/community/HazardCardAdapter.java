package com.gitgud.citywatch.ui.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gitgud.citywatch.R;
import com.gitgud.citywatch.model.HazardCard;
import com.gitgud.citywatch.data.repository.DataRepository;
import com.gitgud.citywatch.util.VoteButtonAnimationHelper;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/**
 * RecyclerView adapter for displaying hazard cards
 */
public class HazardCardAdapter extends RecyclerView.Adapter<HazardCardAdapter.HazardViewHolder> {

    private List<HazardCard> hazardCards;
    private OnCardClickListener onCardClickListener;
    private DataRepository dataRepository;

    /**
     * Interface for card click events
     */
    public interface OnCardClickListener {
        void onCardClick(HazardCard hazardCard);
    }

    public HazardCardAdapter(List<HazardCard> hazardCards) {
        this.hazardCards = hazardCards;
        this.dataRepository = null; // Will be set via setDataRepository
    }

    public void setDataRepository(DataRepository repository) {
        this.dataRepository = repository;
    }

    public void setOnCardClickListener(OnCardClickListener listener) {
        this.onCardClickListener = listener;
    }

    @NonNull
    @Override
    public HazardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.hazard_card, parent, false);
        return new HazardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HazardViewHolder holder, int position) {
        HazardCard hazard = hazardCards.get(position);
        holder.bind(hazard, onCardClickListener, dataRepository);
    }

    @Override
    public int getItemCount() {
        return hazardCards.size();
    }

    public void updateList(List<HazardCard> newList) {
        this.hazardCards = newList;
        notifyDataSetChanged();
    }

    static class HazardViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivCardProfile;
        private final TextView tvCardName;
        private final TextView tvCardTitle;
        private final TextView tvTagTertiary;
        private final TextView tvTagSecondary;
        private final ImageView ivCardPhoto;
        private final ImageButton btnUpvote;
        private final ImageButton btnDownvote;
        private final ImageButton btnComments;
        private final TextView tvVotes;
        private final TextView tvComments;

        HazardViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCardProfile = itemView.findViewById(R.id.ivCardProfile);
            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardTitle = itemView.findViewById(R.id.tvCardTitle);
            tvTagTertiary = itemView.findViewById(R.id.tvTagTertiary);
            tvTagSecondary = itemView.findViewById(R.id.tvTagSecondary);
            ivCardPhoto = itemView.findViewById(R.id.ivCardPhoto);
            btnUpvote = itemView.findViewById(R.id.btnUpvote);
            btnDownvote = itemView.findViewById(R.id.btnDownvote);
            btnComments = itemView.findViewById(R.id.btnComments);
            tvVotes = itemView.findViewById(R.id.tvVotes);
            tvComments = itemView.findViewById(R.id.tvComments);
        }

        void bind(HazardCard hazard, OnCardClickListener listener, DataRepository dataRepository) {
            // Set user name with time ago estimate
            String userName = hazard.getUserName() != null ? hazard.getUserName() : "Anonymous";
            String timeAgo = getTimeAgoEstimate(hazard.getCreatedAt());
            tvCardName.setText(userName + " • " + timeAgo);

            // Load report author's profile picture
            loadProfilePicture(hazard.getProfilePictureUrl());

            // ...existing code...
            tvCardTitle.setText(hazard.getHazardType() + " @ " + hazard.getLocationDetails());

            // Set status badge
            tvTagTertiary.setText(hazard.getStatus());

            // Set local government badge
            tvTagSecondary.setText(hazard.getLocalGov());

            // Load photo from Storage bucket if URL is available
            loadPhotoFromStorage(hazard.getPhotoUrl());

            // Set score and update vote button states
            tvVotes.setText(String.valueOf(hazard.getScore()));

            // Set vote button colors immediately based on cached user vote
            int activeColor = itemView.getContext().getColor(R.color.md_theme_primary);
            int inactiveColor = itemView.getContext().getColor(R.color.md_theme_onSurfaceVariant);

            if (hazard.getUserVote() == 1) {
                // Upvoted
                btnUpvote.setColorFilter(activeColor);
                btnDownvote.setColorFilter(inactiveColor);
            } else if (hazard.getUserVote() == -1) {
                // Downvoted
                btnUpvote.setColorFilter(inactiveColor);
                btnDownvote.setColorFilter(activeColor);
            } else {
                // No vote
                btnUpvote.setColorFilter(inactiveColor);
                btnDownvote.setColorFilter(inactiveColor);
            }

            // Set comment count
            tvComments.setText(String.valueOf(hazard.getComments()));

            // Upvote button listener
            btnUpvote.setOnClickListener(v -> {
                VoteButtonAnimationHelper.animateVoteButton(btnUpvote);
                handleVote(hazard, 1, dataRepository);
            });

            // Downvote button listener
            btnDownvote.setOnClickListener(v -> {
                VoteButtonAnimationHelper.animateVoteButton(btnDownvote);
                handleVote(hazard, -1, dataRepository);
            });

            // Comment button listener - navigate to thread
            btnComments.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCardClick(hazard);
                }
            });

            // Card click listener - navigate to thread
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCardClick(hazard);
                }
            });
        }

        /**
         * Handle vote button click
         * @param hazard The hazard card being voted on
         * @param voteType 1 for upvote, -1 for downvote
         * @param dataRepository The data repository for cache updates
         */
        private void handleVote(HazardCard hazard, int voteType, DataRepository dataRepository) {
            String userId = com.gitgud.citywatch.util.SessionManager.getCurrentUserId();
            if (userId == null) {
                android.widget.Toast.makeText(itemView.getContext(),
                        "Please log in to vote", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine actual vote type (toggle if same vote)
            int actualVoteType = hazard.getUserVote() == voteType ? 0 : voteType;

            // Optimistic UI update
            int previousVote = hazard.getUserVote();
            long previousScore = hazard.getScore();
            long newScore = previousScore - previousVote + (actualVoteType == 0 ? 0 : actualVoteType);
            int newUserVote = actualVoteType;

            hazard.setScore(newScore);
            hazard.setUserVote(newUserVote);
            tvVotes.setText(String.valueOf(newScore));
            updateVoteButtonStates(newUserVote);

            // Use DataRepository if available, otherwise fall back to ApiClient
            if (dataRepository != null) {
                dataRepository.voteReport(hazard.getDocumentId(), actualVoteType,
                        new DataRepository.VoteCallback() {
                            @Override
                            public void onSuccess(long score, int userVote) {
                                hazard.setScore(score);
                                hazard.setUserVote(userVote);
                                tvVotes.setText(String.valueOf(score));
                                updateVoteButtonStates(userVote);
                            }

                            @Override
                            public void onError(Exception e) {
                                hazard.setScore(previousScore);
                                hazard.setUserVote(previousVote);
                                tvVotes.setText(String.valueOf(previousScore));
                                updateVoteButtonStates(previousVote);
                                android.widget.Toast.makeText(itemView.getContext(),
                                        "Failed to vote", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                // Fallback to ApiClient
                com.gitgud.citywatch.util.ApiClient.voteReport(
                        hazard.getDocumentId(), userId, actualVoteType)
                        .addOnSuccessListener(result -> {
                            hazard.setScore(result.score);
                            hazard.setUserVote(result.userVote);
                            tvVotes.setText(String.valueOf(result.score));
                            updateVoteButtonStates(result.userVote);
                        })
                        .addOnFailureListener(e -> {
                            hazard.setScore(previousScore);
                            hazard.setUserVote(previousVote);
                            tvVotes.setText(String.valueOf(previousScore));
                            updateVoteButtonStates(previousVote);
                            android.widget.Toast.makeText(itemView.getContext(),
                                    "Failed to vote", android.widget.Toast.LENGTH_SHORT).show();
                        });
            }
        }

        /**
         * Update vote button visual states based on user's current vote
         * @param userVote 1 = upvoted, -1 = downvoted, 0 = no vote
         */
        private void updateVoteButtonStates(int userVote) {
            // Get context colors
            int activeColor = itemView.getContext().getColor(R.color.md_theme_primary);
            int inactiveColor = itemView.getContext().getColor(R.color.md_theme_onSurfaceVariant);

            if (userVote == 1) {
                // Upvoted
                btnUpvote.setColorFilter(activeColor);
                btnDownvote.setColorFilter(inactiveColor);
            } else if (userVote == -1) {
                // Downvoted
                btnUpvote.setColorFilter(inactiveColor);
                btnDownvote.setColorFilter(activeColor);
            } else {
                // No vote
                btnUpvote.setColorFilter(inactiveColor);
                btnDownvote.setColorFilter(inactiveColor);
            }
        }

        /**
         * Load profile picture from Storage bucket using download URL
         * @param profilePictureUrl Download URL from Storage
         */
        private void loadProfilePicture(String profilePictureUrl) {
            if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                android.util.Log.d("HazardCardAdapter", "Loading profile picture: " +
                    profilePictureUrl.substring(0, Math.min(50, profilePictureUrl.length())));
                Glide.with(itemView.getContext())
                        .load(profilePictureUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(ivCardProfile);
            } else {
                android.util.Log.d("HazardCardAdapter", "Profile picture URL is null or empty");
                ivCardProfile.setImageResource(R.drawable.ic_profile);
            }
        }

        /**
         * Load photo from Storage bucket using signed URL
         * @param photoUrl Signed URL from Cloud Function
         */
        private void loadPhotoFromStorage(String photoUrl) {
            if (photoUrl != null && !photoUrl.isEmpty()) {
                // Load image from Storage using signed URL
                android.util.Log.d("HazardCardAdapter", "Loading photo: " + photoUrl.substring(0, Math.min(50, photoUrl.length())));
                Glide.with(itemView.getContext())
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_pic)
                        .error(R.drawable.ic_pic)
                        .centerCrop()
                        .into(ivCardPhoto);
            } else {
                // No photo available, show placeholder
                android.util.Log.d("HazardCardAdapter", "Photo URL is null or empty");
                ivCardPhoto.setImageResource(R.drawable.ic_pic);
            }
        }


        /**
         * Calculate time ago estimate from timestamp
         * @param createdAtTimestamp Timestamp in milliseconds from Firestore
         * @return String like "2m ago", "1h ago", "1d ago"
         */
        private String getTimeAgoEstimate(long createdAtTimestamp) {
            if (createdAtTimestamp == 0) {
                return "";
            }

            long currentTime = System.currentTimeMillis();
            long differenceMs = currentTime - createdAtTimestamp;

            // Convert to seconds
            long seconds = differenceMs / 1000;
            if (seconds < 60) {
                return "now";
            }

            // Convert to minutes
            long minutes = seconds / 60;
            if (minutes < 60) {
                return minutes + "m ago";
            }

            // Convert to hours
            long hours = minutes / 60;
            if (hours < 24) {
                return hours + "h ago";
            }

            // Convert to days
            long days = hours / 24;
            if (days < 7) {
                return days + "d ago";
            }

            // Convert to weeks
            long weeks = days / 7;
            if (weeks < 4) {
                return weeks + "w ago";
            }

            // Convert to months
            long months = days / 30;
            if (months < 12) {
                return months + "mo ago";
            }

            // Convert to years
            long years = days / 365;
            return years + "y ago";
        }
    }
}


