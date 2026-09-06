package kz.damulab;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Проверяет права и HTTP-контракт загрузки встроенных изображений лекций. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "damulab.lectures.images.storage-dir=build/test-lecture-images")
class LectureImageIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void adminCanUploadLectureImage() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", png);

        mockMvc.perform(multipart("/api/admin/lecture-images")
                        .file(file)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/files/lecture-images/")))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void nonAdminCannotUploadLectureImage() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

        mockMvc.perform(multipart("/api/admin/lecture-images")
                        .file(new MockMultipartFile("file", "screenshot.png", "image/png", png))
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
