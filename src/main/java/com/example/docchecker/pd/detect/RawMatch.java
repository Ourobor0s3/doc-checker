package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;

/** Сырая находка детектора: позиции относятся к анализируемой строке. */
public record RawMatch(PdType type, int start, int end, String value, double confidence, String detector) {
    public int length() {
        return end - start;
    }
}
