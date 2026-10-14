package com.example.docchecker.extract;

import com.example.docchecker.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ExtractionService {

    private final FileTypeDetector detector;
    private final ImageExtractor imageExtractor;
    private final PdfExtractor pdfExtractor;
    private final DocxExtractor docxExtractor;

    public ExtractionService(FileTypeDetector detector, ImageExtractor imageExtractor,
                             PdfExtractor pdfExtractor, DocxExtractor docxExtractor) {
        this.detector = detector;
        this.imageExtractor = imageExtractor;
        this.pdfExtractor = pdfExtractor;
        this.docxExtractor = docxExtractor;
    }

    /** Содержимое проверяемого файла в виде чанков для модели. */
    public ExtractedContent extract(byte[] data, FileType type) {
        return switch (type) {
            case PNG, JPEG -> imageExtractor.extract(data);
            case PDF -> pdfExtractor.extract(data);
            case DOCX -> docxExtractor.extract(data);
        };
    }

    /** Простой текст из файла с требованиями: txt, md, docx, pdf. */
    public String extractPlainText(byte[] data, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return TextUtil.normalize(new String(data, StandardCharsets.UTF_8));
        }
        FileType type = detector.detect(data);
        return switch (type) {
            case PDF -> pdfExtractor.extractText(data);
            case DOCX -> docxExtractor.extractText(data);
            default -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Файл с требованиями должен быть txt, md, docx или pdf: " + filename);
        };
    }
}
