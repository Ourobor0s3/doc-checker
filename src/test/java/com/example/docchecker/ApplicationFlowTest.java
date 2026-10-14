package com.example.docchecker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.docchecker.domain.CheckJob;
import com.example.docchecker.domain.JobStatus;
import com.example.docchecker.domain.Overall;
import com.example.docchecker.domain.RequirementSet;
import com.example.docchecker.domain.Verdict;
import com.example.docchecker.llm.Answers.CheckAnswer;
import com.example.docchecker.llm.Answers.SummaryAnswer;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.service.CheckService;
import com.example.docchecker.service.RequirementService;
import com.example.docchecker.service.SplitMode;
import com.example.docchecker.service.UploadedFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/** Сквозной тест без Redis и без Ollama: in-memory хранилище и подставная LLM. */
@SpringBootTest(properties = "app.storage.type=memory")
@AutoConfigureMockMvc
class ApplicationFlowTest {

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            return new LlmClient() {
                @Override
                public <T> T chatJson(String model, String system, String user, List<String> images,
                                      Map<String, Object> schema, Class<T> type) {
                    if (type == CheckAnswer.class) {
                        boolean pass = user.contains("Требование: Документ содержит дату")
                                && user.contains("Дата: 01.01.2025");
                        return type.cast(pass
                                ? new CheckAnswer("PASS", "Дата: 01.01.2025", "Дата указана")
                                : new CheckAnswer("UNKNOWN", "", "Нет данных"));
                    }
                    if (type == SummaryAnswer.class) {
                        return type.cast(new SummaryAnswer("Часть требований не подтверждена."));
                    }
                    throw new IllegalStateException("unexpected type " + type);
                }

                @Override
                public List<String> listModels() {
                    return List.of("gemma3:4b");
                }
            };
        }
    }

    @Autowired RequirementService requirementService;
    @Autowired CheckService checkService;
    @Autowired MockMvc mvc;

    @Test
    void docxIsCheckedAgainstRequirements() throws Exception {
        RequirementSet set = requirementService.create("test",
                "1. Документ содержит дату\n2. Документ содержит подпись", List.of(), SplitMode.LINES).set();
        assertEquals(2, set.requirements().size());

        CheckJob job = checkService.submit(new UploadedFile("a.docx", docx("Дата: 01.01.2025")), set.id());
        CheckJob done = awaitDone(job.id());

        assertEquals(JobStatus.COMPLETED, done.status(), done.error());
        assertEquals(Verdict.PASS, done.results().get(0).verdict());
        assertEquals(Verdict.UNKNOWN, done.results().get(1).verdict());
        assertEquals(Overall.INCONCLUSIVE, done.summary().overall());
        assertEquals(1, done.summary().passed());
        assertNotNull(done.summary().text());
    }

    @Test
    void swaggerSpecIsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private CheckJob awaitDone(String id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            CheckJob job = checkService.get(id);
            if (job.status() == JobStatus.COMPLETED || job.status() == JobStatus.FAILED) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Проверка не завершилась за 10 секунд");
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            assertTrue(out.size() > 0);
            return out.toByteArray();
        }
    }
}
