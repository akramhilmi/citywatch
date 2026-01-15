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
import com.gitgud.citywatch.data.repository.DataRepository;

import java.util.ArrayList;
import java.util.List;

public class CommunityFragment extends Fragment {

    private RecyclerView rvCommunityCards;
    private ProgressBar progressSpinner;
    private HazardCardAdapter adapter;
    private List<HazardCard> hazardCardList;
    private DataRepository dataRepository;
    private boolean hasCachedData = false;
    private androidx.activity.result.ActivityResultLauncher<Intent> editReportLauncher;

    public CommunityFragment() {
        // Required empty public constructor
    }

    public static CommunityFragment newInstance() {
        return new CommunityFragment();
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
                        for (int i = 0; i < hazardCardList.size(); i++) {
                            HazardCard card = hazardCardList.get(i);
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
                            loadReports();
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
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize repository
        dataRepository = DataRepository.getInstance(requireContext());

        // Initialize views
        rvCommunityCards = view.findViewById(R.id.rvCommunityCards);
        progressSpinner = view.findViewById(R.id.progressSpinner);
        hazardCardList = new ArrayList<>();
        adapter = new HazardCardAdapter(hazardCardList);
        adapter.setDataRepository(dataRepository);

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
        loadReports();

        // Setup click listeners
        setupClickListeners(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload reports when fragment comes back into focus
        // This ensures vote/comment counts are updated if user modified them in ThreadActivity
        loadReports();
    }

    private void loadReports() {
        hasCachedData = false;

        dataRepository.getAllReports(new DataRepository.DataCallback<List<HazardCard>>() {
            @Override
            public void onCacheData(List<HazardCard> data) {
                // Show cached data immediately
                if (getActivity() == null) return;
                hasCachedData = true;
                updateReportsList(data);
                // Hide spinner since we have cached data to show
                progressSpinner.setVisibility(View.GONE);
            }

            @Override
            public void onFreshData(List<HazardCard> data) {
                // Update with fresh data
                if (getActivity() == null) return;
                updateReportsList(data);
                progressSpinner.setVisibility(View.GONE);
            }

            @Override
            public void onLoading(boolean isLoading) {
                if (getActivity() == null) return;
                // Only show spinner if we don't have cached data
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
        hazardCardList.clear();
        hazardCardList.addAll(reports);

        // Sort by date (newest first)
        hazardCardList.sort((c1, c2) ->
            Long.compare(c2.getCreatedAt(), c1.getCreatedAt()));

        adapter.notifyDataSetChanged();
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnAddReportCommunity).setOnClickListener(v ->
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
        String userId = com.gitgud.citywatch.util.SessionManager.getCurrentUserId();
        if (userId == null) {
            android.widget.Toast.makeText(getContext(), "Not logged in",
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Store position for potential rollback
        int position = hazardCardList.indexOf(hazardCard);

        // Optimistic update - remove from UI immediately
        if (position != -1) {
            hazardCardList.remove(position);
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
                    loadReports();
                })
                .addOnFailureListener(e -> {
                    // Rollback on failure
                    if (position != -1) {
                        hazardCardList.add(position, hazardCard);
                        adapter.notifyItemInserted(position);
                    }
                    dataRepository.updateReportInCache(hazardCard);
                    android.widget.Toast.makeText(getContext(),
                        "Failed to delete: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show();
                });
    }
}
