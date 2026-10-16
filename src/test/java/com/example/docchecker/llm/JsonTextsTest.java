package com.example.docchecker.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.docchecker.pd.agent.PdAnswers.EntityAnswer;
import com.example.docchecker.pd.agent.PdAnswers.OcrAnswer;
import com.example.docchecker.pd.agent.PdAnswers.PhotoCheckAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonTextsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractJsonStripsMarkdownAndSurroundingText() {
        assertEquals("{\"a\":1}", JsonTexts.extractJson("here\n```json\n{\"a\":1}\n```\n"));
        assertEquals("{\"a\":1}", JsonTexts.extractJson("prefix {\"a\":1} suffix"));
    }

    @Test
    void truncatedArrayIsClosed() throws Exception {
        String raw = "{\"findings\":[{\"text\":\"Иванов\",\"type\":\"FULL_NAME\"}, {\"text\":\"Москва";
        EntityAnswer a = mapper.readValue(JsonTexts.repair(raw, mapper), EntityAnswer.class);
        assertEquals(1, a.findings().size());
        assertEquals("Иванов", a.findings().get(0).text());
    }

    @Test
    void truncatedStringInPhotoCheckAnswerIsClosed() throws Exception {
        String raw = "{\"reason\":\"Живой человек\",\"verdict\":\"REAL_PH";
        PhotoCheckAnswer a = mapper.readValue(JsonTexts.repair(raw, mapper), PhotoCheckAnswer.class);
        assertEquals("Живой человек", a.reason());
        assertTrue(a.verdict().startsWith("REAL"));
    }

    @Test
    void truncatedQuoteIsClosed() throws Exception {
        String raw = "{\"transcribedText\":\"hello";
        OcrAnswer a = mapper.readValue(JsonTexts.repair(raw, mapper), OcrAnswer.class);
        assertEquals("hello", a.transcribedText());
    }
}
