package com.example.docchecker.pd.extract;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Определение формата по содержимому, с расширением имени как подсказкой для текстовых и OLE2-файлов. */
public class FormatDetector {

    public FileFormat detect(byte[] d, String name) {
        if (d == null || d.length == 0) {
            return FileFormat.EMPTY;
        }
        String ext = extension(name);
        if (starts(d, 0x89, 'P', 'N', 'G')) {
            return FileFormat.PNG;
        }
        if (starts(d, 0xFF, 0xD8, 0xFF)) {
            return FileFormat.JPEG;
        }
        if (starts(d, 'G', 'I', 'F', '8')) {
            return FileFormat.GIF;
        }
        if (starts(d, 'I', 'I', '*', 0) || starts(d, 'M', 'M', 0, '*')) {
            return FileFormat.TIFF;
        }
        if (starts(d, 'B', 'M') && ext.equals("bmp")) {
            return FileFormat.BMP;
        }
        if (looksLikePdf(d)) {
            return FileFormat.PDF;
        }
        if (starts(d, 'P', 'A', 'R', '1')) {
            return FileFormat.PARQUET;
        }
        if (starts(d, '{', '\\', 'r', 't', 'f')) {
            return FileFormat.RTF;
        }
        if (starts(d, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return (ext.equals("doc") || ext.equals("dot") || ext.isEmpty()) ? FileFormat.DOC : FileFormat.UNSUPPORTED;
        }
        if (starts(d, 'P', 'K', 3, 4)) {
            return classifyZip(d);
        }
        if (looksLikeText(d)) {
            return classifyText(d, ext);
        }
        return FileFormat.UNSUPPORTED;
    }

    public static String extension(String name) {
        if (name == null) {
            return "";
        }
        String n = name.substring(name.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static FileFormat classifyZip(byte[] d) {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(d))) {
            ZipEntry e;
            int guard = 0;
            while ((e = zip.getNextEntry()) != null && guard++ < 300) {
                names.add(e.getName());
            }
        } catch (IOException | RuntimeException ex) {
            // повреждённый или нестандартный zip — считаем обычным архивом
        }
        if (names.contains("word/document.xml")) {
            return FileFormat.DOCX;
        }
        if (names.contains("xl/workbook.xml")) {
            return FileFormat.XLSX;
        }
        if (names.contains("ppt/presentation.xml")) {
            return FileFormat.UNSUPPORTED;
        }
        return FileFormat.ZIP;
    }

    private static FileFormat classifyText(byte[] d, String ext) {
        switch (ext) {
            case "json", "jsonl", "ndjson":
                return FileFormat.JSON;
            case "csv", "tsv":
                return FileFormat.CSV;
            case "html", "htm", "xhtml":
                return FileFormat.HTML;
            case "md", "markdown":
                return FileFormat.MD;
            default:
                break;
        }
        String head = TextDecoder.decode(java.util.Arrays.copyOf(d, Math.min(d.length, 2048))).stripLeading();
        String low = head.toLowerCase(Locale.ROOT);
        if (low.startsWith("<!doctype html") || low.startsWith("<html")) {
            return FileFormat.HTML;
        }
        if (ext.isEmpty() && (head.startsWith("{") || head.startsWith("["))) {
            return FileFormat.JSON;
        }
        return FileFormat.TXT;
    }

    private static boolean looksLikePdf(byte[] d) {
        int limit = Math.min(d.length - 5, 1024);
        for (int i = 0; i <= limit; i++) {
            if (d[i] == '%' && d[i + 1] == 'P' && d[i + 2] == 'D' && d[i + 3] == 'F' && d[i + 4] == '-') {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeText(byte[] d) {
        if (d.length >= 2 && ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xFE
                || (d[0] & 0xFF) == 0xFE && (d[1] & 0xFF) == 0xFF)) {
            return true; // UTF-16 с BOM
        }
        int n = Math.min(d.length, 8192);
        int control = 0;
        for (int i = 0; i < n; i++) {
            int b = d[i] & 0xFF;
            if (b == 0) {
                return false;
            }
            if (b < 9 || (b > 13 && b < 32 && b != 27)) {
                control++;
            }
        }
        return control * 20 <= n;
    }

    private static boolean starts(byte[] d, int... sig) {
        if (d.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((d[i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }
}
