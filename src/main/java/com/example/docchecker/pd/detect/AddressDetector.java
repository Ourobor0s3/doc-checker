package com.example.docchecker.pd.detect;

import com.example.docchecker.pd.model.PdType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Адреса. Улица + дом + квартира — вероятный адрес физического лица; улица + дом без квартиры — слабый признак
 * (так обычно выглядит адрес организации). Фразы «зарегистрирован по адресу…» — сильный признак.
 */
public class AddressDetector implements Detector {

    private static final String STREET = "(?:ул\\.|улица|пр-кт|пр-т|просп\\.|проспект|пер\\.|переулок|ш\\.|шоссе|наб\\.|набережная"
            + "|б-р|бульвар|пл\\.|площадь|мкр\\.|микрорайон|проезд|аллея)";
    private static final Pattern STREET_ADDR = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:г\\.\\s?[А-ЯЁа-яё\\-]{2,25},?\\s*)?" + STREET
                    + "\\s*[А-ЯЁа-яё0-9\\-. ]{2,40}?,?\\s*(?:д\\.|дом)?\\s*\\d{1,4}[А-Яа-я]?"
                    + "(?:\\s*(?:/|к\\.?|корп\\.?|стр\\.?)\\s*\\d{1,3})?"
                    + "(?:,?\\s*(?:кв\\.?|квартира|оф\\.?|офис)\\s*\\d{1,4})?");
    private static final Pattern APARTMENT = Pattern.compile("(?iu)(?:кв\\.?|квартира)\\s*\\d");
    private static final Pattern PHRASE = Pattern.compile(
            "(?iu)(?:зарегистрирован\\p{L}*|проживающ\\p{L}*|проживает|адрес\\s+(?:регистрации|проживания|места\\s+жительства|прописки)|прописк\\p{L}+)"
                    + "\\s*(?:по\\s+адресу)?\\s*[:\\-]?\\s*([А-ЯЁа-яёA-Za-z0-9][^\\n;]{9,120})");

    @Override
    public void detect(String s, int valueStart, String hint, List<RawMatch> out) {
        Matcher m = STREET_ADDR.matcher(s);
        while (m.find()) {
            boolean apartment = APARTMENT.matcher(m.group()).find();
            out.add(new RawMatch(PdType.ADDRESS, m.start(), m.end(), m.group().strip(), apartment ? 0.85 : 0.5, "ADDRESS"));
        }
        m = PHRASE.matcher(s);
        while (m.find()) {
            String v = m.group(1);
            boolean digit = v.matches(".*\\d.*");
            boolean letter = v.matches(".*\\p{L}.*");
            if (digit && letter) {
                out.add(new RawMatch(PdType.ADDRESS, m.start(1), m.end(1), v.strip(), 0.8, "ADDRESS_PHRASE"));
            }
        }
    }
}
