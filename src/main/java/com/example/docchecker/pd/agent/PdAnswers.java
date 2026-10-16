package com.example.docchecker.pd.agent;

import java.util.List;

/** Форматы JSON-ответов модели в модуле поиска персональных данных. */
public final class PdAnswers {

    private PdAnswers() {}

    public record EntityItem(String text, String type) {}

    public record EntityAnswer(List<EntityItem> findings) {}

    /** Классификация изображения: что на нём, сколько лиц и какие черты лица видны. */
    public record SceneAnswer(String description, String imageKind, Integer facesCount, String faceEvidence,
                              Boolean hasReadableText) {}

    /** Дословная переписка текста с изображения. */
    public record OcrAnswer(String transcribedText) {}

    /** Независимая проверка: это реальная фотография человека? */
    public record PhotoCheckAnswer(String reason, String verdict) {}
}
