package com.gitgud.citywatch.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.gitgud.citywatch.R;
import com.gitgud.citywatch.SignInActivity;
import com.gitgud.citywatch.data.repository.DataRepository;
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;

public class ProfileFragment extends Fragment {
    private TextView tvNameHeader, tvNameValue, tvEmailValue, tvPhoneValue;
    private ImageView ivProfileImage;
    private SessionManager sessionManager;
    private ActivityResultLauncher<String> pickImageLauncher;
    private FrameLayout loadingOverlay;
    private int pendingFetches = 0; // tracks pending data fetches from Firebase
    private DataRepository dataRepository;
    private boolean hasCachedData = false;

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
        dataRepository = DataRepository.getInstance(requireContext());

        // Initialize image picker launcher
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadProfilePicture(uri);
                }
            }
        );

        initViews(view);
        loadUserData();
        setupClickListeners(view);
    }

    private void initViews(View view) {
        ivProfileImage = view.findViewById(R.id.ivProfileImage);
        tvNameHeader = view.findViewById(R.id.tvNameHeader);
        tvNameValue = view.findViewById(R.id.tvNameValue);
        tvEmailValue = view.findViewById(R.id.tvEmailValue);
        tvPhoneValue = view.findViewById(R.id.tvPhoneValue);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
    }

    private void loadUserData() {
        hasCachedData = false;

        com.google.firebase.auth.FirebaseUser user = ApiClient.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            String email = user.getEmail();

            // Set email immediately (from Firebase Auth)
            tvEmailValue.setText(email != null ? email : "Not set");

            // Use DataRepository with cache-first strategy
            dataRepository.getUserProfile(userId, new DataRepository.UserProfileCallback() {
                @Override
                public void onCacheData(String name, String phone, String profilePictureUrl) {
                    if (getActivity() == null) return;
                    hasCachedData = true;
                    loadingOverlay.setVisibility(View.GONE);

                    // Display cached data immediately
                    String displayName = name != null && !name.isEmpty() ? name : "User";
                    tvNameHeader.setText(displayName);
                    tvNameValue.setText(displayName);
                    tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "Not set");

                    // Load cached profile picture
                    if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                        Glide.with(ProfileFragment.this)
                                .load(profilePictureUrl)
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .centerCrop()
                                .into(ivProfileImage);
                    }
                }

                @Override
                public void onFreshData(String name, String phone, String profilePictureUrl) {
                    if (getActivity() == null) return;
                    loadingOverlay.setVisibility(View.GONE);

                    // Update with fresh data
                    String displayName = name != null && !name.isEmpty() ? name : "User";
                    tvNameHeader.setText(displayName);
                    tvNameValue.setText(displayName);
                    tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "Not set");

                    // Load fresh profile picture
                    Glide.with(ProfileFragment.this)
                            .load(profilePictureUrl)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .centerCrop()
                            .into(ivProfileImage);
                }

                @Override
                public void onLoading(boolean isLoading) {
                    if (getActivity() == null) return;
                    // Only show loading overlay if we don't have cached data
                    if (!hasCachedData) {
                        loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (getActivity() == null) return;
                    loadingOverlay.setVisibility(View.GONE);
                    tvNameHeader.setText("User");
                    tvNameValue.setText("Not set");
                    tvPhoneValue.setText("Not set");
                }
            });
        } else {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private void decrementPendingFetches() {
        // Kept for compatibility with loadProfilePicture if called directly
        pendingFetches--;
        if (pendingFetches <= 0) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners(View view) {
        //sign out
        view.findViewById(R.id.btnSignOut).setOnClickListener(v -> {
            SessionManager.logout(requireContext());
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

        // Edit email
        view.findViewById(R.id.btnEditEmail).setOnClickListener(v -> showEditEmailDialog());

        // Update photo - launch image picker
        view.findViewById(R.id.btnUpdatePhoto).setOnClickListener(v -> pickImageLauncher.launch("image/*"));
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
                            SessionManager.logout(requireContext());
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
                    dataRepository.updateUserName(newName, new DataRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            tvNameHeader.setText(newName);
                            tvNameValue.setText(newName);
                            Toast.makeText(requireActivity(), "Name updated successfully",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(requireActivity(), "Failed to update name",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(requireActivity(), "Name cannot be empty",
                            Toast.LENGTH_SHORT).show();
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
                    dataRepository.updateUserPhone(newPhone, new DataRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            tvPhoneValue.setText(newPhone);
                            Toast.makeText(requireActivity(), "Phone number updated successfully",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(requireActivity(), "Failed to update phone number",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(requireActivity(), "Phone number cannot be empty",
                            Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditEmailDialog() {
        android.widget.EditText input = new android.widget.EditText(requireActivity());
        input.setText(tvEmailValue.getText());
        input.setSelection(input.getText().length());
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        new AlertDialog.Builder(requireActivity())
            .setTitle("Edit Email")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String newEmail = input.getText().toString().trim();
                if (!newEmail.isEmpty() && isValidEmail(newEmail)) {
                    ApiClient.updateUserEmail(newEmail).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            tvEmailValue.setText(newEmail);
                            Toast.makeText(requireActivity(), "Email updated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Exception error = task.getException();
                            String errorMessage = error != null ? error.getMessage() : "Failed to update email";
                            showErrorDialog("Email Update Failed", errorMessage);
                        }
                    });
                } else {
                    Toast.makeText(requireActivity(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(requireActivity())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void loadProfilePicture(String userId) {
        ApiClient.getProfilePictureUrl(userId).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String downloadUrl = task.getResult();
                // Load image using Glide with a fallback if URL is null
                Glide.with(this)
                        .load(downloadUrl)
                        .placeholder(R.drawable.ic_profile) // Shows while loading or if URL is null
                        .error(R.drawable.ic_profile)       // Shows if loading fails
                        .centerCrop()
                        .into(ivProfileImage);

                if (downloadUrl != null) {
                    android.util.Log.d("Photo", "Loaded: " + downloadUrl);
                } else {
                    android.util.Log.d("Photo", "No profile picture found, using default");
                }
            } else {
                // If the task itself fails, still use the default
                ivProfileImage.setImageResource(R.drawable.ic_profile);
            }
            decrementPendingFetches();
        });
    }

    private void uploadProfilePicture(Uri imageUri) {
        Toast.makeText(requireActivity(), "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        dataRepository.uploadProfilePicture(imageUri, new DataRepository.ProfilePictureCallback() {
            @Override
            public void onSuccess(String downloadUrl) {
                Toast.makeText(requireActivity(), "Profile picture uploaded successfully",
                        Toast.LENGTH_SHORT).show();
                // Load the new profile picture
                Glide.with(ProfileFragment.this)
                        .load(downloadUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .centerCrop()
                        .into(ivProfileImage);
            }

            @Override
            public void onError(Exception e) {
                String errorMessage = e != null ? e.getMessage() : "Failed to upload profile picture";
                showErrorDialog("Upload Failed", errorMessage);
            }
        });
    }
}
