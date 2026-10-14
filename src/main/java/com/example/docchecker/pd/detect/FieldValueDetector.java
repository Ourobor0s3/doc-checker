package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Pattern;

/** Значение в поле/столбце, название которого указывает на персональные данные («ФИО», «Телефон», «СНИЛС» …). */
public class FieldValueDetector implements Detector {

    private static final Pattern NAME_CHARS = Pattern.compile("^[\\p{L}][\\p{L} .'\\-]{1,79}$");
    private static final Pattern DATE_LIKE = Pattern.compile(
            "\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}|\\d{1,2}\\s+\\p{L}+\\s+\\d{4}");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        FieldRules.Rule rule = FieldRules.match(hint);
        if (rule == null || valueStart > s.length()) {
            return;
        }
        String value = s.substring(valueStart).strip();
        if (value.isEmpty() || value.length() > 300) {
            return;
        }
        String digits = Ctx.digits(value);
        double c = 0;
        switch (rule.type()) {
            case FULL_NAME -> c = looksLikeName(value) ? 0.85 : 0;
            case PHONE -> c = digits.length() >= 7 ? 0.85 : 0;
            case PASSPORT_RF -> c = digits.length() >= 6 ? 0.9 : 0;
            case SNILS -> c = digits.length() == 11 ? 0.9 : 0;
            case INN -> c = digits.length() == 12 ? 0.9 : 0;
            case BIRTH_DATE -> c = DATE_LIKE.matcher(value).find() ? 0.88 : 0;
            case ADDRESS -> c = value.length() >= 8 && value.matches(".*\\p{L}.*") ? 0.8 : 0;
            case BANK_CARD -> c = digits.length() >= 13 && digits.length() <= 19 && Ctx.luhn(digits) ? 0.9 : 0;
            default -> c = LabeledSpecialDetector.NEGATIVE.matcher(value).find() ? 0 : 0.75;
        }
        if (c > 0) {
            int start = s.indexOf(value, valueStart);
            out.add(new RawMatch(rule.type(), start, start + value.length(), value, c, "FIELD_VALUE"));
        }
    }

    private static boolean looksLikeName(String v) {
        return NAME_CHARS.matcher(v).matches()
                && !LabeledSpecialDetector.NEGATIVE.matcher(v).find()
                && !v.contains("@");
    }
}
