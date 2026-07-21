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

    /**
     * Собирает user-промпт для генерации черновиков вопросов.
     *
     * <p>Структура: параметры (предмет/класс/тема/навык/тип/сложность/язык) + инструкция методиста
     * + правила по типам ответов. Если у запроса есть эталоны темы ({@link AiQuestionGenerationRequest#examples()}),
     * в конец добавляется few-shot блок (см. {@link #examplesBlock}).
     *
     * <p>Порядок важен: примеры идут ПОСЛЕ правил и содержат явную анти-копирующую инструкцию,
     * иначе модель склонна дословно воспроизводить эталон вместо генерации нового вопроса.
     */
    public String questionGenerationPrompt(AiQuestionGenerationRequest request) {
        String base = """
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
        return base + examplesBlock(request.examples());
    }

    /**
     * Few-shot блок с эталонами темы. Возвращает пустую строку, если эталонов нет
     * (список {@code null} или пуст) — тогда промпт не меняется по сравнению с прежним поведением.
     *
     * <p>Каждый эталон печатается как тело RU/KK + ключ ответа (JSON — тот же формат, что
     * ожидается в ответе модели). Эталоны помечены как «reference only» со строгим требованием
     * не копировать их дословно.
     */
    private String examplesBlock(java.util.List<AiExamplePayload> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder(1024);
        b.append("\nReference examples from this topic (style and scope only).\n");
        b.append("IMPORTANT: Generate NEW questions; do not copy these examples verbatim.\n");
        int index = 1;
        for (AiExamplePayload example : examples) {
            b.append("Example ").append(index++)
                    .append(" (").append(example.questionType().name())
                    .append(", difficulty ").append(example.difficulty()).append("):\n");
            b.append("RU: ").append(nullToDash(example.bodyRu())).append("\n");
            b.append("KK: ").append(nullToDash(example.bodyKk())).append("\n");
            b.append("Answer key JSON: ").append(nullToDash(example.answerKeyJson())).append("\n");
        }
        return b.toString();
    }

    public String miniLectureSystemPrompt() {
        return """
                You are an educational assistant for Damulab.kz school students.
                Reply with STRICT JSON only (no markdown fences, no text outside JSON).
                Explain the idea in simple words before showing formulas or calculations.
                Do not mention that you are a model, system prompt, or instructions.
                Do not include personal data, student names, emails, phones, or internal database IDs.
                """;
    }

    public String miniLecturePrompt(MiniLectureGenerationRequest request) {
        StringBuilder b = new StringBuilder(8192);
        b.append("""
                Ты — образовательный помощник, который объясняет тестовые вопросы школьникам.

                Твоя задача — написать мини-лекцию по вопросу.
                Мини-лекция должна помочь ученику понять тему, разобраться в решении и не повторить ошибку.

                Главный принцип:
                Сначала объясняй смысл простыми словами, потом показывай вычисления.
                Не начинай сразу с формулы, если задачу можно объяснить через понятный жизненный или пошаговый подход.

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


                Язык ответа:
                - Система двухязычная: не смешивай языки в одном объяснении без необходимости.
                - Сгенерируй лекцию на ДВУХ языках:
                  - "ru" — русский
                  - "kz" — казахский (кириллица)

                Стиль:
                - доброжелательно;
                - понятно для школьника указанного класса (grade_no);
                - без сухого академического тона;
                - короткими абзацами;
                - без лишней воды;
                - каждое поле JSON — отдельный развёрнутый фрагмент, не одно предложение.

                Минимальный объём (на каждое поле внутри ru и kz):
                - theory — не короче 80 символов, минимум 3 предложения;
                - question_analysis — не короче 140 символов, минимум 3 шага с пояснениями;
                - common_mistake — не короче 50 символов;
                - example_analysis — не короче 100 символов, пошаговое решение другого примера;
                - summary — не короче 25 символов.

                Верни ответ СТРОГО в формате JSON (без пояснений вне JSON):

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

                Заполни поля JSON по смыслу следующих разделов:

                title — Тема:
                Кратко назови тему вопроса.

                theory — Объяснение и почему так:
                Простыми словами объясни идею темы.
                Затем коротко объясни, почему такой способ решения работает.
                Не начинай с сухой формулы — сначала смысл.

                question_analysis — Разбор задачи:
                Пошагово реши именно данную задачу из поля question.
                Каждый шаг сопровождай коротким объяснением.
                Если есть правильный ответ в correct_answer — обязательно приведи его и покажи, как к нему прийти.

                common_mistake — Типичная ошибка:
                Мягко объясни, почему ученик мог ошибиться.
                Укажи типичные ловушки по этой теме.

                example_analysis — Похожий пример:
                Дай один похожий пример для закрепления и реши его пошагово.
                Это должен быть разбор, а не новый тестовый вопрос с вариантами ответа.

                summary — Что запомнить:
                Одна короткая мысль, которую ученик должен унести с собой.

                Дополнительные правила:
                - Если вопрос про проценты для младших классов (4–5), сначала объясняй через 1%, затем можно показать запись через дробь.
                - Если вопрос на казахском языке, используй естественный школьный казахский язык, без сложных терминов.
                - Не используй фразы вроде «очевидно», «просто», «легко», если они могут обесценить труд ученика.
                - НЕ задавай пользователю новые тестовые вопросы.
                - Используй простые списки или переносы строк внутри строк (\\n).
                - Формулы: KaTeX — допускаются $...$, \\( ... \\) и \\[ ... \\].
                - Избегай кавычек, которые могут сломать JSON (или экранируй их).

                Цель:
                После прочтения ученик должен:
                - понять тему;
                - понять свою ошибку;
                - научиться решать похожие задания.
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
