package kz.damulab.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.Assert;

/** Создаёт Google OIDC client только при явном включении, чтобы пустые секреты не ломали запуск. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleOAuthProperties.class)
@ConditionalOnProperty(name = "damulab.auth.google.enabled", havingValue = "true")
public class GoogleOAuthClientConfig {

    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(GoogleOAuthProperties properties) {
        Assert.hasText(properties.getClientId(), "GOOGLE_CLIENT_ID is required when Google OAuth is enabled");
        Assert.hasText(properties.getClientSecret(), "GOOGLE_CLIENT_SECRET is required when Google OAuth is enabled");

        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .scope("openid", "profile", "email")
                .clientName("Google")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
