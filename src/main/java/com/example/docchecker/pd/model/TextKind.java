package com.example.docchecker.pd.model;

public enum TextKind {
    /** Обычный текст. */
    TEXT,
    /** Значение поля/ячейки; {@code hint} — название поля или столбца. */
    FIELD,
    /** Название поля, столбца или ключа (проверяется на «поле для ПД»). */
    HEADER,
    /** Метаданные файла; {@code hint} — ключ (author, gps, ...). */
    META
}
