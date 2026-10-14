package com.example.docchecker.storage;

import com.example.docchecker.domain.RequirementSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
public class MemoryRequirementSetRepository implements RequirementSetRepository {

    private final Map<String, RequirementSet> data = new ConcurrentHashMap<>();

    @Override
    public RequirementSet save(RequirementSet set) {
        data.put(set.id(), set);
        return set;
    }

    @Override
    public Optional<RequirementSet> findById(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public List<RequirementSet> findAll() {
        return data.values().stream().sorted(Comparator.comparing(RequirementSet::createdAt)).toList();
    }

    @Override
    public boolean delete(String id) {
        return data.remove(id) != null;
    }
}
