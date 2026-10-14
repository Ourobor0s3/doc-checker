package com.example.docchecker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.docchecker.service.RequirementSplitter;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequirementSplitterTest {

    @Test
    void numberedList() {
        List<String> r = RequirementSplitter.splitLines("1. Указана дата\n2) Есть подпись\n3.1 Есть печать");
        assertEquals(List.of("Указана дата", "Есть подпись", "Есть печать"), r);
    }

    @Test
    void bulletsAndHeadingAreHandled() {
        List<String> r = RequirementSplitter.splitLines("Документ должен:\n- Содержать дату;\n- Быть подписан");
        assertEquals(List.of("Содержать дату;", "Быть подписан"), r);
    }

    @Test
    void continuationLinesAreJoined() {
        List<String> r = RequirementSplitter.splitLines("- Документ содержит\nназвание организации\n- Есть подпись");
        assertEquals(List.of("Документ содержит название организации", "Есть подпись"), r);
    }

    @Test
    void plainLines() {
        List<String> r = RequirementSplitter.splitLines("Указана дата\nЕсть подпись");
        assertEquals(2, r.size());
    }

    @Test
    void singleParagraphIsSplitIntoSentences() {
        List<String> r = RequirementSplitter.splitLines("Документ должен быть подписан. Дата должна быть указана.");
        assertEquals(List.of("Документ должен быть подписан.", "Дата должна быть указана."), r);
    }
}
