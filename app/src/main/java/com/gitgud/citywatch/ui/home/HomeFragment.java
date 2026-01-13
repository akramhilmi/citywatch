package com.gitgud.citywatch.ui.home;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitgud.citywatch.R;
import com.gitgud.citywatch.ReportActivity;
import com.gitgud.citywatch.ThreadActivity;
import com.gitgud.citywatch.model.HazardCard;
import com.gitgud.citywatch.ui.community.HazardCardAdapter;
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;
import com.gitgud.citywatch.ui.community.SpacingItemDecoration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView rvYourReports;
    private ProgressBar progressSpinner;
    private HazardCardAdapter adapter;
    private List<HazardCard> userReportsList;

    public HomeFragment() {
        // Required empty public constructor
    }

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        rvYourReports = view.findViewById(R.id.rvCards);
        progressSpinner = view.findViewById(R.id.progressSpinner);
        userReportsList = new ArrayList<>();
        adapter = new HazardCardAdapter(userReportsList);

        rvYourReports.setLayoutManager(new LinearLayoutManager(getContext()));
        rvYourReports.setAdapter(adapter);

        // Add 20dp spacing between cards
        int spacingInDp = (int) (20 * getResources().getDisplayMetrics().density);
        rvYourReports.addItemDecoration(new SpacingItemDecoration(spacingInDp));

        // Set card click listener to navigate to ThreadActivity
        adapter.setOnCardClickListener(hazardCard -> {
            Intent intent = new Intent(getActivity(), ThreadActivity.class);
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
            intent.putExtra("latitude", hazardCard.getLatitude());
            intent.putExtra("longitude", hazardCard.getLongitude());
            startActivity(intent);
        });

        // Show spinner and load reports
        progressSpinner.setVisibility(View.VISIBLE);
        loadUserReports();
        setupClickListeners(view);
    }

    private void loadUserReports() {
        String currentUserId = SessionManager.getCurrentUserId();
        if (currentUserId == null) {
            progressSpinner.setVisibility(View.GONE);
            return;
        }

        ApiClient.getAllReports()
                .addOnSuccessListener(reports -> {
                    userReportsList.clear();

                    // Filter reports for current user
                    for (HazardCard report : reports) {
                        if (currentUserId.equals(report.getUserId())) {
                            userReportsList.add(report);
                        }
                    }

                    // Sort by createdAt descending (newest first)
                    userReportsList.sort((c1, c2) ->
                        Long.compare(c2.getCreatedAt(), c1.getCreatedAt()));

                    fetchUserVotes();
                })
                .addOnFailureListener(e -> {
                    progressSpinner.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to load reports: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchUserVotes() {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null || userReportsList.isEmpty()) {
            progressSpinner.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            return;
        }

        List<String> reportIds = new ArrayList<>();
        for (HazardCard card : userReportsList) {
            reportIds.add(card.getDocumentId());
        }

        ApiClient.getUserVotesForReports(reportIds, userId)
                .addOnSuccessListener(votes -> {
                    for (HazardCard card : userReportsList) {
                        Integer userVote = votes.get(card.getDocumentId());
                        card.setUserVote(userVote != null ? userVote : 0);
                    }
                    progressSpinner.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    progressSpinner.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                });
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnAddReportHome).setOnClickListener(v ->
            startActivity(new Intent(getActivity(), ReportActivity.class)));
    }
}
