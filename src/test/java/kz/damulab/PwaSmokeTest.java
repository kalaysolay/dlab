package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Smoke-тесты PWA-инфраструктуры: manifest, service worker, offline-страница, push API.
 * Проверяют, что ресурсы публично доступны и содержат ожидаемые маркеры.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PwaSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageLinksPwaManifestAndRegistrationScript() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/manifest.webmanifest")))
                .andExpect(content().string(containsString("/js/pwa.js")));
    }

    @Test
    void manifestContainsPngIconsAndStandaloneDisplay() throws Exception {
        mockMvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                // PNG-иконки обязательны для installability (Chrome не показывает «Установить» без них)
                .andExpect(content().string(containsString("icon-192.png")))
                .andExpect(content().string(containsString("icon-512.png")))
                .andExpect(content().string(containsString("icon-maskable-512.png")))
                .andExpect(content().string(containsString("\"display\": \"standalone\"")))
                .andExpect(content().string(containsString("\"theme_color\": \"#1668dc\"")));
    }

    @Test
    void serviceWorkerIsServedAndContainsV2Markers() throws Exception {
        mockMvc.perform(get("/service-worker.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("damulab-shell-v2")))
                // Офлайн-fallback должен быть прописан в SW
                .andExpect(content().string(containsString("/offline")))
                .andExpect(content().string(containsString("self.addEventListener('fetch'")))
                .andExpect(content().string(containsString("self.addEventListener('push'")))
                .andExpect(content().string(containsString("self.addEventListener('notificationclick'")));
    }

    @Test
    void offlinePageIsPubliclyAccessible() throws Exception {
        // /offline кэшируется SW и должна отдаваться без авторизации
        mockMvc.perform(get("/offline"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Нет подключения к сети")));
    }

    @Test
    void pushSubscribeRequiresAuthentication() throws Exception {
        // Эндпоинт подписки должен быть закрыт без авторизации.
        // Spring Security с form login делает 302 redirect на /login для анонимных запросов.
        mockMvc.perform(post("/api/push/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://example.com/push/123\","
                                + "\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"}}"))
                .andExpect(status().is3xxRedirection());
    }
}
