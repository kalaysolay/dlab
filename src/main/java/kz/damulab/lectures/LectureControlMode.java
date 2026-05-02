package kz.damulab.lectures;

public enum LectureControlMode {
    NONE("none"),
    AUTO("auto"),
    MANUAL("manual");

    private final String apiValue;

    LectureControlMode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
