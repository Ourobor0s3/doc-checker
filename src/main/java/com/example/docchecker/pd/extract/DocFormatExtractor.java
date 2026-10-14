package com.example.docchecker.pd.extract;

import com.example.docchecker.extract.ImageEncoder;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.Picture;
import org.springframework.stereotype.Component;

/** Старый формат DOC (OLE2): абзацы, колонтитулы, примечания, автор, встроенные изображения. */
@Component
public class DocFormatExtractor implements FormatExtractor {

    private static final int MIN_IMAGE_SIDE = 80;
    private final ImageEncoder images;

    public DocFormatExtractor(ImageEncoder images) {
        this.images = images;
    }

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.DOC);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(data));
             WordExtractor ex = new WordExtractor(doc)) {
            String[] paragraphs = ex.getParagraphText();
            for (int i = 0; i < paragraphs.length; i++) {
                if (!out.text("абзац " + (i + 1), paragraphs[i].replace('\r', ' ').replace('\u0007', ' '))) {
                    break;
                }
            }
            out.text("верхний колонтитул", ex.getHeaderText());
            out.text("нижний колонтитул", ex.getFooterText());
            String[] comments = ex.getCommentsText();
            for (int i = 0; comments != null && i < comments.length; i++) {
                out.text("примечание " + (i + 1), comments[i]);
            }
            SummaryInformation info = doc.getSummaryInformation();
            if (info != null) {
                out.meta("doc.author", info.getAuthor());
                out.meta("doc.lastAuthor", info.getLastAuthor());
            }
            List<Picture> pictures = doc.getPicturesTable().getAllPictures();
            int n = 0;
            for (Picture pic : pictures) {
                if (out.imagesFull()) {
                    out.partial("Проанализированы только первые изображения документа");
                    break;
                }
                Optional<String> encoded = images.tryEncode(pic.getContent(), MIN_IMAGE_SIDE);
                if (encoded.isPresent()) {
                    n++;
                    out.image("встроенное изображение " + n, encoded.get());
                }
            }
        }
    }
}
