package com.example.docchecker.pd.service;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.config.AppProperties;
import com.example.docchecker.pd.legal.LegalReference;
import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.FileVerdict;
import com.example.docchecker.pd.model.Finding;
import com.example.docchecker.pd.model.PageResult;
import com.example.docchecker.pd.model.ScanJob;
import com.example.docchecker.pd.storage.ScanRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ScanService {

    private final ScanRepository repository;
    private final ScanRunner runner;
    private final ThreadPoolTaskExecutor executor;
    private final LegalReference legal;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public ScanService(ScanRepository repository, ScanRunner runner, @Qualifier("scanExecutor") ThreadPoolTaskExecutor executor,
                       LegalReference legal, ObjectMapper mapper, AppProperties props) {
        this.repository = repository;
        this.runner = runner;
        this.executor = executor;
        this.legal = legal;
        this.mapper = mapper;
        this.props = props;
    }

    /** Ставит сканирование в очередь. Временный файл {@code upload} удаляется после обработки. */
    public ScanJob submit(Path upload, String originalName, boolean useLlm) {
        ScanJob job = new ScanJob();
        job.id = UUID.randomUUID().toString();
        job.name = originalName == null || originalName.isBlank() ? "upload" : originalName;
        job.llmUsed = useLlm && props.pd().llmEnabled();
        job.disclaimer = legal.disclaimer();
        repository.saveJob(job);
        try {
            executor.execute(() -> runner.run(job, upload, job.name, useLlm));
        } catch (TaskRejectedException e) {
            repository.delete(job.id);
            try {
                Files.deleteIfExists(upload);
            } catch (IOException ignored) {
                // временный файл удалится системой
            }
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Очередь сканирований переполнена, повторите запрос позже");
        }
        return job;
    }

    /** История: последние сканирования, новые первыми. */
    public List<ScanJob> recent(int limit) {
        return repository.listJobs(Math.max(1, Math.min(limit, 100)));
    }

    public ScanJob get(String id) {
        return repository.findJob(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Сканирование не найдено (или результат удалён по TTL): " + id));
    }

    public PageResult<FileReport> files(String id, FileVerdict verdict, String query, int page, int size) {
        get(id);
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<FileReport> filtered = repository.findAllFiles(id).stream()
                .filter(f -> verdict == null || f.verdict() == verdict)
                .filter(f -> q.isEmpty() || f.path().toLowerCase(Locale.ROOT).contains(q))
                .toList();
        int s = Math.max(1, Math.min(size, 500));
        int p = Math.max(0, page);
        int from = Math.min(filtered.size(), p * s);
        int to = Math.min(filtered.size(), from + s);
        return new PageResult<>(filtered.subList(from, to), filtered.size(), p, s);
    }

    public void delete(String id) {
        get(id);
        repository.delete(id);
    }

    public byte[] exportJson(String id) {
        ScanJob job = get(id);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("disclaimer", legal.disclaimer());
        doc.put("scan", job);
        doc.put("files", repository.findAllFiles(id));
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сформировать JSON");
        }
    }

    /** CSV для Excel: UTF-8 с BOM, разделитель «;», одна строка на находку (и одна на файл без находок). */
    public byte[] exportCsv(String id) {
        get(id);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(row("Файл", "Вердикт", "Статус", "Тип признака", "Значение", "Место в файле", "Уверенность",
                "Метод", "Норма 152-ФЗ", "Объяснение", "Примечания"));
        for (FileReport f : repository.findAllFiles(id)) {
            String notes = String.join(" | ", f.notes());
            if (f.findings().isEmpty()) {
                sb.append(row(f.path(), f.verdict().name(), f.status().name(), "", "", "", "", "", "", "", notes));
                continue;
            }
            for (Finding x : f.findings()) {
                sb.append(row(f.path(), f.verdict().name(), f.status().name(), x.label(), x.value(), x.locator(),
                        String.valueOf(x.confidence()), x.detector(), x.legal().article(), x.legal().explanation(), notes));
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            String c = cells[i] == null ? "" : cells[i];
            if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0) {
                c = "'" + c; // защита от формул при открытии в Excel
            }
            sb.append('"').append(c.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }
}
