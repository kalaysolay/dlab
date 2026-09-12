package kz.damulab.passkeys;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Optional;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttachment;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выполняет серверную часть WebAuthn: выдаёт challenge, проверяет ответ системной биометрии
 * и хранит только публичный ключ. Отпечаток и другой биометрический материал на сервер не передаются.
 */
@Service
public class PasskeyService {

    private static final int USER_HANDLE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RelyingParty relyingParty;
    private final AppUserRepository users;
    private final PasskeyCredentialRepository credentials;

    public PasskeyService(
            RelyingParty relyingParty,
            AppUserRepository users,
            PasskeyCredentialRepository credentials
    ) {
        this.relyingParty = relyingParty;
        this.users = users;
        this.credentials = credentials;
    }

    /** Создаёт одноразовые параметры регистрации встроенного passkey для вошедшего пользователя. */
    @Transactional
    public PublicKeyCredentialCreationOptions startRegistration(String username) {
        AppUser user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new PasskeyException("User not found"));
        byte[] userHandle = ensureUserHandle(user);

        return relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(user.getEmail())
                        .displayName(user.getFullName())
                        .id(new ByteArray(userHandle))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        // Настройка открывается из PWA именно для биометрии этого телефона/компьютера.
                        // PLATFORM убирает выбор внешнего USB/NFC-ключа и оставляет системный
                        // механизм устройства: отпечаток, Face ID/Windows Hello или резервный PIN.
                        .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                        // REQUIRED даёт настоящий discoverable passkey: на странице входа не нужно
                        // заранее вводить email, устройство само возвращает связанный userHandle.
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .build());
    }

    /** Проверяет attestation против сохранённого challenge/origin/RP ID и сохраняет публичный ключ. */
    @Transactional
    public void finishRegistration(String username, String requestJson, String credentialJson) {
        AppUser user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new PasskeyException("User not found"));
        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> response =
                    PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
            RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(request)
                    .response(response)
                    .build());
            PasskeyCredential credential = new PasskeyCredential(
                    user,
                    result.getKeyId().getId().getBase64Url(),
                    result.getPublicKeyCose().getBytes(),
                    result.getSignatureCount(),
                    result.isDiscoverable().orElse(false),
                    result.isBackupEligible(),
                    result.isBackedUp()
            );
            credentials.save(credential);
        } catch (IOException | RegistrationFailedException | RuntimeException ex) {
            throw new PasskeyException("Passkey registration failed", ex);
        }
    }

    /**
     * Создаёт challenge входа. Пустой username запускает passwordless-поиск discoverable passkey,
     * а переданный email сохраняет совместимость со старыми недискаверируемыми ключами.
     */
    @Transactional(readOnly = true)
    public AssertionRequest startLogin(String username) {
        Optional<String> normalizedUsername = Optional.ofNullable(username)
                .map(String::trim)
                .filter(value -> !value.isBlank());
        if (normalizedUsername.isPresent()
                && !credentials.existsByUserEmailIgnoreCase(normalizedUsername.get())) {
            throw new PasskeyException("No passkey is registered for this user");
        }
        StartAssertionOptions.StartAssertionOptionsBuilder builder = StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED);
        normalizedUsername.ifPresent(builder::username);
        return relyingParty.startAssertion(builder.build());
    }

    /** Проверяет подпись authenticator и возвращает email владельца подтверждённого ключа. */
    @Transactional
    public String finishLogin(String requestJson, String credentialJson) {
        try {
            AssertionRequest request = AssertionRequest.fromJson(requestJson);
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> response =
                    PublicKeyCredential.parseAssertionResponseJson(credentialJson);
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(response)
                    .build());
            if (!result.isSuccess()) {
                throw new PasskeyException("Passkey authentication failed");
            }
            PasskeyCredential credential = credentials.findByCredentialIdAndUserWebAuthnUserHandle(
                            result.getCredentialId().getBase64Url(),
                            result.getUserHandle().getBytes()
                    )
                    .orElseThrow(() -> new PasskeyException("Passkey credential not found"));
            credential.markUsed(result.getSignatureCount(), result.isBackedUp());
            return result.getUsername();
        } catch (IOException | AssertionFailedException | RuntimeException ex) {
            throw new PasskeyException("Passkey authentication failed", ex);
        }
    }

    /** Показывает, есть ли у аккаунта хотя бы один сохранённый публичный WebAuthn-ключ. */
    @Transactional(readOnly = true)
    public boolean hasPasskey(String username) {
        return credentials.existsByUserEmailIgnoreCase(username);
    }

    private byte[] ensureUserHandle(AppUser user) {
        byte[] userHandle = user.getWebAuthnUserHandle();
        if (userHandle != null) {
            return userHandle;
        }
        byte[] generated = new byte[USER_HANDLE_BYTES];
        secureRandom.nextBytes(generated);
        user.setWebAuthnUserHandle(generated);
        users.save(user);
        return generated;
    }
}
