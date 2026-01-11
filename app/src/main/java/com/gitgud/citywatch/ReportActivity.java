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

public class ReportActivity extends AppCompatActivity {

    private ImageView ivPhotoPlaceholder;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.report_page);

        ivPhotoPlaceholder = findViewById(R.id.ivPhotoPlaceholder);

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
        //local government dropdown
        String[] localGovs = {"Kuala Lumpur City Hall (DBKL)","Majlis Bandaraya Petaling Jaya(MBPJ)","Majlis Bandaraya Subang Jaya(MBSJ)"};
        AutoCompleteTextView govSpinner = findViewById(R.id.spinnerLocalGov);
        if (govSpinner != null) {
            govSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, localGovs));
        }
    }

    private void setupLocation() {
        TextInputEditText etLocation = findViewById(R.id.etMapsLocation);
        if (etLocation != null) {
            etLocation.setText(""); //clear placeholder link
            etLocation.setOnClickListener(v -> {
                Uri gmmIntentUri = Uri.parse("geo:0,0?q=Hazards");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                try {
                    startActivity(mapIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Google Maps not found", Toast.LENGTH_SHORT).show();
                }
            });
        }
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
                //get hazard type
                TextInputEditText etHazardType = findViewById(R.id.etHazardType);
                String hazardType = etHazardType != null ? etHazardType.getText().toString() : "";

                if (hazardType.isEmpty()) {
                    Toast.makeText(this, "Please enter a Hazard type", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(this, "Report submitted: " + hazardType, Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}
