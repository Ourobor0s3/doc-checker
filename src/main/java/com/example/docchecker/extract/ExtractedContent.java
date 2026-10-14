package com.example.docchecker.extract;

import java.util.List;

public record ExtractedContent(List<ContentChunk> chunks, List<String> warnings) {}
