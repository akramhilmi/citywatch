package com.gitgud.citywatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.gitgud.citywatch.data.repository.DataRepository;
import com.gitgud.citywatch.util.ApiClient;
import com.gitgud.citywatch.util.SessionManager;

import java.io.IOException;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private ImageView ivPhotoPlaceholder;
    private MaterialCardView cvUploadPhoto;
    private TextInputEditText etMapsLocation;
    private TextInputEditText etLocationDetails;
    private TextInputEditText etDescription;
    private TextInputEditText etHazardType;
    private AutoCompleteTextView spinnerLocalGov;
    private double selectedLatitude = 0, selectedLongitude = 0; // for map picker
    private android.net.Uri selectedImageUri = null; // for image upload
    private android.graphics.Bitmap selectedImageBitmap = null; // for image upload
    private AlertDialog progressDialog; // for submission progress
    private DataRepository dataRepository;

    // Edit mode fields
    private boolean isEditMode = false;
    private String editReportId = null;
    private String editStatus = null;

    //setup gallery launcher
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                selectedImageBitmap = null;
                ivPhotoPlaceholder.setImageURI(uri);
                cvUploadPhoto.setVisibility(View.VISIBLE);
                prepareImageView();
            }
        });

    // setup camera launcher
    private final ActivityResultLauncher<Void> takePhoto =
        registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) {
                selectedImageBitmap = bitmap;
                selectedImageUri = null;
                ivPhotoPlaceholder.setImageBitmap(bitmap);
                cvUploadPhoto.setVisibility(View.VISIBLE);
                prepareImageView();
            }
        });

    // setup location picker launcher
    private final ActivityResultLauncher<Intent> pickLocation =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                selectedLatitude = result.getData().getDoubleExtra("latitude", 0);
                selectedLongitude = result.getData().getDoubleExtra("longitude", 0);
                String locationName = result.getData().getStringExtra("locationName");
                etMapsLocation.setText(locationName != null ? locationName :
                    String.format(Locale.US, "%.4f, %.4f", selectedLatitude, selectedLongitude));
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        dataRepository = DataRepository.getInstance(this);

        ivPhotoPlaceholder = findViewById(R.id.ivPhotoPlaceholder);
        cvUploadPhoto = findViewById(R.id.cvUploadPhoto);
        etMapsLocation = findViewById(R.id.etMapsLocation);
        etLocationDetails = findViewById(R.id.etLocationDetails);
        etDescription = findViewById(R.id.etDescription);
        etHazardType = findViewById(R.id.etHazardType);
        spinnerLocalGov = findViewById(R.id.spinnerLocalGov);

        // Check if in edit mode
        checkEditMode();

        setupHeader();
        setupDropdowns();
        setupLocation();
        setupButtons();

        // Load existing data if in edit mode
        if (isEditMode) {
            loadExistingReportData();
        }
    }

    private void checkEditMode() {
        Intent intent = getIntent();
        isEditMode = intent.getBooleanExtra("isEditMode", false);
        if (isEditMode) {
            editReportId = intent.getStringExtra("reportId");
            editStatus = intent.getStringExtra("status");
        }
    }

    private void loadExistingReportData() {
        Intent intent = getIntent();

        // Pre-fill all fields with existing data
        String description = intent.getStringExtra("description");
        String hazardType = intent.getStringExtra("hazardType");
        String localGov = intent.getStringExtra("localGov");
        String locationDetails = intent.getStringExtra("locationDetails");
        selectedLatitude = intent.getDoubleExtra("latitude", 0);
        selectedLongitude = intent.getDoubleExtra("longitude", 0);

        if (description != null) etDescription.setText(description);
        if (hazardType != null) etHazardType.setText(hazardType);
        if (localGov != null) spinnerLocalGov.setText(localGov, false);
        if (locationDetails != null) etLocationDetails.setText(locationDetails);
        if (selectedLatitude != 0 && selectedLongitude != 0) {
            etMapsLocation.setText(String.format(Locale.US, "%.4f, %.4f",
                selectedLatitude, selectedLongitude));
        }

        // Load existing photo if available
        String photoUrl = intent.getStringExtra("photoUrl");
        if (photoUrl != null && !photoUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(photoUrl)
                .into(ivPhotoPlaceholder);
            prepareImageView();
        }
    }

    private void prepareImageView() {
        //removes the icon padding so photo fills the card
        ivPhotoPlaceholder.setPadding(0, 0, 0, 0);
        ivPhotoPlaceholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private void setupHeader() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupDropdowns() {
        //local government dropdown
        String[] localGovs = {"Dewan Bandaraya Kuala Lumpur (DBKL)","Majlis Bandaraya Petaling Jaya (MBPJ)","Majlis Bandaraya Subang Jaya (MBSJ)"};
        AutoCompleteTextView govSpinner = findViewById(R.id.spinnerLocalGov);
        if (govSpinner != null) {
            govSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, localGovs));
        }
    }

    private void setupLocation() {
        if (etMapsLocation != null) {
            etMapsLocation.setText("");
            etMapsLocation.setOnClickListener(v -> openLocationPicker());
            etMapsLocation.setFocusable(false);
        }
    }

    private void openLocationPicker() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        pickLocation.launch(intent);
    }

    private void setupButtons() {
        MaterialButton btnCapture = findViewById(R.id.btnCapture);
        MaterialButton btnGallery = findViewById(R.id.btnGallery);

        if (isEditMode) {
            // Disable photo buttons in edit mode
            btnCapture.setEnabled(false);
            btnCapture.setAlpha(0.5f);
            btnGallery.setEnabled(false);
            btnGallery.setAlpha(0.5f);
        } else {
            // Enable photo buttons in create mode
            btnCapture.setOnClickListener(v -> takePhoto.launch(null));
            btnGallery.setOnClickListener(v ->
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build())
            );
        }

        MaterialCardView btnSubmit = findViewById(R.id.btnSubmit);
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (isEditMode) {
                    editReport();
                } else {
                    submitReport();
                }
            });
        }
    }

    private String extractShortName(String fullName) {
        if (fullName == null) return "";
        int start = fullName.indexOf('(');
        int end = fullName.indexOf(')');
        if (start != -1 && end != -1 && end > start) {
            return fullName.substring(start + 1, end).trim();
        }
        return fullName.trim();
    }

    private void editReport() {
        // Validate required fields
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String hazardType = etHazardType.getText() != null ? etHazardType.getText().toString().trim() : "";
        String localGovFull = spinnerLocalGov.getText() != null ? spinnerLocalGov.getText().toString().trim() : "";
        String locationDetails = etLocationDetails.getText() != null ? etLocationDetails.getText().toString().trim() : "";

        if (description.isEmpty() || hazardType.isEmpty() || localGovFull.isEmpty() ||
            locationDetails.isEmpty() || selectedLatitude == 0 || selectedLongitude == 0) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String localGov = extractShortName(localGovFull);

        // Get current user ID from SessionManager
        String userId = SessionManager.getCurrentUserId();

        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        showProgressDialog("Updating report...");

        // Call edit API
        ApiClient.editReport(editReportId, userId, description, hazardType, localGov,
                locationDetails, selectedLatitude, selectedLongitude, editStatus)
                .addOnSuccessListener(aVoid -> {
                    dismissProgressDialog();
                    // Invalidate cache to reload with fresh data
                    dataRepository.invalidateReportsCache();
                    Toast.makeText(this, "Report updated successfully!", Toast.LENGTH_SHORT).show();

                    // Return edited data to fragment for immediate UI update
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("reportId", editReportId);
                    resultIntent.putExtra("description", description);
                    resultIntent.putExtra("hazardType", hazardType);
                    resultIntent.putExtra("localGov", localGov);
                    resultIntent.putExtra("locationDetails", locationDetails);
                    resultIntent.putExtra("latitude", selectedLatitude);
                    resultIntent.putExtra("longitude", selectedLongitude);
                    resultIntent.putExtra("status", editStatus);

                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    dismissProgressDialog();
                    Toast.makeText(this, "Failed to update report: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void submitReport() {
        // Validate required fields
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String hazardType = etHazardType.getText() != null ? etHazardType.getText().toString().trim() : "";
        String localGovFull = spinnerLocalGov.getText() != null ? spinnerLocalGov.getText().toString().trim() : "";
        String locationDetails = etLocationDetails.getText() != null ? etLocationDetails.getText().toString().trim() : "";

        if (description.isEmpty() || hazardType.isEmpty() || localGovFull.isEmpty() ||
            locationDetails.isEmpty() || selectedLatitude == 0 || selectedLongitude == 0) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String localGov = extractShortName(localGovFull);

        // Check if image was selected
        if (selectedImageUri == null && selectedImageBitmap == null) {
            Toast.makeText(this, "Please select a photo", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get current user ID from SessionManager
        String userId = SessionManager.getCurrentUserId();

        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        showProgressDialog("Submitting report...");

        // Submit report via DataRepository (automatically invalidates cache)
        dataRepository.submitReport(description, hazardType, localGov, locationDetails,
                selectedLatitude, selectedLongitude,
                new DataRepository.ReportSubmitCallback() {
                    @Override
                    public void onSuccess(String documentId) {
                        // Update progress dialog
                        updateProgressDialog("Uploading photo...");
                        // Upload image with document ID as filename
                        uploadReportImage(documentId);
                    }

                    @Override
                    public void onError(Exception e) {
                        dismissProgressDialog();
                        Toast.makeText(ReportActivity.this, "Failed to submit report",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadReportImage(String documentId) {
        if (selectedImageUri != null) {
            // Upload from URI (gallery picker)
            try {
                byte[] imageBytes = readUriToBytes(selectedImageUri);
                String imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);

                ApiClient.uploadReportPhoto(documentId, imageBase64)
                        .addOnSuccessListener(aVoid -> {
                            dismissProgressDialog();
                            Toast.makeText(ReportActivity.this, "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            dismissProgressDialog();
                            Toast.makeText(ReportActivity.this, "Report created, but image upload failed", Toast.LENGTH_SHORT).show();
                        });
            } catch (Exception e) {
                dismissProgressDialog();
                Toast.makeText(ReportActivity.this, "Failed to read image", Toast.LENGTH_SHORT).show();
            }
        } else if (selectedImageBitmap != null) {
            // Upload from Bitmap (camera)
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            selectedImageBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] imageData = baos.toByteArray();
            String imageBase64 = android.util.Base64.encodeToString(imageData, android.util.Base64.DEFAULT);

            ApiClient.uploadReportPhotoBitmap(documentId, imageBase64)
                    .addOnSuccessListener(aVoid -> {
                        dismissProgressDialog();
                        Toast.makeText(ReportActivity.this, "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        dismissProgressDialog();
                        Toast.makeText(ReportActivity.this, "Report created, but image upload failed", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private byte[] readUriToBytes(android.net.Uri uri) throws java.io.IOException {
        android.content.ContentResolver resolver = getContentResolver();
        java.io.InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Cannot open image stream");
        }

        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        inputStream.close();
        return outputStream.toByteArray();
    }

    private void showProgressDialog(String message) {
        dismissProgressDialog();

        android.widget.LinearLayout progressLayout = new android.widget.LinearLayout(this);
        progressLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        progressLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        progressLayout.setPadding(48, 48, 48, 48);
        progressLayout.setGravity(android.view.Gravity.CENTER);

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
        progressBar.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                100, 100));
        progressLayout.addView(progressBar);

        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(message);
        textView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        textView.setPadding(0, 24, 0, 0);
        progressLayout.addView(textView);

        progressDialog = new AlertDialog.Builder(this)
                .setView(progressLayout)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void updateProgressDialog(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            android.widget.LinearLayout layout = (android.widget.LinearLayout) progressDialog.findViewById(android.R.id.custom);
            if (layout != null && layout.getChildCount() > 1) {
                android.widget.TextView textView = (android.widget.TextView) layout.getChildAt(1);
                textView.setText(message);
            }
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
