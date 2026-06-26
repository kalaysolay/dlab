package kz.damulab.ai;

import java.util.List;

import org.slf4j.Logger;

/**
 * Единый формат логов AI-вызовов: что отправили, какую модель выбрали, что получили.
 * На INFO — выдержки (до {@link #EXCERPT} симв.), полный текст смотрите при DEBUG того же класса.
 */
public final class AiCallLogger {

    static final int EXCERPT = 2500;

    private AiCallLogger() {
    }

    public static void logOutbound(
            Logger log,
            String operation,
            String httpProvider,
            String model,
            String configuredModelKey,
            String endpoint,
            int attempt,
            int maxAttempts,
            String systemPrompt,
            String userPrompt
    ) {
        log.info(
                "AI >>> op={} provider={} model={} (config {}) endpoint={} attempt={}/{} systemLen={} userLen={}",
                operation,
                httpProvider,
                model,
                configuredModelKey,
                endpoint,
                attempt,
                maxAttempts,
                length(systemPrompt),
                length(userPrompt)
        );
        log.info("AI >>> op={} systemPrompt excerpt: {}", operation, excerpt(systemPrompt));
        log.info("AI >>> op={} userPrompt excerpt: {}", operation, excerpt(userPrompt));
    }

    public static void logInboundRaw(Logger log, String operation, String model, int attempt, String rawResponse) {
        log.info(
                "AI <<< op={} model={} attempt={} rawResponseLen={}",
                operation,
                model,
                attempt,
                length(rawResponse)
        );
        log.info("AI <<< op={} rawResponse excerpt: {}", operation, excerpt(rawResponse));
    }

    public static void logMiniLectureParsed(Logger log, String operation, MiniLectureStructuredPayload payload) {
        logLangBlock(log, operation, "ru", payload.ru());
        logLangBlock(log, operation, "kz", payload.kz());
    }

    public static void logQuestionDrafts(Logger log, String operation, String model, List<AiGeneratedQuestionDraft> drafts) {
        log.info("AI <<< op={} model={} questionDraftCount={}", operation, model, drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            AiGeneratedQuestionDraft draft = drafts.get(index);
            log.info(
                    "AI <<< op={} draft[{}] type={} bodyRu excerpt: {}",
                    operation,
                    index + 1,
                    draft.questionType(),
                    excerpt(draft.bodyRu())
            );
            log.info(
                    "AI <<< op={} draft[{}] explanationRu (короткая подсказка, НЕ мини-лекция): {}",
                    operation,
                    index + 1,
                    excerpt(draft.explanationRu())
            );
        }
    }

    private static void logLangBlock(Logger log, String operation, String lang, MiniLectureLangBlock block) {
        log.info(
                "AI <<< op={} lang={} fieldLens title={} theory={} analysis={} mistake={} example={} summary={}",
                operation,
                lang,
                length(block.title()),
                length(block.theory()),
                length(block.questionAnalysis()),
                length(block.commonMistake()),
                length(block.exampleAnalysis()),
                length(block.summary())
        );
        log.info("AI <<< op={} lang={} theory excerpt: {}", operation, lang, excerpt(block.theory()));
        log.info("AI <<< op={} lang={} question_analysis excerpt: {}", operation, lang, excerpt(block.questionAnalysis()));
    }

    public static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= EXCERPT) {
            return normalized;
        }
        return normalized.substring(0, EXCERPT) + "... [truncated, total " + normalized.length() + " chars]";
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }
}
