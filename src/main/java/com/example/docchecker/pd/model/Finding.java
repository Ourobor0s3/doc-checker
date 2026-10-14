package com.example.docchecker.pd.model;

/**
 * Найденный признак персональных данных.
 *
 * @param value   значение (по умолчанию маскированное)
 * @param filePath путь к файлу, включая путь внутри архива
 * @param locator место внутри файла: страница, абзац, ячейка, JSON-путь и т. п.
 * @param weak    true, если уверенность ниже порога подтверждения
 */
public record Finding(
        PdType type,
        String label,
        String value,
        String filePath,
        String locator,
        String detector,
        double confidence,
        boolean weak,
        LegalBasis legal) {}
