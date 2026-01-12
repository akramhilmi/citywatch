package com.gitgud.citywatch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private ImageView ivPhotoPlaceholder;
    private TextInputEditText etMapsLocation;
    private double selectedLatitude = 0, selectedLongitude = 0; // for map picker

    //setup gallery launcher
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                ivPhotoPlaceholder.setImageURI(uri);
                prepareImageView();
            }
        });

    // setup camera launcher
    private final ActivityResultLauncher<Void> takePhoto =
        registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) {
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
            btnSubmit.setOnClickListener(v -> {
                Toast.makeText(this, "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}
