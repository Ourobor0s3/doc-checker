package com.example.docchecker.pd.service;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.pd.extract.ZipNames;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

/**
 * Рекурсивный обход zip-архива с защитой: лимиты на число файлов, размер, вложенность и степень сжатия,
 * безопасные пути (файлы на диск не распаковываются, всё читается в память), восстановление кириллических имён.
 */
@Component
public class ArchiveWalker {

    public interface Listener {
        /** Изменение числа обнаруженных файлов (отрицательное — архив внутри архива заменён его содержимым). */
        void discovered(int delta);

        void file(String path, byte[] data);

        void skipped(String path, long size, String reason);

        void warning(String message);
    }

    private static final class Budget {
        int entries;
        long bytes;
        boolean stopped;
    }

    private static final Set<String> JUNK = Set.of(".ds_store", "thumbs.db", "desktop.ini");

    private final AppProperties.Pd cfg;

    public ArchiveWalker(AppProperties props) {
        this.cfg = props.pd();
    }

    /** true, если файл — zip-архив с файлами, а не документ в формате OOXML (docx, xlsx, pptx). */
    public boolean isArchive(Path file) {
        try (ZipFile zf = new ZipFile(file.toFile(), StandardCharsets.ISO_8859_1)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int guard = 0;
            while (en.hasMoreElements() && guard++ < 10000) {
                String n = en.nextElement().getName();
                if (n.equals("[Content_Types].xml") || n.equals("word/document.xml") || n.equals("xl/workbook.xml")) {
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public void walk(Path zip, String prefix, Listener listener) throws IOException {
        walk(zip, prefix, 1, listener, new Budget());
    }

    private void walk(Path zipPath, String prefix, int depth, Listener l, Budget b) throws IOException {
        try (ZipFile zf = new ZipFile(zipPath.toFile(), StandardCharsets.ISO_8859_1)) {
            List<ZipEntry> entries = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements();) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && !isJunk(e.getName())) {
                    entries.add(e);
                }
            }
            l.discovered(entries.size());

            for (ZipEntry e : entries) {
                if (b.stopped) {
                    return;
                }
                String name = ZipNames.sanitize(ZipNames.decode(e.getName()));
                if (name.isEmpty()) {
                    l.discovered(-1);
                    continue;
                }
                String display = prefix + "/" + name;
                if (++b.entries > cfg.maxArchiveEntries()) {
                    l.warning("Достигнут лимит числа файлов в архиве (" + cfg.maxArchiveEntries() + "): остальные не обработаны");
                    b.stopped = true;
                    return;
                }
                long size = e.getSize();
                long compressed = e.getCompressedSize();
                if (size > cfg.maxEntryBytes()) {
                    l.skipped(display, size, "Файл больше допустимого размера (" + cfg.maxEntryBytes() / (1024 * 1024) + " МБ)");
                    continue;
                }
                if (size > 50L * 1024 * 1024 && compressed > 0 && size / compressed > 1000) {
                    l.skipped(display, size, "Подозрительно высокая степень сжатия (возможная архив-бомба)");
                    continue;
                }
                byte[] bytes;
                try (InputStream in = zf.getInputStream(e)) {
                    bytes = in.readNBytes((int) Math.min(cfg.maxEntryBytes() + 1, Integer.MAX_VALUE));
                } catch (IOException | RuntimeException ex) {
                    l.skipped(display, size, "Не удалось прочитать файл из архива: " + ex.getMessage());
                    continue;
                }
                if (bytes.length > cfg.maxEntryBytes()) {
                    l.skipped(display, bytes.length, "Файл больше допустимого размера");
                    continue;
                }
                b.bytes += bytes.length;
                if (b.bytes > cfg.maxArchiveTotalBytes()) {
                    l.warning("Достигнут лимит общего размера распакованных данных: остальные файлы не обработаны");
                    b.stopped = true;
                    return;
                }
                if (isZipMagic(bytes) && depth < cfg.maxArchiveDepth() && nested(bytes, display, depth, l, b)) {
                    continue;
                }
                l.file(display, bytes);
            }
        }
    }

    /** Если вложенный файл — архив, обходит его и возвращает true; документы OOXML возвращают false. */
    private boolean nested(byte[] bytes, String display, int depth, Listener l, Budget b) throws IOException {
        Path tmp = Files.createTempFile("pd-nested-", ".zip");
        try {
            Files.write(tmp, bytes);
            if (!isArchive(tmp)) {
                return false;
            }
            l.discovered(-1);
            walk(tmp, display, depth + 1, l, b);
            return true;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean isZipMagic(byte[] b) {
        return b.length > 3 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
    }

    private static boolean isJunk(String name) {
        String n = name.replace('\\', '/');
        String last = n.substring(n.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return n.startsWith("__MACOSX/") || n.contains("/__MACOSX/") || JUNK.contains(last) || last.startsWith("._");
    }
}
