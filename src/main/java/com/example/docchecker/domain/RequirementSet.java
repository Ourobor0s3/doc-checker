package com.example.docchecker.domain;

import java.time.Instant;
import java.util.List;

public record RequirementSet(String id, String name, List<Requirement> requirements, Instant createdAt) {}
