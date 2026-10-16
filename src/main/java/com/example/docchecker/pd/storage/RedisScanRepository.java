package com.example.docchecker.pd.storage;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.ScanJob;
import com.example.docchecker.storage.JsonCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "redis", matchIfMissing = true)
public class RedisScanRepository implements ScanRepository {

    private static final String PREFIX = "docchecker:scan:";
    private static final String INDEX = "docchecker:scans";

    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final Duration ttl;

    public RedisScanRepository(StringRedisTemplate redis, JsonCodec json, AppProperties props) {
        this.redis = redis;
        this.json = json;
        this.ttl = props.pd().resultTtl();
    }

    @Override
    public void saveJob(ScanJob job) {
        redis.opsForValue().set(PREFIX + job.id, json.write(job), ttl);
        redis.opsForZSet().add(INDEX, job.id, job.createdAt.toEpochMilli());
    }

    @Override
    public Optional<ScanJob> findJob(String id) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + id)).map(v -> json.read(v, ScanJob.class));
    }

    @Override
    public void appendFile(String scanId, FileReport report) {
        String key = PREFIX + scanId + ":files";
        redis.opsForList().rightPush(key, json.write(report));
        redis.expire(key, ttl);
    }

    @Override
    public List<FileReport> findAllFiles(String scanId) {
        List<String> raw = redis.opsForList().range(PREFIX + scanId + ":files", 0, -1);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(v -> json.read(v, FileReport.class)).toList();
    }

    @Override
    public List<ScanJob> listJobs(int limit) {
        // записи старше срока хранения уже удалены Redis по TTL: убираем их из индекса
        redis.opsForZSet().removeRangeByScore(INDEX, 0, Instant.now().minus(ttl).toEpochMilli());
        Set<String> ids = redis.opsForZSet().reverseRange(INDEX, 0, Math.max(0, limit - 1));
        List<ScanJob> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            Optional<ScanJob> job = findJob(id);
            if (job.isPresent()) {
                result.add(job.get());
            } else {
                redis.opsForZSet().remove(INDEX, id);
            }
        }
        return result;
    }

    @Override
    public void delete(String scanId) {
        redis.delete(List.of(PREFIX + scanId, PREFIX + scanId + ":files"));
        redis.opsForZSet().remove(INDEX, scanId);
    }
}
