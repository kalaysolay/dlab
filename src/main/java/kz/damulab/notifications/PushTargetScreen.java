package kz.damulab.notifications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PushTargetScreen {
    QUIZ_CREATE_ROOM("quiz_create_room", "Викторина: создать комнату"),
    SUBJECT_TEST("subject_test", "Тест по предмету");

    private final String apiValue;
    private final String titleRu;

    PushTargetScreen(String apiValue, String titleRu) {
        this.apiValue = apiValue;
        this.titleRu = titleRu;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    public String titleRu() {
        return titleRu;
    }

    @JsonCreator
    public static PushTargetScreen fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PushTargetScreen screen : values()) {
            if (screen.apiValue.equalsIgnoreCase(value) || screen.name().equalsIgnoreCase(value)) {
                return screen;
            }
        }
        throw new IllegalArgumentException("Unknown push target screen: " + value);
    }
}
