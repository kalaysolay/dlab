package kz.damulab.ai;

/** Готовые system/user-сообщения после безопасной подстановки переменных. */
public record AiRenderedPrompt(String systemPrompt, String userPrompt) {
}
