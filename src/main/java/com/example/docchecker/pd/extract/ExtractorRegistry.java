package com.example.docchecker.pd.extract;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExtractorRegistry {

    private final Map<FileFormat, FormatExtractor> byFormat = new EnumMap<>(FileFormat.class);

    public ExtractorRegistry(List<FormatExtractor> extractors) {
        for (FormatExtractor e : extractors) {
            for (FileFormat f : e.formats()) {
                byFormat.put(f, e);
            }
        }
    }

    public FormatExtractor get(FileFormat format) {
        return byFormat.get(format);
    }
}
