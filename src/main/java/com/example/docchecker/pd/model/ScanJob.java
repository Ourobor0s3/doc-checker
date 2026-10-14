package com.example.docchecker.pd.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Состояние сканирования. Изменяется только потоком-исполнителем; в хранилище попадает копия. */
public class ScanJob {
    public String id;
    public String name;
    public ScanStatus status = ScanStatus.PENDING;
    public boolean llmUsed;
    public Instant createdAt = Instant.now();
    public Instant finishedAt;
    public int discoveredFiles;
    public int processedFiles;
    public String currentFile;
    public int yes;
    public int no;
    public int undetermined;
    public ScanSummary summary;
    public List<String> warnings = new ArrayList<>();
    public String error;
    public String disclaimer;

    public ScanJob() {}
}
