package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.time.Year;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Дата рождения: любая дата, рядом с которой есть указание на рождение. Даты без такого контекста не считаются. */
public class BirthDateDetector implements Detector {

    private static final Pattern NUM = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(?!\\d)");
    private static final Pattern ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern WORD = Pattern.compile(
            "(?iu)(?<!\\d)(\\d{1,2})\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+(\\d{4})");
    private static final Pattern KW_BEFORE = Pattern.compile(
            "родил\\p{L}*|рожд\\p{L}*|д\\.\\s?р\\.?|г\\.\\s?р\\.?|birth\\p{L}*|\\bdob\\b|\\bborn\\b");
    private static final Pattern KW_AFTER = Pattern.compile("^\\s*\\(?\\s*(?:г\\.\\s?р\\.?|года рождения|г\\. рождения|д\\.\\s?р\\.?)");
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля",
            "августа", "сентября", "октября", "ноября", "декабря"};

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = NUM.matcher(s);
        while (m.find()) {
            if (ok(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))) {
                add(s, m, out);
            }
        }
        m = ISO.matcher(s);
        while (m.find()) {
            if (ok(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)))) {
                add(s, m, out);
            }
        }
        m = WORD.matcher(s);
        while (m.find()) {
            String month = m.group(2).toLowerCase();
            int mi = 0;
            for (int i = 0; i < MONTHS.length; i++) {
                if (MONTHS[i].equals(month)) {
                    mi = i + 1;
                }
            }
            if (ok(Integer.parseInt(m.group(1)), mi, Integer.parseInt(m.group(3)))) {
                add(s, m, out);
            }
        }
    }

    private static void add(String s, Matcher m, List<RawMatch> out) {
        if (Ctx.has(KW_BEFORE, s, m.start(), 45) || KW_AFTER.matcher(Ctx.after(s, m.end(), 20)).find()) {
            out.add(new RawMatch(PdType.BIRTH_DATE, m.start(), m.end(), m.group(), 0.92, "BIRTH_DATE"));
        }
    }

    static boolean ok(int day, int month, int year) {
        return day >= 1 && day <= 31 && month >= 1 && month <= 12
                && year >= 1900 && year <= Year.now().getValue();
    }
}
