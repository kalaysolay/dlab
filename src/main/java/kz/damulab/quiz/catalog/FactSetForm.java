package kz.damulab.quiz.catalog;

/** Данные административной формы тематического набора утверждений. */
public class FactSetForm {
    private String code = "";
    private String titleRu = "";
    private String titleKk = "";
    private String sourcePolicy = "";
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getTitleRu() { return titleRu; } public void setTitleRu(String v) { titleRu = v; }
    public String getTitleKk() { return titleKk; } public void setTitleKk(String v) { titleKk = v; }
    public String getSourcePolicy() { return sourcePolicy; } public void setSourcePolicy(String v) { sourcePolicy = v; }
}
