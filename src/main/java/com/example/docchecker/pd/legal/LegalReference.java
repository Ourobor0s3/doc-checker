package com.example.docchecker.pd.legal;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.pd.model.LegalBasis;
import com.example.docchecker.pd.model.PdType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Справочник «тип признака → норма 152-ФЗ и объяснение». Берётся из resources/pd/legal-basis.json
 * или из файла {@code app.pd.legal-basis-file}, поэтому правится без пересборки.
 */
@Component
public class LegalReference {

    public record TypeInfo(String label, String category, String article, String explanation) {}

    public record Doc(String act, String disclaimer, Map<String, TypeInfo> types) {}

    private final Doc doc;

    public LegalReference(AppProperties props, ObjectMapper mapper) throws IOException {
        String file = props.pd().legalBasisFile();
        try (InputStream in = (file == null || file.isBlank())
                ? new ClassPathResource("pd/legal-basis.json").getInputStream()
                : Files.newInputStream(Path.of(file))) {
            this.doc = mapper.readValue(in, Doc.class);
        }
    }

    public Doc document() {
        return doc;
    }

    public String disclaimer() {
        return doc.disclaimer();
    }

    public String label(PdType type) {
        return info(type).label();
    }

    public LegalBasis basis(PdType type) {
        TypeInfo i = info(type);
        return new LegalBasis(doc.act(), i.article(), i.category(), i.explanation());
    }

    private TypeInfo info(PdType type) {
        TypeInfo i = doc.types().get(type.name());
        return i != null ? i : doc.types().get(PdType.OTHER_PERSONAL.name());
    }
}
