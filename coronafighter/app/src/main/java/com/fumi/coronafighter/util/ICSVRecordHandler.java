package com.fumi.coronafighter.util;

import org.apache.commons.csv.CSVRecord;

public interface ICSVRecordHandler {
    void proc(CSVRecord record);
}
