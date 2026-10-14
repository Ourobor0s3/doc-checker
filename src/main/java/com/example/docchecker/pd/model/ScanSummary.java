package com.example.docchecker.pd.model;

import java.util.Map;

public record ScanSummary(
        int totalFiles,
        int withPd,
        int withoutPd,
        int undetermined,
        Map<String, Integer> findingsByType,
        Map<String, Integer> findingsByArticle) {}
