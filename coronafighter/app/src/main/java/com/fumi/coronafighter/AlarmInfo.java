package com.fumi.coronafighter;

import android.os.Parcel;
import android.os.Parcelable;

public class AlarmInfo implements Parcelable {
    private double latitude;
    private double longitude;
    private String locCode;
    private int cnt;
    private double intensity;

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getLocCode() {
        return locCode;
    }

    public void setLocCode(String locCode) {
        this.locCode = locCode;
    }

    public int getCnt() {
        return cnt;
    }

    public void setCnt(int cnt) {
        this.cnt = cnt;
    }

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof AlarmInfo)) {
            return false;
        }
        return (this.locCode.equals(((AlarmInfo)obj).locCode));
    }

    public AlarmInfo () {
    }

    public AlarmInfo (Parcel in) {
        this.latitude = in.readDouble();
        this.longitude = in.readDouble();
        this.locCode = in.readString();
        this.cnt = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(this.latitude);
        dest.writeDouble(this.longitude);
        dest.writeString(this.locCode);
        dest.writeInt(this.cnt);
    }
}