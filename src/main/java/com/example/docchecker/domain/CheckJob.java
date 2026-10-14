package com.example.docchecker.domain;

import java.time.Instant;
import java.util.List;

/** Задача проверки одного файла. Иммутабельна: каждое изменение — новый экземпляр. */
public record CheckJob(
        String id,
        String requirementSetId,
        String fileName,
        String fileType,
        JobStatus status,
        int processed,
        int total,
        List<RequirementResult> results,
        CheckSummary summary,
        List<String> warnings,
        String error,
        Instant createdAt,
        Instant finishedAt) {

    public static CheckJob pending(String id, String requirementSetId, String fileName, String fileType, int total) {
        return new CheckJob(id, requirementSetId, fileName, fileType, JobStatus.PENDING, 0, total,
                List.of(), null, List.of(), null, Instant.now(), null);
    }

    public CheckJob running() {
        return new CheckJob(id, requirementSetId, fileName, fileType, JobStatus.RUNNING, 0, total,
                results, summary, warnings, null, createdAt, null);
    }

    public CheckJob progress(List<RequirementResult> done) {
        return new CheckJob(id, requirementSetId, fileName, fileType, JobStatus.RUNNING, done.size(), total,
                List.copyOf(done), summary, warnings, null, createdAt, null);
    }

    public CheckJob completed(List<RequirementResult> done, CheckSummary summary, List<String> warnings) {
        return new CheckJob(id, requirementSetId, fileName, fileType, JobStatus.COMPLETED, done.size(), total,
                List.copyOf(done), summary, List.copyOf(warnings), null, createdAt, Instant.now());
    }

    public CheckJob failed(String error) {
        return new CheckJob(id, requirementSetId, fileName, fileType, JobStatus.FAILED, processed, total,
                results, summary, warnings, error, createdAt, Instant.now());
    }
}
