package com.example.docchecker.api;

import com.example.docchecker.domain.RequirementSet;
import java.time.Instant;

public record RequirementSetShort(String id, String name, Instant createdAt, int count) {

    public static RequirementSetShort of(RequirementSet set) {
        return new RequirementSetShort(set.id(), set.name(), set.createdAt(), set.requirements().size());
    }
}
