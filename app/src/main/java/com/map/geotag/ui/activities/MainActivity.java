package com.map.geotag.ui.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.map.geotag.R;
import com.map.geotag.database.dbhandler.GeoTagDBHandler;
import com.map.geotag.database.dao.LocationDAO;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.media.MediaRecorder.VideoSource.CAMERA;
import static java.lang.Double.parseDouble;

public class MainActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final int PERMISSION_REQ_CODE_CAMERA = 1;

    private com.map.geotag.model.Location currLocation, prevLocation;
    private GeoTagDBHandler geoTagDBHandler;
    private String pictureImagePath = "";
    private ArrayList<com.map.geotag.model.Location> locations;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        geoTagDBHandler = new GeoTagDBHandler(MainActivity.this);
        locations = new ArrayList<>();
        if (!isNetworkConnected(MainActivity.this)) {
            showNoInternetDialog(MainActivity.this);
        }
        if (getIntent() != null && getIntent().getSerializableExtra("location") != null) {
            prevLocation = (com.map.geotag.model.Location) getIntent().getSerializableExtra("location");
            locations.add(prevLocation);
        }
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {

        googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng point) {
                Geocoder geocoder;
                List<Address> addresses;
                geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                try {
                    addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1);
                    String address = "No Address Available";
                    if (addresses != null && addresses.size() > 0 && addresses.get(0) != null && addresses.get(0).getAddressLine(0) != null) {
                        address = addresses.get(0).getAddressLine(0);
                    }
                    currLocation = new com.map.geotag.model.Location();
                    currLocation.setAddress(address);
                    ;
                    currLocation.setLongi(point.longitude + "");
                    currLocation.setLat(point.latitude + "");

                    googleMap.addMarker(new MarkerOptions().position(point).title(address).
                            icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(point));


                    if (Build.VERSION.SDK_INT >= 23) {
                        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    PERMISSION_REQ_CODE_CAMERA
                            );
                        } else {
                            takePhotoFromCamera();
                        }
                    } else {
                        takePhotoFromCamera();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });


        if (prevLocation != null) {
            LatLng latLng = new LatLng(parseDouble(prevLocation.getLat()), parseDouble(prevLocation.getLongi()));
            googleMap.addMarker(new MarkerOptions().position(latLng).title(prevLocation.getAddress()).
                    icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }

        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                LatLng latLng = marker.getPosition();
                LocationDAO locationDAO = new LocationDAO(MainActivity.this);
                ArrayList<com.map.geotag.model.Location> locations = locationDAO.getLocations();
                if (locations != null) {
                    com.map.geotag.model.Location foundLocation = null;
                    for (com.map.geotag.model.Location location : locations) {
                        double prevLat = parseDouble(location.getLat());
                        double prevLong = parseDouble(location.getLongi());
                        double currlat = latLng.latitude;
                        double currLongi = latLng.longitude;
                        if (currLongi == prevLong && currlat == prevLat) {
                            foundLocation = location;
                        }
                    }
                    if (foundLocation != null) {
                        locations.remove(foundLocation);
                        locations.add(0, foundLocation);
                        Intent intent = new Intent(MainActivity.this, ListActivity.class);
                        intent.putExtra("locations", locations);
                        startActivity(intent);
                    }
                }
                return false;
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQ_CODE_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhotoFromCamera();
                } else {
                    Toast.makeText(MainActivity.this, "Please grant necessary permission(s)", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void takePhotoFromCamera() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = timeStamp + ".jpg";
            File storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
            pictureImagePath = storageDir.getAbsolutePath() + "/" + imageFileName;
            File file = new File(pictureImagePath);
            Uri outputFileUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".provider", file);
            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            startActivityForResult(cameraIntent, CAMERA);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA && resultCode == RESULT_OK) {

            File imgFile = new File(pictureImagePath);


            if (imgFile.exists() && currLocation != null) {
                currLocation.setFile(pictureImagePath);
                LocationDAO locationDAO = new LocationDAO(MainActivity.this);
                locationDAO.insert(currLocation);
                if (locations == null) {
                    locations = new ArrayList<>();
                }
                locations.add(currLocation);
            } else {
                Toast.makeText(MainActivity.this, "Please try again", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
        } else if (id == R.id.taggedplaces) {
            Intent intent = new Intent(MainActivity.this, ListActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_alltags, menu);
        return true;
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert conMgr != null;
        return conMgr.getActiveNetworkInfo() != null
                && conMgr.getActiveNetworkInfo().isAvailable()
                && conMgr.getActiveNetworkInfo().isConnected();
    }

    public static void showNoInternetDialog(Context context) {
        AlertDialog.Builder alertdialogbuilder = new AlertDialog.Builder(context);
        alertdialogbuilder.setTitle("No Internet !!!");
        alertdialogbuilder.setMessage("Check your internet connection and try again.");
        alertdialogbuilder.setPositiveButton("OK", null);
        alertdialogbuilder.show();

    }

}
