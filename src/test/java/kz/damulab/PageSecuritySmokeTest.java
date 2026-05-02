package kz.damulab;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageSecuritySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Damulab.kz")));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
    }

    @Test
    void headerShowsLogoutInsteadOfLoginForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/student").with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Выйти")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/login\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Регистрация"))));
    }

    @Test
    void adminRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void adminAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/admin").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    @Test
    void adminRejectsStudentRole() throws Exception {
        mockMvc.perform(get("/admin").with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }
}
