package kz.damulab.ai;

/** Ответ модели с обязательными русской и казахской версиями. */
public record AiLectureStructuredPayload(AiLectureLanguagePayload ru, AiLectureLanguagePayload kz) {
}
