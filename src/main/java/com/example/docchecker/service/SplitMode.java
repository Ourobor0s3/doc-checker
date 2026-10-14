package com.example.docchecker.service;

public enum SplitMode {
    /** Требования выделяет LLM (при сбое — автоматический откат на LINES). */
    LLM,
    /** Эвристика: каждая строка или пункт списка — отдельное требование. */
    LINES
}
