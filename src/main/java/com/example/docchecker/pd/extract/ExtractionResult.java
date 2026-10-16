package com.example.docchecker.pd.extract;

import com.example.docchecker.pd.model.TextKind;
import com.example.docchecker.pd.model.TextUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Содержимое файла после извлечения: текстовые фрагменты, изображения для vision-модели и заметки. */
public final class ExtractionResult {

    public record ImageUnit(String locator, String base64Jpeg) {}

    public final List<TextUnit> units = new ArrayList<>();
    public final List<ImageUnit> images = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();
    /** true, если часть содержимого не удалось или не стали анализировать. */
    public boolean partial;

    private final int maxUnits;
    private final int maxImages;
    private boolean unitsNoted;
    private boolean imagesNoted;
    private final Set<String> seenImages = new HashSet<>();

    public ExtractionResult(int maxUnits, int maxImages) {
        this.maxUnits = maxUnits;
        this.maxImages = maxImages;
    }

    public boolean unitsFull() {
        return units.size() >= maxUnits;
    }

    public boolean imagesFull() {
        return images.size() >= maxImages;
    }

    /** Добавляет текст; возвращает false, если достигнут лимит (чтобы вызывающий мог прервать цикл). */
    public boolean text(String locator, String value) {
        return add(locator, value, null, TextKind.TEXT);
    }

    public boolean field(String locator, String value, String hint) {
        return add(locator, value, hint, TextKind.FIELD);
    }

    public void header(String locator, String name) {
        if (name != null && !name.isBlank() && name.length() <= 120) {
            units.add(new TextUnit(locator, name.strip(), null, TextKind.HEADER));
        }
    }

    public void meta(String key, String value) {
        if (value != null && !value.isBlank()) {
            units.add(new TextUnit("метаданные: " + key, value.strip(), key, TextKind.META));
        }
    }

    public void image(String locator, String base64Jpeg) {
        if (!seenImages.add(fingerprint(base64Jpeg))) {
            note("Одинаковые изображения (логотипы, повторы) проанализированы по одному разу");
            return;
        }
        if (imagesFull()) {
            if (!imagesNoted) {
                imagesNoted = true;
                partial("Проанализированы только первые " + maxImages + " изображений файла");
            }
            return;
        }
        images.add(new ImageUnit(locator, base64Jpeg));
    }

    public void note(String message) {
        if (!notes.contains(message)) {
            notes.add(message);
        }
    }

    public void partial(String message) {
        partial = true;
        note(message);
    }

    private boolean add(String locator, String value, String hint, TextKind kind) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (unitsFull()) {
            if (!unitsNoted) {
                unitsNoted = true;
                partial("Достигнут лимит " + maxUnits + " фрагментов: остальное содержимое не проанализировано");
            }
            return false;
        }
        units.add(new TextUnit(locator, value.strip(), hint, kind));
        return true;
    }

    private static String fingerprint(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.ISO_8859_1));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(s.hashCode()) + ':' + s.length();
        }
    }
}
