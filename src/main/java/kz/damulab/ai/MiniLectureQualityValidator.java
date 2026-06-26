package kz.damulab.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Проверяет, что ответ модели — полноценная мини-лекция, а не однострочная формула.
 * При провале вызывающий код может повторить запрос с уточняющим промптом.
 */
public final class MiniLectureQualityValidator {

    static final String RETRY_SUFFIX = """

            ВАЖНО: предыдущий ответ отклонён — слишком короткий или только формула без объяснения.
            Каждое поле JSON (кроме title) должно быть развёрнутым текстом с переносами строк \\n.
            theory — минимум 3 предложения: сначала смысл, потом почему способ работает.
            question_analysis — минимум 3 шага (Шаг 1 / Шаг 2 … или нумерованный список), решение именно этой задачи.
            example_analysis — другой пример с пошаговым решением.
            Для процентов: сначала найди 1%, потом умножь на нужное число процентов; не ограничивайся «делим на 10».
            """;

    private static final int MIN_THEORY = 80;
    private static final int MIN_QUESTION_ANALYSIS = 140;
    private static final int MIN_COMMON_MISTAKE = 50;
    private static final int MIN_EXAMPLE = 100;
    private static final int MIN_SUMMARY = 25;

    private static final Pattern STEP_MARKER = Pattern.compile(
            "(?i)(шаг\\s*\\d|step\\s*\\d|\\d+\\)|\\d+\\.)"
    );

    private MiniLectureQualityValidator() {
    }

    public static void validate(MiniLectureStructuredPayload payload, MiniLectureGenerationRequest request) {
        boolean percentTopic = isPercentQuestion(request);
        validateLangBlock(payload.ru(), "ru", percentTopic);
        validateLangBlock(payload.kz(), "kz", percentTopic);
    }

    private static void validateLangBlock(MiniLectureLangBlock block, String langKey, boolean percentTopic) {
        requireMinLength(langKey + ".theory", block.theory(), MIN_THEORY);
        requireMinLength(langKey + ".question_analysis", block.questionAnalysis(), MIN_QUESTION_ANALYSIS);
        requireMinLength(langKey + ".common_mistake", block.commonMistake(), MIN_COMMON_MISTAKE);
        requireMinLength(langKey + ".example_analysis", block.exampleAnalysis(), MIN_EXAMPLE);
        requireMinLength(langKey + ".summary", block.summary(), MIN_SUMMARY);
        requireStepStructure(langKey + ".question_analysis", block.questionAnalysis());
        requireStepStructure(langKey + ".example_analysis", block.exampleAnalysis());
        rejectFormulaOnlySnippet(langKey + ".theory", block.theory());
        if (percentTopic) {
            requirePercentPedagogy(langKey, block);
        }
    }

    private static boolean isPercentQuestion(MiniLectureGenerationRequest request) {
        String combined = (nullToEmpty(request.questionRu()) + " " + nullToEmpty(request.questionKk()))
                .toLowerCase(Locale.ROOT);
        return combined.contains("%")
                || combined.contains("процент")
                || combined.contains("пайыз");
    }

    private static void requirePercentPedagogy(String langKey, MiniLectureLangBlock block) {
        String combined = (block.theory() + "\n" + block.questionAnalysis()).toLowerCase(Locale.ROOT);
        boolean viaOnePercent = combined.contains("1%")
                || combined.contains("1 %")
                || combined.contains("один процент")
                || combined.contains("1 пайыз")
                || combined.contains("бір пайыз");
        if (!viaOnePercent) {
            throw tooBrief(langKey + ": для процентов сначала объясни через 1%");
        }
    }

    private static void rejectFormulaOnlySnippet(String field, String text) {
        String normalized = text.replace('\n', ' ').trim();
        if (normalized.length() > 180) {
            return;
        }
        if (normalized.contains("%") && (normalized.contains("=") || normalized.contains("·") || normalized.contains("*"))) {
            throw tooBrief(field + ": похоже на одну формулу без объяснения");
        }
    }

    private static void requireStepStructure(String field, String text) {
        if (text.lines().filter(line -> !line.isBlank()).count() >= 2) {
            return;
        }
        if (STEP_MARKER.matcher(text).find()) {
            return;
        }
        throw tooBrief(field + ": нужен пошаговый разбор (несколько строк или «Шаг 1»)");
    }

    private static void requireMinLength(String field, String text, int min) {
        if (text == null || text.trim().length() < min) {
            throw tooBrief(field + ": минимум " + min + " символов");
        }
    }

    private static AiProviderException tooBrief(String message) {
        return new AiProviderException("ai_mini_lecture_too_brief", message);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
