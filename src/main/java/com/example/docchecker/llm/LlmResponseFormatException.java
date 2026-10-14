package com.example.docchecker.llm;

/** Модель ответила, но не в ожидаемом JSON-формате (после повторной попытки). */
public class LlmResponseFormatException extends OllamaException {

    public LlmResponseFormatException(String message) {
        super(message);
    }
}
