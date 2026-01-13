package com.gitgud.citywatch;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationPickerActivity extends AppCompatActivity implements MapEventsReceiver {

    private MapView mapView;
    private Geocoder geocoder;
    private GeoPoint selectedLocation;
    private Marker marker;
    private static final double DEFAULT_LAT = 3.1390; // Kuala Lumpur
    private static final double DEFAULT_LNG = 101.6869;
    private static final int DEFAULT_ZOOM = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        // Initialize osmdroid configuration
        Configuration.getInstance().setUserAgentValue(getPackageName());

        geocoder = new Geocoder(this, Locale.getDefault());

        // Setup map view
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Set initial center and zoom
        IMapController controller = mapView.getController();
        controller.setZoom(DEFAULT_ZOOM);
        controller.setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));

        // Add map events overlay for click handling
        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(this, this);
        mapView.getOverlays().add(mapEventsOverlay);

        // Setup back button
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // Setup confirm button
        findViewById(R.id.btnConfirm).setOnClickListener(v -> confirmLocationSelection());
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {
        // Handle single tap on map - place marker
        selectedLocation = p;
        updateMarker(p);
        return true;
    }

    @Override
    public boolean longPressHelper(GeoPoint p) {
        // Handle long press - confirm selection
        selectedLocation = p;
        updateMarker(p);
        confirmLocationSelection();
        return true;
    }

    private void updateMarker(GeoPoint location) {
        // Remove old marker if exists
        if (marker != null) {
            mapView.getOverlays().remove(marker);
        }

        // Get address from coordinates
        String addressText = getAddressFromLocation(location.getLatitude(), location.getLongitude());

        // Add new marker
        marker = new Marker(mapView);
        marker.setPosition(location);
        marker.setTitle(addressText);
        marker.setSnippet(String.format(Locale.US, "%.4f, %.4f",
                location.getLatitude(), location.getLongitude()));
        mapView.getOverlays().add(marker);

        // Center map on selected location
        IMapController controller = mapView.getController();
        controller.setCenter(location);
        controller.setZoom(DEFAULT_ZOOM);

        mapView.invalidate();
        Toast.makeText(this, "Location selected. Long press to confirm or click elsewhere to change.",
                Toast.LENGTH_SHORT).show();
    }

    private String getAddressFromLocation(double latitude, double longitude) {
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressText = new StringBuilder();
                if (address.getThoroughfare() != null) {
                    addressText.append(address.getThoroughfare());
                }
                if (address.getLocality() != null) {
                    if (addressText.length() > 0) addressText.append(", ");
                    addressText.append(address.getLocality());
                }
                if (address.getAdminArea() != null) {
                    if (addressText.length() > 0) addressText.append(", ");
                    addressText.append(address.getAdminArea());
                }
                return addressText.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return String.format(Locale.US, "%.4f, %.4f", latitude, longitude);
    }

    private void confirmLocationSelection() {
        if (selectedLocation != null) {
            String address = getAddressFromLocation(selectedLocation.getLatitude(), selectedLocation.getLongitude());

            Intent resultIntent = new Intent();
            resultIntent.putExtra("latitude", selectedLocation.getLatitude());
            resultIntent.putExtra("longitude", selectedLocation.getLongitude());
            resultIntent.putExtra("locationName", address);

            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}

