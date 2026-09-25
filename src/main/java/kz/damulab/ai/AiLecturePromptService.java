package kz.damulab.ai;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;

/** Адаптер полноразмерных лекций к общему версионируемому каталогу {@code ai_prompts}. */
@Service
public class AiLecturePromptService {

    private final AiPromptService prompts;

    public AiLecturePromptService(AiPromptService prompts) {
        this.prompts = prompts;
    }

    /** Подставляет проверенный серверный контекст темы в активную версию шаблона из БД. */
    public AiRenderedPrompt render(AiLectureGenerationRequest request) {
        return prompts.render(AiPromptCode.LECTURE_GENERATE, Map.of(
                "subjectTitleRu", value(request.subjectTitleRu()),
                "subjectTitleKk", value(request.subjectTitleKk()),
                "gradeNo", Integer.toString(request.gradeNo()),
                "gradeTitleRu", value(request.gradeTitleRu()),
                "gradeTitleKk", value(request.gradeTitleKk()),
                "topicTitleRu", value(request.topicTitleRu()),
                "topicTitleKk", value(request.topicTitleKk()),
                "methodistInstruction", value(request.methodistInstruction())
        ));
    }

    /** Загружает форму из активной версии, поэтому изменения видны без перезапуска. */
    public AiLecturePromptForm currentForm() {
        AiPromptSnapshot current = prompts.current(AiPromptCode.LECTURE_GENERATE);
        AiLecturePromptForm form = new AiLecturePromptForm();
        form.setSystemPrompt(current.systemTemplate());
        form.setUserPromptTemplate(current.userTemplate());
        return form;
    }

    /** Проверяет точный набор обязательных фигурных переменных. */
    public void validate(AiLecturePromptForm form, Errors errors) {
        prompts.validateTemplate(
                AiPromptCode.LECTURE_GENERATE,
                "userPromptTemplate",
                form.getUserPromptTemplate(),
                errors
        );
    }

    /** Создаёт новую неизменяемую версию промпта и активирует её. */
    public void update(AiLecturePromptForm form) {
        prompts.update(AiPromptCode.LECTURE_GENERATE, form.getSystemPrompt(), form.getUserPromptTemplate());
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
