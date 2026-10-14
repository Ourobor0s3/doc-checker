package com.example.docchecker.pd.extract;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.extract.ImageEncoder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * DOCX: абзацы, таблицы (с подписями полей), колонтитулы, сноски, примечания (с их авторами), текстовые поля,
 * свойства документа (автор) и встроенные изображения.
 */
@Component
public class DocxFormatExtractor implements FormatExtractor {

    private static final int MIN_IMAGE_SIDE = 80;
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "tif", "tiff", "bmp");

    private final ImageEncoder images;

    public DocxFormatExtractor(ImageEncoder images, AppProperties props) {
        this.images = images;
    }

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.DOCX);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            body(doc, out);
            for (XWPFHeader h : doc.getHeaderList()) {
                out.text("верхний колонтитул", h.getText());
            }
            for (XWPFFooter f : doc.getFooterList()) {
                out.text("нижний колонтитул", f.getText());
            }
            int fn = 0;
            for (XWPFFootnote note : doc.getFootnotes()) {
                fn++;
                for (XWPFParagraph p : note.getParagraphs()) {
                    out.text("сноска " + fn, p.getText());
                }
            }
            comments(doc, out);
            int box = 0;
            for (String t : textBoxes(data)) {
                box++;
                out.text("текстовое поле " + box, t);
            }
            if (doc.getProperties() != null && doc.getProperties().getCoreProperties() != null) {
                out.meta("docx.creator", doc.getProperties().getCoreProperties().getCreator());
                out.meta("docx.lastModifiedBy", doc.getProperties().getCoreProperties().getLastModifiedByUser());
            }
            pictures(doc, out);
        }
    }

    private void body(XWPFDocument doc, ExtractionResult out) {
        int para = 0;
        int table = 0;
        for (IBodyElement el : doc.getBodyElements()) {
            if (el instanceof XWPFParagraph p) {
                para++;
                if (!out.text("абзац " + para, p.getText())) {
                    return;
                }
            } else if (el instanceof XWPFTable t) {
                table++;
                if (!table(t, "таблица " + table, out)) {
                    return;
                }
            }
        }
    }

    /** Таблица со строкой заголовков — подписи берутся из заголовка столбца; иначе из предыдущей ячейки строки. */
    private boolean table(XWPFTable t, String name, ExtractionResult out) {
        List<XWPFTableRow> rows = t.getRows();
        if (rows.isEmpty()) {
            return true;
        }
        List<String> head = new ArrayList<>();
        for (XWPFTableCell c : rows.get(0).getTableCells()) {
            head.add(c.getText().replace('\n', ' ').strip());
        }
        boolean headerTable = rows.size() >= 3 && head.size() >= 3 && head.stream()
                .allMatch(h -> !h.isBlank() && h.length() <= 40 && h.matches(".*\\p{L}.*") && !h.matches("[\\d.,:/\\- ]+"));
        for (int r = 0; r < rows.size(); r++) {
            List<XWPFTableCell> cells = rows.get(r).getTableCells();
            String previous = null;
            for (int c = 0; c < cells.size(); c++) {
                String text = cells.get(c).getText().replace('\n', ' ').strip();
                String locator = name + ", строка " + (r + 1) + ", ячейка " + (c + 1);
                if (text.isEmpty()) {
                    continue;
                }
                if (headerTable && r == 0) {
                    out.header(locator, text);
                    continue;
                }
                String hint = headerTable ? (c < head.size() ? head.get(c) : null)
                        : (previous != null && previous.length() <= 40 ? previous : null);
                if (!headerTable && text.length() <= 40) {
                    out.header(locator, text);
                }
                if (!out.field(locator, text, hint)) {
                    return false;
                }
                previous = text;
            }
        }
        return true;
    }

    private void comments(XWPFDocument doc, ExtractionResult out) {
        XWPFComment[] comments = doc.getComments();
        if (comments == null) {
            return;
        }
        int n = 0;
        for (XWPFComment c : comments) {
            n++;
            out.meta("comment.author", c.getAuthor());
            for (XWPFParagraph p : c.getParagraphs()) {
                out.text("примечание " + n, p.getText());
            }
        }
    }

    private void pictures(XWPFDocument doc, ExtractionResult out) {
        int n = 0;
        for (XWPFPictureData pic : doc.getAllPictures()) {
            if (out.imagesFull()) {
                out.partial("Проанализированы только первые изображения документа");
                return;
            }
            String ext = Optional.ofNullable(pic.suggestFileExtension()).orElse("").toLowerCase(Locale.ROOT);
            if (!IMAGE_EXT.contains(ext)) {
                continue;
            }
            Optional<String> encoded = images.tryEncode(pic.getData(), MIN_IMAGE_SIDE);
            if (encoded.isPresent()) {
                n++;
                out.image("встроенное изображение " + n, encoded.get());
            }
        }
    }

    /** Текст из текстовых полей (txbxContent) — POI их не показывает. Дубли из mc:Fallback пропускаются. */
    static List<String> textBoxes(byte[] data) {
        List<String> res = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) {
                    readTextBoxes(zip, res);
                    break;
                }
            }
        } catch (Exception ex) {
            // текстовые поля — дополнительный источник; ошибка чтения не должна ронять разбор документа
        }
        return res;
    }

    private static void readTextBoxes(InputStream in, List<String> res) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader r = f.createXMLStreamReader(in);
        int box = 0;
        int fallback = 0;
        boolean inText = false;
        StringBuilder cur = new StringBuilder();
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String ln = r.getLocalName();
                if (ln.equals("Fallback")) {
                    fallback++;
                } else if (ln.equals("txbxContent")) {
                    box++;
                } else if (ln.equals("t") && box > 0 && fallback == 0) {
                    inText = true;
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                String ln = r.getLocalName();
                if (ln.equals("Fallback")) {
                    fallback--;
                } else if (ln.equals("txbxContent")) {
                    box--;
                    if (box == 0 && fallback == 0 && !cur.toString().isBlank()) {
                        res.add(cur.toString().strip());
                    }
                    if (box == 0) {
                        cur.setLength(0);
                    }
                } else if (ln.equals("t")) {
                    inText = false;
                } else if (ln.equals("p") && box > 0 && fallback == 0) {
                    cur.append('\n');
                }
            } else if (ev == XMLStreamConstants.CHARACTERS && inText) {
                cur.append(r.getText());
            }
        }
    }
}
