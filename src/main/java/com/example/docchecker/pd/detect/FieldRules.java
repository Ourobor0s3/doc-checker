package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Pattern;

/** Соответствие названий полей/столбцов типам персональных данных. Порядок правил важен: срабатывает первое. */
final class FieldRules {

    record Rule(Pattern pattern, PdType type) {}

    private static final Pattern NOT_AN_ADDRESS = Pattern.compile("(?iu)e-?mail|ip\\s*address|mac\\s*address|web|url|почт|сайт");

    static final List<Rule> RULES = List.of(
            rule(PdType.ADDRESS, "место\\s+рождения|birth.?place|place.?of.?birth"),
            rule(PdType.BIRTH_DATE, "дата\\s*рожд\\p{L}*|день\\s+рождения|д\\.\\s?р\\.?|birth.?date|date.?of.?birth|birthday|dob|рождения"),
            rule(PdType.FULL_NAME, "фио|ф\\.\\s?и\\.\\s?о\\.?|фамили\\p{L}*|имя|отчество|fio|full.?name|first.?name|last.?name"
                    + "|sur.?name|patronymic|middle.?name|given.?name|family.?name"),
            rule(PdType.PHONE, "тел\\p{L}*|phone|mobile|моб\\p{L}*|msisdn"),
            rule(PdType.PASSPORT_RF, "паспорт\\p{L}*|passport"),
            rule(PdType.SNILS, "снилс|snils"),
            rule(PdType.INN, "инн|inn|tax.?id"),
            rule(PdType.ADDRESS, "адрес\\p{L}*|address|прописк\\p{L}*|проживан\\p{L}*|место\\s+жительства"),
            rule(PdType.BANK_CARD, "номер\\s+карты|банковск\\p{L}*\\s+карт\\p{L}*|card.?(?:number|no|num)|pan|cc.?num"),
            rule(PdType.HEALTH_DATA, "диагноз|diagnos\\p{L}*|заболевани\\p{L}*|инвалидност\\p{L}*|анамнез|мкб"),
            rule(PdType.RELIGION_BELIEFS, "религи\\p{L}*|вероисповедани\\p{L}*|religion"),
            rule(PdType.POLITICAL_VIEWS, "партийност\\p{L}*|политическ\\p{L}*\\s+взгляд\\p{L}*"),
            rule(PdType.RACE_ETHNICITY, "национальност\\p{L}*|ethnic\\p{L}*|race"),
            rule(PdType.CRIMINAL_RECORD, "судимост\\p{L}*|criminal.?record|conviction"));

    private FieldRules() {}

    private static Rule rule(PdType type, String alternatives) {
        return new Rule(Pattern.compile("(?iu)(?<!\\p{L})(?:" + alternatives + ")(?!\\p{L})"), type);
    }

    static Rule match(String hint) {
        if (hint == null || hint.isBlank() || hint.length() > 80) {
            return null;
        }
        for (Rule r : RULES) {
            if (r.pattern().matcher(hint).find()) {
                if (r.type() == PdType.ADDRESS && NOT_AN_ADDRESS.matcher(hint).find()) {
                    continue;
                }
                return r;
            }
        }
        return null;
    }
}
