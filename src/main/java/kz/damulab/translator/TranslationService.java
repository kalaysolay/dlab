package kz.damulab.translator;

import java.util.Locale;

import org.springframework.stereotype.Service;

import kz.damulab.ai.AiProvider;
import kz.damulab.ai.AiTextResult;
import kz.damulab.ai.AiTranslationExplanationMode;
import kz.damulab.ai.AiTranslationExplanationRequest;
import kz.damulab.ai.AiTranslationRequest;

/**
 * Оркестрирует перевод и учебный разбор через общий AI-маршрутизатор.
 * Тексты не сохраняются в БД: каждый HTTP-запрос полностью самодостаточен.
 */
@Service
public class TranslationService {

    private final AiProvider aiProvider;

    public TranslationService(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    /** Переводит нормализованный пользовательский ввод в выбранном направлении. */
    public TranslationResponse translate(TranslationRequest request) {
        TranslationDirection direction = request.direction();
        AiTextResult result = aiProvider.translate(new AiTranslationRequest(
                direction.sourceLanguage(),
                direction.targetLanguage(),
                request.text().trim()
        ));
        return new TranslationResponse(result.text());
    }

    /** Объясняет перевод на языке интерфейса пользователя. */
    public TranslationResponse explain(TranslationExplanationRequest request, Locale locale) {
        TranslationDirection direction = request.direction();
        String explanationLanguage = "kk".equals(locale.getLanguage()) ? "Kazakh" : "Russian";
        AiTextResult result = aiProvider.explainTranslation(new AiTranslationExplanationRequest(
                direction.sourceLanguage(),
                direction.targetLanguage(),
                request.sourceText().trim(),
                request.translatedText().trim(),
                explanationLanguage,
                request.economyMode()
                        ? AiTranslationExplanationMode.ECONOMY
                        : AiTranslationExplanationMode.DETAILED
        ));
        return new TranslationResponse(result.text());
    }
}
