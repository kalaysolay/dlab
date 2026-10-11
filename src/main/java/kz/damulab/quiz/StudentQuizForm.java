package kz.damulab.quiz;

/** Поля пользовательской формы. Вместимость задаёт только сервер, язык формы задаёт текущая locale сайта. */
public class StudentQuizForm {
    private Long subjectId;
    private Long gradeId;
    private String language;
    private Integer questionCount = 15;
    private Integer roundSeconds = 10;
    private String idempotencyKey;

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long value) { subjectId = value; }
    public Long getGradeId() { return gradeId; }
    public void setGradeId(Long value) { gradeId = value; }
    public String getLanguage() { return language; }
    public void setLanguage(String value) { language = value; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer value) { questionCount = value; }
    public Integer getRoundSeconds() { return roundSeconds; }
    public void setRoundSeconds(Integer value) { roundSeconds = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public QuizGameRoomRequest.CustomCreate request() {
        return new QuizGameRoomRequest.CustomCreate(subjectId, gradeId, language, questionCount, roundSeconds, idempotencyKey);
    }
}
