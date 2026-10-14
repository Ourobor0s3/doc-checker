package com.example.docchecker.pd.extract;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.extract.ImageEncoder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF: текст по страницам, поля форм, автор. Страницы почти без текста (сканы) рендерятся в изображения;
 * крупные встроенные картинки (например, фото в резюме) тоже отправляются на анализ.
 */
@Component
public class PdfFormatExtractor implements FormatExtractor {

    private static final int MIN_EMBEDDED_SIDE = 150;

    private final ImageEncoder images;
    private final AppProperties.Extract cfg;

    public PdfFormatExtractor(ImageEncoder images, AppProperties props) {
        this.images = images;
        this.cfg = props.extract();
    }

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.PDF);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) throws Exception {
        try (PDDocument doc = load(data)) {
            PDDocumentInformation info = doc.getDocumentInformation();
            if (info != null) {
                out.meta("pdf.author", info.getAuthor());
            }
            forms(doc, out);

            int pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(doc);
            int rendered = 0;
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc).strip();
                boolean scanned = text.length() < cfg.minTextCharsPerPage();
                if (!text.isBlank() && !out.text("стр. " + p, text)) {
                    return;
                }
                if (scanned) {
                    if (rendered >= cfg.maxScannedPages() || out.imagesFull()) {
                        out.partial("Страницы без текстового слоя проанализированы не все (лимит "
                                + cfg.maxScannedPages() + ")");
                        continue;
                    }
                    rendered++;
                    BufferedImage img = renderer.renderImageWithDPI(p - 1, cfg.pdfRenderDpi(), ImageType.RGB);
                    out.image("стр. " + p + " (скан)", images.encode(img));
                } else {
                    embeddedImages(doc.getPage(p - 1), p, out);
                }
            }
        }
    }

    private void embeddedImages(PDPage page, int pageNo, ExtractionResult out) {
        PDResources res = page.getResources();
        if (res == null) {
            return;
        }
        int n = 0;
        for (COSName name : res.getXObjectNames()) {
            if (out.imagesFull()) {
                return;
            }
            try {
                PDXObject x = res.getXObject(name);
                if (x instanceof PDImageXObject img && img.getWidth() >= MIN_EMBEDDED_SIDE
                        && img.getHeight() >= MIN_EMBEDDED_SIDE) {
                    n++;
                    out.image("стр. " + pageNo + ", изображение " + n, images.encode(img.getImage()));
                }
            } catch (IOException | RuntimeException e) {
                out.note("Не удалось прочитать встроенное изображение на стр. " + pageNo);
            }
        }
    }

    private void forms(PDDocument doc, ExtractionResult out) {
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
        if (form == null) {
            return;
        }
        for (PDField f : form.getFieldTree()) {
            try {
                String value = f.getValueAsString();
                if (value != null && !value.isBlank()) {
                    out.field("поле формы «" + f.getFullyQualifiedName() + "»", value, f.getFullyQualifiedName());
                }
            } catch (RuntimeException e) {
                // нестандартное поле — пропускаем
            }
        }
    }

    private static PDDocument load(byte[] data) throws IOException {
        try {
            return Loader.loadPDF(data);
        } catch (InvalidPasswordException e) {
            throw new ExtractException("PDF защищён паролем");
        }
    }
}
