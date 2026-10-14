package com.example.docchecker.pd.detect;

import java.util.Locale;
import java.util.regex.Pattern;

/** Вспомогательные функции анализа контекста. */
final class Ctx {

    private Ctx() {}

    static String before(String s, int start, int n) {
        return s.substring(Math.max(0, start - n), start).toLowerCase(Locale.ROOT);
    }

    static String after(String s, int end, int n) {
        return s.substring(end, Math.min(s.length(), end + n)).toLowerCase(Locale.ROOT);
    }

    static boolean has(Pattern p, String s, int start, int n) {
        return p.matcher(before(s, start, n)).find();
    }

    static String digits(String v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static boolean luhn(String d) {
        int sum = 0;
        boolean alt = false;
        for (int i = d.length() - 1; i >= 0; i--) {
            int n = d.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
