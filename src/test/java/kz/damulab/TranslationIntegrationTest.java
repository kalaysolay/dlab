package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import kz.damulab.ai.AiProviderCode;
import kz.damulab.ai.AiRuntimeSetting;
import kz.damulab.ai.AiRuntimeSettingRepository;
import kz.damulab.ai.AiUsageType;

/** Проверяет страницу, безопасность, валидацию и stub-поток обеих операций переводчика. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TranslationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRuntimeSettingRepository aiRuntimeSettings;

    @Autowired
    private JdbcTemplate jdbc;

    /** Автотест не должен обращаться к платному внешнему API. */
    @BeforeEach
    void routeAutomatedRequestsToTestProvider() {
        jdbc.update("delete from translation_request_history");
        AiRuntimeSetting setting = aiRuntimeSettings.findById(AiUsageType.TRANSLATIONS).orElseThrow();
        setting.update(AiProviderCode.STUB, "stub", "test");
        aiRuntimeSettings.save(setting);
    }

    @Test
    @WithMockUser(username = "student@damulab.kz", roles = "STUDENT")
    void studentCanOpenMobileTranslatorWithKazakhToRussianDefault() throws Exception {
        mockMvc.perform(get("/student/translator"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/translator"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("KAZAKH_TO_RUSSIAN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("/css/translator.css")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("/js/translator.js")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("translator-economy-mode")
                )));

    }

    @Test
    void translatorRejectsParentRole() throws Exception {
        mockMvc.perform(get("/student/translator")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/access-denied"));
    }

    @Test
    void studentCanTranslateAndExplainThroughStub() throws Exception {
        mockMvc.perform(post("/api/student/translator/translate")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"KAZAKH_TO_RUSSIAN","text":"Сәлем, әлем!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("[Stub: внешний LLM не вызван] Сәлем, әлем!"));

        mockMvc.perform(post("/api/student/translator/explain")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction":"KAZAKH_TO_RUSSIAN",
                                  "sourceText":"Сәлем, әлем!",
                                  "translatedText":"Привет, мир!",
                                  "economyMode":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("тестовый разбор")));

        var rows = jdbc.queryForList("select * from translation_request_history order by id");
        assertThat(rows).hasSize(2);
        Long studentId = jdbc.queryForObject("select id from app_users where email = ?", Long.class,
                "student@damulab.kz");
        for (var row : rows) {
            assertThat(row.get("user_id")).isEqualTo(studentId);
            assertThat(row.get("actor_email")).isEqualTo("student@damulab.kz");
            assertThat(row.get("direction")).isEqualTo("KAZAKH_TO_RUSSIAN");
            assertThat(row.get("source_text")).isEqualTo("Сәлем, әлем!");
            assertThat(row.get("status")).isEqualTo("SUCCESS");
            assertThat(row.get("provider")).isEqualTo("stub");
            assertThat(row.get("model")).isEqualTo("stub");
            assertThat(row.get("created_at")).isNotNull();
            assertThat(row.get("completed_at")).isNotNull();
        }
        assertThat(rows.get(0).get("operation")).isEqualTo("TRANSLATE");
        assertThat(rows.get(0).get("translated_text")).isEqualTo("[Stub: внешний LLM не вызван] Сәлем, әлем!");
        assertThat(rows.get(0).get("explanation_text")).isNull();
        assertThat(rows.get(1).get("operation")).isEqualTo("EXPLAIN");
        assertThat(rows.get(1).get("translated_text")).isEqualTo("Привет, мир!");
        assertThat(rows.get(1).get("explanation_text").toString()).contains("тестовый разбор");
        assertThat(rows.get(1).get("explanation_language")).isEqualTo("Russian");
        assertThat(rows.get(1).get("explanation_mode")).isEqualTo("ECONOMY");
    }

    @Test
    void translationRequiresCsrfAndNonBlankText() throws Exception {
        String body = "{\"direction\":\"RUSSIAN_TO_KAZAKH\",\"text\":\"Привет\"}";
        mockMvc.perform(post("/api/student/translator/translate")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/student/translator/translate")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"RUSSIAN_TO_KAZAKH\",\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_translation_request"));
        assertThat(jdbc.queryForObject("select count(*) from translation_request_history", Long.class)).isZero();
    }

    @Test
    void failedTranslationIsRetainedWithoutChangingApiError() throws Exception {
        // Реальные провайдеры выключены в test: проверяем ошибку без внешнего трафика.
        AiRuntimeSetting setting = aiRuntimeSettings.findById(AiUsageType.TRANSLATIONS).orElseThrow();
        setting.update(AiProviderCode.DEEPSEEK, "test-model", "test");
        aiRuntimeSettings.save(setting);

        mockMvc.perform(post("/api/student/translator/translate")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"RUSSIAN_TO_KAZAKH\",\"text\":\" Привет \"}"))
                .andExpect(status().isServiceUnavailable());

        var row = jdbc.queryForMap("select * from translation_request_history");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_code")).isEqualTo("ai_provider_disabled");
        assertThat(row.get("source_text")).isEqualTo("Привет");
        assertThat(row.get("direction")).isEqualTo("RUSSIAN_TO_KAZAKH");
        assertThat(row.get("translated_text")).isNull();
        assertThat(row.get("completed_at")).isNotNull();
    }

    @Test
    void dashboardPlacesTranslatorLinkImmediatelyAfterLessons() throws Exception {
        MvcResult response = mockMvc.perform(get("/student")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andReturn();
        String html = response.getResponse().getContentAsString();
        int lessons = html.indexOf("href=\"/student/lectures\"");
        int translator = html.indexOf("href=\"/student/translator\"");

        assertThat(lessons).isGreaterThanOrEqualTo(0);
        assertThat(translator).isGreaterThan(lessons);
    }

    @Test
    void explanationLoadsSanitizedMarkdownRenderer() throws Exception {
        mockMvc.perform(get("/student/translator")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/webjars/marked/18.0.7/lib/marked.umd.js"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/webjars/dompurify/3.1.7/dist/purify.min.js"
                )));

        mockMvc.perform(get("/js/translator.js")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DOMPurify.sanitize")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("economyMode: true")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ALLOWED_TAGS")));

        mockMvc.perform(get("/webjars/marked/18.0.7/lib/marked.umd.js")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk());
    }
}
