package com.example.docchecker.storage;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.domain.CheckJob;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "redis", matchIfMissing = true)
public class RedisCheckJobRepository implements CheckJobRepository {

    private static final String KEY_PREFIX = "docchecker:check:";

    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final AppProperties props;

    public RedisCheckJobRepository(StringRedisTemplate redis, JsonCodec json, AppProperties props) {
        this.redis = redis;
        this.json = json;
        this.props = props;
    }

    @Override
    public CheckJob save(CheckJob job) {
        redis.opsForValue().set(KEY_PREFIX + job.id(), json.write(job), props.check().resultTtl());
        return job;
    }

    @Override
    public Optional<CheckJob> findById(String id) {
        String value = redis.opsForValue().get(KEY_PREFIX + id);
        return Optional.ofNullable(value).map(v -> json.read(v, CheckJob.class));
    }

    @Override
    public void delete(String id) {
        redis.delete(KEY_PREFIX + id);
    }
}
