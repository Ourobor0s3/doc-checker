package com.example.docchecker.extract;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {

    private TextUtil() {}

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\uFEFF", "")
                .replace("\u0000", "")
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    /** Режет текст на куски не длиннее {@code max} символов по границам строк. */
    public static List<String> split(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw;
            while (line.length() > max) {
                flush(cur, out);
                out.add(line.substring(0, max));
                line = line.substring(max);
            }
            if (cur.length() + line.length() + 1 > max) {
                flush(cur, out);
            }
            if (cur.length() > 0) {
                cur.append('\n');
            }
            cur.append(line);
        }
        flush(cur, out);
        return out;
    }

    /** Превращает текст документа в чанки с учётом общего лимита; при обрезке добавляет предупреждение. */
    public static List<ContentChunk> toChunks(String text, int maxChunk, int maxTotal, List<String> warnings) {
        String t = text;
        if (t.length() > maxTotal) {
            t = t.substring(0, maxTotal);
            warnings.add("Документ слишком длинный: проверены только первые " + maxTotal + " символов текста");
        }
        if (t.isBlank()) {
            return List.of();
        }
        List<String> parts = split(t, maxChunk);
        List<ContentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String label = parts.size() == 1 ? "документ" : "часть " + (i + 1) + " из " + parts.size();
            chunks.add(ContentChunk.ofText(label, parts.get(i)));
        }
        return chunks;
    }

    private static void flush(StringBuilder cur, List<String> out) {
        if (cur.length() > 0) {
            out.add(cur.toString());
            cur.setLength(0);
        }
    }
}
