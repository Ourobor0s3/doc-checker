package com.example.docchecker.common;

import org.springframework.http.HttpStatus;

/** Ошибка с HTTP-статусом, которая превращается в ответ API. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
