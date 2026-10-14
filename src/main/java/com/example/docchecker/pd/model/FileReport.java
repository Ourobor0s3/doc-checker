package com.example.docchecker.pd.model;

import java.util.List;
import java.util.Map;

/**
 * Результат по одному файлу.
 *
 * @param findingCounts полное число находок по типам (список {@code findings} может быть усечён)
 */
public record FileReport(
        String path,
        String format,
        long size,
        FileStatus status,
        FileVerdict verdict,
        double maxConfidence,
        int findingsTotal,
        Map<String, Integer> findingCounts,
        List<Finding> findings,
        List<String> notes,
        List<String> trace,
        long durationMs) {}
