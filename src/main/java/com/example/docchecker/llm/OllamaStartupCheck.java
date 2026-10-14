package com.example.docchecker.llm;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.config.AppProperties;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** При старте только логирует предупреждения, если Ollama недоступна или нужных моделей нет. */
@Component
public class OllamaStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaStartupCheck.class);

    private final LlmClient llm;
    private final AppProperties props;

    public OllamaStartupCheck(LlmClient llm, AppProperties props) {
        this.llm = llm;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> models = llm.listModels().stream().map(OllamaStartupCheck::normalize).collect(Collectors.toSet());
            for (String m : List.of(props.ollama().textModel(), props.ollama().visionModel())) {
                if (!models.contains(normalize(m))) {
                    log.warn("Модель '{}' не найдена в Ollama. Выполните: ollama pull {}", m, m);
                }
            }
        } catch (ApiException e) {
            log.warn("Ollama пока недоступна: {}", e.getMessage());
        }
    }

    public static String normalize(String name) {
        return name.contains(":") ? name : name + ":latest";
    }
}
