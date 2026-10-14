package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** СНИЛС: 11 цифр с проверкой контрольного числа. */
public class SnilsDetector implements Detector {

    private static final Pattern P = Pattern.compile("(?<!\\d)\\d{3}[- ]?\\d{3}[- ]?\\d{3}[- ]?\\d{2}(?!\\d)");
    private static final Pattern KW = Pattern.compile("снилс|страхов\\p{L}*\\s+(?:номер|свидетельств)|пенсионн\\p{L}*\\s+страхов");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = P.matcher(s);
        while (m.find()) {
            String v = m.group();
            String d = Ctx.digits(v);
            boolean kw = Ctx.has(KW, s, m.start(), 40);
            double c;
            if (valid(d)) {
                c = kw ? 0.98 : (v.length() > 11 ? 0.92 : 0.8);
            } else if (kw) {
                c = 0.6;
            } else {
                continue;
            }
            out.add(new RawMatch(PdType.SNILS, m.start(), m.end(), v, c, "SNILS"));
        }
    }

    static boolean valid(String d) {
        if (d.length() != 11) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (d.charAt(i) - '0') * (9 - i);
        }
        int control = sum;
        if (control > 101) {
            control %= 101;
        }
        if (control == 100 || control == 101) {
            control = 0;
        }
        return control == Integer.parseInt(d.substring(9));
    }
}
