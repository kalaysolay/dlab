package kz.damulab.ai;

/** Унифицированный текстовый ответ AI вместе с фактически использованным маршрутом. */
public record AiTextResult(String provider, String model, String text) {
}
