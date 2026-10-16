package com.example.docchecker.llm;

/** Модель не ответила за отведённое время. В отличие от недоступности сервера, это может быть единичным случаем. */
public class OllamaTimeoutException extends OllamaException {

    public OllamaTimeoutException(String message) {
        super(message);
    }
}
