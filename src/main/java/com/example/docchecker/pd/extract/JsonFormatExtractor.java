package com.example.docchecker.pd.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** JSON и JSON Lines: значения с путями вида {@code $.users[3].phone}, имена ключей — как заголовки полей. */
@Component
public class JsonFormatExtractor implements FormatExtractor {

    private static final int MAX_DEPTH = 64;
    private final ObjectMapper mapper;

    public JsonFormatExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.JSON);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) {
        String text = TextDecoder.decode(data);
        List<JsonNode> roots = new ArrayList<>();
        try (MappingIterator<JsonNode> it = mapper.readerFor(JsonNode.class).readValues(text)) {
            while (it.hasNextValue()) {
                roots.add(it.nextValue());
            }
        } catch (IOException e) {
            if (roots.isEmpty()) {
                out.note("Некорректный JSON: файл обработан как обычный текст");
                TextFormatExtractor.addLines(out, text);
                return;
            }
            out.partial("JSON разобран не полностью: " + e.getMessage());
        }
        Set<String> seenKeys = new HashSet<>();
        if (roots.size() == 1) {
            walk(roots.get(0), "$", null, seenKeys, out, 0);
        } else {
            for (int i = 0; i < roots.size(); i++) {
                if (!walk(roots.get(i), "$[" + i + "]", null, seenKeys, out, 0)) {
                    break;
                }
            }
        }
    }

    private boolean walk(JsonNode n, String path, String hint, Set<String> seenKeys, ExtractionResult out, int depth) {
        if (depth > MAX_DEPTH) {
            out.partial("Слишком глубокая вложенность JSON: часть данных пропущена");
            return true;
        }
        if (n.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String childPath = path + "." + e.getKey();
                if (seenKeys.add(e.getKey())) {
                    out.header(childPath, e.getKey());
                    if (e.getKey().length() >= 5 && !e.getKey().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        out.text(childPath + " [ключ]", e.getKey());
                    }
                }
                if (!walk(e.getValue(), childPath, e.getKey(), seenKeys, out, depth + 1)) {
                    return false;
                }
            }
        } else if (n.isArray()) {
            int i = 0;
            for (JsonNode child : n) {
                if (!walk(child, path + "[" + i++ + "]", hint, seenKeys, out, depth + 1)) {
                    return false;
                }
            }
        } else if (!n.isNull() && !n.isMissingNode()) {
            return out.field(path, n.asText(), hint);
        }
        return true;
    }
}
