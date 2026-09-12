package kz.damulab.ai;

/** Активный неизменяемый снимок промпта, удобный для UI и адаптеров фич. */
public record AiPromptSnapshot(
        AiPromptCode code,
        int version,
        String systemTemplate,
        String userTemplate
) {
}
