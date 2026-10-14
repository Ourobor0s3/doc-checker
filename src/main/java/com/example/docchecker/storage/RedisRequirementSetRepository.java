package com.example.docchecker.storage;

import com.example.docchecker.domain.RequirementSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "redis", matchIfMissing = true)
public class RedisRequirementSetRepository implements RequirementSetRepository {

    private static final String KEY_PREFIX = "docchecker:reqset:";
    private static final String INDEX_KEY = "docchecker:reqsets";

    private final StringRedisTemplate redis;
    private final JsonCodec json;

    public RedisRequirementSetRepository(StringRedisTemplate redis, JsonCodec json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public RequirementSet save(RequirementSet set) {
        redis.opsForValue().set(KEY_PREFIX + set.id(), json.write(set));
        redis.opsForSet().add(INDEX_KEY, set.id());
        return set;
    }

    @Override
    public Optional<RequirementSet> findById(String id) {
        String value = redis.opsForValue().get(KEY_PREFIX + id);
        return Optional.ofNullable(value).map(v -> json.read(v, RequirementSet.class));
    }

    @Override
    public List<RequirementSet> findAll() {
        Set<String> ids = redis.opsForSet().members(INDEX_KEY);
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(RequirementSet::createdAt))
                .toList();
    }

    @Override
    public boolean delete(String id) {
        Boolean deleted = redis.delete(KEY_PREFIX + id);
        redis.opsForSet().remove(INDEX_KEY, id);
        return Boolean.TRUE.equals(deleted);
    }
}
