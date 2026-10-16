package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Полис ОМС нового образца: 16 цифр. Без слова «полис»/«ОМС» рядом число не считается. */
public class OmsPolicyDetector implements Detector {

    private static final Pattern P = Pattern.compile("(?<!\\d)\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)");
    private static final Pattern KW = Pattern.compile("полис\\p{L}*|\\bомс\\b|медицинск\\p{L}*\\s+страхов|\\boms\\b");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = P.matcher(s);
        while (m.find()) {
            if (Ctx.has(KW, s, m.start(), 40)) {
                out.add(new RawMatch(PdType.OMS_POLICY, m.start(), m.end(), m.group(), 0.95, "OMS"));
            }
        }
    }
}
