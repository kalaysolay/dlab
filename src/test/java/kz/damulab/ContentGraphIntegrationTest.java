package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.audit.AdminContentAuditLogRepository;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentGraphIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private AdminContentAuditLogRepository auditLogs;

    @Test
    void adminCanCreateTopicAndSeeItInReferences() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "topic-%s",
                                  "titleRu": "Тестовая тема %s",
                                  "titleKk": "Тест тақырып %s"
                                }
                                """.formatted(subjectId, gradeId, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/topics/")))
                .andExpect(jsonPath("$.titleRu").value("Тестовая тема " + suffix));

        mockMvc.perform(get("/api/content/references")
                        .param("subjectId", subjectId.toString())
                        .param("gradeId", gradeId.toString())
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[*].titleRu", hasItem("Тестовая тема " + suffix)));
    }

    @Test
    void duplicateTopicInSameBranchIsRejected() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {
                  "subjectId": %d,
                  "gradeId": %d,
                  "code": "duplicate-%s",
                  "titleRu": "Дубль %s",
                  "titleKk": "Дубль %s"
                }
                """.formatted(subjectId, gradeId, suffix, suffix, suffix);

        mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("topic_duplicate"));
    }

    @Test
    void topicCanBeRenamed() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long topicId = createTopic(subjectId, gradeId, "rename-" + suffix, "Переименование " + suffix);

        mockMvc.perform(patch("/api/admin/topics/{id}", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "rename-%s",
                                  "titleRu": "Переименовано %s",
                                  "titleKk": "Өзгертілді %s"
                                }
                                """.formatted(subjectId, gradeId, suffix, suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleRu").value("Переименовано " + suffix));
    }

    @Test
    void topicWithChildCannotBeDeleted() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long parentId = createTopic(subjectId, gradeId, "parent-" + suffix, "Родитель " + suffix);

        mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "parentId": %d,
                                  "code": "child-%s",
                                  "titleRu": "Дочерняя %s",
                                  "titleKk": "Бала %s"
                                }
                                """.formatted(subjectId, gradeId, parentId, suffix, suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/topics/{id}", parentId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("topic_has_children"));
    }

    @Test
    void adminCanCreateAtomicSkillAndDuplicateIsRejected() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long topicId = createTopic(subjectId, gradeId, "skill-topic-" + suffix, "Тема навыка " + suffix);

        mockMvc.perform(post("/api/admin/topics/{id}/skills", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "code": "skill-%s",
                                  "titleRu": "Навык %s",
                                  "titleKk": "Дағды %s",
                                  "active": true
                                }
                                """.formatted(topicId, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titleRu").value("Навык " + suffix));

        mockMvc.perform(post("/api/admin/topics/{id}/skills", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "code": "skill-%s",
                                  "titleRu": "Навык %s",
                                  "titleKk": "Дағды %s",
                                  "active": true
                                }
                                """.formatted(topicId, suffix, suffix, suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("skill_duplicate"));
    }

    @Test
    void topicWithAtomicSkillCannotBeDeleted() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long topicId = createTopic(subjectId, gradeId, "topic-with-skill-" + suffix, "Тема с навыком " + suffix);
        createSkill(topicId, "blocking-skill-" + suffix, "Блокирующий навык " + suffix);

        mockMvc.perform(delete("/api/admin/topics/{id}", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("topic_has_skills"));
    }

    @Test
    void atomicSkillCanBeUpdatedAndDeleted() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long topicId = createTopic(subjectId, gradeId, "skill-crud-topic-" + suffix, "CRUD тема " + suffix);
        Long skillId = createSkill(topicId, "skill-crud-" + suffix, "CRUD навык " + suffix);

        mockMvc.perform(patch("/api/admin/skills/{id}", skillId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "code": "skill-crud-%s",
                                  "titleRu": "Обновленный навык %s",
                                  "titleKk": "Жаңартылған дағды %s",
                                  "active": false
                                }
                                """.formatted(topicId, suffix, suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleRu").value("Обновленный навык " + suffix))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/admin/skills/{id}", skillId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void contentChangesWriteAuditLog() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long topicId = createTopic(subjectId, gradeId, "audit-topic-" + suffix, "Audit тема " + suffix);

        org.assertj.core.api.Assertions.assertThat(auditLogs.countByEntityTypeAndEntityId("Topic", topicId))
                .isGreaterThan(0);
    }

    @Test
    void adminTopicsPageIsServerRendered() throws Exception {
        mockMvc.perform(get("/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/topics"))
                .andExpect(content().string(containsString("Справочник тем")))
                .andExpect(content().string(containsString("Открыть дерево")));
    }

    @Test
    void adminTopicTreePageShowsAtomicSkills() throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long topicId = createTopic(subjectId, gradeId, "tree-skill-topic-" + suffix, "Tree тема " + suffix);
        createSkill(topicId, "tree-skill-" + suffix, "Tree навык " + suffix);

        mockMvc.perform(get("/admin/topics/tree")
                        .param("subjectId", subjectId.toString())
                        .param("gradeId", gradeId.toString())
                        .param("topicId", topicId.toString())
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/topic-tree"))
                .andExpect(content().string(containsString("Атомарные навыки")))
                .andExpect(content().string(containsString("Tree навык " + suffix)));
    }

    @Test
    void studentCannotOpenAdminTopicsApi() throws Exception {
        mockMvc.perform(get("/api/admin/topics")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    private Long createTopic(Long subjectId, Long gradeId, String code, String titleRu) throws Exception {
        String response = mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "%s",
                                  "titleRu": "%s",
                                  "titleKk": "%s"
                                }
                                """.formatted(subjectId, gradeId, code, titleRu, titleRu)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.valueOf(response.substring(start, end));
    }

    private Long createSkill(Long topicId, String code, String titleRu) throws Exception {
        String response = mockMvc.perform(post("/api/admin/topics/{id}/skills", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "code": "%s",
                                  "titleRu": "%s",
                                  "titleKk": "%s",
                                  "active": true
                                }
                                """.formatted(topicId, code, titleRu, titleRu)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.valueOf(response.substring(start, end));
    }

    private Long mathSubjectId() {
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private Long grade4Id() {
        return grades.findAllByOrderByGradeNoAsc().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.getGradeNo()))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
