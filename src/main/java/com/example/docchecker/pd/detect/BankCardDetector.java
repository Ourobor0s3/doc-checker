package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Номер банковской карты: 13–19 цифр, алгоритм Луна, допустимая первая цифра. */
public class BankCardDetector implements Detector {

    private static final Pattern P = Pattern.compile("(?<!\\d)(?:\\d{4}[ -]?){3}\\d{1,7}(?!\\d)");
    private static final Pattern KW = Pattern.compile("карт\\p{L}*|card|visa|mastercard|мир\\b");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = P.matcher(s);
        while (m.find()) {
            String v = m.group();
            String d = Ctx.digits(v);
            if (d.length() < 13 || d.length() > 19 || !plausibleIssuer(d) || !Ctx.luhn(d)) {
                continue;
            }
            boolean grouped = v.length() > d.length();
            boolean kw = Ctx.has(KW, s, m.start(), 30);
            double c = grouped ? 0.93 : (kw ? 0.85 : 0.5);
            out.add(new RawMatch(PdType.BANK_CARD, m.start(), m.end(), v, c, "CARD_LUHN"));
        }
    }

    /** Диапазоны номеров платёжных систем: отсекает случайные числа, формально проходящие алгоритм Луна. */
    static boolean plausibleIssuer(String d) {
        int p2 = Integer.parseInt(d.substring(0, 2));
        int p4 = Integer.parseInt(d.substring(0, 4));
        switch (d.charAt(0)) {
            case '4':
                return true;                                   // Visa
            case '5', '6':
                return true;                                   // Mastercard, Maestro, UnionPay, Discover
            case '2':
                return p4 >= 2200 && p4 <= 2204 || p4 >= 2221 && p4 <= 2720; // Мир, Mastercard
            case '3':
                return p2 == 34 || p2 == 37 || p2 >= 35 && p2 <= 36;         // Amex, JCB, Diners
            default:
                return false;
        }
    }
}
