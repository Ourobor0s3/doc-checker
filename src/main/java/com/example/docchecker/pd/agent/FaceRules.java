package com.example.docchecker.pd.agent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Решение о том, есть ли на изображении фотография человека. Малые vision-модели часто «видят» лица в рисунках,
 * логотипах, текстурах и пейзажах, поэтому одного ответа «лицо есть» недостаточно. Решение принимается по тому,
 * что модель сказала о типе изображения, сколько лиц насчитала и какие черты лица смогла назвать.
 */
public final class FaceRules {

    public enum Kind {
        PHOTO_OF_PERSON, ID_DOCUMENT, DRAWING, LOGO_OR_ICON, TEXT_DOCUMENT, DIAGRAM_OR_CHART, PHOTO_WITHOUT_PEOPLE, OTHER
    }

    /** @param confidence 0 — лица нет; note — пояснение для отчёта (может быть null) */
    public record Decision(double confidence, String note) {}

    /** Группы признаков лица. Чем больше разных групп названо, тем правдоподобнее ответ. */
    private static final Pattern[] FEATURES = {
            Pattern.compile("(?iu)глаз|взгляд|eye"),
            Pattern.compile("(?iu)нос|nose"),
            Pattern.compile("(?iu)рот|губ|улыб|mouth|lip|smil"),
            Pattern.compile("(?iu)бров|brow"),
            Pattern.compile("(?iu)волос|прическ|боро[дд]|усы|hair|beard"),
            Pattern.compile("(?iu)лиц|портрет|щек|подбород|лоб|face|cheek|chin|forehead")
    };

    private FaceRules() {}

    public static Kind kind(String text) {
        if (text == null) {
            return Kind.OTHER;
        }
        try {
            return Kind.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Kind.OTHER;
        }
    }

    public static int featureCount(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return 0;
        }
        int n = 0;
        for (Pattern p : FEATURES) {
            if (p.matcher(evidence).find()) {
                n++;
            }
        }
        return n;
    }

    public static Decision decide(String kindText, Integer facesCount, String evidence) {
        Kind kind = kind(kindText);
        int faces = facesCount == null ? 0 : facesCount;
        if (faces <= 0) {
            return new Decision(0, null);
        }
        int features = featureCount(evidence);
        switch (kind) {
            case PHOTO_OF_PERSON:
                if (features >= 2) {
                    return new Decision(0.8, null);
                }
                if (features == 1) {
                    return new Decision(0.65, null);
                }
                return new Decision(0.45, "Модель назвала лицо, но не смогла описать его черты: признак слабый");
            case ID_DOCUMENT:
                return features >= 1 ? new Decision(0.75, null)
                        : new Decision(0.55, "В документе отмечено лицо, но его черты не описаны: признак слабый");
            case DRAWING:
                return new Decision(0, "На изображении нарисованное лицо или персонаж, а не фотография человека: не считается");
            case PHOTO_WITHOUT_PEOPLE:
                return new Decision(0, "Ответ модели противоречив (лица есть, а людей нет): лицо не засчитано");
            default:
                return new Decision(0, null);
        }
    }

    /** Текст читаем только там, где он, по словам модели, есть. Для документов и скриншотов читаем всегда. */
    public static boolean needsOcr(Kind kind, Boolean hasReadableText) {
        return Boolean.TRUE.equals(hasReadableText) || kind == Kind.TEXT_DOCUMENT || kind == Kind.ID_DOCUMENT;
    }
}
