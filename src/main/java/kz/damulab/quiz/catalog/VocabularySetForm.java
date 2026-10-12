package kz.damulab.quiz.catalog;

/** Данные административной формы набора слов; опубликованная редакция через эту модель не изменяется. */
public class VocabularySetForm {
    private String code = "";
    private String sourceLanguage = "en";
    private String targetLanguage = "ru";
    private String titleRu = "";
    private String titleKk = "";
    private String sourceReference = "";
    private boolean crossSetDistractorsAllowed;
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getSourceLanguage() { return sourceLanguage; } public void setSourceLanguage(String v) { sourceLanguage = v; }
    public String getTargetLanguage() { return targetLanguage; } public void setTargetLanguage(String v) { targetLanguage = v; }
    public String getTitleRu() { return titleRu; } public void setTitleRu(String v) { titleRu = v; }
    public String getTitleKk() { return titleKk; } public void setTitleKk(String v) { titleKk = v; }
    public String getSourceReference() { return sourceReference; } public void setSourceReference(String v) { sourceReference = v; }
    public boolean isCrossSetDistractorsAllowed() { return crossSetDistractorsAllowed; }
    public void setCrossSetDistractorsAllowed(boolean v) { crossSetDistractorsAllowed = v; }
}
