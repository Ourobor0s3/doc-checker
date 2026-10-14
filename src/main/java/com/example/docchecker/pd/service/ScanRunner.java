package com.example.docchecker.pd.service;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.pd.agent.ScanAgent;
import com.example.docchecker.pd.legal.LegalReference;
import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.FileStatus;
import com.example.docchecker.pd.model.FileVerdict;
import com.example.docchecker.pd.model.ScanJob;
import com.example.docchecker.pd.model.ScanStatus;
import com.example.docchecker.pd.model.ScanSummary;
import com.example.docchecker.pd.model.PdType;
import com.example.docchecker.pd.storage.ScanRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Выполняет сканирование в потоке очереди: обход архива, проверка каждого файла агентом, учёт прогресса. */
@Component
public class ScanRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanRunner.class);

    private final ArchiveWalker walker;
    private final ScanAgent agent;
    private final ScanRepository repository;
    private final LegalReference legal;
    private final AppProperties.Pd cfg;

    public ScanRunner(ArchiveWalker walker, ScanAgent agent, ScanRepository repository, LegalReference legal,
                      AppProperties props) {
        this.walker = walker;
        this.agent = agent;
        this.repository = repository;
        this.legal = legal;
        this.cfg = props.pd();
    }

    public void run(ScanJob job, Path upload, String originalName, boolean useLlm) {
        try {
            job.status = ScanStatus.RUNNING;
            repository.saveJob(job);

            ArchiveWalker.Listener listener = new ArchiveWalker.Listener() {
                @Override
                public void discovered(int delta) {
                    job.discoveredFiles += delta;
                }

                @Override
                public void file(String path, byte[] data) {
                    job.currentFile = path;
                    repository.saveJob(job);
                    record(job, agent.scan(path, data, useLlm));
                }

                @Override
                public void skipped(String path, long size, String reason) {
                    record(job, new FileReport(path, "UNKNOWN", size, FileStatus.ERROR, FileVerdict.UNDETERMINED, 0, 0,
                            Map.of(), List.of(), List.of(reason), List.of(), 0));
                }

                @Override
                public void warning(String message) {
                    job.warnings.add(message);
                }
            };

            if (walker.isArchive(upload)) {
                walker.walk(upload, originalName, listener);
            } else {
                long size = Files.size(upload);
                listener.discovered(1);
                if (size > cfg.maxEntryBytes()) {
                    listener.skipped(originalName, size, "Файл больше допустимого размера (" + cfg.maxEntryBytes() / (1024 * 1024) + " МБ)");
                } else {
                    listener.file(originalName, Files.readAllBytes(upload));
                }
            }
            job.summary = summarize(repository.findAllFiles(job.id));
            job.status = ScanStatus.COMPLETED;
            log.info("Сканирование {} завершено: файлов {}, с ПД {}", job.id, job.processedFiles, job.yes);
        } catch (Exception e) {
            log.error("Сканирование {} завершилось ошибкой", job.id, e);
            job.status = ScanStatus.FAILED;
            job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } catch (Error e) {
            log.error("Сканирование {} прервано ошибкой JVM/classpath", job.id, e);
            job.status = ScanStatus.FAILED;
            job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            job.currentFile = null;
            job.finishedAt = Instant.now();
            repository.saveJob(job);
            try {
                Files.deleteIfExists(upload);
            } catch (IOException e) {
                log.warn("Не удалось удалить временный файл {}", upload);
            }
        }
    }

    private void record(ScanJob job, FileReport report) {
        repository.appendFile(job.id, report);
        job.processedFiles++;
        switch (report.verdict()) {
            case YES -> job.yes++;
            case NO -> job.no++;
            case UNDETERMINED -> job.undetermined++;
        }
        repository.saveJob(job);
    }

    ScanSummary summarize(List<FileReport> files) {
        int yes = 0;
        int no = 0;
        int undetermined = 0;
        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> byArticle = new TreeMap<>();
        for (FileReport f : files) {
            switch (f.verdict()) {
                case YES -> yes++;
                case NO -> no++;
                case UNDETERMINED -> undetermined++;
            }
            for (Map.Entry<String, Integer> e : f.findingCounts().entrySet()) {
                byType.merge(e.getKey(), e.getValue(), Integer::sum);
                String article = legal.basis(PdType.valueOf(e.getKey())).article();
                byArticle.merge(article, e.getValue(), Integer::sum);
            }
        }
        return new ScanSummary(files.size(), yes, no, undetermined, byType, byArticle);
    }
}
