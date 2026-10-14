package com.example.docchecker.service;

import com.example.docchecker.common.BadDocumentException;
import com.example.docchecker.config.AppProperties;
import com.example.docchecker.domain.CheckJob;
import com.example.docchecker.domain.CheckSummary;
import com.example.docchecker.domain.Requirement;
import com.example.docchecker.domain.RequirementResult;
import com.example.docchecker.domain.RequirementSet;
import com.example.docchecker.domain.Verdict;
import com.example.docchecker.extract.ContentChunk;
import com.example.docchecker.extract.ExtractedContent;
import com.example.docchecker.extract.ExtractionService;
import com.example.docchecker.extract.FileType;
import com.example.docchecker.llm.Answers.CheckAnswer;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.llm.LlmResponseFormatException;
import com.example.docchecker.llm.Prompts;
import com.example.docchecker.storage.CheckJobRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Выполняет проверку файла: извлекает содержимое, прогоняет каждое требование через модель
 * по каждому фрагменту документа и агрегирует вердикты.
 */
@Component
public class CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckRunner.class);
    private static final int MAX_TEXT = 300;

    private final ExtractionService extraction;
    private final LlmClient llm;
    private final CheckJobRepository jobs;
    private final SummaryService summaryService;
    private final AppProperties props;

    public CheckRunner(ExtractionService extraction, LlmClient llm, CheckJobRepository jobs,
                       SummaryService summaryService, AppProperties props) {
        this.extraction = extraction;
        this.llm = llm;
        this.jobs = jobs;
        this.summaryService = summaryService;
        this.props = props;
    }

    public void run(CheckJob initial, RequirementSet set, byte[] data, FileType type) {
        CheckJob current = initial;
        try {
            current = jobs.save(current.running());
            ExtractedContent content = extraction.extract(data, type);
            if (content.chunks().isEmpty()) {
                throw new BadDocumentException("В файле не найдено содержимого для проверки (файл пустой?)");
            }

            List<RequirementResult> results = new ArrayList<>();
            for (Requirement requirement : set.requirements()) {
                results.add(checkRequirement(requirement, content.chunks()));
                current = jobs.save(current.progress(results));
            }

            CheckSummary summary = summaryService.summarize(results);
            jobs.save(current.completed(results, summary, content.warnings()));
            log.info("Проверка {} завершена: {}", initial.id(), summary.overall());
        } catch (Exception e) {
            log.error("Проверка {} завершилась ошибкой", initial.id(), e);
            jobs.save(current.failed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    RequirementResult checkRequirement(Requirement requirement, List<ContentChunk> chunks) {
        RequirementResult firstPass = null;
        RequirementResult firstUnknown = null;
        boolean multi = chunks.size() > 1;

        for (ContentChunk chunk : chunks) {
            CheckAnswer answer = ask(requirement, chunk);
            Verdict verdict = parseVerdict(answer == null ? null : answer.verdict());
            String evidence = cut(prefix(multi, chunk) + nullToEmpty(answer == null ? null : answer.evidence()));
            String comment = cut(nullToEmpty(answer == null ? null : answer.comment()));
            RequirementResult result = new RequirementResult(requirement.id(), requirement.text(), verdict, evidence, comment);

            if (verdict == Verdict.FAIL) {
                return result; // нарушение в любом фрагменте = нарушение; дальше можно не проверять
            }
            if (verdict == Verdict.PASS && firstPass == null) {
                firstPass = result;
            }
            if (verdict == Verdict.UNKNOWN && firstUnknown == null) {
                firstUnknown = result;
            }
        }
        if (firstPass != null) {
            return firstPass;
        }
        if (firstUnknown != null && !firstUnknown.comment().isBlank()) {
            return new RequirementResult(requirement.id(), requirement.text(), Verdict.UNKNOWN, "", firstUnknown.comment());
        }
        return new RequirementResult(requirement.id(), requirement.text(), Verdict.UNKNOWN, "",
                "В документе не найдено информации для проверки этого требования");
    }

    private CheckAnswer ask(Requirement requirement, ContentChunk chunk) {
        String model = chunk.hasImages() ? props.ollama().visionModel() : props.ollama().textModel();
        String user = chunk.hasImages()
                ? Prompts.checkUserImage(chunk.label(), requirement.text())
                : Prompts.checkUserText(chunk.label(), chunk.text(), requirement.text());
        try {
            return llm.chatJson(model, Prompts.CHECK_SYSTEM, user, chunk.imagesBase64(),
                    Prompts.checkSchema(), CheckAnswer.class);
        } catch (LlmResponseFormatException e) {
            log.warn("Требование {}: {}", requirement.id(), e.getMessage());
            return new CheckAnswer("UNKNOWN", "", "Модель вернула ответ в некорректном формате");
        }
        // Остальные OllamaException (сервис недоступен) пробрасываются — проверка завершится как FAILED.
    }

    static Verdict parseVerdict(String s) {
        if (s == null) {
            return Verdict.UNKNOWN;
        }
        try {
            return Verdict.valueOf(s.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Verdict.UNKNOWN;
        }
    }

    private static String prefix(boolean multi, ContentChunk chunk) {
        return multi ? "[" + chunk.label() + "] " : "";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    private static String cut(String s) {
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT) + "…";
    }
}
