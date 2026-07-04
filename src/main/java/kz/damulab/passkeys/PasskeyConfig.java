package kz.damulab.passkeys;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasskeyConfig {

    @Bean
    RelyingParty relyingParty(PasskeyProperties properties, CredentialRepository credentialRepository) {
        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id(properties.getRpId())
                .name(properties.getRpName())
                .build();

        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentialRepository)
                .origins(properties.getAllowedOrigins())
                .build();
    }
}
