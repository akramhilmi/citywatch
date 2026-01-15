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
import com.gitgud.citywatch.model.SpacingItemDecoration;

import java.util.ArrayList;
import java.util.List;

public class CommunityFragment extends Fragment {

    private RecyclerView rvCommunityCards;
    private ProgressBar progressSpinner;
    private HazardCardAdapter adapter;
    private List<HazardCard> hazardCardList;
    private DataRepository dataRepository;
    private boolean hasCachedData = false;

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
}
