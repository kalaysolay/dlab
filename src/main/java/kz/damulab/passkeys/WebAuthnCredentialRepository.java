package kz.damulab.passkeys;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import com.yubico.webauthn.data.exception.Base64UrlException;
import kz.damulab.users.AppUserRepository;
import org.springframework.stereotype.Component;

@Component
public class WebAuthnCredentialRepository implements CredentialRepository {

    private final AppUserRepository users;
    private final PasskeyCredentialRepository credentials;

    public WebAuthnCredentialRepository(AppUserRepository users, PasskeyCredentialRepository credentials) {
        this.users = users;
        this.credentials = credentials;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return credentials.findAllByUserEmailIgnoreCase(username).stream()
                .map(credential -> PublicKeyCredentialDescriptor.builder()
                        .id(byteArrayFromBase64Url(credential.getCredentialId()))
                        .type(PublicKeyCredentialType.PUBLIC_KEY)
                        .build())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return users.findByEmailIgnoreCase(username)
                .map(user -> user.getWebAuthnUserHandle())
                .filter(Objects::nonNull)
                .map(ByteArray::new);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return users.findByWebAuthnUserHandle(userHandle.getBytes())
                .map(user -> user.getEmail());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentials.findByCredentialIdAndUserWebAuthnUserHandle(
                        credentialId.getBase64Url(),
                        userHandle.getBytes()
                )
                .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentials.findAllByCredentialId(credentialId.getBase64Url()).stream()
                .map(this::toRegisteredCredential)
                .collect(Collectors.toUnmodifiableSet());
    }

    private RegisteredCredential toRegisteredCredential(PasskeyCredential credential) {
        return RegisteredCredential.builder()
                .credentialId(byteArrayFromBase64Url(credential.getCredentialId()))
                .userHandle(new ByteArray(credential.getUser().getWebAuthnUserHandle()))
                .publicKeyCose(new ByteArray(credential.getPublicKeyCose()))
                .signatureCount(credential.getSignatureCount())
                .build();
    }

    private ByteArray byteArrayFromBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Base64UrlException ex) {
            throw new IllegalStateException("Invalid stored passkey credential id", ex);
        }
    }
}
