package com.example.docchecker.llm;

import com.example.docchecker.common.ApiException;
import org.springframework.http.HttpStatus;

public class OllamaException extends ApiException {

    public OllamaException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
