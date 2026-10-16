package com.example.docchecker.pd.model;

/** Типы признаков персональных данных. Правовая привязка — в resources/pd/legal-basis.json. */
public enum PdType {
    FULL_NAME, SNILS, INN, PASSPORT_RF, DRIVER_LICENSE, OMS_POLICY, BANK_CARD, PHONE, EMAIL, BIRTH_DATE, ADDRESS,
    DOCUMENT_AUTHOR, GEO_LOCATION, FACE_PHOTO,
    HEALTH_DATA, RELIGION_BELIEFS, POLITICAL_VIEWS, RACE_ETHNICITY, INTIMATE_LIFE, CRIMINAL_RECORD,
    FIELD_NAME, OTHER_PERSONAL
}
