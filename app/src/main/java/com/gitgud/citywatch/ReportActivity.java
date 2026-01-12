package com.gitgud.citywatch;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.gitgud.citywatch.util.ApiClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private ImageView ivPhotoPlaceholder;
    private TextInputEditText etMapsLocation;
    private TextInputEditText etLocationDetails;
    private TextInputEditText etDescription;
    private AutoCompleteTextView spinnerHazardType;
    private AutoCompleteTextView spinnerLocalGov;
    private double selectedLatitude = 0, selectedLongitude = 0; // for map picker
    private android.net.Uri selectedImageUri = null; // for image upload
    private android.graphics.Bitmap selectedImageBitmap = null; // for image upload

    //setup gallery launcher
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                selectedImageBitmap = null;
                ivPhotoPlaceholder.setImageURI(uri);
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
        setContentView(R.layout.report_page);

        ivPhotoPlaceholder = findViewById(R.id.ivPhotoPlaceholder);
        etMapsLocation = findViewById(R.id.etMapsLocation);
        etLocationDetails = findViewById(R.id.etLocationDetails);
        etDescription = findViewById(R.id.etDescription);
        spinnerHazardType = findViewById(R.id.spinnerHazardType);
        spinnerLocalGov = findViewById(R.id.spinnerLocalGov);

        setupHeader();
        setupDropdowns();
        setupLocation();
        setupButtons();
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
        String[] hazardTypes = {"Pothole"};
        AutoCompleteTextView hazardSpinner = findViewById(R.id.spinnerHazardType);
        if (hazardSpinner != null) {
            hazardSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, hazardTypes));
        }

        String[] localGovs = {"Kuala Lumpur City Hall (DBKL)"};
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
        //capture photo
        findViewById(R.id.btnCapture).setOnClickListener(v -> takePhoto.launch(null));

        //gallery
        findViewById(R.id.btnGallery).setOnClickListener(v ->
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build())
        );

        MaterialCardView btnSubmit = findViewById(R.id.btnSubmit);
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> submitReport());
        }
    }

    private void submitReport() {
        // Validate required fields
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String hazardType = spinnerHazardType.getText() != null ? spinnerHazardType.getText().toString().trim() : "";
        String localGov = spinnerLocalGov.getText() != null ? spinnerLocalGov.getText().toString().trim() : "";
        String locationDetails = etLocationDetails.getText() != null ? etLocationDetails.getText().toString().trim() : "";

        if (description.isEmpty() || hazardType.isEmpty() || localGov.isEmpty() ||
            locationDetails.isEmpty() || selectedLatitude == 0 || selectedLongitude == 0) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if image was selected
        if (selectedImageUri == null && selectedImageBitmap == null) {
            Toast.makeText(this, "Please select a photo", Toast.LENGTH_SHORT).show();
            return;
        }

        // Submit report via ApiClient
        ApiClient.submitReport(description, hazardType, localGov, locationDetails, selectedLatitude, selectedLongitude)
                .addOnSuccessListener(documentId -> {
                    // Upload image with document ID as filename
                    uploadReportImage(documentId);
                })
                .addOnFailureListener(e ->
                    Toast.makeText(ReportActivity.this, "Failed to submit report", Toast.LENGTH_SHORT).show()
                );
    }

    private void uploadReportImage(String documentId) {
        if (selectedImageUri != null) {
            // Upload from URI (gallery picker)
            try {
                byte[] imageBytes = readUriToBytes(selectedImageUri);
                String imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);

                ApiClient.uploadReportPhoto(documentId, imageBase64)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(ReportActivity.this, "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        })
                        .addOnFailureListener(e ->
                            Toast.makeText(ReportActivity.this, "Report created, but image upload failed", Toast.LENGTH_SHORT).show()
                        );
            } catch (Exception e) {
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
                        Toast.makeText(ReportActivity.this, "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e ->
                        Toast.makeText(ReportActivity.this, "Report created, but image upload failed", Toast.LENGTH_SHORT).show()
                    );
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
}
