package com.example.docchecker.pd.extract;

import com.example.docchecker.pd.model.TextKind;
import com.example.docchecker.pd.model.TextUnit;
import java.util.ArrayList;
import java.util.List;

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
}
