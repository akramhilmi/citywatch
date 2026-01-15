package com.gitgud.citywatch;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class FullScreenImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_image);

        ImageView ivFullScreen = findViewById(R.id.ivFullScreen);
        ImageButton btnClose = findViewById(R.id.btnClose);

        String photoUrl = getIntent().getStringExtra("photoUrl");

        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .into(ivFullScreen);
        }

        btnClose.setOnClickListener(v -> finish());
    }
}
