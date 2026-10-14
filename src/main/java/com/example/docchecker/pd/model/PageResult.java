package com.example.docchecker.pd.model;

import java.util.List;

public record PageResult<T>(List<T> items, int total, int page, int size) {}
