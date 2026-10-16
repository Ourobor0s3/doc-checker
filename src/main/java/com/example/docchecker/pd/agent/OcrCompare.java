package com.example.docchecker.pd.agent;

import com.example.docchecker.pd.model.PdType;
import java.util.Locale;

/** Сверка значения, прочитанного с изображения, с независимым вторым чтением того же изображения. */
public final class OcrCompare {

    private OcrCompare() {}

    public static boolean confirmed(PdType type, String value, String secondReading) {
        if (secondReading == null || secondReading.isBlank()) {
            return false;
        }
        switch (type) {
            case SNILS, INN, PASSPORT_RF, DRIVER_LICENSE, BANK_CARD, PHONE, BIRTH_DATE, OMS_POLICY:
                String d = digits(value);
                return d.length() < 4 || digits(secondReading).contains(d);
            default:
                String a = alnum(value);
                return a.length() < 4 || alnum(secondReading).contains(a);
        }
    }

    static String digits(String s) {
        return s.replaceAll("\\D", "");
    }

    static String alnum(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
