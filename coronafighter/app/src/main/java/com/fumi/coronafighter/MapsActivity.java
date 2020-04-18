package com.fumi.coronafighter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.fumi.coronafighter.firebase.FireStore;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.maps.android.projection.SphericalMercatorProjection;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "MapsActivity";

    private GoogleMap mMap;
    private CurrentPositionViewModel mViewModel;
    private Toolbar mToolbar;

    public FirebaseAuth mAuth;

    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private static final int RC_SIGN_IN = 123;

    public Date refreshAlarmAreasTime = null;

    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                Log.i(TAG, "MobileAds initialize completed.");

                Iterator i = initializationStatus.getAdapterStatusMap().keySet().iterator();
                while (i.hasNext()){
                    String key = (String)i.next();
                    AdapterStatus status = initializationStatus.getAdapterStatusMap().get(key);

                    Log.i(TAG, key + "=" + status.getInitializationState().name());

                    if (key.equals("com.google.android.gms.ads.MobileAds")) {
                        String msg = "com.google.android.gms.ads.MobileAds: " + status.getInitializationState().name();
                        Toast.makeText(MapsActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });


        mAdView = findViewById(R.id.adView);
        mAdView.setAdListener(new AdListener(){
            @Override
            public void onAdLoaded() {
                // Code to be executed when an ad finishes loading.
                //Toast.makeText(MapsActivity.this, "AdLoaded.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                // Code to be executed when an ad request fails.
                //Toast.makeText(MapsActivity.this, "AdFailedToLoad. erroeCode =" + Integer.toString(errorCode), Toast.LENGTH_LONG).show();

                if (errorCode == AdRequest.ERROR_CODE_INTERNAL_ERROR) {
                    Toast.makeText(MapsActivity.this, "内部エラー", Toast.LENGTH_LONG).show();
                }
                else if (errorCode == AdRequest.ERROR_CODE_INVALID_REQUEST) {
                    Toast.makeText(MapsActivity.this, "広告リクエスト無効", Toast.LENGTH_LONG).show();
                }
                else if (errorCode == AdRequest.ERROR_CODE_NETWORK_ERROR) {
                    Toast.makeText(MapsActivity.this, "ネットワーク接続エラー", Toast.LENGTH_LONG).show();
                }
                else if (errorCode == AdRequest.ERROR_CODE_NO_FILL) {
                    Toast.makeText(MapsActivity.this, "広告枠不足", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onAdClosed() {
                // Code to be executed when the user is about to return
                // to the app after tapping on an ad.
                Toast.makeText(MapsActivity.this, "AdClosed.", Toast.LENGTH_LONG).show();
            }
        });
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        //mToolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(mToolbar);

        mAuth = FirebaseAuth.getInstance();

        initialize();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        if (FireStore.new_coronavirus_infection_flag == 0) {
            MenuItem item = menu.findItem(R.id.infection_report);
            item.setEnabled(true);

            MenuItem item2 = menu.findItem(R.id.infection_report_cancel);
            item2.setEnabled(false);
        }
        else {
            MenuItem item = menu.findItem(R.id.infection_report);
            item.setEnabled(false);

            MenuItem item2 = menu.findItem(R.id.infection_report_cancel);
            item2.setEnabled(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                Intent intent = new Intent(getApplication(), LocationService.class);
                stopService(intent);

                signOut();
                break;
            case R.id.infection_report:
                new AlertDialog.Builder(MapsActivity.this)
                        .setTitle(R.string.infection_report)
                        .setMessage(R.string.infection_report_confirm_msg_by_tracing)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                FireStore.reportNewCoronavirusInfection(mAuth.getCurrentUser(), 1);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                break;
            case R.id.infection_report_cancel:
                FireStore.reportNewCoronavirusInfection(mAuth.getCurrentUser(), 0);
                break;

            case R.id.refresh_alarm_areas:
                FireStore.refreshAlertAreas(FireStore.currentLocation);
                break;
        }
        return false;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                FireStore.refreshAlertAreas(FireStore.currentLocation);
                return false;
            }
        });

        mMap.setOnMyLocationClickListener(new GoogleMap.OnMyLocationClickListener() {
            @Override
            public void onMyLocationClick(@NonNull Location location) {
                FireStore.refreshAlertAreas(location);
            }
        });

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                        Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                String locCode = olc.getCode();

                ArrayList<String> locCodes = new ArrayList<String>();
                locCodes.add(locCode);
                FireStore.refreshAlartAreas(locCodes);
            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(final LatLng latLng) {
                final List<String> locCodes = FireStore.findExistInAlertAreasCode(latLng);
                if (locCodes == null || locCodes.size() == 0) {
                    new AlertDialog.Builder(MapsActivity.this)
                            .setTitle(R.string.infection_report)
                            .setMessage(R.string.infection_report_confirm_msg)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                                            Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                                    String locCode = olc.getCode();

                                    Date date1 = Calendar.getInstance().getTime();
                                    Timestamp timestamp = new Timestamp(date1);
                                    FireStore.registNewCoronavirusInfo(mAuth.getCurrentUser(), locCode, timestamp);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
                else {
                    new AlertDialog.Builder(MapsActivity.this)
                            .setTitle(R.string.infection_area_delete)
                            .setMessage(R.string.infection_area_delete_msg)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Date date1 = Calendar.getInstance().getTime();
                                    Timestamp timestamp = new Timestamp(date1);

                                    for(String locCode : locCodes) {
                                        FireStore.removeNewCoronavirusInfo(mAuth.getCurrentUser(), locCode);
                                    }
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        });
    }

    public void createSignInIntent() {
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.PhoneBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (resultCode == RESULT_OK) {
                initialize();
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                Toast.makeText(this, "Sign in failed.", Toast.LENGTH_SHORT).show();

                finish();
            }
        }
    }

    public void signOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        //Toast.makeText(getApplicationContext(), "Sign out completed.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    public void delete() {
        AuthUI.getInstance()
                .delete(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(getApplicationContext(), "Auth delete completed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Android Quickstart:
     * https://developers.google.com/sheets/api/quickstart/android
     *
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[]permissions, @NonNull int[] grantResults) {

        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // initialize
            initialize();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initialize() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        if (mAuth.getCurrentUser() == null) {
            createSignInIntent();
            return;
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Application app = getApplication();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
        // Use the ViewModel
        mViewModel.getSelected().observe(this, new Observer<Location>() {
            @Override
            public void onChanged(final Location location) {
                if (mMap == null) {
                    return;
                }

                float zoom = mMap.getCameraPosition().zoom;
                if (zoom < SettingInfos.map_min_zoom) {
                    zoom = SettingInfos.map_default_zoom;
                }

                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                CameraUpdate camera = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
                // カメラの位置に移動
                mMap.moveCamera(camera);

                OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                        Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                String locCode = olc.getCode();

                if (FireStore.new_coronavirus_infection_flag == 1) {
                    Date date1 = Calendar.getInstance().getTime();
                    Timestamp timestamp = new Timestamp(date1);
                    FireStore.registNewCoronavirusInfo(mAuth.getCurrentUser(), locCode, timestamp);
                }

                if (FireStore.currentLocation == null
                        || FireStore.currentLocation.distanceTo(location) > SettingInfos.refresh_alarm_distance_min_meter) {
                    FireStore.refreshAlertAreas(location);

                    FireStore.currentLocation = location;
                }
            }
        });

        FireStore.init(this);

        Intent intent = new Intent(getApplication(), LocationService.class);
        startForegroundService(intent);
    }

    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;

    public void setHeatMap(Collection list) {
        if (mOverlay != null) {
            mOverlay.remove();
        }

        if (list == null || list.size() == 0) {
            return;
        }

        // Create a heat map tile provider, passing it the latlngs of the police stations.
        mProvider = new HeatmapTileProvider.Builder()
                .weightedData(list)
                .build();

        // Add a tile overlay to the map, using the heat map tile provider.
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }
}
