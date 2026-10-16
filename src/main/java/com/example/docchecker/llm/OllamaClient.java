package com.example.docchecker.llm;

import com.example.docchecker.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Клиент Ollama REST API: {@code POST /api/chat} и {@code GET /api/tags}. */
@Component
public class OllamaClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final AppProperties.Ollama cfg;
    private final ObjectMapper mapper;
    private final RestClient http;

    public OllamaClient(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.ollama();
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.connectTimeout());
        factory.setReadTimeout(cfg.readTimeout());
        this.http = RestClient.builder().baseUrl(cfg.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public <T> T chatJson(String model, String system, String user, List<String> imagesBase64,
                          Map<String, Object> schema, Class<T> type) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            double temperature = attempt == 1 ? cfg.temperature() : Math.max(cfg.temperature(), 0.2);
            String prompt = attempt == 1 ? user
                    : user + "\n\nПредыдущий ответ был обрезан. Верни полный валидный JSON по схеме, коротко.";
            String content = chat(model, system, prompt, imagesBase64, schema, temperature);
            String json = JsonTexts.extractJson(content);
            try {
                return mapper.readValue(json, type);
            } catch (JsonProcessingException e) {
                log.warn("Модель {} вернула некорректный JSON (попытка {}): {}", model, attempt, e.getOriginalMessage());
                try {
                    T repaired = mapper.readValue(JsonTexts.repair(json, mapper), type);
                    log.info("Модель {}: JSON восстановлен после обрыва ответа", model);
                    return repaired;
                } catch (JsonProcessingException ignored) {
                    // вторая попытка запроса
                }
            }
        }
        throw new LlmResponseFormatException("Модель " + model + " вернула ответ не в ожидаемом JSON-формате");
    }

    @Override
    public List<String> listModels() {
        try {
            JsonNode resp = http.get().uri("/api/tags").retrieve().body(JsonNode.class);
            List<String> names = new ArrayList<>();
            if (resp != null) {
                for (JsonNode m : resp.path("models")) {
                    names.add(m.path("name").asText());
                }
            }
            return names;
        } catch (RestClientResponseException e) {
            throw new OllamaException("Ollama вернула ошибку " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new OllamaException("Ollama недоступна (" + cfg.baseUrl() + "): " + e.getMessage());
        }
    }

    private String chat(String model, String system, String user, List<String> images,
                        Map<String, Object> schema, double temperature) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", user);
        if (images != null && !images.isEmpty()) {
            userMessage.put("images", images);
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        options.put("num_ctx", cfg.numCtx());
        options.put("num_predict", cfg.numPredict());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("format", schema);
        body.put("keep_alive", cfg.keepAlive());
        body.put("options", options);
        if (cfg.think() != null) {
            body.put("think", cfg.think());
        }
        body.put("messages", List.of(Map.of("role", "system", "content", system), userMessage));

        try {
            JsonNode resp = http.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) {
                throw new OllamaException("Пустой ответ от Ollama");
            }
            return resp.path("message").path("content").asText("");
        } catch (RestClientResponseException e) {
            throw new OllamaException("Ollama вернула ошибку " + e.getStatusCode().value() + ": "
                    + abbreviate(e.getResponseBodyAsString(), 300));
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new OllamaTimeoutException("Модель " + model + " не ответила за " + cfg.readTimeout().toSeconds()
                        + " с (можно увеличить app.ollama.read-timeout)");
            }
            throw new OllamaException("Ollama недоступна (" + cfg.baseUrl() + "): " + e.getMessage());
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
