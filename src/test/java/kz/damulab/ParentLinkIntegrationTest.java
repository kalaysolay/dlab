package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.auth.RegisterForm;
import kz.damulab.auth.RegistrationService;
import kz.damulab.parentlink.LinkCode;
import kz.damulab.parentlink.LinkCodeRepository;
import kz.damulab.parentlink.ParentLinkService;
import kz.damulab.users.RoleCode;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParentLinkService parentLinkService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private StudentProfileRepository studentProfiles;

    @Autowired
    private LinkCodeRepository linkCodes;

    @Test
    void parentCanCreateChildAndSeeChildCard() throws Exception {
        String email = "child-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/parent/children")
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "New Child",
                                  "email": "%s",
                                  "gradeNo": 3,
                                  "preferredLanguage": "kk"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("New Child"))
                .andExpect(jsonPath("$.gradeNo").value(3))
                .andExpect(jsonPath("$.preferredLanguage").value("kk"));

        mockMvc.perform(get("/api/parent/children")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem(email)));
    }

    @Test
    void parentSeesOnlyLinkedChildren() throws Exception {
        String childEmail = "private-child-" + UUID.randomUUID() + "@example.test";
        Long childId = parentLinkService.createChild("parent@damulab.kz", childForm("Private Child", childEmail)).studentId();
        String otherParent = "other-parent-" + UUID.randomUUID() + "@example.test";
        registerParent(otherParent);

        mockMvc.perform(get("/api/parent/children/{studentId}", childId)
                        .with(user(otherParent).roles("PARENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("child_not_linked_to_parent"));
    }

    @Test
    void linkCodeCanBeUsedOnlyOnce() throws Exception {
        String code = createSeedStudentCode();

        mockMvc.perform(post("/api/parent/link-codes/{code}/attach", code)
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@damulab.kz"));

        mockMvc.perform(post("/api/parent/link-codes/{code}/attach", code)
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("link_code_not_available"));
    }

    @Test
    void linkCodeResponseContainsQrSvg() throws Exception {
        mockMvc.perform(post("/api/student/link-codes")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.qrSvg").value(containsString("<svg")))
                .andExpect(jsonPath("$.qrSvg").value(containsString("QR код привязки")));
    }

    @Test
    void expiredLinkCodeCannotBeUsed() throws Exception {
        StudentProfile student = studentProfiles.findByUserEmailIgnoreCase("student@damulab.kz").orElseThrow();
        String code = "EX" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        linkCodes.save(new LinkCode(student, code, OffsetDateTime.now().minusMinutes(1)));

        mockMvc.perform(post("/api/parent/link-codes/{code}/attach", code)
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("link_code_not_available"));
    }

    @Test
    void parentDashboardUsesServerRenderedChildForms() throws Exception {
        mockMvc.perform(get("/parent")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("parent/dashboard"))
                .andExpect(content().string(containsString("Добавить ребенка")))
                .andExpect(content().string(containsString("Привязать по коду")));
    }

    @Test
    void parentCanCreateChildFromServerRenderedForm() throws Exception {
        mockMvc.perform(post("/parent/children")
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf())
                        .param("fullName", "Form Child")
                        .param("email", "form-child-" + UUID.randomUUID() + "@example.test")
                        .param("gradeNo", "2")
                        .param("preferredLanguage", "ru"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/parent?childCreated=*"));
    }

    @Test
    void childCardCanGenerateQrLinkCode() throws Exception {
        Long childId = parentLinkService.createChild(
                "parent@damulab.kz",
                childForm("QR Child", "qr-child-" + UUID.randomUUID() + "@example.test")
        ).studentId();

        mockMvc.perform(post("/parent/children/{studentId}/link-code", childId)
                        .with(user("parent@damulab.kz").roles("PARENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/parent/children/*"))
                .andExpect(flash().attributeExists("linkCode"));
    }

    private String createSeedStudentCode() throws Exception {
        String response = mockMvc.perform(post("/api/student/link-codes")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = response.indexOf(":\"") + 2;
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }

    private CreateChildFormAdapter childForm(String fullName, String email) {
        return new CreateChildFormAdapter(fullName, email);
    }

    private void registerParent(String email) {
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setPassword("password");
        form.setFullName("Other Parent");
        form.setRole(RoleCode.PARENT);
        registrationService.register(form);
    }

    private static class CreateChildFormAdapter extends kz.damulab.parentlink.CreateChildForm {

        CreateChildFormAdapter(String fullName, String email) {
            setFullName(fullName);
            setEmail(email);
            setGradeNo(4);
            setPreferredLanguage("ru");
        }
    }
}
