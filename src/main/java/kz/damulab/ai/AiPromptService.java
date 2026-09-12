package kz.damulab.ai;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import kz.damulab.audit.AdminContentAuditService;

/**
 * Универсальное хранилище и renderer AI-промптов. Сервис знает только ключ,
 * версию, шаблоны и карту значений; особенности запроса остаются в адаптере фичи.
 */
@Service
public class AiPromptService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

    private final AiPromptRepository prompts;
    private final AiPromptVersionRepository versions;
    private final AdminContentAuditService audit;

    public AiPromptService(
            AiPromptRepository prompts,
            AiPromptVersionRepository versions,
            AdminContentAuditService audit
    ) {
        this.prompts = prompts;
        this.versions = versions;
        this.audit = audit;
    }

    /** Загружает активную версию перед каждым вызовом — изменения не требуют рестарта. */
    @Transactional(readOnly = true)
    public AiPromptSnapshot current(AiPromptCode code) {
        AiPrompt prompt = requirePrompt(code);
        AiPromptVersion version = requireVersion(code, prompt.getActiveVersion());
        return snapshot(version);
    }

    /** Безопасно подставляет значения за один проход, не обрабатывая токены внутри самих значений. */
    @Transactional(readOnly = true)
    public AiRenderedPrompt render(AiPromptCode code, Map<String, String> values) {
        AiPromptSnapshot prompt = current(code);
        if (!values.keySet().equals(code.requiredPlaceholders())) {
            throw new IllegalArgumentException(
                    "AI prompt values must be exactly: " + formatPlaceholders(code.requiredPlaceholders())
            );
        }
        return new AiRenderedPrompt(
                prompt.systemTemplate(),
                renderTemplate(prompt.userTemplate(), values, code.requiredPlaceholders())
        );
    }

    /** Проверяет поле формы по контракту переменных конкретного промпта. */
    public void validateTemplate(AiPromptCode code, String field, String template, Errors errors) {
        if (template == null || template.isBlank()) {
            return;
        }
        Set<String> actual = templatePlaceholders(template);
        Set<String> required = code.requiredPlaceholders();
        Set<String> missing = required.stream()
                .filter(value -> !actual.contains(value))
                .collect(Collectors.toSet());
        Set<String> unknown = actual.stream()
                .filter(value -> !required.contains(value))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            errors.rejectValue(field, "prompt.placeholders.missing", "Добавьте переменные: " + formatPlaceholders(missing));
        } else if (!unknown.isEmpty()) {
            errors.rejectValue(field, "prompt.placeholders.unknown", "Неизвестные переменные: " + formatPlaceholders(unknown));
        }
    }

    /**
     * Создаёт новую неизменяемую версию и атомарно активирует её. Если текст не
     * изменился, лишняя версия и запись аудита не создаются.
     */
    @Transactional
    public void update(AiPromptCode code, String systemTemplate, String userTemplate) {
        assertTemplate(userTemplate, code.requiredPlaceholders());
        if (systemTemplate == null || systemTemplate.isBlank()) {
            throw new IllegalArgumentException("AI system template must not be blank");
        }
        AiPrompt prompt = prompts.findByCodeForUpdate(code)
                .orElseThrow(() -> new IllegalStateException("AI prompt is missing: " + code));
        AiPromptVersion current = requireVersion(code, prompt.getActiveVersion());
        String normalizedSystem = systemTemplate.strip();
        String normalizedUser = userTemplate.strip();
        if (current.getSystemTemplate().equals(normalizedSystem)
                && current.getUserTemplate().equals(normalizedUser)) {
            return;
        }

        int nextVersion = versions.findFirstByPromptCodeOrderByVersionNoDesc(code)
                .map(AiPromptVersion::getVersionNo)
                .orElse(0) + 1;
        String actor = currentActor();
        versions.save(new AiPromptVersion(
                code,
                nextVersion,
                normalizedSystem,
                normalizedUser,
                actor
        ));
        prompt.activate(nextVersion, actor);
        prompts.save(prompt);
        audit.record(
                "ai_prompt_version_activated",
                "AiPrompt",
                (long) code.ordinal() + 1,
                code + ":version=" + nextVersion
        );
    }

    private AiPrompt requirePrompt(AiPromptCode code) {
        return prompts.findById(code)
                .orElseThrow(() -> new IllegalStateException("AI prompt is missing: " + code));
    }

    private AiPromptVersion requireVersion(AiPromptCode code, int version) {
        return versions.findByPromptCodeAndVersionNo(code, version)
                .orElseThrow(() -> new IllegalStateException(
                        "AI prompt version is missing: " + code + " v" + version
                ));
    }

    private AiPromptSnapshot snapshot(AiPromptVersion version) {
        return new AiPromptSnapshot(
                version.getPromptCode(),
                version.getVersionNo(),
                version.getSystemTemplate(),
                version.getUserTemplate()
        );
    }

    private String renderTemplate(String template, Map<String, String> values, Set<String> required) {
        assertTemplate(template, required);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length() + 128);
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void assertTemplate(String template, Set<String> required) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("AI prompt template must not be blank");
        }
        if (!templatePlaceholders(template).equals(required)) {
            throw new IllegalArgumentException(
                    "AI prompt placeholders must be exactly: " + formatPlaceholders(required)
            );
        }
    }

    private Set<String> templatePlaceholders(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        HashSet<String> result = new HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private String formatPlaceholders(Set<String> placeholders) {
        return placeholders.stream()
                .sorted()
                .map(value -> "{" + value + "}")
                .collect(Collectors.joining(", "));
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();
    }
}
