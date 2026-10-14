package com.example.docchecker.service;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.domain.CheckJob;
import com.example.docchecker.domain.RequirementSet;
import com.example.docchecker.extract.FileType;
import com.example.docchecker.extract.FileTypeDetector;
import com.example.docchecker.storage.CheckJobRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class CheckService {

    private final RequirementService requirements;
    private final FileTypeDetector detector;
    private final CheckJobRepository jobs;
    private final CheckRunner runner;
    private final ThreadPoolTaskExecutor executor;

    public CheckService(RequirementService requirements, FileTypeDetector detector, CheckJobRepository jobs,
                        CheckRunner runner, @Qualifier("checkExecutor") ThreadPoolTaskExecutor executor) {
        this.requirements = requirements;
        this.detector = detector;
        this.jobs = jobs;
        this.runner = runner;
        this.executor = executor;
    }

    /** Валидирует запрос, ставит проверку в очередь и сразу возвращает задачу в статусе PENDING. */
    public CheckJob submit(UploadedFile file, String requirementSetId) {
        RequirementSet set = requirements.get(requirementSetId);
        FileType type = detector.detect(file.data());

        CheckJob job = jobs.save(CheckJob.pending(UUID.randomUUID().toString(), set.id(),
                file.name(), type.name(), set.requirements().size()));
        try {
            executor.execute(() -> runner.run(job, set, file.data(), type));
        } catch (TaskRejectedException e) {
            jobs.delete(job.id());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Очередь проверок переполнена, повторите запрос позже");
        }
        return job;
    }

    public CheckJob get(String id) {
        return jobs.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Проверка не найдена (или результат уже удалён по TTL): " + id));
    }
}
