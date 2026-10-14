package com.example.docchecker.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Эвристическое разбиение текста на требования без LLM. */
public final class RequirementSplitter {

    private static final Pattern MARKER = Pattern.compile(
            "^\\s*(?:[-*•–—]|\\d+(?:\\.\\d+)+[.)]?|\\d+[.)]|[a-zA-Zа-яА-Я][.)])\\s+(.*)$");

    private RequirementSplitter() {}

    public static List<String> splitLines(String text) {
        String[] lines = text.replace("\r", "").split("\n");
        boolean hasMarkers = Arrays.stream(lines).anyMatch(l -> MARKER.matcher(l).matches());

        List<String> out = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher m = MARKER.matcher(raw);
            if (m.matches()) {
                out.add(m.group(1).strip());
            } else if (line.endsWith(":")) {
                // заголовок вида «Документ должен:» — пропускаем
                continue;
            } else if (hasMarkers && !out.isEmpty() && !endsSentence(out.get(out.size() - 1))) {
                // продолжение пункта списка на следующей строке
                int last = out.size() - 1;
                out.set(last, out.get(last) + " " + line);
            } else {
                out.add(line);
            }
        }

        if (!hasMarkers && out.size() == 1) {
            out = new ArrayList<>(Arrays.asList(out.get(0).split("(?<=[.!?;])\\s+")));
        }
        return out.stream().map(String::strip).filter(s -> s.length() >= 3).toList();
    }

    private static boolean endsSentence(String s) {
        return s.endsWith(".") || s.endsWith(";") || s.endsWith("!") || s.endsWith("?");
    }
}
