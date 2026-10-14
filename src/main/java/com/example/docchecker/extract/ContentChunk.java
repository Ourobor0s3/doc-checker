package com.example.docchecker.extract;

import java.util.List;

/** Фрагмент документа, который отправляется модели за один запрос: текст либо изображение. */
public record ContentChunk(String label, String text, List<String> imagesBase64) {

    public static ContentChunk ofText(String label, String text) {
        return new ContentChunk(label, text, List.of());
    }

    public static ContentChunk ofImage(String label, String base64) {
        return new ContentChunk(label, "", List.of(base64));
    }

    public boolean hasImages() {
        return !imagesBase64.isEmpty();
    }
}
