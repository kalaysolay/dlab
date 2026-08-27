package kz.damulab.ai;

/** Поддерживаемые маршрутизатором AI-провайдеры, доступные в настройках админки. */
public enum AiProviderCode {
    STUB("Stub — без внешнего API"),
    OPENAI("OpenAI"),
    DEEPSEEK("DeepSeek");

    private final String title;

    AiProviderCode(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
