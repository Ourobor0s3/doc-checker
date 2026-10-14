package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.Locale;

/** Маскирование найденных значений: в отчёте и в хранилище не должны оседать реальные персональные данные. */
public final class Masker {

    private Masker() {}

    public static String mask(PdType type, String v) {
        if (v == null) {
            return "";
        }
        switch (type) {
            case SNILS, INN, PASSPORT_RF, DRIVER_LICENSE, PHONE:
                return keep(v, 0, 2);
            case BANK_CARD:
                return keep(v, 0, 4);
            case BIRTH_DATE:
                return keep(v, 0, 4);
            case EMAIL:
                return maskEmail(v);
            case FULL_NAME, DOCUMENT_AUTHOR:
                return maskWords(v, 1);
            case GEO_LOCATION:
                return roundCoordinates(v);
            case FIELD_NAME, FACE_PHOTO:
                return v;
            default:
                return maskWords(v, 2);
        }
    }

    /** Оставляет первые {@code keepStart} и последние {@code keepEnd} букв/цифр, остальные заменяет на «*». */
    static String keep(String v, int keepStart, int keepEnd) {
        int total = 0;
        for (int i = 0; i < v.length(); i++) {
            if (Character.isLetterOrDigit(v.charAt(i))) {
                total++;
            }
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(idx < keepStart || idx >= total - keepEnd ? c : '*');
                idx++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String maskEmail(String v) {
        int at = v.indexOf('@');
        if (at <= 0) {
            return maskWords(v, 1);
        }
        return v.charAt(0) + "***" + v.substring(at);
    }

    /** Каждое слово: первые {@code keep} букв + «***»; цифры заменяются на «*». */
    static String maskWords(String v, int keep) {
        StringBuilder sb = new StringBuilder();
        for (String w : v.trim().split("(?<=\\s)|(?=\\s)")) {
            if (w.isBlank()) {
                sb.append(w);
            } else if (w.length() <= keep + 1 || w.matches("\\p{L}\\.")) {
                sb.append(w.replaceAll("\\d", "*"));
            } else {
                sb.append(w, 0, keep).append("***");
            }
        }
        return sb.toString().replaceAll("\\d", "*");
    }

    private static String roundCoordinates(String v) {
        try {
            String[] parts = v.split(",");
            return String.format(Locale.ROOT, "%.1f, %.1f",
                    Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
        } catch (RuntimeException e) {
            return "***";
        }
    }
}
