package com.example.docchecker.service;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.config.AppProperties;
import com.example.docchecker.domain.Requirement;
import com.example.docchecker.domain.RequirementSet;
import com.example.docchecker.extract.ExtractionService;
import com.example.docchecker.extract.TextUtil;
import com.example.docchecker.llm.Answers.RequirementsAnswer;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.llm.OllamaException;
import com.example.docchecker.llm.Prompts;
import com.example.docchecker.storage.RequirementSetRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RequirementService {

    private static final Logger log = LoggerFactory.getLogger(RequirementService.class);
    private static final String DEFAULT_NAME = "Набор требований";

    public record CreateResult(RequirementSet set, List<String> warnings) {}

    private final RequirementSetRepository repository;
    private final ExtractionService extraction;
    private final LlmClient llm;
    private final AppProperties props;

    public RequirementService(RequirementSetRepository repository, ExtractionService extraction,
                              LlmClient llm, AppProperties props) {
        this.repository = repository;
        this.extraction = extraction;
        this.llm = llm;
        this.props = props;
    }

    public CreateResult create(String name, String text, List<UploadedFile> files, SplitMode mode) {
        StringBuilder all = new StringBuilder();
        if (text != null && !text.isBlank()) {
            all.append(text.strip());
        }
        for (UploadedFile f : files) {
            String t = extraction.extractPlainText(f.data(), f.name());
            if (!t.isBlank()) {
                if (all.length() > 0) {
                    all.append("\n\n");
                }
                all.append(t);
            }
        }
        String source = all.toString();
        if (source.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Передайте текст требований и/или файлы с требованиями");
        }
        if (source.length() > props.extract().maxTotalChars()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Текст требований слишком длинный (максимум " + props.extract().maxTotalChars() + " символов)");
        }

        List<String> warnings = new ArrayList<>();
        List<String> texts;
        if (mode == SplitMode.LLM) {
            try {
                texts = extractWithLlm(source);
                if (texts.isEmpty()) {
                    warnings.add("Модель не выделила требований, использовано разбиение по строкам");
                    texts = RequirementSplitter.splitLines(source);
                }
            } catch (OllamaException e) {
                log.warn("Не удалось разобрать требования с помощью LLM: {}", e.getMessage());
                warnings.add("LLM недоступна или ответила некорректно (" + e.getMessage()
                        + "), использовано разбиение по строкам");
                texts = RequirementSplitter.splitLines(source);
            }
        } else {
            texts = RequirementSplitter.splitLines(source);
        }

        RequirementSet set = build(UUID.randomUUID().toString(), name, texts, Instant.now());
        repository.save(set);
        return new CreateResult(set, warnings);
    }

    public RequirementSet get(String id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    public List<RequirementSet> list() {
        return repository.findAll();
    }

    public RequirementSet replace(String id, String name, List<String> texts) {
        RequirementSet old = get(id);
        RequirementSet updated = build(id, name != null ? name : old.name(), texts, old.createdAt());
        return repository.save(updated);
    }

    public void delete(String id) {
        if (!repository.delete(id)) {
            throw notFound(id);
        }
    }

    private List<String> extractWithLlm(String source) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String chunk : TextUtil.split(source, props.extract().maxCharsPerChunk())) {
            RequirementsAnswer answer = llm.chatJson(props.ollama().textModel(), Prompts.REQUIREMENTS_SYSTEM,
                    "Текст:\n<<<\n" + chunk + "\n>>>", List.of(), Prompts.requirementsSchema(), RequirementsAnswer.class);
            if (answer != null && answer.requirements() != null) {
                for (String r : answer.requirements()) {
                    if (r != null && !r.isBlank()) {
                        unique.putIfAbsent(r.strip().toLowerCase(Locale.ROOT), r.strip());
                    }
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private RequirementSet build(String id, String name, List<String> texts, Instant createdAt) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String t : texts) {
            if (t != null && !t.isBlank()) {
                unique.putIfAbsent(t.strip().toLowerCase(Locale.ROOT), t.strip());
            }
        }
        if (unique.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Не удалось выделить ни одного требования");
        }
        int max = props.check().maxRequirements();
        if (unique.size() > max) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Слишком много требований (" + unique.size() + "), максимум " + max
                            + ". Разбейте их на несколько наборов");
        }
        List<Requirement> requirements = new ArrayList<>();
        int i = 1;
        for (String t : unique.values()) {
            requirements.add(new Requirement("R" + i++, t));
        }
        String finalName = (name == null || name.isBlank()) ? DEFAULT_NAME : name.strip();
        return new RequirementSet(id, finalName, requirements, createdAt);
    }

    private static ApiException notFound(String id) {
        return new ApiException(HttpStatus.NOT_FOUND, "Набор требований не найден: " + id);
    }
}
