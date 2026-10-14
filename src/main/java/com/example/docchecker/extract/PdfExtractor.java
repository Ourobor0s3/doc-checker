package com.example.docchecker.extract;

import com.example.docchecker.common.BadDocumentException;
import com.example.docchecker.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF с текстовым слоем → текстовые чанки.
 * Скан (текста почти нет) → страницы рендерятся в картинки для vision-модели.
 */
@Component
public class PdfExtractor {

    private final AppProperties.Extract cfg;
    private final ImageEncoder images;

    public PdfExtractor(AppProperties props, ImageEncoder images) {
        this.cfg = props.extract();
        this.images = images;
    }

    public String extractText(byte[] data) {
        try (PDDocument doc = load(data)) {
            return TextUtil.normalize(new PDFTextStripper().getText(doc));
        } catch (IOException e) {
            throw new BadDocumentException("Не удалось прочитать PDF: " + e.getMessage());
        }
    }

    public ExtractedContent extract(byte[] data) {
        try (PDDocument doc = load(data)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                throw new BadDocumentException("PDF не содержит страниц");
            }
            List<String> warnings = new ArrayList<>();
            String text = TextUtil.normalize(new PDFTextStripper().getText(doc));

            if (text.length() >= (long) cfg.minTextCharsPerPage() * pages) {
                return new ExtractedContent(
                        TextUtil.toChunks(text, cfg.maxCharsPerChunk(), cfg.maxTotalChars(), warnings), warnings);
            }

            // Текстового слоя нет — вероятно, скан. Рендерим страницы в картинки.
            int limit = Math.min(pages, cfg.maxScannedPages());
            if (pages > limit) {
                warnings.add("PDF содержит " + pages + " стр., проверены только первые " + limit);
            }
            warnings.add("В PDF нет текстового слоя: страницы проверяются как изображения");
            PDFRenderer renderer = new PDFRenderer(doc);
            List<ContentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, cfg.pdfRenderDpi(), ImageType.RGB);
                chunks.add(ContentChunk.ofImage("страница " + (i + 1), images.encode(img)));
            }
            return new ExtractedContent(chunks, warnings);
        } catch (IOException e) {
            throw new BadDocumentException("Не удалось прочитать PDF: " + e.getMessage());
        }
    }

    private PDDocument load(byte[] data) throws IOException {
        try {
            return Loader.loadPDF(data);
        } catch (InvalidPasswordException e) {
            throw new BadDocumentException("PDF защищён паролем");
        }
    }
}
