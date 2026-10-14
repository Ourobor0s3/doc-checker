package com.example.docchecker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Doc Checker API")
                .version("1.0.0")
                .description("""
                        Два модуля на основе локальной LLM (Ollama).

                        1) Поиск персональных данных (152-ФЗ): POST /api/scans принимает файл или zip-архив, GET /api/scans/{id} и
                        GET /api/scans/{id}/files возвращают вердикт по каждому файлу, найденные признаки, место в файле и норму закона.
                        Веб-интерфейс доступен на главной странице.

                        2) Проверка файлов (JPEG, JPG, PNG, DOCX, PDF) на соблюдение пользовательских требований.
                        Порядок работы:
                        1. `POST /api/requirement-sets` — загрузить требования (текст и/или файлы), получить `id` набора.
                        2. `POST /api/checks` — отправить файл на проверку с `requirementSetId`, получить `id` проверки.
                        3. `GET /api/checks/{id}` — опрашивать, пока статус не станет `COMPLETED` или `FAILED`.
                        """));
    }
}
