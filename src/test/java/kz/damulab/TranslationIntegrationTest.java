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

    /** Автотест не должен обращаться к платному внешнему API. */
    @BeforeEach
    void routeAutomatedRequestsToTestProvider() {
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"translator-economy-mode\" type=\"checkbox\" checked"
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ALLOWED_TAGS")));

        mockMvc.perform(get("/webjars/marked/18.0.7/lib/marked.umd.js")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk());
    }
}
