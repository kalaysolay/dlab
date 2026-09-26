package kz.damulab.ai;

/** Неизменяемый снимок маршрута и порога качества для одного AI-вызова. */
public record AiRuntimeSelection(AiProviderCode provider, String model, int qualityThreshold) {
}
