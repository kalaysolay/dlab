package kz.damulab.parentlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import kz.damulab.auth.EmailSendResult;
import kz.damulab.auth.EmailVerificationProperties;

/** Фиксирует минимальный набор персональных данных в SMTP-запросе приглашения. */
class SmtpBzParentLinkInvitationEmailSenderTest {

    @Test
    void sendsOnlyRecipientAndSecretLinkWithoutProfileNames() {
        EmailVerificationProperties properties = new EmailVerificationProperties();
        properties.setApiBaseUrl("https://api.smtp.bz/v1");
        properties.setApiKey("test-api-key");
        properties.setFrom("no-reply@damulab.kz");
        properties.setSenderName("Damulab");
        properties.setPublicBaseUrl("https://damulab.kz");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(containsString("https://api.smtp.bz/v1/smtp/send")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "test-api-key"))
                .andExpect(content().contentType(new MediaType("application", "json", StandardCharsets.UTF_8)))
                .andExpect(content().string(containsString("student@example.com")))
                .andExpect(content().string(containsString("/parent-link-invitations/confirm?token=")))
                .andExpect(content().string(containsString("secret_token")))
                .andExpect(content().string(not(containsString("Child Secret Name"))))
                .andExpect(content().string(not(containsString("Parent Secret Name"))))
                .andRespond(withSuccess(
                        "{\"result\":true,\"to\":\"student@example.com\",\"messageid\":\"message-1\"}",
                        MediaType.APPLICATION_JSON
                ));

        EmailSendResult result = new SmtpBzParentLinkInvitationEmailSender(properties, builder)
                .sendInvitation("student@example.com", "secret_token");

        assertThat(result.result()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("message-1");
        server.verify();
    }
}
