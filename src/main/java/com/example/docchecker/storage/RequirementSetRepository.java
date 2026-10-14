package com.example.docchecker.storage;

import com.example.docchecker.domain.RequirementSet;
import java.util.List;
import java.util.Optional;

public interface RequirementSetRepository {
    RequirementSet save(RequirementSet set);

    Optional<RequirementSet> findById(String id);

    List<RequirementSet> findAll();

    boolean delete(String id);
}
