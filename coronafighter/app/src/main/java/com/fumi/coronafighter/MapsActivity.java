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
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
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

    private GoogleMap mMap;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;
    private CurrentPositionViewModel mViewModel;

    private Toolbar mToolbar;

    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private static final int RC_SIGN_IN = 123;

    private Location currentLocation;

    private ListenerRegistration mListenerStatus;
    private int new_coronavirus_infection_flag = 0;
    private ListenerRegistration mListenerAlert;

    final Collection<WeightedLatLng> mAlertAreas = new ArrayList<WeightedLatLng>();

    private Date refreshAlarmAreasTime = null;

    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_app_id));
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        //mToolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(mToolbar);

        mAuth = FirebaseAuth.getInstance();
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

        initialize();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        if (new_coronavirus_infection_flag == 0) {
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
                signOut();
                break;
            /*
            case R.id.trace_position_start:
                TracingIntentService.startActionTracing(getBaseContext(), 180, 15);
                break;
            case R.id.trace_position_stop:
                stopService(new Intent(getBaseContext(), TracingIntentService.class));
                break;
             */
            case R.id.infection_report:
                new AlertDialog.Builder(MapsActivity.this)
                        .setTitle(R.string.infection_report)
                        .setMessage(R.string.infection_report_confirm_msg_by_tracing)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                reportNewCoronavirusInfection(mAuth.getCurrentUser(), 1);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                break;
            case R.id.infection_report_cancel:
                reportNewCoronavirusInfection(mAuth.getCurrentUser(), 0);
                break;

            case R.id.refresh_alarm_areas:
                refreshAlertAreas(currentLocation);
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
                refreshAlertAreas(currentLocation);
                return false;
            }
        });

        mMap.setOnMyLocationClickListener(new GoogleMap.OnMyLocationClickListener() {
            @Override
            public void onMyLocationClick(@NonNull Location location) {
                refreshAlertAreas(location);
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
                refreshAlartAreas(locCodes);
            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(final LatLng latLng) {
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
                                registNewCoronavirusInfo(mAuth.getCurrentUser(), locCode, timestamp);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
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

        mFirebaseFirestore = FirebaseFirestore.getInstance();

        Application app = getApplication();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
        // Use the ViewModel
        mViewModel.getSelected().observe(this, new Observer<Location>() {
            @Override
            public void onChanged(final Location location) {
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

                if (new_coronavirus_infection_flag == 1) {
                    Date date1 = Calendar.getInstance().getTime();
                    Timestamp timestamp = new Timestamp(date1);
                    registNewCoronavirusInfo(mAuth.getCurrentUser(), locCode, timestamp);
                }

                if (currentLocation == null || currentLocation.distanceTo(location) > SettingInfos.refresh_alarm_distance_min_meter) {
                    refreshAlertAreas(location);

                    currentLocation = location;
                }
            }
        });

        mListenerStatus = mFirebaseFirestore.collection(mAuth.getCurrentUser().getEmail())
                .whereEqualTo(FieldPath.documentId(),"status")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        for(DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                            new_coronavirus_infection_flag = document.getLong("new_coronavirus_infection_flag").intValue();

                            invalidateOptionsMenu();
                            break;
                        }
                    }
                });

        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"update-info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            return;
                        }
                        if(currentLocation != null) {
                            refreshAlertAreas(currentLocation);
                        }

                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            SettingInfos.tracing_time_interval_second = doc.getLong("tracing_time_interval_second").intValue();
                            SettingInfos.tracing_min_distance_meter = doc.getLong("tracing_min_distance_meter").intValue();

                            SettingInfos.map_min_zoom = doc.getLong("map_min_zoom").intValue();
                            SettingInfos.map_default_zoom = doc.getLong("map_default_zoom").intValue();

                            SettingInfos.refresh_alarm_distance_min_meter = doc.getLong("refresh_alarm_distance_min_meter").intValue();

                            SettingInfos.refresh_alarm_areas_min_interval_second = doc.getLong("refresh_alarm_areas_min_interval_second").intValue();
                        }
                    }
                });
    }

    private void reportNewCoronavirusInfection(final FirebaseUser currentUser, int new_coronavirus_infection_flag) {
        mListenerAlert.remove();

        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        activityInfo.put("timestamp", Constants.DATE_FORMAT.format(Calendar.getInstance().getTime()));
        activityInfo.put("new_coronavirus_infection_flag", new_coronavirus_infection_flag);

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection(currentUser.getEmail())
                .document("status")
                .set(activityInfo, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("firebase-store", "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("firebase-store", "Error writing document", e);
                    }
                });

        // 警報基準
        // 日時：　過去７日間
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        final Date date1 = cal.getTime();
        Timestamp timestamp = new Timestamp(date1);

        if (new_coronavirus_infection_flag == 1) {
            mFirebaseFirestore.collection(currentUser.getEmail()).whereGreaterThan("timestamp", timestamp)
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                QuerySnapshot snapshot = task.getResult();
                                for(DocumentSnapshot doc: snapshot.getDocuments()){
                                    double latitude = doc.getDouble("latitude");
                                    double longitude = doc.getDouble("longitude");
                                    Timestamp timestamp = doc.getTimestamp("timestamp");

                                    OpenLocationCode olc = new OpenLocationCode(latitude, longitude,
                                            Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                                    String locCode = olc.getCode();
                                    //Log.d("firebase-store", "locCode = " + locCode);
                                    registNewCoronavirusInfo(currentUser, locCode, timestamp);
                                }
                            } else {
                                Log.d("firebase-store", "get failed with ", task.getException());
                            }
                        }
                    });
        } else {
            mFirebaseFirestore.collection("corona-infos")
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                QuerySnapshot snapshot = task.getResult();
                                List<DocumentSnapshot> list = snapshot.getDocuments();
                                for(DocumentSnapshot doc: list){
                                    String docId = doc.getId();
                                    if (docId.equals("update-info")) {
                                        continue;
                                    }

                                    //Log.d("firebase-store", "locCode = " + locCode);
                                    removeNewCoronavirusInfo(currentUser, docId);
                                }
                            } else {
                                Log.d("firebase-store", "get failed with ", task.getException());
                            }
                        }
                    });
        }

        reportNewCoronavirusInfectionComlete(timestamp);
    }

    private void reportNewCoronavirusInfectionComlete(Timestamp timestamp) {
        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"update-info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if(currentLocation != null) {
                             refreshAlertAreas(currentLocation);
                        }
                    }
                });

        Map<String, Object> info2 = new HashMap<>();
        info2.put("timestamp", timestamp);
        mFirebaseFirestore.collection("corona-infos")
                .document("update-info")
                .set(info2, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("firebase-store", "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("firebase-store", "Error writing document", e);
                    }
                });
    }

    private void registNewCoronavirusInfo(FirebaseUser currentUser, String locCode, Timestamp timestamp) {
        // Create a new user with a first and last name
        Map<String, Object> info = new HashMap<>();
        info.put(currentUser.getEmail().replace(".", "-"), timestamp);

        Map<String, Object> info2 = new HashMap<>();
        info2.put("timestamp", timestamp);

        // Add a new document with a generated ID;
        String locCode1 = locCode.substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);
        String locCode2 = locCode.substring(Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);

        mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .set(info2, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("firebase-store", "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("firebase-store", "Error writing document", e);
                    }
                });

        mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .collection("sub-areas")
                .document(locCode2)
                .set(info, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("firebase-store", "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("firebase-store", "Error writing document", e);
                    }
                });
    }

    private void removeNewCoronavirusInfo(final FirebaseUser currentUser, final String locCode) {
        final String field_key = currentUser.getEmail().replace(".", "-");

        // Create a new user with a first and last name
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -3600);
        Date date1 = cal.getTime();
        Timestamp timeStart = new Timestamp(date1);

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection("corona-infos")
                .document(locCode)
                .collection("sub-areas")
                .whereGreaterThan(field_key, timeStart)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d("firebase-store", document.getId() + " => " + document.getData());
                                String locCode2 = document.getId();

                                Map<String, Object> info = new HashMap<>();
                                info.put(field_key, FieldValue.delete());

                                mFirebaseFirestore.collection("corona-infos")
                                        .document(locCode)
                                        .collection("sub-areas")
                                        .document(locCode2)
                                        .update(info)
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void aVoid) {
                                                Log.d("firebase-store", "DocumentSnapshot successfully written!");
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Log.w("firebase-store", "Error writing document", e);
                                            }
                                        });

                            }
                        } else {
                            Log.d("firebase-store", "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;

    private void addHeatMap(Collection list) {
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

    private void refreshAlertAreas(Location location) {
        if (location == null) {
            return;
        }

        ArrayList<String> locCodes = new ArrayList<String>();

        OpenLocationCode olc = new OpenLocationCode(location.getLatitude(), location.getLongitude(),
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        String locCode = olc.getCode();
        locCodes.add(locCode);

        refreshAlartAreas(locCodes);
    }

    private void refreshAlartAreas(ArrayList<String> locCodes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
        Date now = Calendar.getInstance().getTime();

        if (refreshAlarmAreasTime != null && refreshAlarmAreasTime.after(now)) {
            return;
        }

        mAlertAreas.clear();
        // コードの構成は、地域コード、都市コード、街区コード、建物コードからなります。
        // 例えば8Q7XPQ3C+J88というコードの場合は、8Q7Xが地域コード（100×100km）、
        // PQが都市コード（5×5km）、3Cが街区コード（250×250m）、
        // +以降のJ88は建物コード（14×14m）を意味しています。
        addHeatMap(mAlertAreas);

        ArrayList<String> locCode1s = new ArrayList<String>();
        for(String locCode : locCodes){
            String locCode1 = locCode.substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);
            if (!locCode1s.contains(locCode1)) {
                locCode1s.add(locCode1);
            }
        }

        mFirebaseFirestore.collection("corona-infos")
                .whereIn(FieldPath.documentId(), locCode1s)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                final String locCode1 = document.getId();

                                document.getReference()
                                        .collection("sub-areas")
                                        .get()
                                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    CoronaFighterApplication app = (CoronaFighterApplication)getApplication();

                                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                                        Log.d("firebase-store", document.getId() + " => " + document.getData());

                                                        String locCode2 = document.getId();
                                                        String code = locCode1 + locCode2;

                                                        Map<String, Object> data = document.getData();
                                                        addAlertAreas(code, data, mAlertAreas);
                                                    }

                                                    addHeatMap(mAlertAreas);

                                                } else {
                                                    Log.d("firebase-store", "Error getting documents: ", task.getException());
                                                }
                                            }
                                        });
                            }
                        }
                        else {
                            Log.d("firebase-store", "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    public void addAlertAreas(String code, Map<String, Object> data, Collection<WeightedLatLng> alertAreas) {
        // 警報基準
        // 日時：　過去７日間
        // エリア：　PQが都市コード（5×5km）
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        final Date date1 = cal.getTime();

        Iterator<String> i = data.keySet().iterator();
        int cnt = 0;
        while(i.hasNext()) {
            String key = i.next();
            Timestamp timestamp = (Timestamp)data.get(key);

            if(timestamp.toDate().after(date1)) {
                cnt++;
            }
        }

        if (cnt == 0) {
            return;
        }
        OpenLocationCode openLocationCode = new OpenLocationCode(code);
        OpenLocationCode.CodeArea areaCode = openLocationCode.decode();
        LatLng latlng = new LatLng(areaCode.getCenterLatitude(), areaCode.getCenterLongitude());
        double intensity = 0.0;
        if (cnt >= Constants.INFECTION_SATURATION_CNT_MAX) {
            intensity = 1.0;
        }
        else if (cnt <= Constants.INFECTION_SATURATION_CNT_MIN) {
            intensity = 0.0;
        }
        else {
            intensity = ((double) cnt - Constants.INFECTION_SATURATION_CNT_MIN)/Constants.INFECTION_SATURATION_CNT_MAX;
        }
        if (intensity <= 0.0) {
            return;
        }
        WeightedLatLng value = new WeightedLatLng(latlng, intensity);
        alertAreas.add(value);
    }
}
