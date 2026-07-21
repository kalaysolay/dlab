package kz.damulab.ai;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.damulab.questions.QuestionType;

public class AiQuestionGenerationForm {

    @NotNull
    private Long topicId;

    private Long atomicSkillId;

    /**
     * Какие эталоны темы включить в few-shot этой генерации.
     * Семантика (см. AiContentFactoryService.selectExamplesForPrompt):
     * <ul>
     *   <li>{@code null} — поле не пришло (например, вызов API без выбора): берём все активные;</li>
     *   <li>пустой список — методист снял все галочки: не отправлять ни одного эталона;</li>
     *   <li>непустой — только перечисленные (и только если они активны и принадлежат теме).</li>
     * </ul>
     */
    private List<Long> exampleIds;

    @NotNull
    private QuestionType questionType = QuestionType.SCQ;

    @Min(1)
    @Max(5)
    private int difficulty = 2;

    @Min(1)
    @Max(10)
    private int count = 5;

    @NotNull
    private AiLanguageMode languageMode = AiLanguageMode.RU_KK;

    @Size(max = 1000)
    private String instruction;

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public Long getAtomicSkillId() {
        return atomicSkillId;
    }

    public void setAtomicSkillId(Long atomicSkillId) {
        this.atomicSkillId = atomicSkillId;
    }

    public List<Long> getExampleIds() {
        return exampleIds;
    }

    public void setExampleIds(List<Long> exampleIds) {
        this.exampleIds = exampleIds;
    }

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

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public AiLanguageMode getLanguageMode() {
        return languageMode;
    }

    public void setLanguageMode(AiLanguageMode languageMode) {
        this.languageMode = languageMode;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }
}
