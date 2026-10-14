package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Телефоны: российские форматы, международные с «+», номера с подписью «тел.». */
public class PhoneDetector implements Detector {

    private static final Pattern RU = Pattern.compile(
            "(?<![\\d+])(?:\\+7|8|7)[\\s\\-]?(?:\\(\\d{3}\\)|\\d{3})[\\s\\-]?\\d{3}[\\s\\-]?\\d{2}[\\s\\-]?\\d{2}(?!\\d)");
    private static final Pattern LABELED = Pattern.compile(
            "(?iu)(?:тел\\p{L}*|phone|моб\\p{L}*|факс|whatsapp|viber|telegram)\\.?[^\\d+\\n]{0,15}(\\+?\\d[\\d\\s\\-()]{5,18}\\d)(?!\\d)");
    private static final Pattern INTL = Pattern.compile(
            "(?<![\\d+])\\+(?!7)\\d{1,3}[\\s\\-]?\\(?\\d{2,4}\\)?[\\s\\-]?\\d{3}[\\s\\-]?\\d{2,4}(?:[\\s\\-]?\\d{2,4})?(?!\\d)");
    private static final Pattern KW = Pattern.compile("тел\\p{L}*|phone|моб\\p{L}*|whatsapp|viber|telegram");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = RU.matcher(s);
        while (m.find()) {
            String d = Ctx.digits(m.group());
            String code = d.substring(1, 4);
            if ("800".equals(code)) {
                continue; // бесплатные номера организаций
            }
            boolean mobile = code.charAt(0) == '9';
            double c = mobile ? 0.9 : 0.5; // городские номера чаще принадлежат организациям
            if (Ctx.has(KW, s, m.start(), 30)) {
                c = mobile ? 0.95 : 0.55;
            }
            out.add(new RawMatch(PdType.PHONE, m.start(), m.end(), m.group(), c, "PHONE"));
        }
        m = LABELED.matcher(s);
        while (m.find()) {
            String d = Ctx.digits(m.group(1));
            if (d.length() < 7 || d.length() > 15) {
                continue;
            }
            double c = (d.length() == 10 && d.charAt(0) == '9') ? 0.85 : (d.length() == 7 ? 0.5 : 0.55);
            out.add(new RawMatch(PdType.PHONE, m.start(1), m.end(1), m.group(1), c, "PHONE_LABELED"));
        }
        m = INTL.matcher(s);
        while (m.find()) {
            int digits = Ctx.digits(m.group()).length();
            if (digits >= 9 && digits <= 15) {
                out.add(new RawMatch(PdType.PHONE, m.start(), m.end(), m.group(), 0.7, "PHONE_INTL"));
            }
        }
    }
}
