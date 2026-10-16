package com.example.docchecker.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Извлечение и починка JSON из ответа модели. Малые модели (gemma 4b) часто обрывают
 * structured output на середине строки или массива — без починки находки теряются.
 */
final class JsonTexts {

    private JsonTexts() {}

    /** Убирает markdown-ограждения и всё лишнее вокруг JSON-объекта. */
    static String extractJson(String content) {
        String s = content == null ? "" : content.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int from = s.indexOf('{');
        int to = s.lastIndexOf('}');
        if (from >= 0 && to > from) {
            return s.substring(from, to + 1);
        }
        if (from >= 0) {
            return s.substring(from);
        }
        return s;
    }

    /**
     * Закрывает оборванные строки, массивы и объекты. Если результат всё ещё не парсится —
     * отбрасывает хвост начиная с последней запятой (незавершённый элемент массива).
     */
    static String repair(String json, ObjectMapper mapper) {
        String candidate = closeStructure(json == null ? "" : json);
        if (parseable(candidate, mapper)) {
            return candidate;
        }
        int comma = candidate.lastIndexOf(',');
        while (comma > 0) {
            String cut = closeStructure(candidate.substring(0, comma));
            if (parseable(cut, mapper)) {
                return cut;
            }
            comma = candidate.lastIndexOf(',', comma - 1);
        }
        return candidate;
    }

    static String closeStructure(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escape = false;
        int brace = 0;
        int bracket = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    if (c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r' || c == 't'
                            || c == 'u') {
                        sb.append(c);
                    }
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    sb.append(c);
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                sb.append(c);
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
            } else if (c == '{') {
                brace++;
                sb.append(c);
            } else if (c == '}') {
                if (brace > 0) {
                    brace--;
                    sb.append(c);
                }
            } else if (c == '[') {
                bracket++;
                sb.append(c);
            } else if (c == ']') {
                if (bracket > 0) {
                    bracket--;
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        if (escape) {
            sb.deleteCharAt(sb.length() - 1);
        }
        if (inString) {
            sb.append('"');
        }
        stripTrailingSeparators(sb);
        while (bracket > 0) {
            sb.append(']');
            bracket--;
        }
        while (brace > 0) {
            sb.append('}');
            brace--;
        }
        return sb.toString();
    }

    private static void stripTrailingSeparators(StringBuilder sb) {
        int i = sb.length() - 1;
        while (i >= 0) {
            char c = sb.charAt(i);
            if (c == ',' || c == ':' || Character.isWhitespace(c)) {
                sb.deleteCharAt(i);
                i--;
            } else {
                break;
            }
        }
    }

    private static boolean parseable(String json, ObjectMapper mapper) {
        try {
            mapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
