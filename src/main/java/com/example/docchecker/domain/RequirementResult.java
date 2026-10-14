package com.example.docchecker.domain;

public record RequirementResult(
        String requirementId,
        String requirementText,
        Verdict verdict,
        String evidence,
        String comment) {}
