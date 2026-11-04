package kz.damulab.lectures;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Условная синхронизация одного локального урока с административным черновиком.
 * Стабильный externalId предотвращает дубли, SHA всего ответа обнаруживает правки
 * в админке, а общая блокировка Lecture исключает перезапись между сверкой и записью.
 */
@Service
public class LectureStudioSyncService {
    private final LectureRepository lectures;
    private final LectureService service;
    private final LectureImportService importer;
    private final ObjectMapper mapper;

    public LectureStudioSyncService(LectureRepository lectures, LectureService service,
            LectureImportService importer, ObjectMapper mapper) {
        this.lectures = lectures; this.service = service; this.importer = importer; this.mapper = mapper;
    }

    /** Возвращает opaque revision; клиент сохраняет её без попыток воспроизвести Java JSON. */
    @Transactional(readOnly = true)
    public SyncResponse lookup(String externalId) {
        Lecture lecture = lectures.findByExternalIdIgnoreCase(externalId)
                .orElseThrow(() -> new LectureException("lecture_not_found"));
        return response(service.getLecture(lecture.getId()), Map.of());
    }

    /** Создаёт или обновляет черновик; текущая опубликованная версия остаётся доступной ученикам. */
    @Transactional
    public SyncResponse sync(SyncRequest request) {
        Lecture lecture = lectures.findByExternalIdIgnoreCase(request.lesson().externalId()).orElse(null);
        if (lecture != null) {
            lecture = lock(lecture.getId());
            checkRevision(service.getLecture(lecture.getId()), request.expectedRevision());
        } else if (request.expectedRevision() != null) {
            throw new LectureException("lecture_sync_conflict");
        }
        var prepared = importer.prepareForStudio(request.lesson());
        try {
            LectureResponse saved = lecture == null
                    ? service.createImportedLecture(prepared.form(), request.lesson().externalId())
                    : service.updateLecture(lecture.getId(), prepared.form());
            return response(saved, prepared.assetUrls());
        } catch (RuntimeException ex) {
            importer.cleanupStudio(prepared);
            throw ex;
        }
    }

    /** Публикация требует отдельного запроса и той же проверенной версии, что показана клиенту. */
    @Transactional
    public SyncResponse publish(PublishRequest request) {
        Lecture lecture = lectures.findByExternalIdIgnoreCase(request.externalId())
                .orElseThrow(() -> new LectureException("lecture_not_found"));
        lock(lecture.getId());
        checkRevision(service.getLecture(lecture.getId()), request.expectedRevision());
        return response(service.publish(lecture.getId()), Map.of());
    }

    /** Версия renderer читается из поставляемого JS, а не из независимо устаревающей константы. */
    public Map<String, Object> capabilities() {
        try (var stream = new ClassPathResource("static/js/lecture-content.js").getInputStream()) {
            String js = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var match = java.util.regex.Pattern.compile("version: '([^']+)'").matcher(js);
            if (!match.find()) throw new IllegalStateException("Renderer version is missing");
            return Map.of("kind", "damulab.lesson-studio", "syncVersion", "1.0", "lessonSchemaVersion", 2,
                    "rendererVersion", match.group(1), "conditionalWrites", true,
                    "controlModes", Set.of("NONE", "AUTO"),
                    "features", Set.of("formula-display", "table-headers", "table-spans", "nested-lists",
                            "semantic-blocks", "images", "youtube"));
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot read renderer version", ex); }
    }

    private Lecture lock(Long id) {
        return lectures.findByIdForUpdate(id).orElseThrow(() -> new LectureException("lecture_not_found"));
    }
    private void checkRevision(LectureResponse lecture, String expected) {
        if (expected == null || !revision(lecture).equals(expected)) throw new LectureException("lecture_sync_conflict");
    }
    private SyncResponse response(LectureResponse lecture, Map<String, String> assets) {
        return new SyncResponse(lecture, revision(lecture), assets);
    }
    private String revision(LectureResponse lecture) {
        try {
            var document = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(lecture);
            // PostgreSQL/H2 могут округлять наносекунды после commit. Время не
            // определяет содержание версии: иначе первый read-back создаёт ложный
            // конфликт. Статус, ID версии, весь контент, контроль и вложения остаются.
            document.remove(java.util.List.of("updatedAt", "publishedAt"));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(document)));
        }
        catch (Exception ex) { throw new IllegalStateException("Cannot compute lecture revision", ex); }
    }

    public record SyncRequest(@jakarta.validation.Valid @jakarta.validation.constraints.NotNull LectureImportRequest.Lesson lesson,
            @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9]{64}") String expectedRevision) { }
    public record PublishRequest(@jakarta.validation.constraints.NotBlank String externalId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9]{64}") String expectedRevision) { }
    public record SyncResponse(LectureResponse lecture, String revision, Map<String, String> assetUrls) { }
}
