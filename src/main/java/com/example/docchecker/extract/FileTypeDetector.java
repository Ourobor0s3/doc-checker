package com.example.docchecker.extract;

import com.example.docchecker.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Определяет тип файла по содержимому (magic bytes), а не по расширению. */
@Component
public class FileTypeDetector {

    public FileType detect(byte[] d) {
        if (d == null || d.length < 4) {
            throw unsupported();
        }
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return FileType.PNG;
        }
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return FileType.JPEG;
        }
        if (looksLikePdf(d)) {
            return FileType.PDF;
        }
        if (d[0] == 'P' && d[1] == 'K' && d[2] == 3 && d[3] == 4 && looksLikeDocx(d)) {
            return FileType.DOCX;
        }
        throw unsupported();
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

    private static boolean looksLikeDocx(byte[] d) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(d))) {
            ZipEntry entry;
            int guard = 0;
            while ((entry = zip.getNextEntry()) != null && guard++ < 5000) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Неподдерживаемый формат файла. Поддерживаются: JPEG/JPG, PNG, PDF, DOCX");
    }
}
