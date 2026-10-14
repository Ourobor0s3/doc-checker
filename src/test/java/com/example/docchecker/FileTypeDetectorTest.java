package com.example.docchecker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.extract.FileType;
import com.example.docchecker.extract.FileTypeDetector;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void detectsPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals(FileType.PNG, detector.detect(png));
    }

    @Test
    void detectsJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
        assertEquals(FileType.JPEG, detector.detect(jpeg));
    }

    @Test
    void detectsPdf() {
        assertEquals(FileType.PDF, detector.detect("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void detectsDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("test");
            doc.write(out);
            assertEquals(FileType.DOCX, detector.detect(out.toByteArray()));
        }
    }

    @Test
    void rejectsUnknownContent() {
        byte[] text = "просто текст".getBytes(StandardCharsets.UTF_8);
        assertThrows(ApiException.class, () -> detector.detect(text));
    }
}
