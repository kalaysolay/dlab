package kz.damulab.lectures;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Минимальный запрос страницы: весь доверенный учебный контекст сервер получает по теме. */
public class LectureAiGenerationForm {

    @NotNull
    private Long topicId;

    @Size(max = 2000)
    private String methodistInstruction;

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getMethodistInstruction() {
        return methodistInstruction;
    }

    public void setMethodistInstruction(String methodistInstruction) {
        this.methodistInstruction = methodistInstruction;
    }
}
