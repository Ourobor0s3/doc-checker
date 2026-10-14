package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;

/** Слабый признак: в структуре данных есть поле, предназначенное для персональных данных. */
public class FieldNameDetector {

    public List<RawMatch> detect(String header) {
        FieldRules.Rule r = FieldRules.match(header);
        if (r == null) {
            return List.of();
        }
        String v = header.strip();
        return List.of(new RawMatch(PdType.FIELD_NAME, 0, v.length(), v, 0.55, "FIELD_NAME"));
    }
}
