package com.example.docchecker.pd.storage;

import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.ScanJob;
import com.example.docchecker.storage.JsonCodec;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Хранит копии в виде JSON, чтобы читатели не видели объекты, которые продолжает менять исполнитель. */
@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
public class MemoryScanRepository implements ScanRepository {

    private final Map<String, String> jobs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> files = new ConcurrentHashMap<>();
    private final JsonCodec json;

    public MemoryScanRepository(JsonCodec json) {
        this.json = json;
    }

    @Override
    public void saveJob(ScanJob job) {
        jobs.put(job.id, json.write(job));
    }

    @Override
    public Optional<ScanJob> findJob(String id) {
        return Optional.ofNullable(jobs.get(id)).map(v -> json.read(v, ScanJob.class));
    }

    @Override
    public void appendFile(String scanId, FileReport report) {
        files.computeIfAbsent(scanId, k -> new CopyOnWriteArrayList<>()).add(json.write(report));
    }

    @Override
    public List<FileReport> findAllFiles(String scanId) {
        return files.getOrDefault(scanId, List.of()).stream().map(v -> json.read(v, FileReport.class)).toList();
    }

    @Override
    public List<ScanJob> listJobs(int limit) {
        return jobs.values().stream().map(v -> json.read(v, ScanJob.class))
                .sorted(Comparator.comparing((ScanJob j) -> j.createdAt).reversed())
                .limit(limit).toList();
    }

    @Override
    public void delete(String scanId) {
        jobs.remove(scanId);
        files.remove(scanId);
    }
}
