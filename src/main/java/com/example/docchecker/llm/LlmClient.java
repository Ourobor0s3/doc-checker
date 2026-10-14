package com.example.docchecker.llm;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    /**
     * Один запрос к модели с ответом строго в JSON по схеме.
     *
     * @throws OllamaException                 если Ollama недоступна или вернула ошибку
     * @throws LlmResponseFormatException      если ответ не удалось разобрать
     */
    <T> T chatJson(String model, String system, String user, List<String> imagesBase64,
                   Map<String, Object> schema, Class<T> type);

    /** Названия моделей, скачанных в Ollama. */
    List<String> listModels();
}
