package com.example.docchecker.pd.agent;

import com.example.docchecker.pd.model.PdType;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Проверка цитат, которые вернула LLM. Цитата и так обязана дословно присутствовать в тексте; здесь отсекаются
 * ответы неправдоподобного вида (названия организаций вместо ФИО, «адреса» без номера дома и т. п.).
 */
public final class EntityRules {

    private static final Pattern NAME_TOKEN = Pattern.compile("^\\p{Lu}[\\p{L}'\\-]*\\.?$|^(?:\\p{Lu}\\.){1,3}$");
    private static final Set<String> NOT_A_PERSON = Set.of("ооо", "оао", "зао", "пао", "ао", "ип", "банк", "компания",
            "министерство", "федерация", "российская", "правительство", "администрация", "университет", "институт",
            "школа", "больница", "поликлиника", "завод", "фонд", "суд", "отдел", "управление", "департамент", "центр",
            "союз", "ассоциация", "общество", "корпорация", "служба", "комитет", "комиссия", "группа", "group", "ltd",
            "llc", "inc", "corp", "company", "bank", "university");

    private EntityRules() {}

    /** @return уверенность для цитаты данного типа; 0 — цитата отклонена */
    public static double confidence(PdType type, String quote) {
        if (quote == null) {
            return 0;
        }
        String q = quote.strip();
        if (q.length() < 4 || q.length() > 200) {
            return 0;
        }
        switch (type) {
            case FULL_NAME:
                return looksLikePersonName(q) ? 0.7 : 0;
            case ADDRESS:
                return q.length() >= 8 && q.matches(".*\\d.*") && q.matches(".*\\p{L}.*") ? 0.65 : 0;
            default:
                // специальные категории, найденные только моделью, — слабый признак; правило с подписью «Диагноз:» усилит его
                return 0.55;
        }
    }

    static boolean looksLikePersonName(String q) {
        if (q.matches(".*\\d.*")) {
            return false;
        }
        String[] tokens = q.split("\\s+");
        if (tokens.length < 2 || tokens.length > 5) {
            return false;
        }
        for (String t : tokens) {
            if (!NAME_TOKEN.matcher(t).matches() || NOT_A_PERSON.contains(t.toLowerCase().replace(".", ""))) {
                return false;
            }
        }
        return true;
    }
}
