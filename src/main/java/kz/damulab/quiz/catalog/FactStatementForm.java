package kz.damulab.quiz.catalog;

import java.util.ArrayList;
import java.util.List;

/** Данные редакции утверждения; Boolean отличает false от отсутствующего значения формы. */
public class FactStatementForm {
    private Long setId;
    private String textRu = "";
    private String textKk = "";
    private Boolean truthValue;
    private String explanationRu = "";
    private String explanationKk = "";
    private String sourceReference = "";
    private boolean universalAudience;
    private List<Long> gradeIds = new ArrayList<>();
    public Long getSetId() { return setId; } public void setSetId(Long v) { setId = v; }
    public String getTextRu() { return textRu; } public void setTextRu(String v) { textRu = v; }
    public String getTextKk() { return textKk; } public void setTextKk(String v) { textKk = v; }
    public Boolean getTruthValue() { return truthValue; } public void setTruthValue(Boolean v) { truthValue = v; }
    public String getExplanationRu() { return explanationRu; } public void setExplanationRu(String v) { explanationRu = v; }
    public String getExplanationKk() { return explanationKk; } public void setExplanationKk(String v) { explanationKk = v; }
    public String getSourceReference() { return sourceReference; } public void setSourceReference(String v) { sourceReference = v; }
    public boolean isUniversalAudience() { return universalAudience; } public void setUniversalAudience(boolean v) { universalAudience = v; }
    public List<Long> getGradeIds() { return gradeIds; } public void setGradeIds(List<Long> v) { gradeIds = v == null ? new ArrayList<>() : new ArrayList<>(v); }
}
