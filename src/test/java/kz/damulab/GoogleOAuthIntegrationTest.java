package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Проверяет условное включение Google client, UI и стандартный OIDC authorization redirect. */
@SpringBootTest(properties = {
        "damulab.auth.google.enabled=true",
        "damulab.auth.google.client-id=test-google-client-id",
        "damulab.auth.google.client-secret=test-google-client-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleOAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageShowsGoogleActionWhenConfigured() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(containsString("Продолжить с Google")));
    }

    @Test
    void authorizationEndpointRedirectsToGoogle() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/")));
    }

    @Test
    void profileCompletionWithoutPendingIdentityReturnsToLogin() throws Exception {
        mockMvc.perform(get("/register/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?oauthExpired"));
    }
}
