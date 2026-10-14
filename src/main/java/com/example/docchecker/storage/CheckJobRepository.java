package com.example.docchecker.storage;

import com.example.docchecker.domain.CheckJob;
import java.util.Optional;

public interface CheckJobRepository {
    CheckJob save(CheckJob job);

    Optional<CheckJob> findById(String id);

    void delete(String id);
}
