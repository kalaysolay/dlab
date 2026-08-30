package kz.damulab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "damulab.questions.images.storage-dir=build/test-question-images")
class QuestionImageIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void adminCanUploadQuestionImage() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "illustration.png", "image/png", png);

        mockMvc.perform(multipart("/api/admin/question-images")
                        .file(file)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/files/question-images/")))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void nonAdminCannotUploadQuestionImage() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

        mockMvc.perform(multipart("/api/admin/question-images")
                        .file(new MockMultipartFile("file", "illustration.png", "image/png", png))
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
