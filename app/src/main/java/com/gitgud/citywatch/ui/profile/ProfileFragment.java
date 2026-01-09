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
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.materialswitch.MaterialSwitch;

public class ProfileFragment extends Fragment {
    private TextView tvNameHeader, tvNameValue, tvEmailValue, tvPhoneValue;
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
        com.google.firebase.auth.FirebaseUser user = ApiClient.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            String email = user.getEmail();

            // Set email immediately (from Firebase Auth)
            tvEmailValue.setText(email != null ? email : "Not set");

            // Fetch name and phone from Firestore
            ApiClient.getUserName(userId).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String name = task.getResult();
                    String displayName = name != null && !name.isEmpty() ? name : "User";
                    tvNameHeader.setText(displayName);
                    tvNameValue.setText(displayName);
                } else {
                    tvNameHeader.setText("User");
                    tvNameValue.setText("Not set");
                }
            });

            ApiClient.getUserPhone(userId).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String phone = task.getResult();
                    tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "Not set");
                } else {
                    tvPhoneValue.setText("Not set");
                }
            });
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
            String email = ApiClient.getCurrentUserEmail();
            if (email != null) {
                ApiClient.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(requireActivity(), "Reset email sent to " + email, Toast.LENGTH_LONG).show();
                    }
                });
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

        // Edit name
        view.findViewById(R.id.btnEditName).setOnClickListener(v -> showEditNameDialog());

        // Edit phone
        view.findViewById(R.id.btnEditPhone).setOnClickListener(v -> showEditPhoneDialog());

        // TODO :create real edit flow, use placeholders for now
        view.findViewById(R.id.btnUpdatePhoto).setOnClickListener(v -> Toast.makeText(requireActivity(), "Camera access coming soon", Toast.LENGTH_SHORT).show());
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(requireActivity())
            .setTitle("Delete Account")
            .setMessage("Are you sure? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                com.google.firebase.auth.FirebaseUser user = ApiClient.getCurrentUser();
                if (user != null) {
                    ApiClient.deleteAccount(user.getUid()).addOnCompleteListener(task -> {
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

    private void showEditNameDialog() {
        android.widget.EditText input = new android.widget.EditText(requireActivity());
        input.setText(tvNameValue.getText());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireActivity())
            .setTitle("Edit Name")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    com.google.firebase.auth.FirebaseUser user = ApiClient.getCurrentUser();
                    if (user != null) {
                        ApiClient.updateUserName(user.getUid(), newName).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                tvNameHeader.setText(newName);
                                tvNameValue.setText(newName);
                                Toast.makeText(requireActivity(), "Name updated successfully", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireActivity(), "Failed to update name", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    Toast.makeText(requireActivity(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditPhoneDialog() {
        android.widget.EditText input = new android.widget.EditText(requireActivity());
        input.setText(tvPhoneValue.getText());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireActivity())
            .setTitle("Edit Phone Number")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String newPhone = input.getText().toString().trim();
                if (!newPhone.isEmpty()) {
                    com.google.firebase.auth.FirebaseUser user = ApiClient.getCurrentUser();
                    if (user != null) {
                        ApiClient.updateUserPhone(user.getUid(), newPhone).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                tvPhoneValue.setText(newPhone);
                                Toast.makeText(requireActivity(), "Phone number updated successfully", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireActivity(), "Failed to update phone number", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    Toast.makeText(requireActivity(), "Phone number cannot be empty", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}

