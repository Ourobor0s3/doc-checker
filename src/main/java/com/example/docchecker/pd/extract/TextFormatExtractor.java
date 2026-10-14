package com.example.docchecker.pd.extract;

import java.util.Set;
import org.springframework.stereotype.Component;

/** TXT и Markdown: каждая строка — отдельный фрагмент. */
@Component
public class TextFormatExtractor implements FormatExtractor {

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.TXT, FileFormat.MD);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) {
        addLines(out, TextDecoder.decode(data));
    }

    static void addLines(ExtractionResult out, String text) {
        int n = 0;
        for (String line : text.split("\\R")) {
            n++;
            if (!out.text("строка " + n, line)) {
                return;
            }
        }
    }
}
