package com.example.docchecker.pd.api;

import com.example.docchecker.pd.legal.LegalReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pd")
@Tag(name = "Поиск персональных данных (152-ФЗ)")
public class LegalController {

    private final LegalReference legal;

    public LegalController(LegalReference legal) {
        this.legal = legal;
    }

    @Operation(summary = "Справочник: тип признака → норма 152-ФЗ и объяснение",
            description = "Тот же справочник используется в отчётах. Редактируется в legal-basis.json без пересборки.")
    @GetMapping("/legal-basis")
    public LegalReference.Doc legalBasis() {
        return legal.document();
    }
}
