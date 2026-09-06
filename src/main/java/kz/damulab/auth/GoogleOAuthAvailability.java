package kz.damulab.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/** Показывает Google UI только когда для registrationId=google реально заданы client credentials. */
@Component
public class GoogleOAuthAvailability {

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public GoogleOAuthAvailability(ObjectProvider<ClientRegistrationRepository> registrations) {
        this.registrations = registrations;
    }

    public boolean isEnabled() {
        ClientRegistrationRepository repository = registrations.getIfAvailable();
        return repository != null && repository.findByRegistrationId("google") != null;
    }
}
