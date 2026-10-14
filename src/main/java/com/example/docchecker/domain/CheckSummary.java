package com.example.docchecker.domain;

public record CheckSummary(int total, int passed, int failed, int unknown, Overall overall, String text) {}
