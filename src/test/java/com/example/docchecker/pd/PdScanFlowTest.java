package com.example.docchecker.pd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.pd.agent.PdAnswers;
import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.FileVerdict;
import com.example.docchecker.pd.model.Finding;
import com.example.docchecker.pd.model.PdType;
import com.example.docchecker.pd.model.ScanJob;
import com.example.docchecker.pd.model.ScanStatus;
import com.example.docchecker.pd.service.ScanService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Сквозной тест: zip с файлами разных форматов (имена в cp866) → отчёт с путями, находками и нормами. Без Redis и Ollama. */
@SpringBootTest(properties = {
        "app.storage.type=memory",
        "app.pd.mask-values=true"
})
class PdScanFlowTest {

    @TestConfiguration
    static class FakeLlm {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            return new LlmClient() {
                @Override
                public <T> T chatJson(String model, String system, String user, List<String> images,
                                      Map<String, Object> schema, Class<T> type) {
                    if (type == PdAnswers.EntityAnswer.class) {
                        return type.cast(new PdAnswers.EntityAnswer(List.of()));
                    }
                    if (type == PdAnswers.VisionAnswer.class) {
                        return type.cast(new PdAnswers.VisionAnswer("Паспорт: 45 12 123456", true));
                    }
                    throw new IllegalStateException("unexpected " + type);
                }

                @Override
                public List<String> listModels() {
                    return List.of("gemma3:4b");
                }
            };
        }
    }

    @Autowired ScanService service;

    @Test
    void archiveWithManyFormatsIsScanned() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("документы/анкета.csv", ("ФИО;Телефон;Должность\nИванов Иван Иванович;+7 916 123-45-67;Инженер\n")
                .getBytes(StandardCharsets.UTF_8));
        files.put("документы/пользователи.json", "{\"users\":[{\"name\":\"x\",\"snils\":\"112-233-445 95\"}]}"
                .getBytes(StandardCharsets.UTF_8));
        files.put("документы/страница.html", "<html><body><p>Пишите: <a href=\"mailto:petr.sidorov@mail.ru\">Пётр</a></p></body></html>"
                .getBytes(StandardCharsets.UTF_8));
        files.put("документы/анкета.docx", docx(true));
        files.put("документы/справка.docx", docx(false));
        files.put("документы/письмо.pdf", pdf("Please contact our colleague by email john.smith@example.com for details"));
        files.put("документы/сотрудники.parquet", parquet());
        files.put("документы/скан.png", png());
        files.put("readme.txt", "Обычный технический текст. Версия 1.2.3.".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo, Charset.forName("IBM866"))) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        Path upload = Files.createTempFile("pd-test-", ".zip");
        Files.write(upload, bo.toByteArray());

        ScanJob job = awaitDone(service.submit(upload, "выборка.zip", true).id);
        assertEquals(ScanStatus.COMPLETED, job.status, job.error);
        assertEquals(files.size(), job.processedFiles);
        assertNotNull(job.summary);

        List<FileReport> reports = service.files(job.id, null, null, 0, 100).items();
        assertEquals(files.size(), reports.size());

        FileReport csv = find(reports, "выборка.zip/документы/анкета.csv");
        assertEquals(FileVerdict.YES, csv.verdict());
        assertTrue(has(csv, PdType.FULL_NAME) && has(csv, PdType.PHONE), csv.findings().toString());

        FileReport json = find(reports, "пользователи.json");
        assertTrue(json.findings().stream().anyMatch(f -> f.type() == PdType.SNILS && f.locator().equals("$.users[0].snils")));

        assertTrue(has(find(reports, "страница.html"), PdType.EMAIL));
        assertTrue(has(find(reports, "анкета.docx"), PdType.BIRTH_DATE));
        assertEquals(FileVerdict.NO, find(reports, "справка.docx").verdict());
        assertTrue(has(find(reports, "письмо.pdf"), PdType.EMAIL));
        assertTrue(has(find(reports, "сотрудники.parquet"), PdType.PHONE));

        FileReport png = find(reports, "скан.png");
        assertTrue(has(png, PdType.PASSPORT_RF) && has(png, PdType.FACE_PHOTO), png.findings().toString());

        assertEquals(FileVerdict.NO, find(reports, "readme.txt").verdict());

        // значения замаскированы, норма и объяснение приложены
        for (FileReport r : reports) {
            for (Finding f : r.findings()) {
                assertFalse(f.value().contains("112-233"), f.value());
                assertFalse(f.value().contains("Иванов"), f.value());
                assertNotNull(f.legal().article());
                assertFalse(f.legal().explanation().isBlank());
                assertTrue(f.filePath().startsWith("выборка.zip/"));
            }
        }
        assertTrue(new String(service.exportCsv(job.id), StandardCharsets.UTF_8).contains("выборка.zip/документы/анкета.csv"));
    }

    private static FileReport find(List<FileReport> reports, String suffix) {
        return reports.stream().filter(r -> r.path().endsWith(suffix)).findFirst()
                .orElseThrow(() -> new AssertionError("нет отчёта для " + suffix + ": " + reports.stream().map(FileReport::path).toList()));
    }

    private static boolean has(FileReport r, PdType t) {
        return r.findings().stream().anyMatch(f -> f.type() == t);
    }

    private ScanJob awaitDone(String id) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            ScanJob job = service.get(id);
            if (job.status == ScanStatus.COMPLETED || job.status == ScanStatus.FAILED) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Сканирование не завершилось за 30 секунд");
    }

    private static byte[] docx(boolean withPd) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Служебная записка о закупке оборудования");
            if (withPd) {
                XWPFTable t = doc.createTable(1, 2);
                t.getRow(0).getCell(0).setText("Дата рождения");
                t.getRow(0).getCell(1).setText("12.05.1985");
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(40, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] parquet() throws Exception {
        Path p = Files.createTempFile("pd-test-", ".parquet");
        Files.delete(p); // DuckDB сам создаёт файл
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:"); Statement st = c.createStatement()) {
            st.execute("COPY (SELECT 'Иван' AS name, '89161234567' AS phone) TO '" + p.toString().replace("'", "''")
                    + "' (FORMAT PARQUET)");
        }
        try {
            return Files.readAllBytes(p);
        } finally {
            Files.deleteIfExists(p);
        }
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }
}
