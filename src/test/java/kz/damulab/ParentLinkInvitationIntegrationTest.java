package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import kz.damulab.auth.EmailSendResult;
import kz.damulab.auth.RegisterForm;
import kz.damulab.auth.RegistrationService;
import kz.damulab.parentlink.ParentLinkInvitationEmailSender;
import kz.damulab.parentlink.ParentLinkInvitationPageController;
import kz.damulab.users.RoleCode;

/** Проверяет приватность ответа и строгую привязку токена к приглашённому student_profile. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentLinkInvitationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @MockBean
    private ParentLinkInvitationEmailSender emailSender;

    @Test
    void responseDoesNotDiscloseChildAndOnlyInvitedStudentCanConfirm() throws Exception {
        String marker = UUID.randomUUID().toString();
        String parentEmail = "invite-parent-" + marker + "@example.test";
        String studentEmail = "invite-student-" + marker + "@example.test";
        String otherStudentEmail = "other-student-" + marker + "@example.test";
        register(parentEmail, "Private Parent", RoleCode.PARENT);
        register(studentEmail, "Имя Фамилия", RoleCode.STUDENT);
        register(otherStudentEmail, "Other Student", RoleCode.STUDENT);
        when(emailSender.sendInvitation(anyString(), anyString()))
                .thenAnswer(invocation -> new EmailSendResult(
                        true, invocation.getArgument(0), "invite-message", 200
                ));

        mockMvc.perform(post("/api/parent/child-invitations")
                        .with(user(parentEmail).roles("PARENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-" + marker + "@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"status\":\"accepted\"}"));

        mockMvc.perform(post("/api/parent/child-invitations")
                        .with(user(parentEmail).roles("PARENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-" + marker + "@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"status\":\"accepted\"}"));

        mockMvc.perform(post("/api/parent/child-invitations")
                        .with(user(parentEmail).roles("PARENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(content().string(not(containsString("Имя Фамилия"))))
                .andExpect(content().string(not(containsString(studentEmail))));

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendInvitation(org.mockito.ArgumentMatchers.eq(studentEmail), token.capture());
        org.assertj.core.api.Assertions.assertThat(token.getValue()).matches("[A-Za-z0-9_-]{43}");

        mockMvc.perform(post("/api/student/parent-link-invitations/confirm")
                        .with(user(otherStudentEmail).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("invitation_student_mismatch"));

        mockMvc.perform(post("/api/student/parent-link-invitations/confirm")
                        .with(user(studentEmail).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/student/parent-link-invitations/confirm")
                        .with(user(studentEmail).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invitation_not_available"));
    }

    @Test
    void emailLinkIsScrubbedFromUrlAndPageShowsNoPersonalData() throws Exception {
        String marker = UUID.randomUUID().toString();
        String parentEmail = "page-parent-" + marker + "@example.test";
        String studentEmail = "page-student-" + marker + "@example.test";
        register(parentEmail, "Parent Secret Name", RoleCode.PARENT);
        register(studentEmail, "Child Secret Name", RoleCode.STUDENT);
        when(emailSender.sendInvitation(anyString(), anyString()))
                .thenAnswer(invocation -> new EmailSendResult(true, invocation.getArgument(0), "page-message", 200));

        mockMvc.perform(post("/api/parent/child-invitations")
                        .with(user(parentEmail).roles("PARENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendInvitation(org.mockito.ArgumentMatchers.eq(studentEmail), token.capture());

        MvcResult opened = mockMvc.perform(get("/parent-link-invitations/confirm")
                        .param("token", token.getValue()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/parent-link-invitations/confirm"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) opened.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(
                session.getAttribute(ParentLinkInvitationPageController.SESSION_TOKEN_ATTRIBUTE)
        ).isEqualTo(token.getValue());

        mockMvc.perform(get("/parent-link-invitations/confirm").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("parentlink/confirm-invitation"))
                .andExpect(content().string(containsString("Войти")))
                .andExpect(content().string(not(containsString("Parent Secret Name"))))
                .andExpect(content().string(not(containsString("Child Secret Name"))))
                .andExpect(content().string(not(containsString(studentEmail))))
                .andExpect(content().string(not(containsString(token.getValue()))));

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", studentEmail)
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/parent-link-invitations/confirm"));

        mockMvc.perform(get("/parent-link-invitations/confirm").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Подтвердить связь")))
                .andExpect(content().string(not(containsString("Parent Secret Name"))))
                .andExpect(content().string(not(containsString("Child Secret Name"))));

        mockMvc.perform(post("/student/parent-link-invitations/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/parent-link-invitations/confirm?confirmed=true"));

    }

    private void register(String email, String fullName, RoleCode role) {
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setPassword("password123");
        form.setFullName(fullName);
        form.setRole(role);
        if (role == RoleCode.STUDENT) {
            form.setGradeNo(4);
        }
        registrationService.register(form);
    }
}
