package com.example.docchecker.llm;

import java.util.List;

/** Форматы JSON-ответов модели. */
public final class Answers {

    private Answers() {}

    public record CheckAnswer(String verdict, String evidence, String comment) {}

    public record RequirementsAnswer(List<String> requirements) {}

    public record SummaryAnswer(String summary) {}
}
