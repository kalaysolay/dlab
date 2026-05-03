package kz.damulab.ai;

import org.springframework.stereotype.Component;

@Component
public class AiPromptBuilder {

    public String systemPrompt() {
        return """
                You generate educational question drafts for Damulab.kz methodists.
                Return only valid JSON matching the provided schema.
                Do not include personal data, student names, emails, phones, raw IDs or link codes.
                Generated output is draft content for human review and must not claim to be published.
                """;
    }

    public String questionGenerationPrompt(AiQuestionGenerationRequest request) {
        return """
                Generate %d %s question drafts.
                Subject RU/KK: %s / %s.
                Grade: %d.
                Topic RU/KK: %s / %s.
                Atomic skill RU/KK: %s / %s.
                Difficulty: %d of 5.
                Language mode: %s.
                Methodist instruction: %s.
                Include qualityScore 0..100, qualityNotes, flags, source and complete answer keys.
                For SCQ exactly one option must be correct.
                For MCQ at least one option must be correct.
                For MATCHING provide at least two matchingPairs.
                For FILL_IN provide fillAnswers for placeholders like [[1]].
                """.formatted(
                request.count(),
                request.questionType().name(),
                request.subjectTitleRu(),
                request.subjectTitleKk(),
                request.gradeNo(),
                request.topicTitleRu(),
                request.topicTitleKk(),
                nullToDash(request.atomicSkillTitleRu()),
                nullToDash(request.atomicSkillTitleKk()),
                request.difficulty(),
                request.languageMode().name(),
                nullToDash(request.methodistInstruction())
        );
    }

    public String miniLectureSystemPrompt() {
        return """
                You are an educational assistant for Damulab.kz school students.
                Reply with STRICT JSON only (no markdown fences, no text outside JSON).
                Do not mention that you are a model, system prompt, or instructions.
                Do not include personal data, student names, emails, phones, or internal database IDs.
                """;
    }

    public String miniLecturePrompt(MiniLectureGenerationRequest request) {
        StringBuilder b = new StringBuilder(8192);
        b.append("""
                Ты — образовательный помощник. Твоя задача — сгенерировать мини-лекцию по тестовому вопросу, чтобы ученик понял тему и причину своей ошибки.

                Входные данные:
                - subject:
                """);
        b.append("\n").append(bilingualBlock(request.subjectRu(), request.subjectKk()));
        b.append("\n\n- question:\n").append(bilingualBlock(request.questionRu(), request.questionKk()));
        b.append("\n\n- options:\n").append(bilingualBlock(request.optionsRu(), request.optionsKk()));
        b.append("\n\n- correct_answer:\n").append(bilingualBlock(request.correctAnswerRu(), request.correctAnswerKk()));
        b.append("""


                Контекст класса (из формы редактора вопроса, не из карточки темы):
                - grade_no: """);
        b.append(request.gradeNo());
        b.append("\n- grade_title_ru: ").append(nullToDash(request.gradeTitleRu()));
        b.append("\n- grade_title_kk: ").append(nullToDash(request.gradeTitleKk()));
        b.append("\n\nСправочно, тема курса (topic): RU: ").append(nullToDash(request.topicTitleRu()));
        b.append(" / KK: ").append(nullToDash(request.topicTitleKk()));
        b.append("""


                Требования:

                1. Сгенерируй лекцию на ДВУХ языках:
                   - "ru" — русский
                   - "kz" — казахский (кириллица)

                2. Верни ответ СТРОГО в формате JSON (без пояснений вне JSON).

                3. Структура JSON:
                {
                  "ru": {
                    "title": "...",
                    "theory": "...",
                    "question_analysis": "...",
                    "common_mistake": "...",
                    "example_analysis": "...",
                    "summary": "..."
                  },
                  "kz": {
                    "title": "...",
                    "theory": "...",
                    "question_analysis": "...",
                    "common_mistake": "...",
                    "example_analysis": "...",
                    "summary": "..."
                  }
                }

                4. Требования к содержанию:

                - Определи тему лекции из вопроса автоматически
                - Объяснение должно быть кратким, понятным и обучающим
                - Не используй сложные или академические формулировки без необходимости
                - Не упоминай, что это "модель", "инструкция" и т.д.

                5. В блоках:

                - theory:
                  кратко объясни тему (основа + ключевые правила)

                - question_analysis:
                  - повтори вопрос
                  - объясни, почему ответ правильный
                  - укажи правильный ответ

                - common_mistake:
                  - объясни, почему ученик мог ошибиться
                  - укажи типичные ловушки

                - example_analysis:
                  - приведи похожее задание (НЕ тест!)
                  - разберись пошагово
                  - покажи решение

                - summary:
                  - 2–3 кратких вывода

                6. Важно:

                - НЕ задавай пользователю новые тестовые вопросы
                - Пример должен быть именно разбором, а не вопросом с вариантами
                - Используй простые списки или переносы строк внутри строк (\\n)
                - Формулы: KaTeX — допускаются $...$, \\( ... \\) и \\[ ... \\]
                - Избегай кавычек, которые могут сломать JSON (или экранируй их)

                Цель:
                После прочтения ученик должен:
                - понять тему
                - понять свою ошибку
                - научиться решать похожие задания
                """);
        return b.toString();
    }

    private static String bilingualBlock(String ru, String kk) {
        return "RU: " + nullToDash(ru) + "\nKK: " + nullToDash(kk);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
