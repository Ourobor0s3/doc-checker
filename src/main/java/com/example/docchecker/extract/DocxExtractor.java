package com.example.docchecker.extract;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.common.BadDocumentException;
import com.example.docchecker.config.AppProperties;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/** DOCX → текст (абзацы, таблицы, колонтитулы) + встроенные PNG/JPEG для vision-модели. */
@Component
public class DocxExtractor {

    private static final int MIN_IMAGE_SIDE = 100;

    private final AppProperties.Extract cfg;
    private final ImageEncoder images;

    public DocxExtractor(AppProperties props, ImageEncoder images) {
        this.cfg = props.extract();
        this.images = images;
    }

    public String extractText(byte[] data) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            return readText(doc);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BadDocumentException("Не удалось прочитать DOCX: " + e.getMessage());
        }
    }

    public ExtractedContent extract(byte[] data) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            List<String> warnings = new ArrayList<>();
            List<ContentChunk> chunks = new ArrayList<>(
                    TextUtil.toChunks(readText(doc), cfg.maxCharsPerChunk(), cfg.maxTotalChars(), warnings));

            if (cfg.docxImagesEnabled()) {
                int added = 0;
                int skippedByLimit = 0;
                for (XWPFPictureData pic : doc.getAllPictures()) {
                    String ext = Optional.ofNullable(pic.suggestFileExtension()).orElse("").toLowerCase(Locale.ROOT);
                    if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg")) {
                        continue;
                    }
                    Optional<String> encoded = images.tryEncode(pic.getData(), MIN_IMAGE_SIDE);
                    if (encoded.isEmpty()) {
                        continue;
                    }
                    if (added >= cfg.maxDocxImages()) {
                        skippedByLimit++;
                        continue;
                    }
                    added++;
                    chunks.add(ContentChunk.ofImage("встроенное изображение " + added, encoded.get()));
                }
                if (skippedByLimit > 0) {
                    warnings.add("Во встроенных изображениях проверены только первые " + cfg.maxDocxImages()
                            + ", пропущено: " + skippedByLimit);
                }
            }
            return new ExtractedContent(chunks, warnings);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BadDocumentException("Не удалось прочитать DOCX: " + e.getMessage());
        }
    }

    private String readText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph p) {
                String t = p.getText();
                if (t == null || t.isBlank()) {
                    continue;
                }
                String style = p.getStyle();
                if (style != null && style.toLowerCase(Locale.ROOT).startsWith("heading")) {
                    sb.append("# ");
                }
                sb.append(t).append('\n');
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    String line = row.getTableCells().stream()
                            .map(c -> c.getText().replace('\n', ' ').strip())
                            .collect(Collectors.joining(" | "));
                    if (!line.replace("|", "").isBlank()) {
                        sb.append(line).append('\n');
                    }
                }
            }
        }
        for (XWPFHeader h : doc.getHeaderList()) {
            appendHeaderFooter(sb, "Верхний колонтитул", h.getText());
        }
        for (XWPFFooter f : doc.getFooterList()) {
            appendHeaderFooter(sb, "Нижний колонтитул", f.getText());
        }
        return TextUtil.normalize(sb.toString());
    }

    private static void appendHeaderFooter(StringBuilder sb, String title, String text) {
        if (text != null && !text.isBlank()) {
            sb.append('[').append(title).append("] ").append(text.strip().replace('\n', ' ')).append('\n');
        }
    }
}
