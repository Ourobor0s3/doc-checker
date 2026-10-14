package com.example.docchecker.pd.extract;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Имена файлов внутри zip: восстановление кодировки (cp866 / cp1251 / UTF-8) и очистка пути. */
public final class ZipNames {

    private static final Charset CP866 = Charset.forName("IBM866");
    private static final Charset CP1251 = Charset.forName("windows-1251");

    private ZipNames() {}

    /**
     * @param raw имя, прочитанное {@code ZipFile} с кодировкой ISO-8859-1 (байты 1:1). Если в имени есть символы
     *            выше 0xFF, ZipFile уже распознал его как UTF-8 по флагу, и имя возвращается как есть.
     */
    public static String decode(String raw) {
        boolean ascii = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c > 0xFF) {
                return raw;
            }
            if (c > 0x7F) {
                ascii = false;
            }
        }
        if (ascii) {
            return raw;
        }
        byte[] bytes = raw.getBytes(StandardCharsets.ISO_8859_1);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            String a = new String(bytes, CP866);
            String b = new String(bytes, CP1251);
            double sa = TextDecoder.score(a);
            double sb = TextDecoder.score(b);
            if (Math.max(sa, sb) < 1) {
                return raw;
            }
            return sa >= sb ? a : b;
        }
    }

    /** Приводит путь к безопасному виду: прямые слэши, без «..», «.» и пустых сегментов. */
    public static String sanitize(String path) {
        List<String> parts = new ArrayList<>();
        for (String seg : path.replace('\\', '/').split("/")) {
            if (!seg.isEmpty() && !seg.equals(".") && !seg.equals("..")) {
                parts.add(seg);
            }
        }
        return String.join("/", parts);
    }
}
