package com.example.docchecker.pd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.docchecker.pd.detect.Masker;
import com.example.docchecker.pd.detect.RawMatch;
import com.example.docchecker.pd.detect.RuleEngine;
import com.example.docchecker.pd.model.PdType;
import com.example.docchecker.pd.model.TextKind;
import com.example.docchecker.pd.model.TextUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdDetectorsTest {

    private final RuleEngine engine = new RuleEngine();

    private List<RawMatch> scan(String text) {
        return engine.scanText(text, null);
    }

    private static boolean has(List<RawMatch> r, PdType t) {
        return r.stream().anyMatch(m -> m.type() == t);
    }

    @Test
    void snilsRequiresValidChecksum() {
        assertTrue(has(scan("СНИЛС 112-233-445 95"), PdType.SNILS));
        assertFalse(has(scan("номер 112-233-445 96"), PdType.SNILS));
    }

    @Test
    void innIsTwelveDigitsWithChecksum() {
        assertEquals(0.97, scan("ИНН 500100732259").get(0).confidence(), 1e-9);
        assertTrue(scan("ИНН 7707083893").isEmpty(), "десятизначный ИНН принадлежит организации");
    }

    @Test
    void bankCardUsesLuhn() {
        assertTrue(has(scan("карта 4111 1111 1111 1111"), PdType.BANK_CARD));
        assertFalse(has(scan("4111 1111 1111 1112"), PdType.BANK_CARD));
    }

    @Test
    void passportAndDriverLicenseAreToldApartByContext() {
        assertTrue(has(scan("паспорт серия 45 12 № 123456"), PdType.PASSPORT_RF));
        assertTrue(has(scan("водительское удостоверение 99 21 654321"), PdType.DRIVER_LICENSE));
        assertTrue(scan("заказ 4512123456").isEmpty());
    }

    @Test
    void phonesAndEmails() {
        assertTrue(has(scan("тел. +7 (916) 123-45-67"), PdType.PHONE));
        assertTrue(scan("8 800 555 35 35").isEmpty(), "бесплатный номер организации");
        assertEquals(0.9, scan("ivan.petrov@mail.ru").get(0).confidence(), 1e-9);
        assertTrue(scan("info@company.ru").get(0).confidence() < 0.6, "ролевой ящик — слабый признак");
    }

    @Test
    void birthDateNeedsContext() {
        assertTrue(has(scan("родился 12.05.1985 в Москве"), PdType.BIRTH_DATE));
        assertTrue(has(scan("15 мая 1990 г.р."), PdType.BIRTH_DATE));
        assertTrue(scan("договор от 12.05.2023").isEmpty());
    }

    @Test
    void fullNamesAndOrganisations() {
        assertTrue(has(scan("Иванов Иван Иванович проживает"), PdType.FULL_NAME));
        assertTrue(has(scan("выдан Петровой Анне Сергеевне"), PdType.FULL_NAME));
        assertTrue(has(scan("подпись: Сидоров А.Б."), PdType.FULL_NAME));
        assertTrue(scan("Российская Федерация Совет Министров").isEmpty());
    }

    @Test
    void addresses() {
        assertEquals(0.85, scan("г. Москва, ул. Ленина, д. 5, кв. 12").get(0).confidence(), 1e-9);
        assertEquals(0.5, scan("ул. Тверская, д. 7").get(0).confidence(), 1e-9, "без квартиры — слабый признак");
    }

    @Test
    void specialCategoriesInLabelledFields() {
        assertEquals(PdType.HEALTH_DATA, scan("Диагноз: сахарный диабет").get(0).type());
        assertEquals(0.5, scan("Судимость: не имеет").get(0).confidence(), 1e-9);
    }

    @Test
    void fieldNameGivesContextToValue() {
        assertEquals(PdType.PHONE, engine.scanText("89161234567", "Телефон").get(0).type());
        assertEquals(PdType.FULL_NAME, engine.scanText("Иван", "Имя").get(0).type());
        assertEquals(PdType.EMAIL, engine.scanText("a@b.com", "Email address").get(0).type());
    }

    @Test
    void headersAndMetadata() {
        assertEquals(1, engine.scan(new TextUnit("x", "ФИО", null, TextKind.HEADER)).size());
        assertEquals(0, engine.scan(new TextUnit("x", "Название", null, TextKind.HEADER)).size());
        assertEquals(1, engine.scan(new TextUnit("m", "Иванов Иван", "author", TextKind.META)).size());
        assertEquals(0, engine.scan(new TextUnit("m", "admin", "author", TextKind.META)).size());
    }

    @Test
    void valuesAreMasked() {
        assertEquals("***-***-*** 95", Masker.mask(PdType.SNILS, "112-233-445 95"));
        assertEquals("**** **** **** 1111", Masker.mask(PdType.BANK_CARD, "4111 1111 1111 1111"));
        assertEquals("i***@mail.ru", Masker.mask(PdType.EMAIL, "ivan.petrov@mail.ru"));
        assertEquals("**.**.1985", Masker.mask(PdType.BIRTH_DATE, "12.05.1985"));
        assertFalse(Masker.mask(PdType.FULL_NAME, "Иванов Иван Иванович").contains("Иванов"));
    }
}
