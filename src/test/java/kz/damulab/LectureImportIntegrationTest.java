package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.content.Grade;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.lectures.LectureRepository;

/**
 * Проверяет агентский импорт как единый пользовательский сценарий: разрешение
 * учебного графа, две локализации, Quill HTML, KaTeX, inline image assets,
 * вложения, AUTO checkpoints, безопасность и идемпотентность externalId.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "damulab.lectures.images.storage-dir=build/test-lecture-import-images")
class LectureImportIntegrationTest {

    private static final Path IMAGE_STORAGE = Path.of("build/test-lecture-import-images");
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private LectureRepository lectures;

    @Test
    void checkedInExampleMatchesTheLiveImportContract() throws Exception {
        String example = Files.readString(Path.of("docs/lesson-import-example.json"));

        mockMvc.perform(post("/api/admin/lectures/import")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(example))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(1))
                .andExpect(jsonPath("$.lessons[0].externalId").value("math-4-finding-percent-001"))
                .andExpect(jsonPath("$.lessons[0].status").value("draft"));
    }

    @Test
    void adminImportsBilingualHtmlFormulaImageAttachmentAndAutoCheckpoint() throws Exception {
        TopicPathFixture fixture = createTopicPath("lecture-import-full-");
        createPublishedQuestion(fixture);
        String externalId = "math-4-percent-" + suffix();

        String response = mockMvc.perform(post("/api/admin/lectures/import")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importPayload(fixture, externalId, "AUTO", "image/png", safeRuHtml(), safeKkHtml(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(1))
                .andExpect(jsonPath("$.lessons[0].externalId").value(externalId))
                .andExpect(jsonPath("$.lessons[0].status").value("draft"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long lectureId = objectMapper.readTree(response).path("lessons").get(0).path("lectureId").asLong();
        String lectureJson = mockMvc.perform(get("/api/admin/lectures/{id}", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(fixture.childTopicId()))
                .andExpect(jsonPath("$.titleRu").value("Проценты и дроби"))
                .andExpect(jsonPath("$.titleKk").value("Пайыздар мен бөлшектер"))
                .andExpect(jsonPath("$.contentRu").value(containsString("<h2>Rich HTML</h2>")))
                .andExpect(jsonPath("$.contentRu").value(containsString("<strong>25%</strong>")))
                .andExpect(jsonPath("$.contentRu").value(containsString("class=\"ql-formula\"")))
                .andExpect(jsonPath("$.contentRu").value(containsString("data-value=\"\\frac{25}{100}\"")))
                .andExpect(jsonPath("$.contentRu").value(containsString("/files/lecture-images/")))
                .andExpect(jsonPath("$.contentRu").value(containsString("alt=\"Процентная клетка\"")))
                .andExpect(jsonPath("$.contentRu").value(not(containsString("asset://"))))
                .andExpect(jsonPath("$.contentRu").value(not(containsString("<script>"))))
                .andExpect(jsonPath("$.contentKk").value(containsString("пайыздың жүзден бір бөлік екенін көрсетеді")))
                .andExpect(jsonPath("$.controlMode").value("auto"))
                .andExpect(jsonPath("$.autoCheckpointCount").value(3))
                .andExpect(jsonPath("$.checkpointCount").value(1))
                .andExpect(jsonPath("$.attachmentCount").value(1))
                .andExpect(jsonPath("$.attachments[0].title").value("Қосымша материал / Дополнительный материал"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode lecture = objectMapper.readTree(lectureJson);
        String imageUrl = imageUrlFrom(lecture.path("contentRu").asText());
        mockMvc.perform(get(imageUrl).with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        assertThat(lectures.existsByExternalIdIgnoreCase(externalId)).isTrue();

        // Импорт не публикует автоматически, но созданный draft полностью готов к ручной публикации.
        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"));
    }

    @Test
    void repeatedExternalIdIsRejectedWithoutCreatingDuplicate() throws Exception {
        TopicPathFixture fixture = createTopicPath("lecture-import-duplicate-");
        String externalId = "duplicate-" + suffix();
        String payload = importPayload(fixture, externalId, "AUTO", "image/png", safeRuHtml(), safeKkHtml(), true);

        mockMvc.perform(post("/api/admin/lectures/import")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        long before = lectures.count();
        mockMvc.perform(post("/api/admin/lectures/import")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("lecture_import_external_id_duplicate"));
        assertThat(lectures.count()).isEqualTo(before);
    }

    @Test
    void importRejectsUnknownAndInconsistentGraphIds() throws Exception {
        TopicPathFixture fixture = createTopicPath("lecture-import-graph-ids-");
        String payload = importPayload(
                fixture, "graph-ids-" + suffix(), "AUTO", "image/png",
                safeRuHtml(), safeKkHtml(), true
        );

        // Каждый внешний ключ проверяется отдельно: понятная ошибка полезнее общего
        // constraint violation уже во время сохранения лекции.
        assertImportError(
                withMetadataId(payload, "subjectId", Long.MAX_VALUE),
                "lecture_import_subject_not_found"
        );
        assertImportError(
                withMetadataId(payload, "gradeId", Long.MAX_VALUE),
                "lecture_import_grade_not_found"
        );
        assertImportError(
                withMetadataId(payload, "topicId", Long.MAX_VALUE),
                "lecture_import_topic_not_found"
        );

        // Существующий ID тоже недостаточен: topic должен относиться именно к
        // заявленным subjectId и gradeId.
        long anotherSubjectId = subjects.findByCodeIgnoreCase("kazakh_language").orElseThrow().getId();
        assertImportError(
                withMetadataId(payload, "subjectId", anotherSubjectId),
                "lecture_import_topic_scope_mismatch"
        );
        long anotherGradeId = grades.findByGradeNo(3).orElseThrow().getId();
        assertImportError(
                withMetadataId(payload, "gradeId", anotherGradeId),
                "lecture_import_topic_scope_mismatch"
        );
    }

    @Test
    void importRejectsManualControlExternalImagesAndUnsafeFormula() throws Exception {
        TopicPathFixture fixture = createTopicPath("lecture-import-invalid-");

        assertImportError(
                importPayload(fixture, "manual-" + suffix(), "MANUAL", "image/png", safeRuHtml(), safeKkHtml(), true),
                "lecture_import_control_mode_invalid"
        );
        assertImportError(
                importPayload(
                        fixture,
                        "external-image-" + suffix(),
                        "AUTO",
                        "image/png",
                        "<p>Text</p><img src=\"https://tracker.example/pixel.png\" alt=\"Tracker\">",
                        safeKkHtml(),
                        true
                ),
                "lecture_import_image_source_invalid"
        );
        assertImportError(
                importPayload(
                        fixture,
                        "unsafe-formula-" + suffix(),
                        "AUTO",
                        "image/png",
                        "<p>Formula <span class=\"ql-formula\" data-value=\"\\href{https://evil.example}{x}\"></span></p><img src=\"asset://percent-cell\" alt=\"Cell\">",
                        safeKkHtml(),
                        true
                ),
                "lecture_import_formula_invalid"
        );
    }

    @Test
    void assetMimeMismatchRollsBackDatabaseAndDeletesStoredFile() throws Exception {
        TopicPathFixture fixture = createTopicPath("lecture-import-rollback-");
        long lecturesBefore = lectures.count();
        long filesBefore = storedFileCount();

        assertImportError(
                importPayload(
                        fixture,
                        "mime-mismatch-" + suffix(),
                        "AUTO",
                        "image/jpeg",
                        safeRuHtml(),
                        safeKkHtml(),
                        true
                ),
                "lecture_import_asset_mime_mismatch"
        );

        assertThat(lectures.count()).isEqualTo(lecturesBefore);
        assertThat(storedFileCount()).isEqualTo(filesBefore);
    }

    @Test
    void studentCannotUseLectureImportEndpoint() throws Exception {
        TopicPathFixture fixture = createTopicPath("lecture-import-security-");

        mockMvc.perform(post("/api/admin/lectures/import")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importPayload(
                                fixture, "forbidden-" + suffix(), "AUTO", "image/png",
                                safeRuHtml(), safeKkHtml(), true)))
                .andExpect(status().isForbidden());
    }

    private void assertImportError(String payload, String error) throws Exception {
        mockMvc.perform(post("/api/admin/lectures/import")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(error));
    }

    private String importPayload(
            TopicPathFixture fixture,
            String externalId,
            String controlMode,
            String mimeType,
            String ruHtml,
            String kkHtml,
            boolean attachment
    ) throws Exception {
        Map<String, Object> lesson = Map.of(
                "externalId", externalId,
                "metadata", Map.of(
                        "subjectId", fixture.subject().getId(),
                        "gradeId", fixture.grade().getId(),
                        "topicId", fixture.childTopicId(),
                        "title", localized("Проценты и дроби", "Пайыздар мен бөлшектер"),
                        "primaryLanguage", "kk",
                        "source", "Интеграционный тест импорта"
                ),
                "assets", List.of(Map.of(
                        "id", "percent-cell",
                        "kind", "image",
                        "mimeType", mimeType,
                        "source", Map.of(
                                "type", "base64",
                                "data", Base64.getEncoder().encodeToString(PNG)
                        ),
                        "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PNG)),
                        "credit", "Damulab test fixture"
                )),
                "content", Map.of("ruHtml", ruHtml, "kkHtml", kkHtml),
                "attachments", attachment
                        ? List.of(Map.of(
                                "title", "Қосымша материал / Дополнительный материал",
                                "url", "https://example.org/percent-handbook.pdf",
                                "mediaType", "pdf"
                        ))
                        : List.of(),
                "completionControl", Map.of("mode", controlMode, "questionCount", 3)
        );
        return objectMapper.writeValueAsString(Map.of(
                "schemaVersion", "1.0",
                "kind", "damulab.lesson-import",
                "lessons", List.of(lesson)
        ));
    }

    private Map<String, String> localized(String ru, String kk) {
        return Map.of("ru", ru, "kk", kk);
    }

    /** Возвращает независимую копию JSON с одним изменённым ID учебного графа. */
    private String withMetadataId(String payload, String field, long value) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(payload);
        ObjectNode metadata = (ObjectNode) root.path("lessons").get(0).path("metadata");
        metadata.put(field, value);
        return objectMapper.writeValueAsString(root);
    }

    private String safeRuHtml() {
        return """
                <h2>Rich HTML</h2>
                <p><strong>25%</strong> — это четверть. <span class="ql-formula" data-value="\\frac{25}{100}"></span></p>
                <img src="asset://percent-cell" alt="Процентная клетка" title="Одна сотая">
                <a href="https://example.org" target="_blank">Источник</a>
                <script>alert('removed')</script>
                """;
    }

    private String safeKkHtml() {
        return """
                <h2>Қазақша мазмұн</h2>
                <p>Бұл сурет пайыздың жүзден бір бөлік екенін көрсетеді. <span class="ql-formula" data-value="\\frac{25}{100}"></span></p>
                <img src="asset://percent-cell" alt="Пайыз ұяшығы" title="Жүзден бір бөлік">
                """;
    }

    private String imageUrlFrom(String html) {
        int start = html.indexOf("/files/lecture-images/");
        int end = html.indexOf('"', start);
        return html.substring(start, end);
    }

    private long storedFileCount() throws Exception {
        if (!Files.isDirectory(IMAGE_STORAGE)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> files = Files.list(IMAGE_STORAGE)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    /** Создаёт активную тему и возвращает реальные ID всех трёх узлов учебного графа. */
    private TopicPathFixture createTopicPath(String prefix) throws Exception {
        Subject subject = subjects.findByCodeIgnoreCase("math").orElseThrow();
        Grade grade = grades.findByGradeNo(4).orElseThrow();
        String marker = suffix();
        String rootCode = prefix + "root-" + marker;
        String childCode = prefix + "child-" + marker;
        long rootId = createTopic(subject.getId(), grade.getId(), null, rootCode, "Корень " + marker, "Түбір " + marker);
        String childRu = "Импортируемая тема " + marker;
        String childKk = "Импортталатын тақырып " + marker;
        long childId = createTopic(subject.getId(), grade.getId(), rootId, childCode, childRu, childKk);
        return new TopicPathFixture(subject, grade, childId);
    }

    private long createTopic(
            long subjectId,
            long gradeId,
            Long parentId,
            String code,
            String titleRu,
            String titleKk
    ) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("subjectId", subjectId);
        body.put("gradeId", gradeId);
        if (parentId != null) body.put("parentId", parentId);
        body.put("code", code);
        body.put("titleRu", titleRu);
        body.put("titleKk", titleKk);
        String response = mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private void createPublishedQuestion(TopicPathFixture fixture) throws Exception {
        String response = mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", fixture.subject().getId(),
                                "topicIds", List.of(fixture.childTopicId()),
                                "gradeIds", List.of(fixture.grade().getId()),
                                "type", "SCQ",
                                "difficulty", 2,
                                "bodyRu", "Найдите 25% от 80",
                                "bodyKk", "80 санының 25 пайызын табыңыз",
                                "source", "Lecture import test",
                                "options", List.of(
                                        option("A", "10", false),
                                        option("B", "20", true),
                                        option("C", "30", false)
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long questionId = objectMapper.readTree(response).path("id").asLong();

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private Map<String, Object> option(String label, String text, boolean correct) {
        return Map.of("label", label, "textRu", text, "textKk", text, "correct", correct);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record TopicPathFixture(
            Subject subject,
            Grade grade,
            long childTopicId
    ) {
    }
}
