package com.example.docchecker.service;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.config.AppProperties;
import com.example.docchecker.domain.CheckSummary;
import com.example.docchecker.domain.Overall;
import com.example.docchecker.domain.RequirementResult;
import com.example.docchecker.domain.Verdict;
import com.example.docchecker.llm.Answers.SummaryAnswer;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.llm.Prompts;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Итог проверки. Счётчики и общий вердикт считаются кодом (маленькие модели путают цифры),
 * LLM только пишет краткий текстовый комментарий.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);
    private static final int MAX_LISTED = 30;

    private final LlmClient llm;
    private final AppProperties props;

    public SummaryService(LlmClient llm, AppProperties props) {
        this.llm = llm;
        this.props = props;
    }

    public CheckSummary summarize(List<RequirementResult> results) {
        int total = results.size();
        int passed = count(results, Verdict.PASS);
        int failed = count(results, Verdict.FAIL);
        int unknown = count(results, Verdict.UNKNOWN);
        Overall overall = failed > 0 ? Overall.FAILED : (passed == total ? Overall.PASSED : Overall.INCONCLUSIVE);

        String header = "Проверено требований: " + total + " — выполнено: " + passed
                + ", нарушено: " + failed + ", не подтверждено: " + unknown + ".";
        String details;
        if (failed == 0 && unknown == 0) {
            details = "Документ соответствует всем требованиям.";
        } else {
            details = llmComment(results, total, passed, failed, unknown);
        }
        return new CheckSummary(total, passed, failed, unknown, overall, header + " " + details);
    }

    private String llmComment(List<RequirementResult> results, int total, int passed, int failed, int unknown) {
        try {
            String user = "Всего требований: " + total + "; выполнено: " + passed + "; нарушено: " + failed
                    + "; не подтверждено: " + unknown + ".\n\n"
                    + "Нарушения:\n" + list(results, Verdict.FAIL)
                    + "\nНе подтверждено:\n" + list(results, Verdict.UNKNOWN);
            SummaryAnswer answer = llm.chatJson(props.ollama().textModel(), Prompts.SUMMARY_SYSTEM, user,
                    List.of(), Prompts.summarySchema(), SummaryAnswer.class);
            if (answer != null && answer.summary() != null && !answer.summary().isBlank()) {
                return answer.summary().strip();
            }
        } catch (ApiException e) {
            log.warn("Не удалось получить текстовую сводку от LLM: {}", e.getMessage());
        }
        return fallback(results);
    }

    private static String fallback(List<RequirementResult> results) {
        StringBuilder sb = new StringBuilder();
        String failed = ids(results, Verdict.FAIL);
        String unknown = ids(results, Verdict.UNKNOWN);
        if (!failed.isEmpty()) {
            sb.append("Нарушены: ").append(failed).append(". ");
        }
        if (!unknown.isEmpty()) {
            sb.append("Не удалось подтвердить: ").append(unknown).append(".");
        }
        return sb.toString().strip();
    }

    private static String ids(List<RequirementResult> results, Verdict v) {
        return results.stream().filter(r -> r.verdict() == v)
                .map(RequirementResult::requirementId).collect(Collectors.joining(", "));
    }

    private static String list(List<RequirementResult> results, Verdict v) {
        String s = results.stream().filter(r -> r.verdict() == v).limit(MAX_LISTED)
                .map(r -> "- [" + r.requirementId() + "] " + r.requirementText()
                        + (r.comment() == null || r.comment().isBlank() ? "" : " — " + r.comment()))
                .collect(Collectors.joining("\n"));
        return s.isEmpty() ? "(нет)\n" : s + "\n";
    }

    private static int count(List<RequirementResult> results, Verdict v) {
        return (int) results.stream().filter(r -> r.verdict() == v).count();
    }
}
