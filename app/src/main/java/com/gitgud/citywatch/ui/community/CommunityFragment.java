package com.gitgud.citywatch.ui.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.gitgud.citywatch.R;
import com.gitgud.citywatch.ReportActivity;

public class CommunityFragment extends Fragment {

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
        // Initialize views and setup listeners here
        setupClickListeners(view);
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnAddReportCommunity).setOnClickListener(v ->
            startActivity(new Intent(getActivity(), ReportActivity.class)));
    }
}

