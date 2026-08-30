package kz.damulab.auth;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

import kz.damulab.users.AppUser;

/** Фиксирует контракт HTTP-запроса SMTP.BZ без обращения во внешнюю сеть. */
class SmtpBzVerificationEmailSenderTest {

    @Test
    void sendsExpectedAuthorizationHeaderAndJsonPayload() {
        EmailVerificationProperties properties = new EmailVerificationProperties();
        properties.setApiBaseUrl("https://api.smtp.bz/v1");
        properties.setApiKey("test-api-key");
        properties.setFrom("no-reply@damulab.kz");
        properties.setSenderName("Damulab");
        properties.setPublicBaseUrl("https://damulab.kz");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.smtp.bz/v1/smtp/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "test-api-key"))
                .andExpect(content().contentType(new MediaType("application", "json", StandardCharsets.UTF_8)))
                .andExpect(content().json("""
                        {
                          "name": "Damulab",
                          "from": "no-reply@damulab.kz",
                          "to": "student@example.com",
                          "subject": "Damulab аккаунтын белсендіру / Активация аккаунта Damulab"
                        }
                        """, false))
                .andExpect(content().string(containsString("Сәлеметсіз бе")))
                .andExpect(content().string(containsString("Аккаунтты белсендіру")))
                .andExpect(content().string(containsString("Активация аккаунта")))
                .andExpect(content().string(containsString("/activate-account?token=test_token-123")))
                .andRespond(withSuccess(
                        "{\"result\":true,\"to\":\"student@example.com\",\"messageid\":\"1a04a292159000814c\"}",
                        MediaType.APPLICATION_JSON
                ));

        SmtpBzVerificationEmailSender sender = new SmtpBzVerificationEmailSender(properties, builder);
        EmailSendResult result = sender.sendVerificationEmail(
                new AppUser("student@example.com", "hash", "Student <One>", null),
                "test_token-123"
        );

        assertThat(result.result()).isTrue();
        assertThat(result.recipientEmail()).isEqualTo("student@example.com");
        assertThat(result.providerMessageId()).isEqualTo("1a04a292159000814c");
        assertThat(result.httpStatus()).isEqualTo(200);
        server.verify();
    }
}
