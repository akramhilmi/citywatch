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
import com.gitgud.citywatch.data.repository.DataRepository;
import com.gitgud.citywatch.util.SessionManager;
import com.gitgud.citywatch.ui.community.SpacingItemDecoration;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView rvYourReports;
    private ProgressBar progressSpinner;
    private HazardCardAdapter adapter;
    private List<HazardCard> userReportsList;
    private DataRepository dataRepository;
    private boolean hasCachedData = false;
    private androidx.activity.result.ActivityResultLauncher<Intent> editReportLauncher;

    // Statistics TextViews
    private android.widget.TextView tvNumberSubmitted;
    private android.widget.TextView tvNumberConfirmed;
    private android.widget.TextView tvNumberInProgress;
    private android.widget.TextView tvNumberResolved;
    private ProgressBar statsProgressSpinner;

    public HomeFragment() {
        // Required empty public constructor
    }

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize edit report launcher
        editReportLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    // Report was edited successfully, update UI immediately
                    Intent data = result.getData();
                    String reportId = data.getStringExtra("reportId");

                    if (reportId != null) {
                        // Find the report in the list and update it
                        for (int i = 0; i < userReportsList.size(); i++) {
                            HazardCard card = userReportsList.get(i);
                            if (card.getDocumentId().equals(reportId)) {
                                // Update the report with new data
                                card.setDescription(data.getStringExtra("description"));
                                card.setHazardType(data.getStringExtra("hazardType"));
                                card.setLocalGov(data.getStringExtra("localGov"));
                                card.setLocationDetails(data.getStringExtra("locationDetails"));
                                card.setLatitude(data.getDoubleExtra("latitude", 0));
                                card.setLongitude(data.getDoubleExtra("longitude", 0));
                                card.setStatus(data.getStringExtra("status"));

                                // Update cache immediately
                                if (dataRepository != null) {
                                    dataRepository.updateReportInCache(card);
                                }

                                // Notify adapter that this item changed
                                adapter.notifyItemChanged(i);
                                break;
                            }
                        }

                        // Reload in background to ensure consistency with server
                        if (dataRepository != null) {
                            loadUserReports();
                        }
                    }
                }
            }
        );
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

        // Initialize repository
        dataRepository = DataRepository.getInstance(requireContext());

        // Initialize views
        rvYourReports = view.findViewById(R.id.rvCards);
        progressSpinner = view.findViewById(R.id.progressSpinner);

        // Initialize statistics TextViews
        tvNumberSubmitted = view.findViewById(R.id.tvNumberSubmitted);
        tvNumberConfirmed = view.findViewById(R.id.tvNumberConfirmed);
        tvNumberInProgress = view.findViewById(R.id.tvNumberInProgress);
        tvNumberResolved = view.findViewById(R.id.tvNumberResolved);
        statsProgressSpinner = view.findViewById(R.id.statsProgressSpinner);

        userReportsList = new ArrayList<>();
        adapter = new HazardCardAdapter(userReportsList);
        adapter.setDataRepository(dataRepository);

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

        // Set report action listener for edit/delete
        adapter.setOnReportActionListener(new HazardCardAdapter.OnReportActionListener() {
            @Override
            public void onEditReport(HazardCard hazardCard) {
                showEditReportDialog(hazardCard);
            }

            @Override
            public void onDeleteReport(HazardCard hazardCard) {
                showDeleteReportDialog(hazardCard);
            }
        });

        // Load reports using cache-first strategy
        loadUserReports();
        // Load statistics
        loadStatistics();
        setupClickListeners(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload user reports when fragment comes back into focus
        // This ensures vote/comment counts are updated if user modified them in ThreadActivity
        loadUserReports();
        // Reload statistics to reflect any changes
        loadStatistics();
    }

    private void loadUserReports() {
        String currentUserId = SessionManager.getCurrentUserId();
        if (currentUserId == null) {
            progressSpinner.setVisibility(View.GONE);
            return;
        }

        hasCachedData = false;

        dataRepository.getUserReports(new DataRepository.DataCallback<List<HazardCard>>() {
            @Override
            public void onCacheData(List<HazardCard> data) {
                if (getActivity() == null) return;
                hasCachedData = true;
                updateReportsList(data);
                progressSpinner.setVisibility(View.GONE);
            }

            @Override
            public void onFreshData(List<HazardCard> data) {
                if (getActivity() == null) return;
                updateReportsList(data);
                progressSpinner.setVisibility(View.GONE);
            }

            @Override
            public void onLoading(boolean isLoading) {
                if (getActivity() == null) return;
                if (!hasCachedData) {
                    progressSpinner.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                progressSpinner.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Failed to load reports: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateReportsList(List<HazardCard> reports) {
        userReportsList.clear();
        userReportsList.addAll(reports);
        adapter.notifyDataSetChanged();
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnAddReportHome).setOnClickListener(v ->
            startActivity(new Intent(getActivity(), ReportActivity.class)));
    }

    private void showEditReportDialog(HazardCard hazardCard) {
        Intent intent = new Intent(getActivity(), com.gitgud.citywatch.ReportActivity.class);
        // Set edit mode flag
        intent.putExtra("isEditMode", true);
        intent.putExtra("reportId", hazardCard.getDocumentId());

        // Pass all existing report data
        intent.putExtra("description", hazardCard.getDescription());
        intent.putExtra("hazardType", hazardCard.getHazardType());
        intent.putExtra("localGov", hazardCard.getLocalGov());
        intent.putExtra("locationDetails", hazardCard.getLocationDetails());
        intent.putExtra("latitude", hazardCard.getLatitude());
        intent.putExtra("longitude", hazardCard.getLongitude());
        intent.putExtra("status", hazardCard.getStatus());
        intent.putExtra("photoUrl", hazardCard.getPhotoUrl());

        editReportLauncher.launch(intent);
    }

    private void showDeleteReportDialog(HazardCard hazardCard) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Report")
                .setMessage("Are you sure you want to delete this report? This will also delete all associated comments.")
                .setPositiveButton("Delete", (dialog, which) -> deleteReport(hazardCard))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReport(HazardCard hazardCard) {
        String userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            android.widget.Toast.makeText(getContext(), "Not logged in",
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Store position for potential rollback
        int position = userReportsList.indexOf(hazardCard);

        // Optimistic update - remove from UI immediately
        if (position != -1) {
            userReportsList.remove(position);
            adapter.notifyItemRemoved(position);
        }

        // Remove from local cache immediately
        dataRepository.invalidateReportsCache();

        // Call API
        com.gitgud.citywatch.util.ApiClient.deleteReport(hazardCard.getDocumentId(), userId)
                .addOnSuccessListener(aVoid -> {
                    android.widget.Toast.makeText(getContext(), "Report deleted",
                        android.widget.Toast.LENGTH_SHORT).show();
                    // Reload reports to ensure consistency with server
                    loadUserReports();
                    // Reload statistics immediately to reflect the deletion
                    loadStatistics();
                })
                .addOnFailureListener(e -> {
                    // Rollback on failure
                    if (position != -1) {
                        userReportsList.add(position, hazardCard);
                        adapter.notifyItemInserted(position);
                    }
                    dataRepository.updateReportInCache(hazardCard);
                    android.widget.Toast.makeText(getContext(),
                        "Failed to delete: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Load statistics from Firebase Cloud Function
     */
    private void loadStatistics() {
        // Show loading spinner
        statsProgressSpinner.setVisibility(View.VISIBLE);

        com.gitgud.citywatch.util.ApiClient.getStats()
            .addOnSuccessListener(stats -> {
                if (getActivity() == null) return;

                // Hide loading spinner
                statsProgressSpinner.setVisibility(View.GONE);

                // Update TextViews with real statistics
                if (stats.containsKey("submitted")) {
                    tvNumberSubmitted.setText(String.valueOf(stats.get("submitted")));
                }
                if (stats.containsKey("confirmed")) {
                    tvNumberConfirmed.setText(String.valueOf(stats.get("confirmed")));
                }
                if (stats.containsKey("inProgress")) {
                    tvNumberInProgress.setText(String.valueOf(stats.get("inProgress")));
                }
                if (stats.containsKey("resolved")) {
                    tvNumberResolved.setText(String.valueOf(stats.get("resolved")));
                }
            })
            .addOnFailureListener(e -> {
                if (getActivity() == null) return;

                // Hide loading spinner
                statsProgressSpinner.setVisibility(View.GONE);

                // Keep default values on error, optionally show a toast
                android.util.Log.e("HomeFragment", "Failed to load statistics: " + e.getMessage());
            });
    }
}
