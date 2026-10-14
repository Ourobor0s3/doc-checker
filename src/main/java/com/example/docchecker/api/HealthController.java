package com.example.docchecker.api;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.config.AppProperties;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.llm.OllamaStartupCheck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Служебное")
public class HealthController {

    public record HealthResponse(
            String status,
            boolean ollamaAvailable,
            String textModel,
            boolean textModelPulled,
            String visionModel,
            boolean visionModelPulled,
            String storage,
            boolean storageAvailable,
            List<String> hints) {}

    private final LlmClient llm;
    private final AppProperties props;
    private final ObjectProvider<StringRedisTemplate> redis;

    public HealthController(LlmClient llm, AppProperties props, ObjectProvider<StringRedisTemplate> redis) {
        this.llm = llm;
        this.props = props;
        this.redis = redis;
    }

    @Operation(summary = "Доступность Ollama, нужных моделей и хранилища")
    @GetMapping
    public HealthResponse health() {
        List<String> hints = new ArrayList<>();
        String text = props.ollama().textModel();
        String vision = props.ollama().visionModel();

        boolean ollamaUp = false;
        boolean textOk = false;
        boolean visionOk = false;
        try {
            Set<String> models = llm.listModels().stream()
                    .map(OllamaStartupCheck::normalize).collect(Collectors.toSet());
            ollamaUp = true;
            textOk = models.contains(OllamaStartupCheck.normalize(text));
            visionOk = models.contains(OllamaStartupCheck.normalize(vision));
            if (!textOk) {
                hints.add("Модель не скачана: ollama pull " + text);
            }
            if (!visionOk && !vision.equals(text)) {
                hints.add("Модель не скачана: ollama pull " + vision);
            }
        } catch (ApiException e) {
            hints.add(e.getMessage());
        }

        boolean storageUp = true;
        if (props.storage().isRedis()) {
            try {
                StringRedisTemplate template = redis.getObject();
                String pong = template.execute((RedisCallback<String>) connection -> connection.ping());
                storageUp = pong != null;
            } catch (RuntimeException e) {
                storageUp = false;
                hints.add("Redis недоступен: " + e.getMessage());
            }
        }

        String status = ollamaUp && textOk && visionOk && storageUp ? "UP" : "DEGRADED";
        return new HealthResponse(status, ollamaUp, text, textOk, vision, visionOk,
                props.storage().type(), storageUp, hints);
    }
}
