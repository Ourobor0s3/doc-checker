package com.example.docchecker.pd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.docchecker.pd.agent.EntityRules;
import com.example.docchecker.pd.agent.FaceRules;
import com.example.docchecker.pd.agent.ImageStats;
import com.example.docchecker.pd.agent.OcrCompare;
import com.example.docchecker.pd.model.PdType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Правила, защищающие от галлюцинаций vision-модели и LLM. Модель для них не нужна. */
class PdImageRulesTest {

    @Test
    void faceNeedsPhotoKindAndNamedFeatures() {
        assertEquals(0.8, FaceRules.decide("PHOTO_OF_PERSON", 1, "глаза, нос, рот").confidence(), 1e-9);
        assertEquals(0.65, FaceRules.decide("PHOTO_OF_PERSON", 1, "лицо").confidence(), 1e-9);
        assertTrue(FaceRules.decide("PHOTO_OF_PERSON", 1, "").confidence() < 0.6, "лицо без описания черт — слабый признак");
        assertEquals(0.75, FaceRules.decide("ID_DOCUMENT", 1, "глаза").confidence(), 1e-9);
    }

    @Test
    void drawingsAndContradictionsAreNotFaces() {
        FaceRules.Decision drawing = FaceRules.decide("DRAWING", 1, "глаза, нос, рот");
        assertEquals(0, drawing.confidence());
        assertNotNull(drawing.note());
        assertEquals(0, FaceRules.decide("PHOTO_WITHOUT_PEOPLE", 2, "глаза").confidence());
        assertEquals(0, FaceRules.decide("LOGO_OR_ICON", 1, "глаза, нос").confidence());
        assertEquals(0, FaceRules.decide("PHOTO_OF_PERSON", 0, "глаза, нос").confidence(), "ноль лиц — лица нет");
        assertEquals(0, FaceRules.decide("PHOTO_OF_PERSON", null, "глаза").confidence());
        assertEquals(FaceRules.Kind.OTHER, FaceRules.kind("что-то выдуманное"));
    }

    @Test
    void ocrIsRunOnlyWhereTextIsExpected() {
        assertFalse(FaceRules.needsOcr(FaceRules.Kind.DRAWING, false));
        assertTrue(FaceRules.needsOcr(FaceRules.Kind.DRAWING, true));
        assertTrue(FaceRules.needsOcr(FaceRules.Kind.TEXT_DOCUMENT, null));
    }

    @Test
    void llmQuotesMustLookLikeWhatTheyClaimToBe() {
        assertEquals(0.7, EntityRules.confidence(PdType.FULL_NAME, "Иванов Иван"));
        assertEquals(0.7, EntityRules.confidence(PdType.FULL_NAME, "Сидоров А.Б."));
        assertEquals(0, EntityRules.confidence(PdType.FULL_NAME, "Иван"), "одно слово — не ФИО");
        assertEquals(0, EntityRules.confidence(PdType.FULL_NAME, "ООО Ромашка"));
        assertEquals(0, EntityRules.confidence(PdType.FULL_NAME, "Министерство Финансов"));
        assertEquals(0, EntityRules.confidence(PdType.FULL_NAME, "Иванов 12"));
        assertEquals(0.65, EntityRules.confidence(PdType.ADDRESS, "г. Казань, ул. Баумана, 3"));
        assertEquals(0, EntityRules.confidence(PdType.ADDRESS, "ул. Ленина"), "без номера дома — не адрес");
        assertTrue(EntityRules.confidence(PdType.HEALTH_DATA, "сахарный диабет") < 0.6, "только по мнению модели — слабый признак");
    }

    @Test
    void imageReadTwiceMustAgree() {
        assertTrue(OcrCompare.confirmed(PdType.SNILS, "112-233-445 95", "СНИЛС: 11223344595"));
        assertFalse(OcrCompare.confirmed(PdType.SNILS, "112-233-445 95", "СНИЛС: 111-111-111 11"));
        assertFalse(OcrCompare.confirmed(PdType.SNILS, "112-233-445 95", ""));
        assertTrue(OcrCompare.confirmed(PdType.FULL_NAME, "Иванов Иван", "ФИО: ИВАНОВ  иван"));
        assertFalse(OcrCompare.confirmed(PdType.FULL_NAME, "Иванов Иван", "Петров Пётр"));
    }

    @Test
    void blankAndTinyImagesAreFilteredBeforeTheModel() throws Exception {
        ImageStats.Stats blank = ImageStats.analyze(jpeg(300, false), 48);
        assertTrue(blank.blank());
        ImageStats.Stats tiny = ImageStats.analyze(jpeg(20, true), 48);
        assertTrue(tiny.tiny());
        ImageStats.Stats photo = ImageStats.analyze(jpeg(300, true), 48);
        assertFalse(photo.blank() || photo.tiny());
    }

    private static String jpeg(int size, boolean noise) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, y, noise ? r.nextInt(0xFFFFFF) : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
