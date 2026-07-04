package kz.damulab.passkeys;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {

    List<PasskeyCredential> findAllByUserEmailIgnoreCase(String email);

    List<PasskeyCredential> findAllByCredentialId(String credentialId);

    Optional<PasskeyCredential> findByCredentialIdAndUserWebAuthnUserHandle(String credentialId, byte[] userHandle);

    boolean existsByUserEmailIgnoreCase(String email);
}
