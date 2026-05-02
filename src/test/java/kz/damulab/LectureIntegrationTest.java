package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import kz.damulab.audit.AdminContentAuditLogRepository;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LectureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private AdminContentAuditLogRepository auditLogs;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanPublishLectureWithAutoCheckpointAndAttachment() throws Exception {
        Long topicId = createTopic("lecture-auto-topic-");
        createPublishedQuestion(topicId);

        String response = mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectureBody(topicId, "AUTO", 1, true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/lectures/")))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.attachmentCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long lectureId = idFrom(response);

        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.checkpointCount").value(1))
                .andExpect(jsonPath("$.attachments[*].title", hasItem("percent_examples.pdf")));

        org.assertj.core.api.Assertions.assertThat(auditLogs.countByEntityTypeAndEntityId("Lecture", lectureId))
                .isGreaterThan(0);
    }

    @Test
    void lectureWithoutTopicCannotBePublished() throws Exception {
        Long lectureId = idFrom(mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectureBody(null, "NONE", 0, false)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("lecture_topic_required"));
    }

    @Test
    void ruAndKkContentAreStoredSeparatelyAndRichHtmlIsSanitized() throws Exception {
        Long topicId = createTopic("lecture-sanitized-topic-");

        String response = mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "Безопасный HTML",
                                  "titleKk": "Қауіпсіз HTML",
                                  "contentRu": "Формула <script>alert(1)</script>\\nP = (a / b) * 100%%",
                                  "contentKk": "Формула мәтіні",
                                  "source": "Ручной ввод",
                                  "controlMode": "NONE"
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentRu").value(containsString("Формула")))
                .andExpect(jsonPath("$.contentRu").value(org.hamcrest.Matchers.not(containsString("<script>"))))
                .andExpect(jsonPath("$.contentKk").value(containsString("Формула мәтіні")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long lectureId = idFrom(response);

        mockMvc.perform(patch("/api/admin/lectures/{id}", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectureBody(topicId, "NONE", 0, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1));
    }

    @Test
    void lecturePagesAreServerRenderedAndStudentViewShowsAttachment() throws Exception {
        Long topicId = createTopic("lecture-page-topic-");
        Long lectureId = idFrom(mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectureBody(topicId, "NONE", 0, true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/lectures"))
                .andExpect(content().string(containsString("База лекций")));

        mockMvc.perform(get("/admin/lectures/new")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/lecture-form"))
                .andExpect(content().string(containsString("Новая лекция")));

        mockMvc.perform(get("/student/lectures/{id}", lectureId)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/lecture"))
                .andExpect(content().string(containsString("percent_examples.pdf")));

        mockMvc.perform(patch("/api/admin/lectures/{id}", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectureBody(topicId, "NONE", 0, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.versionNo").value(1));

        mockMvc.perform(get("/student/lectures/{id}", lectureId)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/lecture"))
                .andExpect(content().string(containsString("percent_examples.pdf")));
    }

    @Test
    void adminPageMultipartUploadStoresAttachmentAndServesBinary() throws Exception {
        Long topicId = createTopic("lecture-upload-topic-");
        String marker = UUID.randomUUID().toString().substring(0, 8);
        MockMultipartFile file = new MockMultipartFile(
                "attachmentFiles",
                "upload-" + marker + ".pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "binary-pdf-content".getBytes()
        );

        mockMvc.perform(multipart("/admin/lectures")
                        .file(file)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("topicId", String.valueOf(topicId))
                        .param("titleRu", "Upload " + marker)
                        .param("titleKk", "Upload " + marker)
                        .param("contentRu", "Контент для проверки upload")
                        .param("contentKk", "Upload тест")
                        .param("source", "upload-source-" + marker)
                        .param("controlMode", "NONE")
                        .param("autoCheckpointCount", "0")
                        .param("attachments[0].title", "upload-" + marker + ".pdf")
                        .param("attachments[0].mediaType", "pdf")
                        .param("attachments[0].url", "")
                        .param("action", "draft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/lectures"));

        String listed = mockMvc.perform(get("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("query", "upload-source-" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attachmentCount").value(1))
                .andExpect(jsonPath("$[0].attachments[0].uploaded").value(true))
                .andExpect(jsonPath("$[0].attachments[0].storageKey").isNotEmpty())
                .andExpect(jsonPath("$[0].attachments[0].url").value(containsString("/files/lecture-attachments/")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode attachment = objectMapper.readTree(listed).get(0).path("attachments").get(0);
        String fileUrl = attachment.path("url").asText();
        mockMvc.perform(get(fileUrl)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes("binary-pdf-content".getBytes()));
    }

    @Test
    void adminPageMultipartUpdateCanReplaceAttachmentAndDeletePreviousStorageFile() throws Exception {
        Long topicId = createTopic("lecture-replace-topic-");
        String marker = UUID.randomUUID().toString().substring(0, 8);
        MockMultipartFile firstFile = new MockMultipartFile(
                "attachmentFiles",
                "replace-" + marker + "-v1.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "first-version-content".getBytes()
        );

        mockMvc.perform(multipart("/admin/lectures")
                        .file(firstFile)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("topicId", String.valueOf(topicId))
                        .param("titleRu", "Replace " + marker)
                        .param("titleKk", "Replace " + marker)
                        .param("contentRu", "RU replace " + marker)
                        .param("contentKk", "KK replace " + marker)
                        .param("source", "replace-source-" + marker)
                        .param("controlMode", "NONE")
                        .param("autoCheckpointCount", "0")
                        .param("attachments[0].title", "replace-" + marker + "-v1.pdf")
                        .param("attachments[0].mediaType", "pdf")
                        .param("attachments[0].url", "")
                        .param("action", "draft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/lectures"));

        JsonNode firstList = objectMapper.readTree(mockMvc.perform(get("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("query", "replace-source-" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attachmentCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode firstLecture = firstList.get(0);
        Long lectureId = firstLecture.path("id").asLong();
        JsonNode firstAttachment = firstLecture.path("attachments").get(0);
        String oldStorageKey = firstAttachment.path("storageKey").asText();
        String oldUrl = firstAttachment.path("url").asText();
        Path oldFilePath = Path.of("uploads", "lecture-attachments", oldStorageKey);
        org.assertj.core.api.Assertions.assertThat(Files.exists(oldFilePath)).isTrue();

        MockMultipartFile replacementFile = new MockMultipartFile(
                "attachmentFiles[0]",
                "replace-" + marker + "-v2.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "second-version-content".getBytes()
        );

        mockMvc.perform(multipart("/admin/lectures/{id}", lectureId)
                        .file(replacementFile)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("topicId", String.valueOf(topicId))
                        .param("titleRu", "Replace " + marker)
                        .param("titleKk", "Replace " + marker)
                        .param("contentRu", "RU replace update " + marker)
                        .param("contentKk", "KK replace update " + marker)
                        .param("source", "replace-source-updated-" + marker)
                        .param("controlMode", "NONE")
                        .param("autoCheckpointCount", "0")
                        .param("attachments[0].title", "replace-" + marker + "-v2.pdf")
                        .param("attachments[0].mediaType", "pdf")
                        .param("attachments[0].url", "")
                        .param("action", "draft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/lectures"));

        JsonNode secondList = objectMapper.readTree(mockMvc.perform(get("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("query", "replace-source-updated-" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attachmentCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode secondAttachment = secondList.get(0).path("attachments").get(0);
        String newStorageKey = secondAttachment.path("storageKey").asText();
        String newUrl = secondAttachment.path("url").asText();
        Path newFilePath = Path.of("uploads", "lecture-attachments", newStorageKey);

        org.assertj.core.api.Assertions.assertThat(newStorageKey).isNotEqualTo(oldStorageKey);
        org.assertj.core.api.Assertions.assertThat(newUrl).contains(newStorageKey);
        org.assertj.core.api.Assertions.assertThat(Files.exists(newFilePath)).isTrue();
        org.assertj.core.api.Assertions.assertThat(Files.exists(oldFilePath)).isFalse();

        mockMvc.perform(get(oldUrl)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(newUrl)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes("second-version-content".getBytes()));
    }

    @Test
    void manualCheckpointIdsArePersistedWhenLectureIsSubmittedFromPageForm() throws Exception {
        Long topicId = createTopic("lecture-manual-submit-topic-");
        Long checkpointVersionId = createPublishedQuestionVersion(topicId);
        String marker = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(multipart("/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("topicId", String.valueOf(topicId))
                        .param("titleRu", "Manual submit " + marker)
                        .param("titleKk", "Manual submit " + marker)
                        .param("contentRu", "Manual checkpoint RU " + marker)
                        .param("contentKk", "Manual checkpoint KK " + marker)
                        .param("source", "manual-submit-" + marker)
                        .param("controlMode", "MANUAL")
                        .param("autoCheckpointCount", "0")
                        .param("checkpointQuestionVersionIds", String.valueOf(checkpointVersionId))
                        .param("action", "publish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/lectures"));

        mockMvc.perform(get("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("query", "manual-submit-" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("published"))
                .andExpect(jsonPath("$[0].controlMode").value("manual"))
                .andExpect(jsonPath("$[0].checkpointCount").value(1))
                .andExpect(jsonPath("$[0].checkpoints[0].questionVersionId").value(checkpointVersionId));
    }

    @Test
    void sanitizerKeepsSafeRichContentAndHardensLinksAndFormula() throws Exception {
        Long topicId = createTopic("lecture-sanitizer-rich-topic-");

        mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "Sanitizer check",
                                  "titleKk": "Sanitizer check",
                                  "contentRu": "<p>Word-like block</p><table><tr><td>15%%</td></tr></table><a href='https://example.org' target='_blank'>external</a><a href='/materials/handbook.pdf'>internal</a><span class='ql-formula' data-value='\\\\\\\\frac{a}{b}'></span><span class='ql-formula' data-value='\\\\\\\\href{javascript:alert(1)}{x}'></span><script>alert(1)</script>",
                                  "contentKk": "safe content",
                                  "controlMode": "NONE"
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentRu").value(org.hamcrest.Matchers.not(containsString("<script>"))))
                .andExpect(jsonPath("$.contentRu").value(containsString("<table>")))
                .andExpect(jsonPath("$.contentRu").value(containsString("href=\"https://example.org\"")))
                .andExpect(jsonPath("$.contentRu").value(containsString("target=\"_blank\"")))
                .andExpect(jsonPath("$.contentRu").value(containsString("rel=\"noopener noreferrer nofollow\"")))
                .andExpect(jsonPath("$.contentRu").value(containsString("href=\"/materials/handbook.pdf\"")))
                .andExpect(jsonPath("$.contentRu").value(containsString("class=\"ql-formula\"")))
                .andExpect(jsonPath("$.contentRu").value(org.hamcrest.Matchers.not(containsString("\\\\href{javascript"))));
    }

    @Test
    void lectureAttachmentsValidationRejectsInvalidTypeMismatchAndLimit() throws Exception {
        Long topicId = createTopic("lecture-attachments-topic-");

        mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "Attachment invalid type",
                                  "contentRu": "content",
                                  "controlMode": "NONE",
                                  "attachments": [
                                    {"title": "malware.bin", "url": "https://example.org/malware.bin", "mediaType": "exe"}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("lecture_attachment_media_type_invalid"));

        mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "Attachment mismatch",
                                  "contentRu": "content",
                                  "controlMode": "NONE",
                                  "attachments": [
                                    {"title": "diagram.png", "url": "https://example.org/diagram.png", "mediaType": "pdf"}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("lecture_attachment_type_url_mismatch"));

        mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "Attachment limit",
                                  "contentRu": "content",
                                  "controlMode": "NONE",
                                  "attachments": [
                                    {"title":"a1.pdf","url":"https://example.org/a1.pdf","mediaType":"pdf"},
                                    {"title":"a2.pdf","url":"https://example.org/a2.pdf","mediaType":"pdf"},
                                    {"title":"a3.pdf","url":"https://example.org/a3.pdf","mediaType":"pdf"},
                                    {"title":"a4.pdf","url":"https://example.org/a4.pdf","mediaType":"pdf"},
                                    {"title":"a5.pdf","url":"https://example.org/a5.pdf","mediaType":"pdf"},
                                    {"title":"a6.pdf","url":"https://example.org/a6.pdf","mediaType":"pdf"},
                                    {"title":"a7.pdf","url":"https://example.org/a7.pdf","mediaType":"pdf"},
                                    {"title":"a8.pdf","url":"https://example.org/a8.pdf","mediaType":"pdf"},
                                    {"title":"a9.pdf","url":"https://example.org/a9.pdf","mediaType":"pdf"}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("lecture_attachment_limit_exceeded"));
    }

    @Test
    void topicWithLectureCannotBeDeleted() throws Exception {
        Long topicId = createTopic("lecture-blocks-topic-");

        mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectureBody(topicId, "NONE", 0, false)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/topics/{id}", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("topic_has_lectures"));
    }

    @Test
    void studentCannotOpenAdminLecturesApi() throws Exception {
        mockMvc.perform(get("/api/admin/lectures")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    private void createPublishedQuestion(Long topicId) throws Exception {
        Long questionId = idFrom(mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "SCQ",
                                  "difficulty": 2,
                                  "bodyRu": "Найдите 25%% от 80",
                                  "bodyKk": "80 санының 25 пайызын табыңыз",
                                  "source": "Ручной ввод",
                                  "options": [
                                    {"label":"A","textRu":"15","textKk":"15","correct":false},
                                    {"label":"B","textRu":"20","textKk":"20","correct":true},
                                    {"label":"C","textRu":"25","textKk":"25","correct":false}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private Long createPublishedQuestionVersion(Long topicId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "SCQ",
                                  "difficulty": 2,
                                  "bodyRu": "РЈРєР°Р¶РёС‚Рµ 30%% РѕС‚ 100",
                                  "bodyKk": "100 СЃР°РЅС‹РЅС‹ТЈ 30 РїР°Р№С‹Р·С‹РЅ РєУ©СЂСЃРµС‚С–ТЈС–Р·",
                                  "source": "manual-checkpoint-submit",
                                  "options": [
                                    {"label":"A","textRu":"20","textKk":"20","correct":false},
                                    {"label":"B","textRu":"30","textKk":"30","correct":true},
                                    {"label":"C","textRu":"40","textKk":"40","correct":false}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(response);
        Long questionId = created.path("id").asLong();
        Long versionId = created.path("currentVersionId").asLong();

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return versionId;
    }

    private String lectureBody(Long topicId, String controlMode, int autoCount, boolean attachment) {
        String topicLine = topicId == null ? "" : "\"topicId\": %d,".formatted(topicId);
        String attachmentLine = attachment
                ? """
                  "attachments": [
                    {"title": "percent_examples.pdf", "url": "/files/percent_examples.pdf", "mediaType": "pdf"}
                  ],
                """
                : "";
        return """
                {
                  %s
                  "titleRu": "Проценты в повседневной жизни",
                  "titleKk": "Күнделікті өмірдегі пайыздар",
                  "contentRu": "Процент - это сотая часть числа.\\nФормула: P = (a / b) * 100%%",
                  "contentKk": "Пайыз - санның жүзден бір бөлігі.",
                  "source": "Ручной ввод",
                  %s
                  "controlMode": "%s",
                  "autoCheckpointCount": %d
                }
                """.formatted(topicLine, attachmentLine, controlMode, autoCount);
    }

    private Long createTopic(String prefix) throws Exception {
        Long subjectId = subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst()
                .orElseThrow()
                .getId();
        Long gradeId = grades.findAllByOrderByGradeNoAsc().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.getGradeNo()))
                .findFirst()
                .orElseThrow()
                .getId();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String response = mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "%s%s",
                                  "titleRu": "Lecture topic %s",
                                  "titleKk": "Lecture topic %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return idFrom(response);
    }

    private Long idFrom(String response) {
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.valueOf(response.substring(start, end));
    }
}
