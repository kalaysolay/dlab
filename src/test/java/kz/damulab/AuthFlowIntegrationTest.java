package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import kz.damulab.users.AppUserRepository;
import kz.damulab.users.ParentProfileRepository;
import kz.damulab.users.StudentProfileRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private StudentProfileRepository studentProfiles;

    @Autowired
    private ParentProfileRepository parentProfiles;

    @Test
    void apiRegistersStudentWithoutEmailConfirmation() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.student@example.com",
                                  "password": "password123",
                                  "fullName": "New Student",
                                  "role": "STUDENT",
                                  "gradeNo": 4,
                                  "preferredLanguage": "kk"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.student@example.com"))
                .andExpect(jsonPath("$.roles", hasItem("STUDENT")));

        org.assertj.core.api.Assertions.assertThat(users.existsByEmailIgnoreCase("NEW.STUDENT@example.com")).isTrue();
        org.assertj.core.api.Assertions.assertThat(studentProfiles.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void apiRegistersParentProfile() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.parent@example.com",
                                  "password": "password123",
                                  "fullName": "New Parent",
                                  "phone": "+77001112233",
                                  "role": "PARENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItem("PARENT")));

        org.assertj.core.api.Assertions.assertThat(parentProfiles.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void apiRejectsDuplicateEmail() throws Exception {
        String payload = """
                {
                  "email": "duplicate@example.com",
                  "password": "password123",
                  "fullName": "Duplicate User",
                  "role": "STUDENT"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_email"));
    }

    @Test
    void apiLoginCreatesSessionForMeEndpoint() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@damulab.kz",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("STUDENT")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@damulab.kz"));
    }

    @Test
    void formLoginUsesDatabaseUsers() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin@damulab.kz")
                .param("password", "password")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void loginPageRedirectsAuthenticatedUsersToDashboard() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void registerPageShowsValidationErrors() throws Exception {
        mockMvc.perform(post("/register")
                        .param("email", "bad-email")
                        .param("password", "short")
                        .param("fullName", "")
                        .param("role", "STUDENT")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("Регистрация")));
    }
}
