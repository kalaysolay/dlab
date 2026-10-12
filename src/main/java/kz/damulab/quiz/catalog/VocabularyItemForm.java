package kz.damulab.quiz.catalog;

import java.util.ArrayList;
import java.util.List;

/** Данные одной учебной редакции слова; альтернативы вводятся отдельно от единственного primary. */
public class VocabularyItemForm {
    private Long setId;
    private String sourceText = "";
    private String pronunciation = "";
    private String partOfSpeech = "";
    private String meaningNoteRu = "";
    private String meaningNoteKk = "";
    private int difficulty = 1;
    private String sourceReference = "";
    private boolean universalAudience;
    private List<Long> gradeIds = new ArrayList<>();
    private String primaryAnswer = "";
    private String alternativeAnswers = "";
    public Long getSetId() { return setId; } public void setSetId(Long v) { setId = v; }
    public String getSourceText() { return sourceText; } public void setSourceText(String v) { sourceText = v; }
    public String getPronunciation() { return pronunciation; } public void setPronunciation(String v) { pronunciation = v; }
    public String getPartOfSpeech() { return partOfSpeech; } public void setPartOfSpeech(String v) { partOfSpeech = v; }
    public String getMeaningNoteRu() { return meaningNoteRu; } public void setMeaningNoteRu(String v) { meaningNoteRu = v; }
    public String getMeaningNoteKk() { return meaningNoteKk; } public void setMeaningNoteKk(String v) { meaningNoteKk = v; }
    public int getDifficulty() { return difficulty; } public void setDifficulty(int v) { difficulty = v; }
    public String getSourceReference() { return sourceReference; } public void setSourceReference(String v) { sourceReference = v; }
    public boolean isUniversalAudience() { return universalAudience; } public void setUniversalAudience(boolean v) { universalAudience = v; }
    public List<Long> getGradeIds() { return gradeIds; } public void setGradeIds(List<Long> v) { gradeIds = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public String getPrimaryAnswer() { return primaryAnswer; } public void setPrimaryAnswer(String v) { primaryAnswer = v; }
    public String getAlternativeAnswers() { return alternativeAnswers; } public void setAlternativeAnswers(String v) { alternativeAnswers = v; }
}
