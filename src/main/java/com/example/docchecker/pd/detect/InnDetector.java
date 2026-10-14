package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ИНН физического лица: 12 цифр с проверкой двух контрольных чисел.
 * Десятизначный ИНН относится к юридическим лицам и не считается персональными данными.
 */
public class InnDetector implements Detector {

    private static final Pattern P = Pattern.compile("(?<!\\d)\\d{12}(?!\\d)");
    private static final Pattern KW = Pattern.compile("инн|налогоплательщик|tax.?id");
    private static final int[] W11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] W12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = P.matcher(s);
        while (m.find()) {
            String d = m.group();
            boolean kw = Ctx.has(KW, s, m.start(), 30);
            boolean valid = valid(d);
            double c;
            if (valid) {
                c = kw ? 0.97 : 0.55;
            } else if (kw) {
                c = 0.5;
            } else {
                continue;
            }
            out.add(new RawMatch(PdType.INN, m.start(), m.end(), d, c, "INN"));
        }
    }

    static boolean valid(String d) {
        if (d.length() != 12) {
            return false;
        }
        return check(d, W11, 10) && check(d, W12, 11);
    }

    private static boolean check(String d, int[] w, int pos) {
        int sum = 0;
        for (int i = 0; i < w.length; i++) {
            sum += (d.charAt(i) - '0') * w[i];
        }
        return sum % 11 % 10 == d.charAt(pos) - '0';
    }
}
