package com.example.docchecker.pd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.docchecker.pd.extract.FileFormat;
import com.example.docchecker.pd.extract.FormatDetector;
import com.example.docchecker.pd.extract.RtfParser;
import com.example.docchecker.pd.extract.TextDecoder;
import com.example.docchecker.pd.extract.ZipNames;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class PdHelpersTest {

    @Test
    void rtfInCp1251IsDecoded() {
        String rtf = "{\\rtf1\\ansi\\ansicpg1251\\deff0{\\fonttbl{\\f0 Arial;}}\\f0 \\'cf\\'f0\\'e8\\'e2\\'e5\\'f2\\par Second \\u1055?}";
        RtfParser.Result r = RtfParser.parse(rtf.getBytes(StandardCharsets.ISO_8859_1));
        assertTrue(r.text().contains("Привет"));
        assertTrue(r.text().contains("Second П"));
        assertTrue(!r.text().contains("Arial"), "таблица шрифтов должна быть пропущена");
    }

    @Test
    void legacyCyrillicEncodingsAreDetected() throws Exception {
        String text = "Привет, мир. Фамилия Иванов";
        for (String cs : new String[] {"windows-1251", "IBM866", "KOI8-R", "UTF-8"}) {
            assertEquals(text, TextDecoder.decode(text.getBytes(cs)), cs);
        }
    }

    @Test
    void zipNamesInCp866AreRestored() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo, Charset.forName("IBM866"))) {
            z.putNextEntry(new ZipEntry("Договоры/Договор №5.docx"));
            z.write(1);
            z.closeEntry();
        }
        File f = File.createTempFile("zipnames", ".zip");
        try {
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bo.toByteArray());
            }
            List<String> names = new ArrayList<>();
            try (ZipFile zf = new ZipFile(f, StandardCharsets.ISO_8859_1)) {
                for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements();) {
                    names.add(ZipNames.decode(en.nextElement().getName()));
                }
            }
            assertEquals(List.of("Договоры/Договор №5.docx"), names);
        } finally {
            f.delete();
        }
    }

    @Test
    void pathsAreSanitized() {
        assertEquals("a/b/c.txt", ZipNames.sanitize("..\\..\\a/./b//c.txt"));
    }

    @Test
    void formatsAreDetectedByContent() {
        FormatDetector d = new FormatDetector();
        assertEquals(FileFormat.JSON, d.detect("{\"a\":1}".getBytes(), "x.json"));
        assertEquals(FileFormat.CSV, d.detect("a,b\n1,2".getBytes(), "x.csv"));
        assertEquals(FileFormat.HTML, d.detect("<html><body>x</body></html>".getBytes(), "page"));
        assertEquals(FileFormat.TXT, d.detect("hello".getBytes(), "x.txt"));
        assertEquals(FileFormat.PARQUET, d.detect(new byte[] {'P', 'A', 'R', '1', 1, 2, 3}, "x"));
        assertEquals(FileFormat.UNSUPPORTED, d.detect(new byte[] {0, 1, 2, 3, 0, 0, 5}, "x.bin"));
        assertEquals(FileFormat.EMPTY, d.detect(new byte[0], "x"));
    }
}
