package com.example.docchecker.pd.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** CSV/TSV: разделитель определяется автоматически, первая строка считается заголовком, если похожа на него. */
@Component
public class CsvFormatExtractor implements FormatExtractor {

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.CSV);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) {
        String text = TextDecoder.decode(data);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(detectDelimiter(text))
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .setTrim(true)
                .build();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            List<String> header = null;
            boolean first = true;
            for (CSVRecord rec : parser) {
                long line = rec.getRecordNumber();
                if (first) {
                    first = false;
                    List<String> cells = new ArrayList<>();
                    rec.forEach(cells::add);
                    if (isHeader(cells)) {
                        header = cells;
                        for (int i = 0; i < cells.size(); i++) {
                            out.header("заголовок, столбец " + (i + 1), cells.get(i));
                        }
                        continue;
                    }
                }
                for (int i = 0; i < rec.size(); i++) {
                    String h = header != null && i < header.size() ? header.get(i) : null;
                    String col = h != null && !h.isBlank() ? "«" + h + "»" : String.valueOf(i + 1);
                    if (!out.field("строка " + line + ", столбец " + col, rec.get(i), h)) {
                        return;
                    }
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            out.partial("CSV разобран не полностью: " + e.getMessage());
        }
    }

    static char detectDelimiter(String text) {
        String head = text.substring(0, Math.min(text.length(), 2000));
        int nl = head.indexOf('\n');
        String line = nl > 0 ? head.substring(0, nl) : head;
        char best = ',';
        int bestCount = 0;
        for (char c : new char[] {',', ';', '\t', '|'}) {
            int count = (int) line.chars().filter(ch -> ch == c).count();
            if (count > bestCount) {
                bestCount = count;
                best = c;
            }
        }
        return best;
    }

    private static boolean isHeader(List<String> cells) {
        boolean any = false;
        for (String c : cells) {
            if (c.isBlank()) {
                continue;
            }
            any = true;
            if (c.length() > 60 || !c.matches(".*\\p{L}.*") || c.matches("[\\d.,:/\\- ]+")) {
                return false;
            }
        }
        return any;
    }
}
