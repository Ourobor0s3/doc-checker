package com.example.docchecker.pd.extract;

/** Ошибка разбора файла с сообщением, пригодным для показа пользователю. */
public class ExtractException extends RuntimeException {
    public ExtractException(String message) {
        super(message);
    }
}
