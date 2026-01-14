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

        // Initialize repository
        dataRepository = DataRepository.getInstance(requireContext());

        // Initialize views
        rvYourReports = view.findViewById(R.id.rvCards);
        progressSpinner = view.findViewById(R.id.progressSpinner);
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

        // Load reports using cache-first strategy
        loadUserReports();
        setupClickListeners(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload user reports when fragment comes back into focus
        // This ensures vote/comment counts are updated if user modified them in ThreadActivity
        loadUserReports();
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
}
