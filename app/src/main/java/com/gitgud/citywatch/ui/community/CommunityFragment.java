package com.gitgud.citywatch.ui.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitgud.citywatch.R;
import com.gitgud.citywatch.ReportActivity;
import com.gitgud.citywatch.model.HazardCard;
import com.gitgud.citywatch.util.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class CommunityFragment extends Fragment {

    private RecyclerView rvCommunityCards;
    private ProgressBar progressSpinner;
    private HazardCardAdapter adapter;
    private List<HazardCard> hazardCardList;

    public CommunityFragment() {
        // Required empty public constructor
    }

    public static CommunityFragment newInstance() {
        return new CommunityFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        rvCommunityCards = view.findViewById(R.id.rvCommunityCards);
        progressSpinner = view.findViewById(R.id.progressSpinner);
        hazardCardList = new ArrayList<>();
        adapter = new HazardCardAdapter(hazardCardList);

        // Setup RecyclerView
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 1);
        rvCommunityCards.setLayoutManager(gridLayoutManager);
        rvCommunityCards.setAdapter(adapter);

        // Using 20dp directly if the above isn't what you want:
        int spacingInDp = (int) (20 * getResources().getDisplayMetrics().density);
        rvCommunityCards.addItemDecoration(new SpacingItemDecoration(spacingInDp));

        // Set card click listener to navigate to ThreadActivity
        adapter.setOnCardClickListener(hazardCard -> {
            Intent intent = new Intent(getActivity(), com.gitgud.citywatch.ThreadActivity.class);
            intent.putExtra("documentId", hazardCard.getDocumentId());
            intent.putExtra("userName", hazardCard.getUserName());
            intent.putExtra("userId", hazardCard.getUserId());
            intent.putExtra("hazardType", hazardCard.getHazardType());
            intent.putExtra("locationDetails", hazardCard.getLocationDetails());
            intent.putExtra("status", hazardCard.getStatus());
            intent.putExtra("localGov", hazardCard.getLocalGov());
            intent.putExtra("description", hazardCard.getDescription());
            intent.putExtra("photoUrl", hazardCard.getPhotoUrl());
            intent.putExtra("profilePictureUrl", hazardCard.getProfilePictureUrl());
            intent.putExtra("score", hazardCard.getScore());
            intent.putExtra("userVote", hazardCard.getUserVote());
            intent.putExtra("createdAt", hazardCard.getCreatedAt());
            intent.putExtra("comments", hazardCard.getComments());

            // Add map location data
            intent.putExtra("latitude", hazardCard.getLatitude());
            intent.putExtra("longitude", hazardCard.getLongitude());
            android.util.Log.d("CommunityFragment", "Passing location to ThreadActivity: " +
                hazardCard.getLatitude() + ", " + hazardCard.getLongitude());

            startActivity(intent);
        });

        // Show spinner and load reports from Firestore
        progressSpinner.setVisibility(View.VISIBLE);
        loadReports();

        // Setup click listeners
        setupClickListeners(view);
    }

    private void loadReports() {
        ApiClient.getAllReports()
                .addOnSuccessListener(reports -> {
                    hazardCardList.clear();
                    hazardCardList.addAll(reports);

                    // Sort by date (newest first)
                    hazardCardList.sort((c1, c2) ->
                        Long.compare(c2.getCreatedAt(), c1.getCreatedAt()));

                    // Fetch user votes for all reports
                    fetchUserVotes();
                })
                .addOnFailureListener(e -> {
                    progressSpinner.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to load reports: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchUserVotes() {
        String userId = com.gitgud.citywatch.util.SessionManager.getCurrentUserId();
        if (userId == null || hazardCardList.isEmpty()) {
            progressSpinner.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            return;
        }

        // Collect all report IDs
        java.util.List<String> reportIds = new java.util.ArrayList<>();
        for (com.gitgud.citywatch.model.HazardCard card : hazardCardList) {
            reportIds.add(card.getDocumentId());
        }

        // Fetch user votes for all reports
        ApiClient.getUserVotesForReports(reportIds, userId)
                .addOnSuccessListener(votes -> {
                    // Update each card with user's vote
                    for (com.gitgud.citywatch.model.HazardCard card : hazardCardList) {
                        Integer userVote = votes.get(card.getDocumentId());
                        card.setUserVote(userVote != null ? userVote : 0);
                    }
                    progressSpinner.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    // Still show cards even if votes failed to load
                    progressSpinner.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                });
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnAddReportCommunity).setOnClickListener(v ->
            startActivity(new Intent(getActivity(), ReportActivity.class)));
    }
}
