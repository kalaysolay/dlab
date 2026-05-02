package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    void manifestAndServiceWorkerAreServed() throws Exception {
        mockMvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"display\": \"standalone\"")))
                .andExpect(content().string(containsString("/icons/damulab-icon.svg")));

        mockMvc.perform(get("/service-worker.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("damulab-shell-v1")))
                .andExpect(content().string(containsString("self.addEventListener('fetch'")));
    }
}
