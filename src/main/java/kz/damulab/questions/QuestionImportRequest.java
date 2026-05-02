package kz.damulab.questions;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class QuestionImportRequest {

    @Valid
    @NotEmpty
    private List<QuestionForm> questions = new ArrayList<>();

    public List<QuestionForm> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionForm> questions) {
        this.questions = questions == null ? new ArrayList<>() : questions;
    }
}
