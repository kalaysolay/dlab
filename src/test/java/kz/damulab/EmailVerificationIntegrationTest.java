package kz.damulab;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.auth.VerificationEmailSender;
import kz.damulab.auth.EmailDeliveryAttemptRepository;
import kz.damulab.auth.EmailDeliveryAttemptStatus;
import kz.damulab.auth.EmailDeliveryException;
import kz.damulab.auth.EmailSendResult;
import kz.damulab.auth.EmailVerificationStatus;
import kz.damulab.auth.EmailVerificationTokenRepository;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

/** Проверяет полный путь: регистрация → заблокированный вход → ссылка из письма → вход. */
@SpringBootTest(properties = {
        "damulab.email-verification.enabled=true",
        "damulab.demo-users.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private EmailVerificationTokenRepository tokens;

    @Autowired
    private EmailDeliveryAttemptRepository deliveryAttempts;

    @MockBean
    private VerificationEmailSender emailSender;

    @Test
    void registeredUserCanLoginOnlyAfterFollowingVerificationLink() throws Exception {
        String email = "verification@example.com";
        when(emailSender.sendVerificationEmail(any(AppUser.class), any(String.class)))
                .thenReturn(new EmailSendResult(true, email, "1a04a292159000814c", 200));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "verification@example.com",
                                  "password": "password123",
                                  "fullName": "Verification User",
                                  "role": "STUDENT",
                                  "gradeNo": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.emailDeliveryStatus").value("ACCEPTED"));

        AppUser pendingUser = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(pendingUser.isEnabled()).isFalse();
        var verification = tokens.findByUserId(pendingUser.getId()).orElseThrow();
        var attempts = deliveryAttempts.findAllByVerificationIdOrderByAttemptedAtAsc(verification.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getStatus()).isEqualTo(EmailDeliveryAttemptStatus.ACCEPTED);
        assertThat(attempts.getFirst().getProviderResult()).isTrue();
        assertThat(attempts.getFirst().getRecipientEmail()).isEqualTo(email);
        assertThat(attempts.getFirst().getProviderMessageId()).isEqualTo("1a04a292159000814c");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"verification@example.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(any(AppUser.class), tokenCaptor.capture());

        mockMvc.perform(get("/activate-account").param("token", tokenCaptor.getValue()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(redirectedUrl("/login?verified=true"));

        AppUser verifiedUser = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verifiedUser.isEnabled()).isTrue();
        assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();
        var verifiedToken = tokens.findByUserId(verifiedUser.getId()).orElseThrow();
        assertThat(verifiedToken.getStatus()).isEqualTo(EmailVerificationStatus.VERIFIED);
        assertThat(verifiedToken.getActivatedAt()).isNotNull();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"verification@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk());

        // Токен действительно одноразовый: повторный переход больше не подтверждается.
        mockMvc.perform(get("/activate-account").param("token", tokenCaptor.getValue()))
                .andExpect(redirectedUrl("/login?verificationError=true"));
    }

    @Test
    void failedDeliveryDoesNotRollbackAccountAndIsStoredAsNormalizedAttempt() throws Exception {
        String email = "delivery-failure@example.com";
        when(emailSender.sendVerificationEmail(any(AppUser.class), any(String.class)))
                .thenThrow(new EmailDeliveryException(
                        EmailDeliveryAttemptStatus.NETWORK_ERROR,
                        null,
                        "PROVIDER_UNAVAILABLE",
                        "SMTP.BZ недоступен"
                ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "delivery-failure@example.com",
                                  "password": "password123",
                                  "fullName": "Delivery Failure",
                                  "role": "STUDENT",
                                  "gradeNo": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.emailDeliveryStatus").value("NETWORK_ERROR"));

        AppUser pendingUser = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(pendingUser.isEnabled()).isFalse();
        var verification = tokens.findByUserId(pendingUser.getId()).orElseThrow();
        var attempt = deliveryAttempts.findAllByVerificationIdOrderByAttemptedAtAsc(verification.getId())
                .getFirst();
        assertThat(attempt.getStatus()).isEqualTo(EmailDeliveryAttemptStatus.NETWORK_ERROR);
        assertThat(attempt.getProviderResult()).isNull();
        assertThat(attempt.getProviderMessageId()).isNull();
        assertThat(attempt.getErrorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void immediateResendIsSilentlyRateLimitedInDatabase() throws Exception {
        String email = "rate-limit@example.com";
        when(emailSender.sendVerificationEmail(any(AppUser.class), any(String.class)))
                .thenReturn(new EmailSendResult(true, email, "initial-message", 200));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "rate-limit@example.com",
                                  "password": "password123",
                                  "fullName": "Rate Limit",
                                  "role": "STUDENT",
                                  "gradeNo": 4
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/verification-email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rate-limit@example.com\"}"))
                .andExpect(status().isOk());

        verify(emailSender, times(1)).sendVerificationEmail(any(AppUser.class), any(String.class));
        AppUser pendingUser = users.findByEmailIgnoreCase(email).orElseThrow();
        var verification = tokens.findByUserId(pendingUser.getId()).orElseThrow();
        assertThat(deliveryAttempts.findAllByVerificationIdOrderByAttemptedAtAsc(verification.getId()))
                .hasSize(1);
    }
}
