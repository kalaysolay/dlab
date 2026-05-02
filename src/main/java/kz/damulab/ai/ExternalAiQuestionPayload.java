package kz.damulab.ai;

import java.util.ArrayList;
import java.util.List;

public class ExternalAiQuestionPayload {

    private List<AiGeneratedQuestionDraft> questions = new ArrayList<>();

    public List<AiGeneratedQuestionDraft> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AiGeneratedQuestionDraft> questions) {
        this.questions = questions == null ? new ArrayList<>() : questions;
    }
}
