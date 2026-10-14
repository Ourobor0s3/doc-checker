package com.example.docchecker.storage;

import com.example.docchecker.domain.CheckJob;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
public class MemoryCheckJobRepository implements CheckJobRepository {

    private final Map<String, CheckJob> data = new ConcurrentHashMap<>();

    @Override
    public CheckJob save(CheckJob job) {
        data.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<CheckJob> findById(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public void delete(String id) {
        data.remove(id);
    }
}
