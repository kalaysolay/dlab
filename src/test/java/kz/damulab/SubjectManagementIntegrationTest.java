package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicRepository;

/** Проверяет административный CRUD предметов вместе с PNG и ограничением удаления. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubjectManagementIntegrationTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private TopicRepository topics;

    @Autowired
    private GradeRepository grades;

    /** Администратор может создать двуязычный предмет и затем получить его иконку. */
    @Test
    void adminCreatesSubjectWithPngIcon() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MockMultipartFile icon = new MockMultipartFile("icon", "science.png", "image/png", PNG);

        mockMvc.perform(multipart("/admin/subjects")
                        .file(icon)
                        .param("titleRu", "Естествознание " + suffix)
                        .param("titleKk", "Жаратылыстану " + suffix)
                        .param("descriptionRu", "Описание предмета")
                        .param("descriptionKk", "Пәннің сипаттамасы")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/subjects"));

        Subject created = subjects.findAll().stream()
                .filter(subject -> subject.getTitleRu().equals("Естествознание " + suffix))
                .findFirst()
                .orElseThrow();
        assertThat(created.getDescriptionKk()).isEqualTo("Пәннің сипаттамасы");
        assertThat(created.getIconStorageKey()).endsWith(".png");

        mockMvc.perform(get("/admin/subjects")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/subjects"))
                .andExpect(content().string(containsString("Естествознание " + suffix)));

        mockMvc.perform(get("/files/subject-icons/{key}", created.getIconStorageKey())
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    /** Файл с неверной сигнатурой не сохраняется, даже если назван PNG. */
    @Test
    void fakePngIsRejected() throws Exception {
        MockMultipartFile icon = new MockMultipartFile("icon", "fake.png", "image/png", "not png".getBytes());

        mockMvc.perform(multipart("/admin/subjects")
                        .file(icon)
                        .param("titleRu", "Неверная иконка")
                        .param("titleKk", "Қате белгіше")
                        .param("descriptionRu", "Описание")
                        .param("descriptionKk", "Сипаттама")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/subject-form"))
                .andExpect(content().string(containsString("Иконка должна быть файлом PNG")));
    }

    /** Администратор может изменить обе локализации существующего предмета. */
    @Test
    void adminUpdatesSubjectTexts() throws Exception {
        Subject subject = saveSubject("update");

        mockMvc.perform(get("/admin/subjects/{id}/edit", subject.getId())
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/subject-form"));

        mockMvc.perform(multipart("/admin/subjects/{id}", subject.getId())
                        .param("titleRu", "Обновлённый предмет")
                        .param("titleKk", "Жаңартылған пән")
                        .param("descriptionRu", "Новое описание")
                        .param("descriptionKk", "Жаңа сипаттама")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/subjects"));

        Subject updated = subjects.findById(subject.getId()).orElseThrow();
        assertThat(updated.getTitleRu()).isEqualTo("Обновлённый предмет");
        assertThat(updated.getDescriptionKk()).isEqualTo("Жаңа сипаттама");
    }

    /** Предмет без тем удаляется обычной CRUD-операцией. */
    @Test
    void subjectWithoutTopicsCanBeDeleted() throws Exception {
        Subject subject = saveSubject("empty");

        mockMvc.perform(post("/admin/subjects/{id}/delete", subject.getId())
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/subjects"));

        assertThat(subjects.existsById(subject.getId())).isFalse();
    }

    /** Сервер запрещает удаление предмета при наличии хотя бы одной темы. */
    @Test
    void subjectWithTopicCannotBeDeleted() throws Exception {
        Subject subject = saveSubject("used");
        Topic topic = new Topic(
                subject,
                grades.findAll().getFirst(),
                null,
                "topic-" + UUID.randomUUID(),
                "Тема",
                "Тақырып"
        );
        topics.saveAndFlush(topic);

        mockMvc.perform(post("/admin/subjects/{id}/delete", subject.getId())
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Нельзя удалить предмет, у которого есть темы"));

        assertThat(subjects.existsById(subject.getId())).isTrue();
    }

    /** Ученик не получает доступ к административному разделу предметов. */
    @Test
    void studentCannotOpenSubjectAdmin() throws Exception {
        mockMvc.perform(get("/admin/subjects")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/access-denied"));
    }

    private Subject saveSubject(String marker) {
        String suffix = marker + "-" + UUID.randomUUID();
        return subjects.saveAndFlush(new Subject(
                "subject-" + UUID.randomUUID(),
                "Предмет " + suffix,
                "Пән " + suffix,
                "Описание " + suffix,
                "Сипаттама " + suffix
        ));
    }
}
