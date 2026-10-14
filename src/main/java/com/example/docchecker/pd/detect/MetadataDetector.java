package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Метаданные файла: автор/редактор документа, геопозиция снимка. */
public class MetadataDetector {

    private static final Set<String> GENERIC = Set.of("admin", "administrator", "user", "owner", "unknown", "pc",
            "microsoft office user", "microsoft corporation", "microsoft", "windows user", "python-docx", "apache poi",
            "libreoffice", "openoffice", "author", "автор", "пользователь", "администратор", "default", "anonymous",
            "adobe", "word", "wps office", "canon", "nikon", "sony", "samsung", "apple", "root", "guest", "none");
    private static final Pattern ORG = Pattern.compile("(?iu)\\b(?:ltd|llc|inc|corp|gmbh|ооо|оао|зао|пао|ао|ип)\\b");

    public List<RawMatch> detect(String key, String value) {
        if (value == null || value.isBlank() || key == null) {
            return List.of();
        }
        String v = value.strip();
        if (key.equals("gps")) {
            return List.of(new RawMatch(PdType.GEO_LOCATION, 0, v.length(), v, 0.6, "METADATA"));
        }
        if (plausibleName(v)) {
            return List.of(new RawMatch(PdType.DOCUMENT_AUTHOR, 0, v.length(), v, 0.65, "METADATA"));
        }
        return List.of();
    }

    static boolean plausibleName(String v) {
        String low = v.toLowerCase(Locale.ROOT);
        if (v.length() < 3 || v.length() > 80 || GENERIC.contains(low) || ORG.matcher(v).find()
                || !v.matches(".*\\p{L}.*")) {
            return false;
        }
        String[] words = v.trim().split("\\s+");
        if (words.length >= 2) {
            for (String w : words) {
                if (!Character.isLetter(w.charAt(0))) {
                    return false;
                }
            }
            return true;
        }
        return v.length() >= 4 && v.matches("\\p{Lu}\\p{Ll}+") ;
    }
}
