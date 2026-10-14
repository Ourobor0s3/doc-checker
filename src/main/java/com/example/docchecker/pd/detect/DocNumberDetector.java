package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Паспорт РФ и водительское удостоверение: серия и номер, тип определяется по контексту. */
public class DocNumberDetector implements Detector {

    private static final Pattern SERIES = Pattern.compile(
            "(?iu)сер(?:ия|ии|\\.)?\\s*(\\d{2}\\s?\\d{2})\\s*(?:№|n|номер|ном\\.?)?\\s*(\\d{6})(?!\\d)");
    private static final Pattern PLAIN = Pattern.compile("(?<!\\d)\\d{2}[ -]?\\d{2}[ -]?\\d{6}(?!\\d)");
    private static final Pattern LETTERS = Pattern.compile("(?iu)(?<!\\d)\\d{2}\\s?[A-ZА-Я]{2}\\s?\\d{6}(?!\\d)");
    private static final Pattern PASS_KW = Pattern.compile("паспорт|пасп\\.|удостоверени\\p{L}*\\s+личност|серия и номер|passport");
    private static final Pattern DL_KW = Pattern.compile("водител\\p{L}*|в/у|\\bву\\b|водит\\.|права\\s+(?:категори|серия)|driver");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = SERIES.matcher(s);
        while (m.find()) {
            boolean dl = Ctx.has(DL_KW, s, m.start(), 50);
            out.add(new RawMatch(dl ? PdType.DRIVER_LICENSE : PdType.PASSPORT_RF, m.start(1), m.end(2),
                    s.substring(m.start(1), m.end(2)), dl ? 0.93 : 0.95, "DOC_SERIES"));
        }
        m = PLAIN.matcher(s);
        while (m.find()) {
            boolean pass = Ctx.has(PASS_KW, s, m.start(), 50);
            boolean dl = Ctx.has(DL_KW, s, m.start(), 50);
            String v = m.group();
            if (dl) {
                out.add(new RawMatch(PdType.DRIVER_LICENSE, m.start(), m.end(), v, 0.92, "DOC_NUMBER"));
            } else if (pass) {
                out.add(new RawMatch(PdType.PASSPORT_RF, m.start(), m.end(), v, 0.95, "DOC_NUMBER"));
            } else if (v.indexOf(' ') >= 0 || v.indexOf('-') >= 0) {
                out.add(new RawMatch(PdType.PASSPORT_RF, m.start(), m.end(), v, 0.5, "DOC_NUMBER"));
            }
        }
        m = LETTERS.matcher(s);
        while (m.find()) {
            if (Ctx.has(DL_KW, s, m.start(), 50)) {
                out.add(new RawMatch(PdType.DRIVER_LICENSE, m.start(), m.end(), m.group(), 0.92, "DOC_NUMBER"));
            }
        }
    }
}
