package kz.damulab.testing;

public enum TestType {
    SUBJECT("Предметный срез"),
    MODO("МОДО"),
    SOR("СОР"),
    SOCH("СОЧ"),
    KAZAKH_LANGUAGE("Казахский язык");

    private final String titleRu;

    TestType(String titleRu) {
        this.titleRu = titleRu;
    }

    public String getTitleRu() {
        return titleRu;
    }
}
