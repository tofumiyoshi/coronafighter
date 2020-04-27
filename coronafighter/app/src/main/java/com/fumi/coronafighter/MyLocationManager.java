package com.fumi.coronafighter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MyLocationManager extends LocationCallback {
    private static final String TAG = MyLocationManager.class.getName();

    private Context context;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private OnLocationResultListener mListener;

    public interface OnLocationResultListener {
        void onLocationResult(LocationResult locationResult);
    }

    public MyLocationManager(Context context, OnLocationResultListener mListener) {
        this.context = context;
        this.mListener = mListener;
        this.fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @Override
    public void onLocationResult(LocationResult locationResult) {
        super.onLocationResult(locationResult);
        mListener.onLocationResult(locationResult);
    }

    public void startLocationUpdates() {
        // パーミッションの確認
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission required.");
            ActivityCompat.requestPermissions((Activity) context, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
            }, Constants.LOCATION_PERMISSION_REQUEST_CODE);

            return;
        }

        // 端末の位置情報サービスが無効になっている場合、設定画面を表示して有効化を促す
        if (!isGPSEnabled()) {
            showLocationSettingDialog();
            return;
        }

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(SettingInfos.tracing_time_interval_second*1000);
        locationRequest.setSmallestDisplacement(SettingInfos.tracing_min_distance_meter);

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, this,null);
    }

    public void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(this);
    }

    private Boolean isGPSEnabled() {
        android.location.LocationManager locationManager = (android.location.LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    private void showLocationSettingDialog() {
        new android.app.AlertDialog.Builder(context)
                .setMessage("設定画面で位置情報サービスを有効にしてください")
                .setPositiveButton("設定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        context.startActivity(intent);
                    }
                })
                .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //NOP
                    }
                })
                .create()
                .show();
    }
}
