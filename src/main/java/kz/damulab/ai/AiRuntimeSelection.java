package kz.damulab.ai;

/** Неизменяемый снимок выбранного маршрута для одного AI-вызова. */
public record AiRuntimeSelection(AiProviderCode provider, String model) {
}
