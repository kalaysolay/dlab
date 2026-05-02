package kz.damulab.lectures;

public enum LectureStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    ARCHIVED("archived");

    private final String apiValue;

    LectureStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
