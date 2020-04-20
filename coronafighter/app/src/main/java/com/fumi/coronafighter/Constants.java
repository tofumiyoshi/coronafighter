package com.fumi.coronafighter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Constants {
    public final static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public final static DateFormat DATE_FORMAT_SHORT = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    public final static DateFormat DATE_FORMAT_4_NAME = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public static final int OPEN_LOCATION_CODE_LENGTH_TO_GENERATE = 11;
    public static final int OPEN_LOCATION_CODE_LENGTH_TO_COMPARE = 6;
}
