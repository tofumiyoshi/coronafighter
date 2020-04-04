package com.fumi.coronafighter.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ZipFileHandlerUtil {
    //	全角となっている町域部分の文字数が38文字を越える場合、また半角となっているフリガナ部分の文字数が76文字を越える場合は、複数レコードに分割しています。
    //	この郵便番号データファイルでは、以下の順に配列しています。
    //	全国地方公共団体コード（JIS X0401、X0402）………　半角数字
    //	（旧）郵便番号（5桁）………………………………………　半角数字
    //	郵便番号（7桁）………………………………………　半角数字
    //	都道府県名　…………　半角カタカナ（コード順に掲載）　（注1）
    //	市区町村名　…………　半角カタカナ（コード順に掲載）　（注1）
    //	町域名　………………　半角カタカナ（五十音順に掲載）　（注1）
    //	都道府県名　…………　漢字（コード順に掲載）　（注1,2）
    //	市区町村名　…………　漢字（コード順に掲載）　（注1,2）
    //	町域名　………………　漢字（五十音順に掲載）　（注1,2）
    //	一町域が二以上の郵便番号で表される場合の表示　（注3）　（「1」は該当、「0」は該当せず）
    //	小字毎に番地が起番されている町域の表示　（注4）　（「1」は該当、「0」は該当せず）
    //	丁目を有する町域の場合の表示　（「1」は該当、「0」は該当せず）
    //	一つの郵便番号で二以上の町域を表す場合の表示　（注5）　（「1」は該当、「0」は該当せず）
    //	更新の表示（注6）（「0」は変更なし、「1」は変更あり、「2」廃止（廃止データのみ使用））
    //	変更理由　（「0」は変更なし、「1」市政・区政・町政・分区・政令指定都市施行、「2」住居表示の実施、「3」区画整理、「4」郵便区調整等、「5」訂正、「6」廃止（廃止データのみ使用））
    public static void parse(String filename, ICSVRecordHandler handler) throws IOException {
        ZipFile zf = new ZipFile(filename);
        Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement();

            // System.out.println(entry.getName());
            if (entry.isDirectory() || (!entry.getName().endsWith("CSV") && !entry.getName().endsWith("csv"))) {
                continue;
            }

            try (InputStream is = zf.getInputStream(entry)) {
                Reader reader = new InputStreamReader(is, "SJIS");
                BufferedReader br = new BufferedReader(reader);

                Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(br);
                for (CSVRecord record : records) {
                    if(record.get(0) == null || record.get(0).length() < 2) {
                        continue;
                    }

                    if (handler != null) {
                        handler.proc(record);
                    }
                }
            }
        }
        zf.close();
    }

    public static void parse(InputStream inputStream, ICSVRecordHandler handler) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(inputStream)) {
            // zip内のファイルがなくなるまで読み続ける
            ZipEntry entry = zipIn.getNextEntry();
            while (null != entry) {
                if (entry.isDirectory() || (!entry.getName().endsWith("CSV") && !entry.getName().endsWith("csv"))) {
                    entry = zipIn.getNextEntry();
                    continue;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(zipIn, "Shift_JIS"));
                Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(reader);
                for (CSVRecord record : records) {
                    if(record.get(0) == null || record.get(0).length() < 2) {
                        continue;
                    }

                    if (handler != null) {
                        handler.proc(record);
                    }
                }

                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }
}
