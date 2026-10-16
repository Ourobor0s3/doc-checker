package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.TextUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Прогоняет детекторы по фрагменту и разрешает пересечения (например, 11 цифр — СНИЛС или телефон). */
public class RuleEngine {

    private final List<Detector> detectors = List.of(
            new SnilsDetector(), new InnDetector(), new BankCardDetector(), new OmsPolicyDetector(), new DocNumberDetector(),
            new PhoneDetector(), new EmailDetector(), new BirthDateDetector(), new AddressDetector(),
            new FullNameDetector(), new LabeledSpecialDetector(), new FieldValueDetector());
    private final FieldNameDetector fieldNames = new FieldNameDetector();
    private final MetadataDetector metadata = new MetadataDetector();

    public List<RawMatch> scan(TextUnit u) {
        switch (u.kind()) {
            case HEADER:
                return fieldNames.detect(u.text());
            case META:
                return metadata.detect(u.hint(), u.text());
            default:
                return scanText(u.text(), u.hint());
        }
    }

    public List<RawMatch> scanText(String text, String hint) {
        String prefix = (hint == null || hint.isBlank()) ? "" : hint.strip() + ": ";
        String s = prefix + text;
        List<RawMatch> all = new ArrayList<>();
        for (Detector d : detectors) {
            d.detect(s, prefix.length(), hint, all);
        }
        all.removeIf(m -> m.start() < prefix.length());
        return resolve(all);
    }

    static List<RawMatch> resolve(List<RawMatch> all) {
        List<RawMatch> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble(RawMatch::confidence).reversed()
                .thenComparing(Comparator.comparingInt(RawMatch::length).reversed()));
        List<RawMatch> kept = new ArrayList<>();
        for (RawMatch m : sorted) {
            boolean conflict = false;
            for (RawMatch k : kept) {
                if (conflicts(k, m)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                kept.add(m);
            }
        }
        kept.sort(Comparator.comparingInt(RawMatch::start));
        return kept;
    }

    private static boolean conflicts(RawMatch a, RawMatch b) {
        int overlap = Math.min(a.end(), b.end()) - Math.max(a.start(), b.start());
        if (overlap <= 0) {
            return false;
        }
        if (a.type() == b.type()) {
            return true;
        }
        int union = Math.max(a.end(), b.end()) - Math.min(a.start(), b.start());
        return (double) overlap / union >= 0.5;
    }
}
