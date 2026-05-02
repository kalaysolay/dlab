package kz.damulab.questions;

public enum QuestionStatus {
    DRAFT,
    NEEDS_REVIEW,
    APPROVED,
    PUBLISHED,
    ARCHIVED;

    public String apiValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
