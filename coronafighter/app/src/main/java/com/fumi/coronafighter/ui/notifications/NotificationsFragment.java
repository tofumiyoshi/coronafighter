package com.fumi.coronafighter.ui.notifications;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.fumi.coronafighter.AlarmInfo;
import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.MainActivity;
import com.fumi.coronafighter.R;
import com.fumi.coronafighter.SettingInfos;
import com.fumi.coronafighter.Utils;
import com.fumi.coronafighter.firebase.FireStore;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.maps.android.projection.SphericalMercatorProjection;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class NotificationsFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = NotificationsFragment.class.getName();

    private List<AlarmInfo> mAlarminfos = new ArrayList<AlarmInfo>();

    public FirebaseAuth mAuth;
    private GoogleMap mMap;
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;
    AlarmTask mAlarmTask = null;

    @Override
    public void onCreate(@NonNull Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View root = inflater.inflate(R.layout.fragment_notifications, container, false);

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
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_notification, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                ((MainActivity)getActivity()).signOut();
                break;
            case R.id.refresh_inflection_areas:
                if (mAlarmTask != null && mAlarmTask.getStatus() == AsyncTask.Status.FINISHED ) {
                    mAlarmTask = new AlarmTask();
                    mAlarmTask.execute();
                }

                break;
        }

        return super.onOptionsItemSelected(item);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        mAlarmTask = new AlarmTask();
        mAlarmTask.execute();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getContext());
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location location = task.getResult();
                    refreshLocation(location);
                }
            });
        }
    }

    private void refreshLocation(Location location) {
        if (mMap == null || location == null) {
            Log.i(TAG, "No value in map or location of CurrentPositionViewModel's on change.");
            return;
        }

        float zoom = mMap.getCameraPosition().zoom;
        if (zoom < SettingInfos.map_min_zoom) {
            zoom = SettingInfos.map_default_zoom;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraUpdate camera = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            // カメラの位置に移動
            mMap.moveCamera(camera);
        }
    }

    public void setAlarmAreas(List<AlarmInfo> alarminfos) {
        if (mMap == null || alarminfos == null) {
            return;
        }

        if (mOverlay != null) {
            mOverlay.remove();
        }
        if (alarminfos.size() == 0) {
            return;
        }

        ArrayList<WeightedLatLng> infos = new ArrayList<WeightedLatLng>();
        for (AlarmInfo info: alarminfos) {
            LatLng latLng = new LatLng(info.getLatitude(), info.getLongitude());
            double intensity = info.getIntensity();

            WeightedLatLng weightedLatLng = new WeightedLatLng(latLng, intensity);

            //String str = String.format("%+10.4f, %+10.4f, %+10.4f", weightedLatLng.getPoint().x, weightedLatLng.getPoint().y, weightedLatLng.getIntensity());
            //Log.e(TAG, "weightedLatLng = " + str);

            infos.add(weightedLatLng);
        }

        mProvider = new HeatmapTileProvider.Builder()
                .weightedData(infos)
                .build();
        // Add a tile overlay to the map, using the heat map tile provider.
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }

    public static Date refreshAlarmAreasTime = null;

    public class AlarmTask extends AsyncTask<Void, Void, ArrayList<AlarmInfo>> {
        @Override
        protected ArrayList<AlarmInfo> doInBackground(Void... voids) {
            Log.d(TAG, "doInBackground ...");

            final ArrayList<AlarmInfo> res = new ArrayList<AlarmInfo>();

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                return res;
            }

            try {
                Collection<WeightedLatLng> inflectionAreas = FireStore.getInflectionAreas();
                Log.i(TAG, "[inflectionAreas info] cnt: " + inflectionAreas.size());
                if (inflectionAreas.size() == 0) {
                    return res;
                }

                ArrayList<AlarmInfo> traceInfos = FireStore.getTraceInfos(user);
                Log.i(TAG, "[trace info] cnt: " + traceInfos.size());
                if (traceInfos.size() == 0) {
                    return res;
                }

                SphericalMercatorProjection sProjection = new SphericalMercatorProjection(1.0D);
                for (WeightedLatLng weightedLatLng : inflectionAreas) {
                    LatLng latLng = sProjection.toLatLng(weightedLatLng.getPoint());
                    OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                            Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                    String code = olc.getCode();

                    for (AlarmInfo info: traceInfos) {
                        if (code.equals(info.getLocCode())) {
                            info.setIntensity(info.getCnt() * weightedLatLng.getIntensity());

                            Log.i(TAG, "add to alarm areas: " + info.getLocCode() + "(" + info.getIntensity() + ")");
                            res.add(info);
                            break;
                        }
                    }
                }
            } catch (ExecutionException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                // delete past data not used
                Calendar cal2 = Calendar.getInstance();
                cal2.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
                Date time2 = cal2.getTime();
                if (refreshAlarmAreasTime != null && refreshAlarmAreasTime.before(time2)) {
                    FireStore.maintainace();
                }
            }

            Log.i(TAG, "Task finished.");
            return res;
        }

        @Override
        protected void onPostExecute(ArrayList<AlarmInfo> infos) {
            Log.d(TAG, "onPostExecute ...");

            if (mMap == null || infos == null) {
                Log.i(TAG, "No value in map or infos of AlarmAreasViewModel's on change.");
                return;
            }
            Log.i(TAG, "Count of AlarmInfo: " + infos.size());

            mAlarminfos.clear();
            mAlarminfos.addAll(infos);
            setAlarmAreas(mAlarminfos);
        }
    }
}
