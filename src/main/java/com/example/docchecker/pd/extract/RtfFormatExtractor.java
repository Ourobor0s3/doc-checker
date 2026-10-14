package com.example.docchecker.pd.extract;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RtfFormatExtractor implements FormatExtractor {

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.RTF);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) {
        RtfParser.Result r = RtfParser.parse(data);
        int n = 0;
        for (String line : r.text().split("\\R")) {
            n++;
            if (!out.text("абзац " + n, line)) {
                break;
            }
        }
        r.meta().forEach((k, v) -> out.meta("rtf." + k, v));
    }
}
