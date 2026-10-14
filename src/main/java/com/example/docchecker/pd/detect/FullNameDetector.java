package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ФИО по шаблонам: «Фамилия Имя Отчество», «Имя Отчество Фамилия», «Фамилия И. О.», «И. О. Фамилия». */
public class FullNameDetector implements Detector {

    private static final String SP = "[ \\t\\u00A0]+";
    private static final String CAP = "[А-ЯЁ][а-яё]{1,24}(?:-[А-ЯЁ][а-яё]{1,24})?";
    private static final String PATR_SIMPLE = "[А-ЯЁ][а-яё]{1,20}(?:ович|евич|ьич|овн|евн|ичн|ьичн)[а-яё]{0,3}";
    private static final String PATR = "(?:" + PATR_SIMPLE + "|[А-ЯЁ][а-яё]{1,20}\\s?(?:кызы|оглы|улы))";
    private static final String NO_LETTER_BEFORE = "(?<![А-ЯЁа-яё])";
    private static final String NO_LOWER_AFTER = "(?![а-яё])";

    private static final Pattern FIO = Pattern.compile(NO_LETTER_BEFORE + CAP + SP + CAP + SP + PATR + NO_LOWER_AFTER);
    private static final Pattern IOF = Pattern.compile(NO_LETTER_BEFORE + CAP + SP + PATR_SIMPLE + SP + CAP + NO_LOWER_AFTER);
    private static final Pattern SURNAME_INITIALS = Pattern.compile(
            NO_LETTER_BEFORE + CAP + SP + "[А-ЯЁ]\\.[ \\t\\u00A0]?[А-ЯЁ]\\.");
    private static final Pattern INITIALS_SURNAME = Pattern.compile(
            NO_LETTER_BEFORE + "[А-ЯЁ]\\.[ \\t\\u00A0]?[А-ЯЁ]\\.[ \\t\\u00A0]?" + CAP + NO_LOWER_AFTER);

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        add(FIO, s, 0.9, "FIO_PATTERN", out);
        add(IOF, s, 0.88, "FIO_PATTERN", out);
        add(SURNAME_INITIALS, s, 0.8, "NAME_INITIALS", out);
        add(INITIALS_SURNAME, s, 0.8, "NAME_INITIALS", out);
    }

    private static void add(Pattern p, String s, double c, String name, List<RawMatch> out) {
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(new RawMatch(PdType.FULL_NAME, m.start(), m.end(), m.group(), c, name));
        }
    }
}
