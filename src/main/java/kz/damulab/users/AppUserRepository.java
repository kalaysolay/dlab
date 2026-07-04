package kz.damulab.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByWebAuthnUserHandle(byte[] webAuthnUserHandle);

    boolean existsByEmailIgnoreCase(String email);
}
