package kz.damulab.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Детерминированно оценивает ответ модели до передачи в браузер. Порог 95 означает,
 * что неполная языковая версия, слабая структура, уход от темы или небезопасная формула
 * всегда приводят к повторной генерации, а не к выдаче сомнительного черновика.
 */
public final class AiLectureQualityValidator {

    public static final int MINIMUM_SCORE = 95;
    private static final int MIN_LANGUAGE_CHARS = 650;
    private static final int MIN_PARAGRAPHS = 7;
    private static final Pattern CYRILLIC = Pattern.compile(".*[А-Яа-яӘәҒғҚқҢңӨөҰұҮүҺһІі].*", Pattern.DOTALL);
    private static final Pattern RAW_MARKUP = Pattern.compile("(?s).*(<[^>]+>|```|\\$\\$|\\\\\\[|\\\\\\(|\\\\(?:frac|sqrt|sum|int)\\b).*");
    private static final Pattern UNSAFE_FORMULA = Pattern.compile(
            "(?i).*\\\\(?:htmlClass|htmlId|htmlStyle|htmlData|href|url|includegraphics)\\b.*",
            Pattern.DOTALL
    );

    private AiLectureQualityValidator() {
    }

    /** Возвращает полный отчёт; результат ниже 95 вызывающий код обязан отклонить. */
    public static AiLectureQualityReport evaluate(
            AiLectureStructuredPayload payload,
            AiLectureGenerationRequest request
    ) {
        List<AiLectureQualityCheck> checks = new ArrayList<>();
        boolean bilingual = payload != null && payload.ru() != null && payload.kz() != null
                && present(payload.ru().title()) && present(payload.kz().title());
        checks.add(check("bilingual", "Две языковые версии", bilingual, 20,
                bilingual ? "Русская и казахская версии присутствуют." : "Не хватает RU или KZ версии."));

        LanguageStats ru = stats(payload == null ? null : payload.ru());
        LanguageStats kz = stats(payload == null ? null : payload.kz());
        boolean structure = ru.sections() >= 3 && kz.sections() >= 3
                && ru.paragraphs() >= MIN_PARAGRAPHS && kz.paragraphs() >= MIN_PARAGRAPHS;
        checks.add(check("structure", "Структура", structure, 20,
                "RU: %d разделов/%d абзацев; KZ: %d разделов/%d абзацев."
                        .formatted(ru.sections(), ru.paragraphs(), kz.sections(), kz.paragraphs())));

        boolean volume = ru.characters() >= MIN_LANGUAGE_CHARS && kz.characters() >= MIN_LANGUAGE_CHARS;
        checks.add(check("volume", "Полнота материала", volume, 15,
                "Объём без разметки: RU %d, KZ %d символов (минимум %d)."
                        .formatted(ru.characters(), kz.characters(), MIN_LANGUAGE_CHARS)));

        boolean topic = mentionsTopic(payload == null ? null : payload.ru(), request.topicTitleRu())
                && mentionsTopic(payload == null ? null : payload.kz(), request.topicTitleKk());
        checks.add(check("topic", "Соответствие теме", topic, 15,
                topic ? "Обе версии связаны с выбранной темой." : "Одна из версий недостаточно связана с темой."));

        boolean language = containsCyrillic(payload == null ? null : payload.ru())
                && containsCyrillic(payload == null ? null : payload.kz());
        checks.add(check("language", "Язык и читаемость", language, 10,
                language ? "Обе версии содержат связный кириллический текст." : "Обнаружена пустая или некириллическая версия."));

        boolean safeProse = proseIsSafe(payload == null ? null : payload.ru())
                && proseIsSafe(payload == null ? null : payload.kz());
        checks.add(check("safe_content", "Безопасная разметка", safeProse, 10,
                safeProse ? "HTML, Markdown и сырой LaTeX в абзацах отсутствуют." : "Формулы или разметка попали в обычный текст."));

        FormulaStats formulas = formulaStats(payload);
        checks.add(check("katex", "Формулы KaTeX", formulas.valid(), 10,
                formulas.valid()
                        ? "Проверено формул: %d; они будут сохранены как KaTeX-блоки.".formatted(formulas.count())
                        : "Найдена пустая, слишком длинная, несбалансированная или небезопасная формула."));

        int score = checks.stream().filter(AiLectureQualityCheck::passed)
                .mapToInt(AiLectureQualityCheck::points).sum();
        String summary = score >= MINIMUM_SCORE
                ? "Лекция прошла проверку качества: %d из 100.".formatted(score)
                : "Лекция отклонена валидатором: %d из 100, требуется не менее %d."
                        .formatted(score, MINIMUM_SCORE);
        return new AiLectureQualityReport(score, MINIMUM_SCORE, summary, List.copyOf(checks));
    }

    /** Проверяет порог и несёт отчёт в исключении для ретрая/ответа API. */
    public static AiLectureQualityReport requireAccepted(
            AiLectureStructuredPayload payload,
            AiLectureGenerationRequest request
    ) {
        AiLectureQualityReport report = evaluate(payload, request);
        if (report.score() < report.minimumScore()) {
            throw new AiLectureQualityException(report);
        }
        return report;
    }

    /** Добавка к следующему запросу содержит конкретные проваленные проверки. */
    public static String retrySuffix(AiLectureQualityReport report) {
        String issues = report.checks().stream()
                .filter(item -> !item.passed())
                .map(item -> "- " + item.title() + ": " + item.details())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- Исправь структуру и полноту лекции.");
        return "\n\nПредыдущий ответ получил %d/100 и отклонён. Исправь все пункты:\n%s\n"
                .formatted(report.score(), issues);
    }

    private static AiLectureQualityCheck check(
            String code,
            String title,
            boolean passed,
            int points,
            String details
    ) {
        return new AiLectureQualityCheck(code, title, passed, points, details);
    }

    private static LanguageStats stats(AiLectureLanguagePayload block) {
        if (block == null) {
            return new LanguageStats(0, 0, 0);
        }
        int sections = block.sections() == null ? 0 : block.sections().size();
        int paragraphs = 0;
        StringBuilder text = new StringBuilder();
        append(text, block.title());
        append(text, block.introduction());
        append(text, block.conclusion());
        if (block.sections() != null) {
            for (AiLectureSectionPayload section : block.sections()) {
                if (section == null) {
                    continue;
                }
                append(text, section.heading());
                if (section.paragraphs() != null) {
                    paragraphs += (int) section.paragraphs().stream()
                            .filter(AiLectureQualityValidator::present)
                            .count();
                    section.paragraphs().forEach(value -> append(text, value));
                }
            }
        }
        if (present(block.introduction())) {
            paragraphs++;
        }
        if (present(block.conclusion())) {
            paragraphs++;
        }
        return new LanguageStats(sections, paragraphs, text.toString().trim().length());
    }

    private static boolean mentionsTopic(AiLectureLanguagePayload block, String topicTitle) {
        if (block == null || !present(topicTitle)) {
            return false;
        }
        String text = allProse(block).toLowerCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>();
        for (String word : topicTitle.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 4) {
                tokens.add(word);
            }
        }
        return tokens.isEmpty() ? text.contains(topicTitle.toLowerCase(Locale.ROOT))
                : tokens.stream().anyMatch(text::contains);
    }

    private static boolean containsCyrillic(AiLectureLanguagePayload block) {
        return block != null && CYRILLIC.matcher(allProse(block)).matches();
    }

    private static boolean proseIsSafe(AiLectureLanguagePayload block) {
        if (block == null) {
            return false;
        }
        return !RAW_MARKUP.matcher(allProse(block)).matches();
    }

    private static FormulaStats formulaStats(AiLectureStructuredPayload payload) {
        int count = 0;
        boolean valid = true;
        List<AiLectureLanguagePayload> blocks = new ArrayList<>();
        if (payload != null) {
            blocks.add(payload.ru());
            blocks.add(payload.kz());
        }
        for (AiLectureLanguagePayload block : blocks) {
            if (block == null || block.sections() == null) {
                continue;
            }
            for (AiLectureSectionPayload section : block.sections()) {
                if (section == null || section.formulas() == null) {
                    continue;
                }
                for (String formula : section.formulas()) {
                    count++;
                    valid &= validFormula(formula);
                }
            }
        }
        return new FormulaStats(count, valid);
    }

    private static boolean validFormula(String formula) {
        if (!present(formula) || formula.length() > 1000 || formula.contains("<") || formula.contains(">")
                || formula.contains("$") || UNSAFE_FORMULA.matcher(formula).matches()) {
            return false;
        }
        int balance = 0;
        for (int i = 0; i < formula.length(); i++) {
            if (formula.charAt(i) == '{') {
                balance++;
            } else if (formula.charAt(i) == '}' && --balance < 0) {
                return false;
            }
        }
        return balance == 0;
    }

    private static String allProse(AiLectureLanguagePayload block) {
        StringBuilder text = new StringBuilder();
        append(text, block.title());
        append(text, block.introduction());
        append(text, block.conclusion());
        if (block.sections() != null) {
            for (AiLectureSectionPayload section : block.sections()) {
                if (section == null) {
                    continue;
                }
                append(text, section.heading());
                if (section.paragraphs() != null) {
                    section.paragraphs().forEach(value -> append(text, value));
                }
            }
        }
        return text.toString();
    }

    private static void append(StringBuilder target, String value) {
        if (present(value)) {
            target.append(value.trim()).append('\n');
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record LanguageStats(int sections, int paragraphs, int characters) {
    }

    private record FormulaStats(int count, boolean valid) {
    }
}
