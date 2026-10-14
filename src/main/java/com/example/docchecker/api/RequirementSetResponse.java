package com.example.docchecker.api;

import com.example.docchecker.domain.Requirement;
import com.example.docchecker.domain.RequirementSet;
import java.time.Instant;
import java.util.List;

public record RequirementSetResponse(
        String id,
        String name,
        Instant createdAt,
        int count,
        List<Requirement> requirements,
        List<String> warnings) {

    public static RequirementSetResponse of(RequirementSet set, List<String> warnings) {
        return new RequirementSetResponse(set.id(), set.name(), set.createdAt(),
                set.requirements().size(), set.requirements(), warnings);
    }
}
