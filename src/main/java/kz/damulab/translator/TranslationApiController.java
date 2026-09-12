package kz.damulab.translator;

import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Защищённый ученический API для перевода и последующего учебного разбора. */
@RestController
@RequestMapping("/api/student/translator")
public class TranslationApiController {

    private final TranslationService service;

    public TranslationApiController(TranslationService service) {
        this.service = service;
    }

    /** Выполняет один LLM-запрос на перевод. */
    @PostMapping("/translate")
    TranslationResponse translate(@Valid @RequestBody TranslationRequest request) {
        return service.translate(request);
    }

    /** Выполняет отдельный LLM-запрос на объяснение уже показанного перевода. */
    @PostMapping("/explain")
    TranslationResponse explain(
            @Valid @RequestBody TranslationExplanationRequest request,
            Locale locale
    ) {
        return service.explain(request, locale);
    }
}
