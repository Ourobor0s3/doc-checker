package com.example.docchecker.pd.extract;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Декодирование текста с неизвестной кодировкой: BOM → строгий UTF-8 → лучшая из кириллических однобайтовых. */
public final class TextDecoder {

    private static final List<Charset> LEGACY = List.of(
            Charset.forName("windows-1251"), Charset.forName("KOI8-R"), Charset.forName("IBM866"));

    private TextDecoder() {}

    public static String decode(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xEF && (d[1] & 0xFF) == 0xBB && (d[2] & 0xFF) == 0xBF) {
            return new String(d, 3, d.length - 3, StandardCharsets.UTF_8);
        }
        if (d.length >= 2 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xFE) {
            return new String(d, 2, d.length - 2, StandardCharsets.UTF_16LE);
        }
        if (d.length >= 2 && (d[0] & 0xFF) == 0xFE && (d[1] & 0xFF) == 0xFF) {
            return new String(d, 2, d.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(d)).toString();
        } catch (CharacterCodingException e) {
            return decodeLegacy(d);
        }
    }

    static String decodeLegacy(byte[] d) {
        Charset best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        String bestText = null;
        for (Charset cs : LEGACY) {
            String t = new String(d, cs);
            double sc = score(t);
            if (sc > bestScore) {
                bestScore = sc;
                best = cs;
                bestText = t;
            }
        }
        if (best == null || cyrillicLetters(bestText) < 3) {
            return new String(d, Charset.forName("windows-1252"));
        }
        return bestText;
    }

    /** Чем больше строчных кириллических букв и меньше «мусора» (заглавных посреди слов, псевдографики) — тем лучше. */
    public static double score(String s) {
        int lower = 0;
        int upper = 0;
        int junk = 0;
        int limit = Math.min(s.length(), 20000);
        for (int i = 0; i < limit; i++) {
            char c = s.charAt(i);
            if (c >= 'а' && c <= 'я' || c == 'ё') {
                lower++;
            } else if (c >= 'А' && c <= 'Я' || c == 'Ё') {
                upper++;
            } else if (c >= 0x2500 && c <= 0x257F || c >= 0x0400 && c <= 0x04FF || c >= 0x0080 && c <= 0x009F) {
                junk++; // прочая кириллица (укр., сербск.), псевдографика, управляющие
            }
        }
        return lower - 1.5 * upper - 3.0 * junk;
    }

    static int cyrillicLetters(String s) {
        int n = 0;
        int limit = Math.min(s.length(), 20000);
        for (int i = 0; i < limit; i++) {
            char c = s.charAt(i);
            if (c >= 'а' && c <= 'я' || c >= 'А' && c <= 'Я' || c == 'ё' || c == 'Ё') {
                n++;
            }
        }
        return n;
    }
}
