package com.gitgud.citywatch.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.gitgud.citywatch.R;
import com.gitgud.citywatch.SignInActivity;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {
    private TextView tvNameHeader, tvNameValue, tvEmailValue, tvPhoneValue;
    private FirebaseAuth mAuth;
    private SessionManager sessionManager;

    public ProfileFragment() {
        // Required empty public constructor
    }

    public static ProfileFragment newInstance() {
        return new ProfileFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager();

        initViews(view);
        loadUserData();
        setupClickListeners(view);
    }

    private void initViews(View view) {
        tvNameHeader = view.findViewById(R.id.tvNameHeader);
        tvNameValue = view.findViewById(R.id.tvNameValue);
        tvEmailValue = view.findViewById(R.id.tvEmailValue);
        tvPhoneValue = view.findViewById(R.id.tvPhoneValue);
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String email = user.getEmail();
            String phone = user.getPhoneNumber();
            //fallbacks for missing profile page data
            tvNameHeader.setText(name != null && !name.isEmpty() ? name : "User");
            tvNameValue.setText(name != null && !name.isEmpty() ? name : "Not set");
            tvEmailValue.setText(email != null ? email : "Not set");
            tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "Not set");
        }
    }

    private void setupClickListeners(View view) {
        //sign out
        view.findViewById(R.id.btnSignOut).setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(requireActivity(), SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        //change password
        view.findViewById(R.id.btnChangePassword).setOnClickListener(v -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                String email = currentUser.getEmail();
                if (email != null) {
                    mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireActivity(), "Reset email sent to " + email, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
        //delete account
        view.findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> showDeleteConfirmation());

        //contactUs(email)
        view.findViewById(R.id.btnContactUs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@citywatch.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "CityWatch Support Request");
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        //darkmode switch
        MaterialSwitch switchDark = view.findViewById(R.id.switchDarkMode);
        switchDark.setOnCheckedChangeListener((btn, isChecked) ->
            AppCompatDelegate.setDefaultNightMode(isChecked ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO)
        );

        // TODO :create real edit flow, use placeholders for now
        view.findViewById(R.id.btnEditName).setOnClickListener(v -> Toast.makeText(requireActivity(), "Edit Name feature coming soon", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.btnUpdatePhoto).setOnClickListener(v -> Toast.makeText(requireActivity(), "Camera access coming soon", Toast.LENGTH_SHORT).show());
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(requireActivity())
            .setTitle("Delete Account")
            .setMessage("Are you sure? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    user.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            sessionManager.logout();
                            startActivity(new Intent(requireActivity(), SignInActivity.class));
                            requireActivity().finish();
                        }
                    });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}

