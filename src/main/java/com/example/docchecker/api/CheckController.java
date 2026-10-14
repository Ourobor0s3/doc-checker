package com.example.docchecker.api;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.domain.CheckJob;
import com.example.docchecker.service.CheckService;
import com.example.docchecker.service.UploadedFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/checks")
@Tag(name = "Проверки", description = "Проверка файлов на соответствие требованиям")
public class CheckController {

    private final CheckService service;

    public CheckController(CheckService service) {
        this.service = service;
    }

    @Operation(summary = "Отправить файл на проверку",
            description = "Поддерживаются JPEG/JPG, PNG, PDF, DOCX (тип определяется по содержимому). "
                    + "Проверка выполняется асинхронно: ответ 202 содержит id, результат — в GET /api/checks/{id}.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CheckJob> submit(
            @Parameter(description = "Проверяемый файл") @RequestParam("file") MultipartFile file,
            @Parameter(description = "id набора требований") @RequestParam("requirementSetId") String requirementSetId)
            throws IOException {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Файл пустой");
        }
        CheckJob job = service.submit(new UploadedFile(file.getOriginalFilename(), file.getBytes()), requirementSetId);
        return ResponseEntity.accepted().location(URI.create("/api/checks/" + job.id())).body(job);
    }

    @Operation(summary = "Статус и результат проверки",
            description = "status: PENDING → RUNNING → COMPLETED | FAILED. После COMPLETED доступны results "
                    + "(по каждому требованию) и summary (краткая сводка).")
    @GetMapping("/{id}")
    public CheckJob get(@PathVariable String id) {
        return service.get(id);
    }
}
