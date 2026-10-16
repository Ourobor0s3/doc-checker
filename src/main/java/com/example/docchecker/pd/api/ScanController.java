package com.example.docchecker.pd.api;

import com.example.docchecker.common.ApiException;
import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.FileVerdict;
import com.example.docchecker.pd.model.PageResult;
import com.example.docchecker.pd.model.ScanJob;
import com.example.docchecker.pd.service.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/scans")
@Tag(name = "Поиск персональных данных (152-ФЗ)",
        description = "Загрузка файла или zip-архива и получение отчёта: есть ли персональные данные, где именно и на каком основании")
public class ScanController {

    private final ScanService service;

    public ScanController(ScanService service) {
        this.service = service;
    }

    @Operation(summary = "Запустить сканирование файла или zip-архива",
            description = "Форматы: docx, doc, rtf, pdf, html, md, txt, csv, json, parquet, xlsx, png, jpg, gif, tif, bmp. "
                    + "Архив обходится рекурсивно. Выполняется асинхронно: статус и результаты — в GET /api/scans/{id}. "
                    + "Система фиксирует только наличие признаков ПД и не оценивает законность обработки.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanJob> submit(
            @Parameter(description = "Файл или zip-архив") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Использовать LLM и vision-модель (медленнее, но находит ФИО, адреса и текст на изображениях)")
            @RequestParam(defaultValue = "true") boolean useLlm) throws IOException {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Файл пустой");
        }
        Path tmp = Files.createTempFile("pd-upload-", ".bin");
        file.transferTo(tmp);
        ScanJob job = service.submit(tmp, file.getOriginalFilename(), useLlm);
        return ResponseEntity.accepted().location(URI.create("/api/scans/" + job.id)).body(job);
    }

    @Operation(summary = "История сканирований",
            description = "Последние сканирования (новые первыми) со статусом и счётчиками. Идентификатор можно скопировать и открыть результат.")
    @GetMapping
    public List<ScanJob> recent(@RequestParam(defaultValue = "30") int limit) {
        return service.recent(limit);
    }

    @Operation(summary = "Статус и сводка сканирования")
    @GetMapping("/{id}")
    public ScanJob get(@PathVariable String id) {
        return service.get(id);
    }

    @Operation(summary = "Отчёты по файлам (постранично)",
            description = "Для каждого файла: вердикт YES/NO/UNDETERMINED, находки с типом, значением, местом в файле, "
                    + "нормой 152-ФЗ и объяснением, а также журнал шагов агента.")
    @GetMapping("/{id}/files")
    public PageResult<FileReport> files(@PathVariable String id,
                                        @RequestParam(required = false) FileVerdict verdict,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return service.files(id, verdict, q, page, size);
    }

    @Operation(summary = "Выгрузить отчёт", description = "format = json | csv")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id, @RequestParam(defaultValue = "json") String format) {
        boolean csv = format.equalsIgnoreCase("csv");
        if (!csv && !format.equalsIgnoreCase("json")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "format должен быть json или csv");
        }
        byte[] body = csv ? service.exportCsv(id) : service.exportJson(id);
        return ResponseEntity.ok()
                .contentType(csv ? MediaType.parseMediaType("text/csv;charset=UTF-8") : MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("pd-scan-" + id + (csv ? ".csv" : ".json")).build().toString())
                .body(body);
    }

    @Operation(summary = "Удалить результаты сканирования")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
