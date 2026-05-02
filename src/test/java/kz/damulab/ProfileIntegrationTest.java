package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void studentCanReadAndUpdateOwnProfile() throws Exception {
        mockMvc.perform(get("/api/student/profile")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@damulab.kz"));

        mockMvc.perform(patch("/api/student/profile")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Student",
                                  "phone": "+77000000001",
                                  "gradeNo": 5,
                                  "preferredLanguage": "kk"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Student"))
                .andExpect(jsonPath("$.gradeNo").value(5))
                .andExpect(jsonPath("$.preferredLanguage").value("kk"));
    }

    @Test
    void parentCanReadAndUpdateOwnProfile() throws Exception {
        mockMvc.perform(get("/api/parent/profile")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("parent@damulab.kz"));

        mockMvc.perform(patch("/api/parent/profile")
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Parent",
                                  "phone": "+77000000002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Parent"))
                .andExpect(jsonPath("$.phone").value("+77000000002"));
    }

    @Test
    void parentCannotAccessStudentProfileApi() throws Exception {
        mockMvc.perform(get("/api/student/profile")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentProfilePageUsesServerRenderedForm() throws Exception {
        mockMvc.perform(get("/student/profile")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/profile"))
                .andExpect(content().string(containsString("Профиль ученика")));
    }

    @Test
    void studentCanSaveLanguageFromHeaderSelector() throws Exception {
        mockMvc.perform(post("/student/profile/language")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .header("Referer", "http://localhost/student/profile")
                        .param("preferredLanguage", "kk"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/profile?lang=kk"));

        mockMvc.perform(get("/api/student/profile")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("kk"));
    }

    @Test
    void parentProfilePageCanSaveForm() throws Exception {
        mockMvc.perform(post("/parent/profile")
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf())
                        .param("fullName", "Parent From Form")
                        .param("phone", "+77000000003"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/parent/profile?saved=true"));
    }
}
