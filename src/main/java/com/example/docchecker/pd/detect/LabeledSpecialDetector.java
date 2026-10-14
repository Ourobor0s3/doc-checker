package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Специальные категории в явных подписях вида «Диагноз: …», «Национальность: …». */
public class LabeledSpecialDetector implements Detector {

    private static final Pattern P = Pattern.compile(
            "(?iu)(диагноз|национальност\\p{L}*|вероисповедани\\p{L}*|религи\\p{L}*|партийност\\p{L}*|судимост\\p{L}*"
                    + "|состояни\\p{L}*\\s+здоровья|группа\\s+инвалидности|инвалидност\\p{L}*)\\s*[:\\-–—]\\s*([^\\n;]{2,80})");
    static final Pattern NEGATIVE = Pattern.compile(
            "(?iu)^\\s*(?:нет|не\\s+(?:имеет|имел\\p{L}*|судим\\p{L}*|состо\\p{L}*|указан\\p{L}*)|отсутств\\p{L}*|-|—|н/д)\\s*\\.?\\s*$");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = P.matcher(s);
        while (m.find()) {
            PdType type = typeOf(m.group(1).toLowerCase());
            double c = NEGATIVE.matcher(m.group(2)).find() ? 0.5 : 0.8;
            out.add(new RawMatch(type, m.start(2), m.end(2), m.group(2).strip(), c, "LABELED_FIELD"));
        }
    }

    static PdType typeOf(String key) {
        if (key.startsWith("национал")) {
            return PdType.RACE_ETHNICITY;
        }
        if (key.startsWith("вероисп") || key.startsWith("религ")) {
            return PdType.RELIGION_BELIEFS;
        }
        if (key.startsWith("партий")) {
            return PdType.POLITICAL_VIEWS;
        }
        if (key.startsWith("судим")) {
            return PdType.CRIMINAL_RECORD;
        }
        return PdType.HEALTH_DATA;
    }
}
