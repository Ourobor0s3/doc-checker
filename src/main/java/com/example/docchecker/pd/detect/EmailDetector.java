package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailDetector implements Detector {

    private static final Pattern P = Pattern.compile(
            "(?<![\\w.+\\-])[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}");
    private static final Set<String> ROLE = Set.of("info", "support", "admin", "noreply", "no-reply", "sales",
            "office", "contact", "mail", "hello", "help", "service", "hr", "press", "pr", "mailer-daemon");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = P.matcher(s);
        while (m.find()) {
            String local = m.group().substring(0, m.group().indexOf('@')).toLowerCase(Locale.ROOT);
            double c = ROLE.contains(local) ? 0.4 : 0.9; // ролевые ящики — как правило, не персональные
            out.add(new RawMatch(PdType.EMAIL, m.start(), m.end(), m.group(), c, "EMAIL"));
        }
    }
}
