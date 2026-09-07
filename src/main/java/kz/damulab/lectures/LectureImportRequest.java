package kz.damulab.lectures;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Версионированный JSON-контракт агентского импорта лекций.
 *
 * <p>Контент остаётся Quill-совместимым HTML, а бинарные изображения передаются
 * отдельно и связываются с HTML через {@code asset://id}. Вложенные records держат
 * весь внешний контракт рядом и не смешивают его с внутренним {@link LectureForm}.</p>
 */
public record LectureImportRequest(
        @NotBlank String schemaVersion,
        @NotBlank String kind,
        @Valid @NotEmpty @Size(max = 100) List<Lesson> lessons
) {

    /** Одна независимо идентифицируемая лекция в batch-файле. */
    public record Lesson(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "[a-z0-9][a-z0-9-]*")
            String externalId,
            @Valid @NotNull Metadata metadata,
            @Valid @Size(max = 50) List<Asset> assets,
            @Valid @NotNull LocalizedContent content,
            @Valid @Size(max = 8) List<LectureAttachmentForm> attachments,
            @Valid @NotNull CompletionControl completionControl
    ) {
    }

    /**
     * Метаданные ссылаются на существующий учебный граф напрямую по первичным ключам.
     *
     * <p>Все три ID передаются намеренно, хотя {@code topicId} уже косвенно определяет
     * предмет и класс. Это делает JSON самодокументируемым, а сервис может проверить,
     * что агент не смешал тему с предметом или классом из другого контекста.</p>
     */
    public record Metadata(
            @NotNull @Min(1) Long subjectId,
            @NotNull @Min(1) Long gradeId,
            @NotNull @Min(1) Long topicId,
            @Valid @NotNull LocalizedText title,
            @NotBlank @Pattern(regexp = "kk|ru") String primaryLanguage,
            @NotBlank @Size(max = 512) String source
    ) {
    }

    public record LocalizedText(
            @NotBlank @Size(max = 255) String ru,
            @NotBlank @Size(max = 255) String kk
    ) {
    }

    /** HTML локализаций хранится отдельно, как и в текущей версии лекции. */
    public record LocalizedContent(
            @NotBlank String ruHtml,
            @NotBlank String kkHtml
    ) {
    }

    /** Описание реального изображения; HTTP endpoint версии 1.0 принимает только Base64. */
    public record Asset(
            @NotBlank
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
            String id,
            @NotBlank String kind,
            @NotBlank String mimeType,
            @Valid @NotNull AssetSource source,
            @Pattern(regexp = "[a-f0-9]{64}") String sha256,
            @Size(max = 512) String credit
    ) {
    }

    public record AssetSource(
            @NotBlank String type,
            @NotBlank String data
    ) {
    }

    /** MANUAL намеренно не представлен: первая версия всегда использует автоподбор. */
    public record CompletionControl(
            @NotBlank String mode,
            @NotNull @Min(1) @Max(10) Integer questionCount
    ) {
    }
}
