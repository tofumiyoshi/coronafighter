package com.fumi.coronafighter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //mToolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(mToolbar);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mAuth = FirebaseAuth.getInstance();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            createSignInIntent();
        }

        Application app = getApplication();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
        // Use the ViewModel
        mViewModel.getSelected().observe(this, new Observer<LatLng>() {
            @Override
            public void onChanged(final LatLng latLng) {
                mMap.addMarker(new MarkerOptions().position(latLng).
                        visible(true).
                        title("Me"));

                CameraUpdate camera = CameraUpdateFactory.newLatLngZoom(latLng, 15);
                // カメラの位置に移動
                mMap.moveCamera(camera);
            }
        });
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                signOut();
                break;
            case R.id.trace_position_start:
                TracingIntentService.startActionTracing(getBaseContext(), 180, 15);
                break;
            case R.id.trace_position_stop:
                stopService(new Intent(getBaseContext(), TracingIntentService.class));
                break;
            case R.id.infection_report:
                reportNewCoronavirusInfection(mAuth.getCurrentUser(), 1);
                break;
            case R.id.infection_report_cancel:
                reportNewCoronavirusInfection(mAuth.getCurrentUser(), 0);

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
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        mFirebaseFirestore = FirebaseFirestore.getInstance();

        TracingIntentService.startActionTracing(getBaseContext(), 180, 15);
    }

    private static final int CODE_LENGTH_TO_GENERATE = 11;
    private void reportNewCoronavirusInfection(final FirebaseUser currentUser, int new_coronavirus_infection_flag) {
        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        activityInfo.put("timestamp", Constants.DATE_FORMAT.format(Calendar.getInstance().getTime()));
        activityInfo.put("new_coronavirus_infection_flag", new_coronavirus_infection_flag);

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection(currentUser.getEmail())
                .document("status")
                .set(activityInfo)
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


        if (new_coronavirus_infection_flag == 1) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -7);
            Date timestamp = cal.getTime();
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
                                    Date timestamp = doc.getDate("timestamp");

                                    OpenLocationCode olc = new OpenLocationCode(latitude, longitude, CODE_LENGTH_TO_GENERATE);
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
                                for(DocumentSnapshot doc: snapshot.getDocuments()){
                                    Object timestamp = doc.getData().get(currentUser.getEmail().replace(".", "-"));
                                    if (timestamp == null) {
                                        continue;
                                    }
                                    String locCode = doc.getId();
                                    //Log.d("firebase-store", "locCode = " + locCode);
                                    removeNewCoronavirusInfo(currentUser, locCode);
                                }
                            } else {
                                Log.d("firebase-store", "get failed with ", task.getException());
                            }
                        }
                    });
        }
    }

    private void registNewCoronavirusInfo(FirebaseUser currentUser, String locCode, Date timestamp) {
        // Create a new user with a first and last name
        Map<String, Object> info = new HashMap<>();
        info.put(currentUser.getEmail().replace(".", "-"), timestamp);

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection("corona-infos")
                .document(locCode)
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

    private void removeNewCoronavirusInfo(FirebaseUser currentUser, String locCode) {
        // Create a new user with a first and last name
        Map<String, Object> info = new HashMap<>();
        info.put(currentUser.getEmail().replace(".", "-"), FieldValue.delete());

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection("corona-infos")
                .document(locCode)
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

}
