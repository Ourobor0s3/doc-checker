package com.example.docchecker.pd.extract;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Упрощённый разбор RTF в текст. Учитывает кодовую страницу (ansicpg, например 1251), шестнадцатеричные и юникод-последовательности, пропускает служебные
 * группы (таблицы шрифтов, стили, картинки) и забирает автора документа (author, operator) в метаданные.
 */
public final class RtfParser {

    public record Result(String text, Map<String, String> meta) {}

    private static final Set<String> SKIP = Set.of("fonttbl", "colortbl", "stylesheet", "pict", "object", "fldinst",
            "themedata", "latentstyles", "listtable", "listoverridetable", "rsidtbl", "generator", "datastore",
            "xmlnstbl", "mmathpr", "colorschememapping", "info", "bkmkstart", "bkmkend", "private");

    private static final class Group {
        boolean skip;
        int uc = 1;
        String capKey;
        StringBuilder cap;

        Group copy() {
            Group g = new Group();
            g.skip = skip;
            g.uc = uc;
            return g;
        }
    }

    private final String s;
    private final StringBuilder out = new StringBuilder();
    private final Map<String, String> meta = new LinkedHashMap<>();
    private final Deque<Group> stack = new ArrayDeque<>();
    private Group cur = new Group();
    private Charset charset = Charset.forName("windows-1252");
    private int skipChars;

    private RtfParser(byte[] data) {
        this.s = new String(data, StandardCharsets.ISO_8859_1);
    }

    public static Result parse(byte[] data) {
        RtfParser p = new RtfParser(data);
        p.run();
        return new Result(p.out.toString(), p.meta);
    }

    private void run() {
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '{') {
                stack.push(cur);
                cur = cur.copy();
                i++;
            } else if (c == '}') {
                if (cur.cap != null && cur.capKey != null) {
                    meta.put(cur.capKey, cur.cap.toString().strip());
                }
                cur = stack.isEmpty() ? new Group() : stack.pop();
                i++;
            } else if (c == '\\') {
                i = control(i + 1, n);
            } else if (c == '\n' || c == '\r') {
                i++;
            } else {
                literal(c);
                i++;
            }
        }
    }

    private int control(int i, int n) {
        if (i >= n) {
            return n;
        }
        char d = s.charAt(i);
        if (d == '\\' || d == '{' || d == '}') {
            literal(d);
            return i + 1;
        }
        if (d == '\'') {
            if (i + 3 <= n) {
                try {
                    byte b = (byte) Integer.parseInt(s.substring(i + 1, i + 3), 16);
                    if (skipChars > 0) {
                        skipChars--;
                    } else {
                        emit(new String(new byte[] {b}, charset));
                    }
                } catch (NumberFormatException e) {
                    // некорректная последовательность — пропускаем
                }
            }
            return Math.min(n, i + 3);
        }
        if (d == '*') {
            cur.skip = true; // неизвестное «игнорируемое» назначение
            return i + 1;
        }
        if (d == '~') {
            emit(" ");
            return i + 1;
        }
        if (d == '\n' || d == '\r') {
            emit("\n");
            return i + 1;
        }
        if (!isAsciiLetter(d)) {
            return i + 1; // \-, \_ и прочие управляющие символы
        }
        int j = i;
        while (j < n && isAsciiLetter(s.charAt(j))) {
            j++;
        }
        String word = s.substring(i, j);
        Integer param = null;
        int sign = 1;
        if (j < n && s.charAt(j) == '-') {
            sign = -1;
            j++;
        }
        int ps = j;
        while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
            j++;
        }
        if (j > ps) {
            try {
                param = sign * Integer.parseInt(s.substring(ps, Math.min(j, ps + 9)));
            } catch (NumberFormatException e) {
                param = 0;
            }
        }
        if (j < n && s.charAt(j) == ' ') {
            j++;
        }
        word(word, param);
        return j;
    }

    private void word(String word, Integer param) {
        switch (word) {
            case "ansicpg":
                if (param != null) {
                    try {
                        charset = Charset.forName("windows-" + param);
                    } catch (RuntimeException e) {
                        // неизвестная кодовая страница — остаёмся на текущей
                    }
                }
                break;
            case "uc":
                cur.uc = param == null ? 1 : param;
                break;
            case "u":
                if (param != null) {
                    int code = param < 0 ? param + 65536 : param;
                    emit(String.valueOf((char) code));
                    skipChars = cur.uc;
                }
                break;
            case "par", "line", "sect", "page", "row":
                emit("\n");
                break;
            case "cell", "tab":
                emit("\t");
                break;
            case "emdash":
                emit("—");
                break;
            case "endash":
                emit("–");
                break;
            case "bullet":
                emit("•");
                break;
            case "author", "operator":
                cur.capKey = word;
                cur.cap = new StringBuilder();
                break;
            default:
                if (SKIP.contains(word)) {
                    cur.skip = true;
                }
        }
    }

    private void literal(char c) {
        if (skipChars > 0) {
            skipChars--;
            return;
        }
        emit(c < 0x80 ? String.valueOf(c) : new String(new byte[] {(byte) c}, charset));
    }

    private void emit(String t) {
        if (cur.cap != null) {
            cur.cap.append(t);
        } else if (!cur.skip) {
            out.append(t);
        }
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }
}
