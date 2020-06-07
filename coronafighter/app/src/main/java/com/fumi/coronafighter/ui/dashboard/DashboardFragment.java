package com.fumi.coronafighter.ui.dashboard;

import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.MainActivity;
import com.fumi.coronafighter.R;
import com.fumi.coronafighter.SettingInfos;
import com.fumi.coronafighter.firebase.FireStore;
import com.fumi.coronafighter.firebase.GetInflectionAreasAsyncTask;
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
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.openlocationcode.OpenLocationCode;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DashboardFragment extends Fragment implements OnMapReadyCallback, GetInflectionAreasAsyncTask.Callback {
    private static final String TAG = "DashboardFragment";

    public FirebaseAuth mAuth;
    private GoogleMap mMap;
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private LocationRequest mLocationRequest;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;
    LocationCallback mLocationCallback = null;

    private FirebaseFirestore mFirebaseFirestore;
    private ListenerRegistration mListenerStatus;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        setHasOptionsMenu(true);

        mAuth = FirebaseAuth.getInstance();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getContext());
        mLocationCallback = new LocationCallback(){
                                        public void onLocationResult(LocationResult result) {
                                            Location location = result.getLastLocation();
                                            if (location != null) {
                                                refreshLocation(location);
                                            }
                                        }
                                    };
        createLocationRequest();

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();

        requestLocationUpdates();
    }

    @Override
    public void onStop() {
        removeLocationUpdates();

        super.onStop();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        try {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    refreshInflectionAreas(latLng);
                }
            });

            mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                @Override
                public void onMapLongClick(final LatLng latLng) {
                    final List<String> locCodes = FireStore.findExistInAlertAreasCode(latLng, mWeightedLatLngs);
                    if (locCodes == null || locCodes.size() == 0) {
                        new AlertDialog.Builder(getActivity())
                                .setTitle(R.string.infection_report)
                                .setMessage(R.string.infection_report_confirm_msg)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                                                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                                        String locCode = olc.getCode();

                                        FireStore.registNewCoronavirusInfo(mAuth.getCurrentUser(), locCode);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                    else {
                        new AlertDialog.Builder(getActivity())
                                .setTitle(R.string.infection_area_delete)
                                .setMessage(R.string.infection_area_delete_msg)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        FireStore.removeInflectionInfo(locCodes);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                }
            });
        } catch (Throwable t) {
            Log.i(TAG, t.getMessage(), t);
        }

        Application app = getActivity().getApplication();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
    }

    private void refreshLocation(Location location) {
        if (mMap == null || location == null) {
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

        refreshInflectionAreas(location);
    }

    public void setHeatMap(Collection list) {
        if (mOverlay != null) {
            mOverlay.remove();
        }

        if (list == null || list.size() == 0) {
            return;
        }

        mProvider = new HeatmapTileProvider.Builder()
                .weightedData(list)
                .build();

        // Add a tile overlay to the map, using the heat map tile provider.
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        // Note: apps running on "O" devices (regardless of targetSdkVersion) may receive updates
        // less frequently than this interval when the app is no longer in the foreground.
        mLocationRequest.setInterval(SettingInfos.tracing_time_interval_second * 1000);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(SettingInfos.refresh_alarm_areas_min_interval_second * 1000);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Sets the maximum time when batched location updates are delivered. Updates may be
        // delivered sooner than this interval.
        mLocationRequest.setMaxWaitTime(SettingInfos.tracing_time_interval_second * 3000);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_dashboard, menu);

        if (FireStore.infection_flag == 0) {
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
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                ((MainActivity)getActivity()).signOut();
                break;
            case R.id.infection_report:
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.infection_report)
                        .setMessage(R.string.infection_report_confirm_msg_by_tracing)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    FireStore.reportNewCoronavirusInfection(mAuth.getCurrentUser(), 1);
                                } catch (ExecutionException e) {
                                    Log.i(TAG, e.getMessage(), e);
                                } catch (InterruptedException e) {
                                    Log.i(TAG, e.getMessage(), e);
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                break;
            case R.id.infection_report_cancel:
                try {
                    FireStore.reportNewCoronavirusInfection(mAuth.getCurrentUser(), 0);
                } catch (ExecutionException e) {
                    Log.i(TAG, e.getMessage(), e);
                } catch (InterruptedException e) {
                    Log.i(TAG, e.getMessage(), e);
                }
                break;

            case R.id.refresh_inflection_areas:
                refreshInflectionAreasTime = null;
                refreshInflectionAreas((LatLng) null);
                break;
        }
        return false;
    }

    /**
     * Handles the Request Updates button and requests start of location updates.
     */
    public void requestLocationUpdates() {
        try {
            Log.i(TAG, "Starting location updates");
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (SecurityException e) {
            Log.i(TAG, e.getMessage(), e);
        }
    }

    /**
     * Handles the Remove Updates button, and requests removal of location updates.
     */
    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    public static Date refreshInflectionAreasTime = null;
    GetInflectionAreasAsyncTask mGetInflectionAreasAsyncTask = null;

    public void refreshInflectionAreas(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        refreshInflectionAreas(latLng);
    }

    public void refreshInflectionAreas(LatLng latLng) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
        Date time1 = cal.getTime();

        if (refreshInflectionAreasTime != null && refreshInflectionAreasTime.after(time1)) {
            return;
        }
        refreshInflectionAreasTime = Calendar.getInstance().getTime();

        mGetInflectionAreasAsyncTask = new GetInflectionAreasAsyncTask(this);
        mGetInflectionAreasAsyncTask.execute();
    }

    Collection<WeightedLatLng> mWeightedLatLngs = null;
    @Override
    public void setInflectionAreas(Collection<WeightedLatLng> result) {
        mWeightedLatLngs = result;

        DashboardFragment.this.setHeatMap(result);
    }
}
