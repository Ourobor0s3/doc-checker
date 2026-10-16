package com.example.docchecker.pd.storage;

import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.ScanJob;
import java.util.List;
import java.util.Optional;

/** Задача сканирования хранится отдельно от отчётов по файлам, чтобы не перезаписывать большой объём при каждом файле. */
public interface ScanRepository {

    void saveJob(ScanJob job);

    Optional<ScanJob> findJob(String id);

    void appendFile(String scanId, FileReport report);

    List<FileReport> findAllFiles(String scanId);

    /** Последние сканирования, новые первыми. */
    List<ScanJob> listJobs(int limit);

    void delete(String scanId);
}
