package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import kz.damulab.ai.AiProviderCode;
import kz.damulab.ai.AiRuntimeSelection;
import kz.damulab.ai.AiRuntimeSetting;
import kz.damulab.ai.AiRuntimeSettingRepository;
import kz.damulab.ai.AiRuntimeSettingsService;
import kz.damulab.ai.AiUsageType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Проверяет страницу, права и немедленное применение раздельных AI-маршрутов из БД. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiRuntimeSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRuntimeSettingRepository repository;

    @Autowired
    private AiRuntimeSettingsService settings;

    @BeforeEach
    void restoreStubRoutes() {
        update(AiUsageType.QUESTIONS, AiProviderCode.STUB, "stub");
        update(AiUsageType.LECTURES, AiProviderCode.STUB, "stub");
    }

    @Test
    void adminCanOpenAiSettingsPage() throws Exception {
        mockMvc.perform(get("/admin/settings/ai")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/ai-settings"))
                .andExpect(model().attributeExists("aiSettingsForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Настройки AI")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("deepseek-v4-pro")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gpt-5.6")));
    }

    @Test
    void studentCannotOpenOrChangeAiSettings() throws Exception {
        mockMvc.perform(get("/admin/settings/ai")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/access-denied"));

        mockMvc.perform(post("/admin/settings/ai")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .param("questionsProvider", "DEEPSEEK")
                        .param("questionsModel", "deepseek-v4-pro")
                        .param("lecturesProvider", "OPENAI")
                        .param("lecturesModel", "gpt-5.6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/access-denied"));
    }

    @Test
    void savedRoutesApplyIndependentlyWithoutRestart() throws Exception {
        mockMvc.perform(post("/admin/settings/ai")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("questionsProvider", "DEEPSEEK")
                        .param("questionsModel", "deepseek-v4-pro")
                        .param("lecturesProvider", "OPENAI")
                        .param("lecturesModel", "gpt-5.6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings/ai"));

        AiRuntimeSelection questions = settings.resolve(AiUsageType.QUESTIONS);
        AiRuntimeSelection lectures = settings.resolve(AiUsageType.LECTURES);
        assertThat(questions.provider()).isEqualTo(AiProviderCode.DEEPSEEK);
        assertThat(questions.model()).isEqualTo("deepseek-v4-pro");
        assertThat(lectures.provider()).isEqualTo(AiProviderCode.OPENAI);
        assertThat(lectures.model()).isEqualTo("gpt-5.6");
        assertThat(repository.findById(AiUsageType.QUESTIONS).orElseThrow().getUpdatedBy())
                .isEqualTo("admin@damulab.kz");
    }

    @Test
    void blankModelDoesNotPartiallyChangeRoutes() throws Exception {
        mockMvc.perform(post("/admin/settings/ai")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("questionsProvider", "DEEPSEEK")
                        .param("questionsModel", "")
                        .param("lecturesProvider", "OPENAI")
                        .param("lecturesModel", "gpt-5.6"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/ai-settings"))
                .andExpect(model().attributeHasFieldErrors("aiSettingsForm", "questionsModel"));

        assertThat(settings.resolve(AiUsageType.QUESTIONS).provider()).isEqualTo(AiProviderCode.STUB);
        assertThat(settings.resolve(AiUsageType.LECTURES).provider()).isEqualTo(AiProviderCode.STUB);
    }

    private void update(AiUsageType usageType, AiProviderCode provider, String model) {
        AiRuntimeSetting setting = repository.findById(usageType).orElseThrow();
        setting.update(provider, model, "test");
        repository.save(setting);
    }
}
