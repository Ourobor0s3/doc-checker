package com.example.docchecker.pd.extract;

import java.util.Set;

/** Извлекает содержимое файлов определённых форматов в {@link ExtractionResult}. */
public interface FormatExtractor {

    Set<FileFormat> formats();

    void extract(byte[] data, String fileName, ExtractionResult out) throws Exception;
}
