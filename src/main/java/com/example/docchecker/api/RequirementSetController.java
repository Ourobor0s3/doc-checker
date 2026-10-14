package com.example.docchecker.api;

import com.example.docchecker.service.RequirementService;
import com.example.docchecker.service.RequirementService.CreateResult;
import com.example.docchecker.service.SplitMode;
import com.example.docchecker.service.UploadedFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/requirement-sets")
@Tag(name = "Требования", description = "Загрузка, просмотр и редактирование наборов требований")
public class RequirementSetController {

    private final RequirementService service;

    public RequirementSetController(RequirementService service) {
        this.service = service;
    }

    @Operation(summary = "Создать набор требований",
            description = "Принимает текст и/или файлы (txt, md, docx, pdf). Текст разбивается на отдельные "
                    + "проверяемые требования. Результат можно проверить и поправить через PUT.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RequirementSetResponse> create(
            @Parameter(description = "Название набора") @RequestParam(required = false) String name,
            @Parameter(description = "Текст требований") @RequestParam(required = false) String text,
            @Parameter(description = "Файлы с требованиями: txt, md, docx, pdf")
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @Parameter(description = "LLM — выделяет требования модель (с откатом на LINES при сбое); "
                    + "LINES — каждая строка/пункт списка = одно требование")
            @RequestParam(defaultValue = "LLM") SplitMode splitMode) throws IOException {

        List<UploadedFile> uploaded = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (!f.isEmpty()) {
                    uploaded.add(new UploadedFile(f.getOriginalFilename(), f.getBytes()));
                }
            }
        }
        CreateResult result = service.create(name, text, uploaded, splitMode);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RequirementSetResponse.of(result.set(), result.warnings()));
    }

    @Operation(summary = "Список наборов требований")
    @GetMapping
    public List<RequirementSetShort> list() {
        return service.list().stream().map(RequirementSetShort::of).toList();
    }

    @Operation(summary = "Получить набор требований")
    @GetMapping("/{id}")
    public RequirementSetResponse get(@PathVariable String id) {
        return RequirementSetResponse.of(service.get(id), List.of());
    }

    @Operation(summary = "Заменить список требований в наборе",
            description = "Позволяет вручную исправить результат автоматического разбиения.")
    @PutMapping("/{id}")
    public RequirementSetResponse update(@PathVariable String id,
                                         @Valid @RequestBody UpdateRequirementSetRequest request) {
        return RequirementSetResponse.of(service.replace(id, request.name(), request.requirements()), List.of());
    }

    @Operation(summary = "Удалить набор требований")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
