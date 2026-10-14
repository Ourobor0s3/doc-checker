package com.example.docchecker.common;

import org.springframework.http.HttpStatus;

/** Файл не удалось прочитать или он не содержит пригодного содержимого. */
public class BadDocumentException extends ApiException {

    public BadDocumentException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
