package kz.damulab.translator;

import java.util.Locale;

import org.springframework.stereotype.Service;

import kz.damulab.ai.AiProvider;
import kz.damulab.ai.AiProviderException;
import kz.damulab.ai.AiTextResult;
import kz.damulab.ai.AiTranslationExplanationMode;
import kz.damulab.ai.AiTranslationExplanationRequest;
import kz.damulab.ai.AiTranslationRequest;

/**
 * Оркестрирует перевод и учебный разбор через общий AI-маршрутизатор.
 * Каждое принятое обращение сохраняется в журнале вместе с ответом или кодом ошибки.
 */
@Service
public class TranslationService {

    private final AiProvider aiProvider;
    private final TranslationHistoryService history;

    public TranslationService(AiProvider aiProvider, TranslationHistoryService history) {
        this.aiProvider = aiProvider;
        this.history = history;
    }

    /** Переводит нормализованный пользовательский ввод в выбранном направлении. */
    public TranslationResponse translate(TranslationRequest request) {
        TranslationDirection direction = request.direction();
        AiTranslationRequest aiRequest = new AiTranslationRequest(
                direction.sourceLanguage(),
                direction.targetLanguage(),
                request.text().trim()
        );
        Long historyId = history.start(direction, aiRequest.text(), null, null, null);
        AiTextResult result;
        try {
            result = aiProvider.translate(aiRequest);
        } catch (RuntimeException ex) {
            recordFailure(historyId, ex);
            throw ex;
        }
        history.succeed(historyId, result);
        return new TranslationResponse(result.text());
    }

    /** Объясняет перевод на языке интерфейса пользователя. */
    public TranslationResponse explain(TranslationExplanationRequest request, Locale locale) {
        TranslationDirection direction = request.direction();
        String explanationLanguage = "kk".equals(locale.getLanguage()) ? "Kazakh" : "Russian";
        AiTranslationExplanationRequest aiRequest = new AiTranslationExplanationRequest(
                direction.sourceLanguage(),
                direction.targetLanguage(),
                request.sourceText().trim(),
                request.translatedText().trim(),
                explanationLanguage,
                request.economyMode()
                        ? AiTranslationExplanationMode.ECONOMY
                        : AiTranslationExplanationMode.DETAILED
        );
        Long historyId = history.start(direction, aiRequest.sourceText(), aiRequest.translatedText(),
                explanationLanguage, aiRequest.explanationMode().name());
        AiTextResult result;
        try {
            result = aiProvider.explainTranslation(aiRequest);
        } catch (RuntimeException ex) {
            recordFailure(historyId, ex);
            throw ex;
        }
        history.succeed(historyId, result);
        return new TranslationResponse(result.text());
    }

    private void recordFailure(Long historyId, RuntimeException failure) {
        // Не записываем сообщение исключения: провайдер может включить в него исходный текст.
        String code = failure instanceof AiProviderException providerFailure
                ? providerFailure.getCode() : "translation_request_failed";
        try {
            history.fail(historyId, code);
        } catch (RuntimeException historyFailure) {
            // Ошибка журнала не должна скрывать первоначальную причину отказа AI.
            failure.addSuppressed(historyFailure);
        }
    }
}
