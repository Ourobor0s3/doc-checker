package com.example.docchecker.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateRequirementSetRequest(
        @Schema(description = "Новое название набора (необязательно)", example = "Требования к договору")
        String name,

        @Schema(description = "Полный новый список требований (заменяет прежний)",
                example = "[\"В документе указана дата\", \"Документ подписан\"]")
        @NotEmpty List<@NotBlank String> requirements) {}
