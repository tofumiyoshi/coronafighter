package com.fumi.coronafighter.ui.dashboard;

import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.CurrentPositionViewModel;
import com.fumi.coronafighter.R;
import com.fumi.coronafighter.SettingInfos;
import com.fumi.coronafighter.firebase.FireStore;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.openlocationcode.OpenLocationCode;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class DashboardFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "DashboardFragment";
    private CurrentPositionViewModel mViewModel;

    public FirebaseAuth mAuth;
    private GoogleMap mMap;
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        mAuth = FirebaseAuth.getInstance();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();

        refreshInflectionAreas(FireStore.currentLocation);
    }

    @Override
    public void onStop() {
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
                    FireStore.refreshInflectionAreas();
                }
            });

            mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                @Override
                public void onMapLongClick(final LatLng latLng) {
                    final List<String> locCodes = FireStore.findExistInAlertAreasCode(latLng);
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

                                        FireStore.refreshInflectionAreasTime = null;
                                        new FireStore.InflectionReportTask().execute(Integer.toString(6), locCode);
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
                                        Date date1 = Calendar.getInstance().getTime();
                                        Timestamp timestamp = new Timestamp(date1);

                                        String[] args = new String[locCodes.size() + 1];
                                        args[0] = Integer.toString(5);
                                        for (int i=0; i<locCodes.size(); i++) {
                                            args[i+1] = locCodes.get(i);
                                        }
                                        FireStore.refreshInflectionAreasTime = null;
                                        new FireStore.InflectionReportTask().execute(args);
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

        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
        // Use the ViewModel
        mViewModel.getSelected().observe(this, new Observer<Location>() {
            @Override
            public void onChanged(final Location location) {
                refreshInflectionAreas(location);
            }
        });

        mViewModel.getAlertAreas().observe(this, new Observer<Collection<WeightedLatLng>>() {
            @Override
            public void onChanged(final Collection<WeightedLatLng> alertAreas) {
                if (mMap == null) {
                    return;
                }

                setHeatMap(alertAreas);
            }
        });
    }

    private void refreshInflectionAreas(Location location) {
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

        FireStore.currentLocation = location;
        FireStore.refreshInflectionAreas();
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
}
