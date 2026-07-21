package kz.damulab.content;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import kz.damulab.questions.ChoiceOptionForm;
import kz.damulab.questions.FillAnswerForm;
import kz.damulab.questions.MatchingPairForm;
import kz.damulab.questions.QuestionType;

/**
 * Форма создания/редактирования эталонного вопроса темы (вариант B).
 *
 * <p>Структура намеренно повторяет {@code QuestionForm} банка вопросов: те же
 * {@link ChoiceOptionForm}/{@link MatchingPairForm}/{@link FillAnswerForm}. Благодаря этому
 * редактор эталона и редактор вопроса выглядят одинаково, а {@code TopicAiExampleService}
 * переиспользует те же правила валидации ключа ответа.
 *
 * <p>Чего здесь нет по сравнению с вопросом банка: {@code source}, {@code status},
 * {@code subject/topic/grade} (тема задаётся путём {@code /admin/topics/{id}/ai-examples}),
 * мини-лекции. Эталон — это учебный образец, а не публикуемый контент.
 *
 * <p>Только один из списков ({@link #options}/{@link #matchingPairs}/{@link #fillAnswers})
 * реально используется — согласно {@link #questionType}; остальные игнорируются сервисом.
 */
public class TopicAiExampleForm {

    @NotNull
    private QuestionType questionType = QuestionType.SCQ;

    @Min(1)
    @Max(5)
    private int difficulty = 2;

    @NotBlank
    private String bodyRu;

    @NotBlank
    private String bodyKk;

    /** Отправлять ли эталон в промпт. По умолчанию да; можно временно отключить без удаления. */
    private boolean includeInAi = true;

    /** Внутренняя заметка методиста — в provider DTO не уходит. */
    @Size(max = 1000)
    private String internalNote;

    private List<ChoiceOptionForm> options = new ArrayList<>();
    private List<MatchingPairForm> matchingPairs = new ArrayList<>();
    private List<FillAnswerForm> fillAnswers = new ArrayList<>();

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public String getBodyRu() {
        return bodyRu;
    }

    public void setBodyRu(String bodyRu) {
        this.bodyRu = bodyRu;
    }

    public String getBodyKk() {
        return bodyKk;
    }

    public void setBodyKk(String bodyKk) {
        this.bodyKk = bodyKk;
    }

    public boolean isIncludeInAi() {
        return includeInAi;
    }

    public void setIncludeInAi(boolean includeInAi) {
        this.includeInAi = includeInAi;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public void setInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }

    public List<ChoiceOptionForm> getOptions() {
        return options;
    }

    public void setOptions(List<ChoiceOptionForm> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public List<MatchingPairForm> getMatchingPairs() {
        return matchingPairs;
    }

    public void setMatchingPairs(List<MatchingPairForm> matchingPairs) {
        this.matchingPairs = matchingPairs == null ? new ArrayList<>() : matchingPairs;
    }

    public List<FillAnswerForm> getFillAnswers() {
        return fillAnswers;
    }

    public void setFillAnswers(List<FillAnswerForm> fillAnswers) {
        this.fillAnswers = fillAnswers == null ? new ArrayList<>() : fillAnswers;
    }
}
