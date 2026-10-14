package com.example.docchecker.pd.agent;

import java.util.List;

/** Форматы JSON-ответов модели в модуле поиска персональных данных. */
public final class PdAnswers {

    private PdAnswers() {}

    public record EntityItem(String text, String type) {}

    public record EntityAnswer(List<EntityItem> findings) {}

    public record VisionAnswer(String transcribedText, Boolean faceVisible) {}
}
